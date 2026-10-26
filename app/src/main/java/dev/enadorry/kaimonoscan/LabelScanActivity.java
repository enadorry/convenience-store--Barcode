package dev.enadorry.kaimonoscan;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Bundle;
import android.util.Size;
import android.view.*;
import android.widget.*;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.camera.core.*;
import androidx.camera.lifecycle.ProcessCameraProvider;
import androidx.camera.view.PreviewView;
import androidx.core.content.ContextCompat;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.color.DynamicColors;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.mlkit.vision.common.InputImage;
import com.google.mlkit.vision.text.*;
import com.google.mlkit.vision.text.japanese.JapaneseTextRecognizerOptions;
import java.io.File;
import java.util.concurrent.*;

/** Dedicated label camera. Images remain in private cache and are deleted after recognition. */
public final class LabelScanActivity extends AppCompatActivity {
    private ImageCapture capture;
    private Camera camera;
    private ProcessCameraProvider provider;
    private TextRecognizer recognizer;
    private final ExecutorService imageWorker=Executors.newSingleThreadExecutor();
    private MaterialButton shutter,torch;
    private TextView status;
    private File pending;
    private boolean processing,light,closingCamera;
    private final android.os.Handler closeHandler=new android.os.Handler(android.os.Looper.getMainLooper());
    private int dp(float n){return (int)(n*getResources().getDisplayMetrics().density+.5f);}
    private int color(int attr,int fallback){android.util.TypedValue v=new android.util.TypedValue();return getTheme().resolveAttribute(attr,v,true)?(v.resourceId==0?v.data:getColor(v.resourceId)):getColor(fallback);}
    private MaterialButton button(String text,Runnable action){MaterialButton b=new MaterialButton(this);b.setText(text);b.setMinHeight(dp(52));b.setCornerRadius(dp(28));b.setInsetTop(0);b.setInsetBottom(0);b.setOnClickListener(v->action.run());return b;}
    @Override public void onCreate(Bundle state){
        DynamicColors.applyToActivityIfAvailable(this);super.onCreate(state);
        if(ContextCompat.checkSelfPermission(this,Manifest.permission.CAMERA)!=PackageManager.PERMISSION_GRANTED){finish();return;}
        File[] old=getCacheDir().listFiles((dir,name)->name.startsWith("price-label-")&&name.endsWith(".jpg"));if(old!=null)for(File file:old)file.delete();
        recognizer=TextRecognition.getClient(new JapaneseTextRecognizerOptions.Builder().build());
        LinearLayout root=new LinearLayout(this);root.setOrientation(LinearLayout.VERTICAL);root.setPadding(dp(20),dp(12),dp(20),dp(12));root.setBackgroundColor(color(com.google.android.material.R.attr.colorSurface,R.color.ui_canvas));
        root.setOnApplyWindowInsetsListener((v,i)->{if(android.os.Build.VERSION.SDK_INT>=30){android.graphics.Insets b=i.getInsets(WindowInsets.Type.systemBars());root.setPadding(dp(20)+b.left,dp(12)+b.top,dp(20)+b.right,dp(12)+b.bottom);}else root.setPadding(dp(20)+i.getSystemWindowInsetLeft(),dp(12)+i.getSystemWindowInsetTop(),dp(20)+i.getSystemWindowInsetRight(),dp(12)+i.getSystemWindowInsetBottom());return i;});
        TextView title=new TextView(this);title.setText("値札を読む");title.setTextSize(28);title.setTextColor(color(com.google.android.material.R.attr.colorOnSurface,R.color.ui_ink));title.setPadding(0,0,0,dp(12));root.addView(title);
        PreviewView preview=new PreviewView(this);preview.setScaleType(PreviewView.ScaleType.FIT_CENTER);preview.setImplementationMode(PreviewView.ImplementationMode.COMPATIBLE);
        GradientDrawable shape=new GradientDrawable();shape.setColor(Color.BLACK);shape.setCornerRadius(dp(28));preview.setBackground(shape);preview.setClipToOutline(true);root.addView(preview,new LinearLayout.LayoutParams(-1,0,1));
        status=new TextView(this);status.setText("1枚の値札を大きく写してください");status.setTextSize(14);status.setTextColor(color(com.google.android.material.R.attr.colorOnSurfaceVariant,R.color.ui_muted));status.setGravity(Gravity.CENTER);status.setPadding(0,dp(12),0,dp(12));root.addView(status);
        LinearLayout actions=new LinearLayout(this);actions.setOrientation(LinearLayout.HORIZONTAL);
        torch=button("ライトON",()->{if(camera!=null){light=!light;camera.getCameraControl().enableTorch(light);torch.setText(light?"ライトOFF":"ライトON");}});torch.setEnabled(false);actions.addView(torch,new LinearLayout.LayoutParams(0,-2,1));
        actions.addView(button("戻る",this::finish),new LinearLayout.LayoutParams(0,-2,1));root.addView(actions);
        shutter=button("撮影して読み取る",this::takeLabel);shutter.setEnabled(false);root.addView(shutter,new LinearLayout.LayoutParams(-1,dp(64)));setContentView(root);root.requestApplyInsets();
        ListenableFuture<ProcessCameraProvider> future=ProcessCameraProvider.getInstance(this);
        future.addListener(()->{
            if(closingCamera||isFinishing()||isDestroyed())return;
            try{
                provider=future.get();Preview live=new Preview.Builder().setTargetAspectRatio(AspectRatio.RATIO_4_3).build();live.setSurfaceProvider(preview.getSurfaceProvider());
                capture=new ImageCapture.Builder().setCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY).setTargetResolution(new Size(1600,1200)).build();
                camera=provider.bindToLifecycle(this,CameraSelector.DEFAULT_BACK_CAMERA,live,capture);shutter.setEnabled(true);torch.setEnabled(camera.getCameraInfo().hasFlashUnit());
                preview.setOnTouchListener((v,event)->{if(event.getAction()==android.view.MotionEvent.ACTION_UP&&camera!=null){MeteringPoint point=preview.getMeteringPointFactory().createPoint(event.getX(),event.getY());camera.getCameraControl().startFocusAndMetering(new FocusMeteringAction.Builder(point).build());v.performClick();}return true;});
            }catch(Exception e){status.setText("カメラを開始できません。戻って手入力してください。");}
        },ContextCompat.getMainExecutor(this));
    }
    private void takeLabel(){
        if(closingCamera||processing||capture==null)return;processing=true;shutter.setEnabled(false);torch.setEnabled(false);status.setText("読み取り中…");
        try{
            pending=File.createTempFile("price-label-",".jpg",getCacheDir());final File photo=pending;
            capture.takePicture(new ImageCapture.OutputFileOptions.Builder(photo).build(),ContextCompat.getMainExecutor(this),new ImageCapture.OnImageSavedCallback(){
                @Override public void onImageSaved(@NonNull ImageCapture.OutputFileResults output){
                    if(closingCamera||isFinishing()||isDestroyed()){photo.delete();return;}
                    imageWorker.execute(()->{
                        try{
                            InputImage image=InputImage.fromFilePath(LabelScanActivity.this,Uri.fromFile(photo));
                            recognizer.process(image).addOnSuccessListener(result->{
                                if(closingCamera||isFinishing()||isDestroyed())return;
                                if(result.getText().trim().isEmpty()){failed("文字が見つかりません。近づけて撮り直してください。");return;}
                                setResult(RESULT_OK,new Intent().putExtra("labelText",result.getText().substring(0,Math.min(16000,result.getText().length()))));finish();
                            }).addOnFailureListener(error->failed("読み取れませんでした。ピントと明るさを確認して撮り直してください。"))
                              .addOnCompleteListener(task->photo.delete());
                        }catch(Exception e){photo.delete();runOnUiThread(()->failed("画像を読み取れませんでした。撮り直してください。"));}
                    });
                }
                @Override public void onError(@NonNull ImageCaptureException e){photo.delete();failed("撮影できませんでした。もう一度試してください。");}
            });
        }catch(Exception e){failed("撮影の準備ができませんでした。戻って再試行してください。");}
    }
    private void failed(String message){if(closingCamera||isFinishing()||isDestroyed())return;processing=false;status.setText(message);shutter.setEnabled(true);torch.setEnabled(camera!=null&&camera.getCameraInfo().hasFlashUnit());}
    @Override public void finish(){
        if(closingCamera)return;
        closingCamera=true;
        if(provider==null||camera==null){super.finish();return;}
        if(shutter!=null)shutter.setEnabled(false);
        provider.unbindAll();
        camera.getCameraInfo().getCameraState().observe(this,state->{if(state.getType()==CameraState.Type.CLOSED)finishAfterRelease();});
        closeHandler.postDelayed(this::finishAfterRelease,1500);
    }
    private void finishAfterRelease(){closeHandler.removeCallbacksAndMessages(null);if(camera!=null)camera.getCameraInfo().getCameraState().removeObservers(this);if(!isFinishing()&&!isDestroyed())super.finish();}
    @Override protected void onDestroy(){closeHandler.removeCallbacksAndMessages(null);if(provider!=null)provider.unbindAll();if(recognizer!=null)recognizer.close();imageWorker.shutdownNow();if(pending!=null)pending.delete();super.onDestroy();}
}
