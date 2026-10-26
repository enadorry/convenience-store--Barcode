package dev.enadorry.kaimonoscan;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.materialswitch.MaterialSwitch;
import com.google.android.material.color.DynamicColors;
import com.google.android.material.checkbox.MaterialCheckBox;
import android.content.*;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.os.Vibrator;
import android.os.VibrationEffect;
import android.Manifest;
import android.content.pm.PackageManager;
import androidx.activity.OnBackPressedCallback;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.core.content.ContextCompat;
import com.google.zxing.BarcodeFormat;
import com.google.zxing.ResultPoint;
import android.text.InputType;
import android.view.*;
import android.widget.*;
import androidx.activity.ComponentActivity;
import androidx.activity.result.ActivityResultLauncher;
import com.journeyapps.barcodescanner.*;
import org.json.*;
import java.text.NumberFormat;
import java.util.*;
import java.util.concurrent.*;

public final class MainActivity extends AppCompatActivity {
    private int BG,CARD,INK,MUTED,ACCENT,LINE,LINK,PRIMARY_TEXT,CONTAINER,ON_CONTAINER;
    private boolean dark;
    private final ArrayList<Item> cart=new ArrayList<>();
    private final ExecutorService worker=Executors.newSingleThreadExecutor();
    private android.content.SharedPreferences prefs;
    private LinearLayout root, rows;
    private TextView total, count, status, walletSummary, scanWallet;
    private Button scanButton, codeButton;
    private boolean busy=false;
    private long requestId=0;
    private final ExecutorService autoWorker=Executors.newSingleThreadExecutor();
    private boolean autoSending;
    private long autoRetryAt;
    static final class Item {
        String jan, name, source; long price, cents; int qty;
        String sourceUrl="", region=""; long checkedAt;
        Item(String j,String n,long p,int q,String s) { jan=j;name=n;price=p;cents=p*100;qty=q;source=s; }
        JSONObject json() throws JSONException {
            return new JSONObject().put("jan",jan).put("name",name).put("price",price).put("qty",qty).put("source",source).put("cents",cents).put("sourceUrl",sourceUrl).put("region",region).put("checkedAt",checkedAt);
        }
        static Item read(JSONObject o) throws JSONException {
            Item i=new Item(o.getString("jan"),o.getString("name"),o.getLong("price"),o.getInt("qty"),o.optString("source","手入力"));
            i.cents=o.optLong("cents",i.price*100);i.sourceUrl=o.optString("sourceUrl","");i.region=o.optString("region","");i.checkedAt=o.optLong("checkedAt",0);
            Pricing.fractionalSubtotal(i.cents,i.qty); return i;
        }
    }
    private FrameLayout screen;
    private LinearLayout scanPanel;
    private DecoratedBarcodeView scanCamera;
    private TextView scanTotal,scanCount,scanNotice;
    private Button repeatButton,torchButton;
    private boolean scanning,activityResumed,scanProcessing,decoderRunning,torch;
    private int activeDialogs,quantityBefore;
    private String lastScan="";
    private ScanGate scanGate=new ScanGate();
    private final Handler scanHandler=new Handler(Looper.getMainLooper());
    private final OnBackPressedCallback scanBack=new OnBackPressedCallback(false) {
        @Override public void handleOnBackPressed(){stopContinuousScan();}
    };
    private final ActivityResultLauncher<String> cameraPermission=registerForActivityResult(new ActivityResultContracts.RequestPermission(),granted->{
        if(granted)startContinuousScan();
        else message("カメラ権限を許可すると連続スキャンできます。JAN入力も利用できます。");
    });
    // Every app dialog participates in pausing/resuming the same live camera session.
    // Register before show(), so transitions between nested dialogs cannot resume too early.
    private final class ScanDialogBuilder extends MaterialAlertDialogBuilder {
        ScanDialogBuilder(android.content.Context context){super(context);}
        @Override public MaterialAlertDialogBuilder setItems(CharSequence[] items,DialogInterface.OnClickListener listener){
            // A dialog's default single TextView makes adjacent multiline products hard to distinguish.
            // Use separate, wrapping fields; keep the standard ListView click and dismiss behavior.
            final CharSequence[] labels=items.clone();
            BaseAdapter adapter=new BaseAdapter(){
                @Override public int getCount(){return labels.length;}
                @Override public Object getItem(int position){return labels[position];}
                @Override public long getItemId(int position){return position;}
                @Override public View getView(int position,View recycled,ViewGroup parent){
                    LinearLayout row=new LinearLayout(MainActivity.this);row.setOrientation(LinearLayout.VERTICAL);
                    LinearLayout fields=new LinearLayout(MainActivity.this);fields.setOrientation(LinearLayout.VERTICAL);
                    fields.setPadding(dp(24),dp(14),dp(24),dp(14));
                    String[] lines=String.valueOf(labels[position]).split("\n",-1);
                    for(int n=0;n<lines.length;n++){
                        TextView field=text(lines[n],n==0?18:16,n==0?INK:lines[n].contains("円")?ACCENT:MUTED);
                        if(n==0)field.setTypeface(Typeface.create("sans-serif-medium",Typeface.NORMAL));
                        field.setSingleLine(false);
                        fields.addView(field,new LinearLayout.LayoutParams(-1,-2));
                    }
                    row.addView(fields,new LinearLayout.LayoutParams(-1,-2));
                    if(position<labels.length-1){
                        View divider=new View(MainActivity.this);divider.setBackgroundColor(LINE);
                        LinearLayout.LayoutParams line=new LinearLayout.LayoutParams(-1,dp(1));
                        line.setMargins(dp(24),0,dp(24),0);row.addView(divider,line);
                    }
                    return row;
                }
            };
            return super.setAdapter(adapter,listener);
        }
        @Override public MaterialAlertDialogBuilder setView(View view){
            if(view instanceof ScrollView)return super.setView(view);
            ScrollView scroll=new ScrollView(MainActivity.this);scroll.addView(view);return super.setView(scroll);
        }
        @Override public AlertDialog create(){
            AlertDialog d=super.create();activeDialogs++;pauseDecoder();
            d.setOnDismissListener(x->{activeDialogs=Math.max(0,activeDialogs-1);queueScanResume();});
            return d;
        }
    }
    @Override public void onCreate(Bundle saved) {
        DynamicColors.applyToActivityIfAvailable(this);
        super.onCreate(saved);
        dark=(getResources().getConfiguration().uiMode & android.content.res.Configuration.UI_MODE_NIGHT_MASK)==android.content.res.Configuration.UI_MODE_NIGHT_YES;
        BG=themeColor(com.google.android.material.R.attr.colorSurface,R.color.ui_canvas);
        CARD=themeColor(com.google.android.material.R.attr.colorSurfaceVariant,R.color.ui_surface);
        INK=themeColor(com.google.android.material.R.attr.colorOnSurface,R.color.ui_ink);
        MUTED=themeColor(com.google.android.material.R.attr.colorOnSurfaceVariant,R.color.ui_muted);
        ACCENT=themeColor(com.google.android.material.R.attr.colorPrimary,R.color.ui_accent);
        PRIMARY_TEXT=themeColor(com.google.android.material.R.attr.colorOnPrimary,R.color.ui_primary_text);
        CONTAINER=themeColor(com.google.android.material.R.attr.colorPrimaryContainer,R.color.ui_container);
        ON_CONTAINER=themeColor(com.google.android.material.R.attr.colorOnPrimaryContainer,R.color.ui_on_container);
        LINE=themeColor(com.google.android.material.R.attr.colorOutlineVariant,R.color.ui_line);LINK=ACCENT;
        getWindow().setStatusBarColor(BG);getWindow().setNavigationBarColor(BG);
        getWindow().getDecorView().setSystemUiVisibility(dark?0:View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR|(android.os.Build.VERSION.SDK_INT>=26?View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR:0));
        prefs=getSharedPreferences("shopping",MODE_PRIVATE);
        try {
            JSONArray a=new JSONArray(prefs.getString("cart","[]"));
            for(int i=0;i<a.length();i++) cart.add(Item.read(a.getJSONObject(i)));
        } catch(Exception e) { cart.clear(); }
        build(); refresh();
        getOnBackPressedDispatcher().addCallback(this,scanBack);
        if(saved!=null&&saved.getBoolean("continuousScanning",false)){
            startContinuousScan();lastScan=saved.getString("lastScan","");
            if(scanning)scanGate.restorePrevious(lastScan,SystemClock.elapsedRealtime());
        }
    }
    private int themeColor(int attr,int fallback){
        android.util.TypedValue v=new android.util.TypedValue();
        if(getTheme().resolveAttribute(attr,v,true))return v.resourceId!=0?getColor(v.resourceId):v.data;
        return getColor(fallback);
    }
    private int dp(float v) {return (int)(v*getResources().getDisplayMetrics().density+.5f);}
    private TextView text(String value,int size,int color) {
        TextView t=new TextView(this);t.setText(value);t.setTextSize(size);t.setTextColor(color);
        t.setTypeface(Typeface.create("sans-serif",Typeface.NORMAL));t.setLetterSpacing(0);
        t.setLineSpacing(0,1.15f);t.setPadding(0,dp(4),0,dp(4));return t;
    }
    private Typeface displayFace(){return Typeface.create("sans-serif",Typeface.NORMAL);}
    private TextView paragraph(String value,int color){return text(value,14,color);}
    private GradientDrawable shape(int color,int radius) {
        GradientDrawable d=new GradientDrawable();d.setColor(color);d.setCornerRadius(dp(radius));return d;
    }
    private LinearLayout vertical() {LinearLayout l=new LinearLayout(this);l.setOrientation(LinearLayout.VERTICAL);return l;}
    private LinearLayout horizontal() {LinearLayout l=new LinearLayout(this);l.setOrientation(LinearLayout.HORIZONTAL);l.setGravity(Gravity.CENTER_VERTICAL);return l;}
    private Button button(String label,boolean primary,Runnable action) {
        MaterialButton b=new MaterialButton(this);b.setText(label);b.setTextSize(14);b.setAllCaps(false);
        b.setTypeface(Typeface.create("sans-serif-medium",Typeface.NORMAL));b.setLetterSpacing(.01f);
        b.setMinHeight(dp(52));b.setMinimumWidth(0);b.setMinWidth(0);b.setPadding(dp(20),dp(12),dp(20),dp(12));
        b.setInsetTop(0);b.setInsetBottom(0);b.setCornerRadius(dp(28));b.setStrokeWidth(0);
        android.content.res.ColorStateList labelColors=new android.content.res.ColorStateList(new int[][]{new int[]{android.R.attr.state_enabled},new int[]{}},new int[]{primary?PRIMARY_TEXT:INK,MUTED});
        b.setTextColor(labelColors);
        b.setBackgroundTintList(new android.content.res.ColorStateList(new int[][]{new int[]{android.R.attr.state_enabled},new int[]{}},new int[]{primary?ACCENT:CARD,CARD}));
        b.setRippleColor(android.content.res.ColorStateList.valueOf(dark?0x22FFFFFF:0x16000000));
        b.setIconTint(labelColors);b.setIconSize(dp(22));b.setIconPadding(dp(10));
        if(label.equals("スキャン"))b.setIconResource(R.drawable.ic_scan);
        else if(label.equals("JAN入力"))b.setIconResource(R.drawable.ic_keyboard);
        else if(label.equals("手入力"))b.setIconResource(R.drawable.ic_add);
        else if(label.equals("セブンで探す"))b.setIconResource(R.drawable.ic_search);
        else if(label.equals("PayPay残高"))b.setIconResource(R.drawable.ic_wallet);
        LinearLayout.LayoutParams spacing=new LinearLayout.LayoutParams(-1,-2);spacing.setMargins(0,dp(4),0,dp(4));b.setLayoutParams(spacing);
        b.setElevation(0);b.setStateListAnimator(null);b.setOnClickListener(v->action.run());return b;
    }
    private View iconButton(int icon,String label,Runnable action){
        ImageButton b=new ImageButton(this);b.setImageResource(icon);b.setImageTintList(android.content.res.ColorStateList.valueOf(INK));
        b.setContentDescription(label);b.setPadding(dp(12),dp(12),dp(12),dp(12));
        b.setBackground(new android.graphics.drawable.RippleDrawable(android.content.res.ColorStateList.valueOf(dark?0x22FFFFFF:0x16000000),shape(CARD,24),null));
        b.setOnClickListener(v->action.run());return b;
    }
    private void divider(LinearLayout parent){View line=new View(this);line.setBackgroundColor(LINE);parent.addView(line,new LinearLayout.LayoutParams(-1,dp(1)));}
    private void space(LinearLayout l,int h) {View s=new View(this);l.addView(s,new LinearLayout.LayoutParams(1,dp(h)));}
    private void weighted(LinearLayout l,View v) {LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(0,-2,1);lp.setMargins(dp(3),0,dp(3),0);l.addView(v,lp);}
    private void build() {
        root=vertical();root.setPadding(dp(20),dp(14),dp(20),dp(8));root.setBackgroundColor(BG);
        if(android.os.Build.VERSION.SDK_INT>=30) root.setOnApplyWindowInsetsListener((v,insets)->{
            android.graphics.Insets bars=insets.getInsets(WindowInsets.Type.systemBars());
            root.setPadding(dp(20)+bars.left,dp(12)+bars.top,dp(20)+bars.right,dp(8)+bars.bottom);return insets;
        });
        // API 24-29 use the legacy inset API.
        if(android.os.Build.VERSION.SDK_INT<30) root.setOnApplyWindowInsetsListener((v,i)->{
            root.setPadding(dp(20)+i.getSystemWindowInsetLeft(),dp(12)+i.getSystemWindowInsetTop(),dp(20)+i.getSystemWindowInsetRight(),dp(8)+i.getSystemWindowInsetBottom());return i;
        });
        LinearLayout header=horizontal();
        LinearLayout brand=vertical();
        TextView title=text("かいもの",32,INK);title.setTypeface(displayFace());brand.addView(title);
        header.addView(brand,new LinearLayout.LayoutParams(0,-2,1));header.addView(iconButton(R.drawable.ic_settings,"設定",this::settings),new LinearLayout.LayoutParams(dp(48),dp(48)));root.addView(header);space(root,16);
        ScrollView scroll=new ScrollView(this);scroll.setFillViewport(false);scroll.setClipToPadding(false);
        LinearLayout content=vertical();content.setPadding(dp(4),dp(4),dp(4),dp(20));scroll.addView(content);
        root.addView(scroll,new LinearLayout.LayoutParams(-1,0,1));
        LinearLayout summary=vertical();summary.setPadding(dp(20),dp(20),dp(20),dp(20));summary.setBackground(shape(CONTAINER,28));
        summary.addView(text("合計（税込）",14,ON_CONTAINER));
        total=text("¥0",56,ON_CONTAINER);total.setTypeface(displayFace());total.setLetterSpacing(-.035f);
        total.setSingleLine(true);if(android.os.Build.VERSION.SDK_INT>=26)total.setAutoSizeTextTypeUniformWithConfiguration(24,56,1,android.util.TypedValue.COMPLEX_UNIT_SP);
        else total.setTextSize(40);summary.addView(total);
        count=text("0点",14,ON_CONTAINER);summary.addView(count);space(summary,8);
        walletSummary=text("",16,INK);summary.addView(walletSummary);space(summary,12);
        Button balanceButton=button("PayPay残高",false,this::walletSettings);summary.addView(balanceButton);
        content.addView(summary);space(content,20);
        LinearLayout actions=horizontal();codeButton=button("JAN入力",false,this::codeInput);weighted(actions,codeButton);
        weighted(actions,button("手入力",false,()->edit("","",null,null,null)));content.addView(actions);space(content,8);
        content.addView(button("セブンで探す",false,this::sevenEntry));space(content,12);
        status=paragraph("",MUTED);status.setVisibility(View.GONE);content.addView(status);space(content,16);
        content.addView(text("買い物リスト",20,INK));space(content,12);
        rows=vertical();content.addView(rows);space(content,16);
        content.addView(button("買い物をリセット",false,()->{
            if(cart.isEmpty())return;
            new ScanDialogBuilder(this).setTitle("買い物をリセット？").setMessage("今回の一覧を消します。残高・保存した価格・セブンとのひも付けは残ります。")
                .setNegativeButton("戻る",null).setPositiveButton("リセット",(d,w)->{cart.clear();save();refresh();}).show();
        }));space(content,16);
        TextView credit=text("Webサービス by Yahoo! JAPAN",16,MUTED);credit.setTextColor(LINK);credit.setGravity(Gravity.CENTER);credit.setOnClickListener(v->openUrl("https://developer.yahoo.co.jp/"));content.addView(credit);
        space(root,12);scanButton=button("スキャン",true,this::startContinuousScan);
        root.addView(scanButton,new LinearLayout.LayoutParams(-1,dp(64)));
        screen=new FrameLayout(this);screen.addView(root,new FrameLayout.LayoutParams(-1,-1));
        setContentView(screen);root.requestApplyInsets();
    }

    private long cartTotal(){long cents=0;for(Item i:cart)cents+=Pricing.fractionalSubtotal(i.cents,i.qty);return cents/100;}
    private void renderWallet(TextView view,long purchase,boolean details){
        if(view==null)return;
        if(!prefs.contains("walletBalance")){view.setText("");view.setVisibility(View.GONE);return;}
        view.setVisibility(View.VISIBLE);
        long balance=prefs.getLong("walletBalance",0),remaining=Wallet.remaining(balance,purchase);
        String result=remaining>=0?"買い物後 "+yen(remaining):"不足 "+yen(-remaining);
        if(details)result="残高 "+yen(balance)+"\n"+result;
        view.setText(result);view.setTextColor(remaining<0?(dark?0xFFFFB4AB:0xFFBA1A1A):ON_CONTAINER);view.setTypeface(Typeface.create("sans-serif",remaining<0?Typeface.BOLD:Typeface.NORMAL));
    }
    private void walletSettings(){
        LinearLayout body=vertical();body.setPadding(dp(20),dp(8),dp(20),dp(8));
        EditText amount=field("残高（円）",prefs.contains("walletBalance")?Long.toString(prefs.getLong("walletBalance",0)):"",false);
        amount.setInputType(InputType.TYPE_CLASS_NUMBER);body.addView(amount);
        if(prefs.contains("walletBalance"))body.addView(text("前回設定："+stamp(prefs.getLong("walletUpdatedAt",0)),12,MUTED));
        Button paidButton=button("支払済みを記録…",false,()->{});
        if(prefs.contains("walletBalance")&&!cart.isEmpty())body.addView(paidButton);
        AlertDialog d=new ScanDialogBuilder(this).setTitle("PayPay残高（手動）").setView(wrap(body)).setPositiveButton("保存",null).setNegativeButton("閉じる",null)
            .setNeutralButton("残高設定を解除",(dialog,which)->{prefs.edit().remove("walletBalance").remove("walletUpdatedAt").apply();refresh();}).create();
        paidButton.setOnClickListener(v->{d.dismiss();recordPayment();});
        d.setOnShowListener(x->d.getButton(-1).setOnClickListener(v->{
            try{long value=Wallet.parse(amount.getText().toString());prefs.edit().putLong("walletBalance",value).putLong("walletUpdatedAt",System.currentTimeMillis()).apply();refresh();d.dismiss();}
            catch(IllegalArgumentException e){amount.setError("0〜999,999,999円の整数を入力してください");}
        }));d.show();
    }
    private void recordPayment(){
        if(busy||scanProcessing){message("商品の追加が完了してから記録してください。");return;}
        if(cart.isEmpty()||!prefs.contains("walletBalance"))return;
        long balance=prefs.getLong("walletBalance",0);
        LinearLayout body=vertical();body.setPadding(dp(20),dp(8),dp(20),dp(8));
        body.addView(paragraph("PayPayで支払いを済ませた後に記録します。レシート等で、実際に残高から支払った金額を確認してください。",INK));
        body.addView(paragraph("記録すると、このアプリ内の残高を減らして買い物一覧を空にします。PayPayへの請求・送金は行いません。",MUTED));
        EditText actual=field("実際に残高から支払った金額（円）",Long.toString(cartTotal()),true);body.addView(actual);
        TextView preview=text("",14,INK);body.addView(preview);
        actual.addTextChangedListener(new android.text.TextWatcher(){public void beforeTextChanged(CharSequence s,int start,int count,int after){}public void onTextChanged(CharSequence s,int start,int before,int count){
            try{long left=Wallet.remaining(balance,Wallet.parse(s.toString()));preview.setText(left>=0?"記録後の残高："+yen(left):"設定残高を超えています。残高か支払額を確認してください。");}catch(Exception e){preview.setText("支払額を入力してください。");}
        }public void afterTextChanged(android.text.Editable e){}});
        actual.setText(actual.getText().toString());
        AlertDialog d=new ScanDialogBuilder(this).setTitle("支払済みを記録").setView(wrap(body)).setPositiveButton("記録して一覧を空にする",null).setNegativeButton("戻る",null).create();
        d.setOnShowListener(x->d.getButton(-1).setOnClickListener(v->{
            try{
                long paid=Wallet.parse(actual.getText().toString()),left=Wallet.paid(balance,paid);
                prefs.edit().putLong("walletBalance",left).putLong("walletUpdatedAt",System.currentTimeMillis()).putString("cart","[]").apply();
                cart.clear();refresh();d.dismiss();message("支払いを記録しました。残高："+yen(left));
            }catch(IllegalArgumentException e){actual.setError("0円以上、設定残高以下で入力してください");}
        }));d.show();
    }

    private int quantity(String jan){int result=0;for(Item i:cart)if(i.jan.equals(jan))result+=i.qty;return result;}
    private void updateScanSummary(){
        if(scanTotal==null)return;
        long amount=0;int n=0;for(Item i:cart){amount+=Pricing.fractionalSubtotal(i.cents,i.qty);n+=i.qty;}
        scanTotal.setText(yen(amount/100));
        scanCount.setText(n+"点");
        renderWallet(scanWallet,amount/100,false);
    }
    private void startContinuousScan(){
        if(busy){message("通信が完了してからスキャンを開始してください。");return;}
        if(ContextCompat.checkSelfPermission(this,Manifest.permission.CAMERA)!=PackageManager.PERMISSION_GRANTED){cameraPermission.launch(Manifest.permission.CAMERA);return;}
        if(scanning)return;
        if(scanPanel==null)buildScanPanel();
        scanGate=new ScanGate();scanProcessing=false;lastScan="";torch=false;
        scanCamera.setTorchOff();torchButton.setText("ライトON");
        scanning=true;scanBack.setEnabled(true);root.setVisibility(View.GONE);scanPanel.setVisibility(View.VISIBLE);
        scanNotice.setText("バーコードを枠に合わせる");
        updateScanSummary();
        if(activityResumed)scanCamera.resume();
        queueScanResume();
    }
    private void buildScanPanel(){
        scanPanel=vertical();scanPanel.setBackgroundColor(BG);
        scanPanel.setPadding(dp(16),dp(12),dp(16),dp(12));
        scanPanel.setOnApplyWindowInsetsListener((v,i)->{
            if(android.os.Build.VERSION.SDK_INT>=30){android.graphics.Insets b=i.getInsets(WindowInsets.Type.systemBars());scanPanel.setPadding(dp(16)+b.left,dp(12)+b.top,dp(16)+b.right,dp(12)+b.bottom);}
            else scanPanel.setPadding(dp(16)+i.getSystemWindowInsetLeft(),dp(12)+i.getSystemWindowInsetTop(),dp(16)+i.getSystemWindowInsetRight(),dp(12)+i.getSystemWindowInsetBottom());
            return i;
        });
        LinearLayout summary=vertical();summary.setPadding(dp(20),dp(16),dp(20),dp(16));
        summary.setBackground(shape(CONTAINER,28));
        LinearLayout heading=horizontal();heading.setGravity(Gravity.CENTER_VERTICAL);
        heading.addView(text("合計（税込）",14,ON_CONTAINER),new LinearLayout.LayoutParams(0,-2,1));
        scanCount=text("",14,ON_CONTAINER);heading.addView(scanCount);summary.addView(heading);
        scanTotal=text("",52,ON_CONTAINER);scanTotal.setTypeface(displayFace());
        scanTotal.setSingleLine(true);scanTotal.setIncludeFontPadding(false);
        androidx.core.widget.TextViewCompat.setAutoSizeTextTypeUniformWithConfiguration(scanTotal,24,52,1,android.util.TypedValue.COMPLEX_UNIT_SP);
        summary.addView(scanTotal,new LinearLayout.LayoutParams(-1,dp(72)));
        scanWallet=text("",16,INK);scanWallet.setOnClickListener(v->walletSettings());summary.addView(scanWallet);
        scanPanel.addView(summary,new LinearLayout.LayoutParams(-1,-2));space(scanPanel,12);
        scanCamera=new DecoratedBarcodeView(this);
        scanCamera.getBarcodeView().setDecoderFactory(new DefaultDecoderFactory(Arrays.asList(BarcodeFormat.EAN_13,BarcodeFormat.EAN_8)));
        scanCamera.setStatusText("");
        FrameLayout cameraFrame=new FrameLayout(this);cameraFrame.setBackground(shape(Color.BLACK,32));cameraFrame.setClipToOutline(true);
        cameraFrame.addView(scanCamera,new FrameLayout.LayoutParams(-1,-1));
        scanPanel.addView(cameraFrame,new LinearLayout.LayoutParams(-1,0,1));
        scanNotice=text("",14,MUTED);scanNotice.setGravity(Gravity.CENTER);scanNotice.setMinLines(1);scanNotice.setMaxLines(2);scanNotice.setEllipsize(android.text.TextUtils.TruncateAt.END);scanPanel.addView(scanNotice);
        repeatButton=button("同じコードを再読取",false,()->{
            if(scanProcessing||busy||activeDialogs>0)return;
            scanGate.allowRepeat();scanNotice.setText("同じバーコードを枠に合わせる");queueScanResume();
        });scanPanel.addView(repeatButton);
        space(scanPanel,8);LinearLayout buttons=horizontal();
        torchButton=button("ライトON",false,()->{torch=!torch;if(torch)scanCamera.setTorchOn();else scanCamera.setTorchOff();torchButton.setText(torch?"ライトOFF":"ライトON");});
        weighted(buttons,torchButton);weighted(buttons,button("一覧に戻る",false,this::stopContinuousScan));scanPanel.addView(buttons);
        screen.addView(scanPanel,new FrameLayout.LayoutParams(-1,-1));scanPanel.requestApplyInsets();
        scanCamera.getBarcodeView().addStateListener(new CameraPreview.StateListener(){
            @Override public void previewSized(){}
            @Override public void previewStarted(){}
            @Override public void previewStopped(){}
            @Override public void cameraClosed(){}
            @Override public void cameraError(Exception error){if(scanning){stopContinuousScan();message("カメラを起動できません。権限を確認するか、JAN入力を利用してください。");}}
        });
    }
    private final BarcodeCallback continuousCallback=new BarcodeCallback(){
        @Override public void barcodeResult(BarcodeResult result){
            if(!scanning||scanProcessing||busy||activeDialogs>0||!hasWindowFocus()||!activityResumed)return;
            String jan=result.getText()==null?"":result.getText().trim();
            if(!scanGate.accept(jan,SystemClock.elapsedRealtime()))return;
            lastScan=jan;quantityBefore=quantity(jan);scanProcessing=true;pauseDecoder();
            scanNotice.setText("商品を確認中…");
            scanFeedback();
            lookup(jan);queueScanResume();
        }
        @Override public void possibleResultPoints(List<ResultPoint> points){
            if(!scanning||scanProcessing||busy||activeDialogs>0||!hasWindowFocus()||!activityResumed)return;
            if(scanGate.frame(points!=null&&!points.isEmpty(),SystemClock.elapsedRealtime()))
                scanNotice.setText("次の商品をスキャン");
        }
    };
    private void scanFeedback(){
        if(!prefs.getBoolean("scanVibrate",true))return;
        Vibrator v=getSystemService(Vibrator.class);if(v==null||!v.hasVibrator())return;
        try{
            if(android.os.Build.VERSION.SDK_INT>=29)v.vibrate(VibrationEffect.createPredefined(VibrationEffect.EFFECT_CLICK));
            else if(android.os.Build.VERSION.SDK_INT>=26)v.vibrate(VibrationEffect.createOneShot(40,VibrationEffect.DEFAULT_AMPLITUDE));
            else v.vibrate(40);
        }catch(SecurityException ignored){/* Visual feedback is still shown. */}
    }
    private void pauseDecoder(){
        if(repeatButton!=null)repeatButton.setEnabled(false);
        if(scanCamera!=null&&decoderRunning){scanCamera.getBarcodeView().stopDecoding();decoderRunning=false;}
        scanGate.pauseTracking();
    }
    private void queueScanResume(){if(scanning&&!isFinishing()&&!isDestroyed())scanHandler.post(this::resumeScanWhenReady);}
    private void resumeScanWhenReady(){
        if(!scanning||!activityResumed||!hasWindowFocus()||activeDialogs>0||busy){pauseDecoder();return;}
        if(scanProcessing){
            // Reaching this point without an added item means the user closed/cancelled confirmation.
            scanProcessing=false;
            scanNotice.setText("未追加 · 次の商品をスキャン");
        }
        repeatButton.setEnabled(true);
        if(!decoderRunning){scanGate.pauseTracking();scanCamera.decodeContinuous(continuousCallback);decoderRunning=true;}
    }
    private void stopContinuousScan(){
        if(!scanning)return;
        scanning=false;scanProcessing=false;scanBack.setEnabled(false);scanHandler.removeCallbacksAndMessages(null);pauseDecoder();
        if(scanCamera!=null){scanCamera.setTorchOff();scanCamera.pause();}
        scanPanel.setVisibility(View.GONE);root.setVisibility(View.VISIBLE);refresh();root.requestApplyInsets();
    }
    private String yen(long v) {return "¥"+NumberFormat.getIntegerInstance(Locale.JAPAN).format(v);}
    private String yenUnit(long cents) {
        NumberFormat f=NumberFormat.getNumberInstance(Locale.JAPAN);f.setMaximumFractionDigits(2);
        return "¥"+f.format(java.math.BigDecimal.valueOf(cents,2));
    }
    private String stamp(long time) {return new java.text.SimpleDateFormat("yyyy/MM/dd HH:mm",Locale.JAPAN).format(new Date(time));}
    private void refresh() {
        long sum=0;int n=0;rows.removeAllViews();
        for(Item i:cart) {sum+=Pricing.fractionalSubtotal(i.cents,i.qty);n+=i.qty;}
        total.setText(yen(sum/100));count.setText(getString(R.string.item_count,n,cart.size()));
        renderWallet(walletSummary,sum/100,true);
        updateScanSummary();
        if(scanning&&scanProcessing&&quantity(lastScan)>quantityBefore) {
            scanProcessing=false;
            Item added=cartItem(lastScan);
            if(scanNotice!=null)scanNotice.setText("追加 · "+(added==null?lastScan:added.name));
            queueScanResume();
        }
        if(cart.isEmpty()) {
            space(rows,24);TextView empty=text("商品がありません",17,MUTED);empty.setGravity(Gravity.CENTER);rows.addView(empty);return;
        }
        for(Item i:cart) {
            LinearLayout card=vertical();card.setPadding(dp(16),dp(16),dp(16),dp(16));card.setBackground(shape(CARD,24));
            LinearLayout productHeader=horizontal();
            TextView name=text(i.name,18,INK);name.setTypeface(Typeface.create("sans-serif-medium",Typeface.NORMAL));
            productHeader.addView(name,new LinearLayout.LayoutParams(0,-2,1));
            View more=iconButton(R.drawable.ic_more,i.name+"の操作",()->itemActions(i));
            productHeader.addView(more,new LinearLayout.LayoutParams(dp(48),dp(48)));card.addView(productHeader);
            String origin=!i.sourceUrl.isEmpty()?"セブン掲載":i.source.contains("共有")?"共有価格":"手入力";
            card.addView(text(yenUnit(i.cents)+" / 個",14,MUTED));space(card,8);
            LinearLayout controls=horizontal();
            controls.addView(button("−",false,()->{if(i.qty>1)i.qty--;else cart.remove(i);save();refresh();}),new LinearLayout.LayoutParams(dp(48),dp(48)));
            TextView q=text(" "+i.qty+" ",17,INK);q.setGravity(Gravity.CENTER);controls.addView(q,new LinearLayout.LayoutParams(dp(42),-2));
            controls.addView(button("＋",false,()->{if(i.qty<999){i.qty++;save();refresh();}else message("数量は999個までです。");}),new LinearLayout.LayoutParams(dp(48),dp(48)));
            TextView line=text(yen(Pricing.fractionalSubtotal(i.cents,i.qty)/100),20,INK);line.setGravity(Gravity.END);controls.addView(line,new LinearLayout.LayoutParams(0,-2,1));card.addView(controls);
            rows.addView(card);space(rows,8);
        }
    }
    private void itemActions(Item item){
        String[] actions=item.jan.isEmpty()?new String[]{"編集","詳細"}:new String[]{"編集","セブン価格","店頭価格を共有","詳細"};
        new ScanDialogBuilder(this).setTitle(item.name).setItems(actions,(d,w)->{
            if(w==0){edit(item.jan,item.name,item.price,true,item);return;}
            if(!item.jan.isEmpty()&&w==1){
                SevenLookup.Product linked=readSeven(item.jan);
                if(linked==null)promptSeven(item.jan,item.name,item);else sevenActions(item.jan,linked,item);
                return;
            }
            if(!item.jan.isEmpty()&&w==2){sharePrice(item);return;}
            String detail=yenUnit(item.cents)+" / 個（税込）\n"+item.source;
            if(!item.jan.isEmpty())detail+="\nJAN "+item.jan;
            if(!item.region.isEmpty())detail+="\n"+item.region;
            if(item.checkedAt>0)detail+="\n確認 "+stamp(item.checkedAt);
            AlertDialog.Builder info=new ScanDialogBuilder(this).setTitle(item.name).setMessage(detail).setNegativeButton("閉じる",null);
            if(!item.sourceUrl.isEmpty())info.setPositiveButton("公式ページ",(x,y)->openUrl(item.sourceUrl));
            info.show();
        }).setNegativeButton("閉じる",null).show();
    }
    private void save() {
        try {JSONArray a=new JSONArray();for(Item i:cart)a.put(i.json());prefs.edit().putString("cart",a.toString()).apply();}
        catch(JSONException e){message("保存に失敗しました。");}
    }
    private void codeInput() {
        EditText code=field("8桁または13桁のJANコード","",true);
        AlertDialog dialog=new ScanDialogBuilder(this).setTitle("JAN番号を入力").setView(wrap(code)).setNegativeButton("戻る",null).setPositiveButton("検索",null).create();
        dialog.setOnShowListener(d->dialog.getButton(-1).setOnClickListener(v->{
            String jan=code.getText().toString().trim();if(!Pricing.validJan(jan)){code.setError("正しい8桁・13桁の番号を入力してください");return;}dialog.dismiss();lookup(jan);
        }));dialog.show();
    }
    private EditText field(String hint,String value,boolean numeric) {
        EditText e=new EditText(this);e.setTextColor(INK);e.setHintTextColor(MUTED);e.setHint(hint);e.setText(value);
        e.setInputType(numeric?InputType.TYPE_CLASS_NUMBER:InputType.TYPE_CLASS_TEXT|InputType.TYPE_TEXT_FLAG_CAP_SENTENCES);e.setSingleLine(true);
        e.setTextSize(16);e.setMinHeight(dp(56));e.setPadding(dp(12),dp(12),dp(12),dp(12));
        GradientDrawable background=shape(CARD,16);background.setStroke(dp(1),LINE);e.setBackgroundTintList(null);e.setBackground(background);
        LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(-1,-2);lp.setMargins(0,dp(8),0,dp(8));e.setLayoutParams(lp);return e;
    }
    private LinearLayout wrap(View v) {LinearLayout l=vertical();l.setPadding(dp(20),dp(8),dp(20),dp(8));l.addView(v);return l;}
    private void loading(boolean value) {
        busy=value;scanButton.setEnabled(!value);codeButton.setEnabled(!value);
        status.setText(value?"商品を確認中…":"");status.setVisibility(value?View.VISIBLE:View.GONE);
        if(scanning&&value){pauseDecoder();if(scanNotice!=null)scanNotice.setText("商品を確認中…");}
        queueScanResume();
    }
    private PBLookup pbIndex;
    private void lookup(String jan) {
        if(busy)return;
        if(!Pricing.validJan(jan)){message("このコードは対応するJANコードではありません。手入力で追加できます。");return;}
        for(Item i:cart) if(i.jan.equals(jan)) {if(i.qty<999){i.qty++;save();refresh();}else message("数量は999個までです。");return;}
        try {
            JSONObject catalog=new JSONObject(prefs.getString("manual","{}"));
            if(catalog.has(jan)) {
                Item saved=Item.read(catalog.getJSONObject(jan));
                if(scanning){saved.qty=1;saved.source="保存した手入力価格";add(saved);return;}
                new ScanDialogBuilder(this).setTitle("保存済みの手入力商品").setMessage(saved.name+"\n"+yen(saved.price)+" / 個（税込）\n保存時から価格が変わっていないか確認してください。")
                    .setPositiveButton("追加",(d,w)->add(new Item(jan,saved.name,saved.price,1,"保存した手入力価格")))
                    .setNeutralButton("修正",(d,w)->edit(jan,saved.name,saved.price,true,null))
                    .setNegativeButton("価格を再検索",(d,w)->{if(readSeven(jan)!=null)loadSeven(jan,true,null);else lookupPB(jan);}).show();return;
            }
        } catch(Exception ignored) {}
        if(readSeven(jan)!=null){loadSeven(jan,false,null);return;}
        lookupPB(jan);
    }
    private void lookupRegular(String jan) {
        if(!communityEndpoint().isEmpty()){communityLookup(jan);return;}
        remote(jan);
    }
    private void lookupPB(String jan) {
        sevenTask(()->{
            if(pbIndex==null)try(java.io.Reader reader=new java.io.InputStreamReader(getAssets().open("pb-names.json"),java.nio.charset.StandardCharsets.UTF_8)){pbIndex=new PBLookup(reader);}
            return pbIndex.find(jan);
        },names->{
            if(names.isEmpty()){lookupRegular(jan);return;}
            if(names.size()==1){searchSeven(jan,PBLookup.query(names.get(0).name),null);return;}
            String[] labels=new String[names.size()];for(int i=0;i<labels.length;i++)labels[i]=names.get(i).name;
            new ScanDialogBuilder(this).setTitle("PB商品名の候補").setItems(labels,(d,w)->searchSeven(jan,PBLookup.query(names.get(w).name),null))
                .setNeutralButton("別の商品を探す",(d,w)->lookupRegular(jan)).setNegativeButton("戻る",null).show();
        },error->lookupRegular(jan));
    }
    private void remote(String jan) {
        String id=prefs.getString("clientId","").trim();
        if(!CommunityConfig.yahooEnabled(id)) {promptSeven(jan,"",null);return;}
        loading(true);long token=++requestId;
        worker.execute(()->{
            try {
                YahooLookup.SearchResult found=YahooLookup.lookup(id,jan);
                runOnUiThread(()->{if(isFinishing()||isDestroyed()||token!=requestId)return;loading(false);choose(jan,found);});
            } catch(Exception e) {
                String msg=YahooLookup.userMessage(e);
                runOnUiThread(()->{if(isFinishing()||isDestroyed()||token!=requestId)return;loading(false);
                    new ScanDialogBuilder(this).setTitle("Yahoo!で検索できませんでした").setMessage(msg).setPositiveButton("セブンで検索",(d,w)->promptSeven(jan,"",null)).setNeutralButton("手入力",(d,w)->edit(jan,"",null,null,null)).setNegativeButton("戻る",null).show();});
            }
        });
    }
    private void choose(String jan,YahooLookup.SearchResult result) {
        List<YahooLookup.Candidate> found=result.prices;
        String keyword=SearchTerms.bestSeed(result.names);
        if(found.isEmpty()&&keyword.isEmpty()){
            if(!result.hints.isEmpty()){
                String[] labels=new String[result.hints.size()];for(int i=0;i<labels.length;i++)labels[i]=SearchTerms.clean(result.hints.get(i))+"\nJAN未確認：商品名の検索候補のみ";
                new ScanDialogBuilder(this).setTitle("商品名の候補を選択（JAN未確認）").setItems(labels,(d,w)->promptSeven(jan,SearchTerms.clean(result.hints.get(w)),null))
                    .setNeutralButton("商品名を自分で入力",(d,w)->promptSeven(jan,"",null)).setNegativeButton("戻る",null).show();
            }else{if(!result.notice.isEmpty())message(result.notice);promptSeven(jan,"",null);}
            return;
        }
        if(found.isEmpty()) {
            new ScanDialogBuilder(this).setTitle("希望小売価格が見つかりません")
                .setMessage(keyword.isEmpty()?"JANが一致する商品情報がありません。商品名でセブンを検索するか、公式商品URLを登録できます。":"商品名は取得できましたが、希望小売価格がありません。\n\n"+keyword+"\n\nセブンの掲載価格を取得できます。")
                .setPositiveButton("セブンで検索",(d,w)->promptSeven(jan,keyword,null)).setNeutralButton("手入力",(d,w)->edit(jan,keyword,null,null,null)).setNegativeButton("戻る",null).show();return;
        }
        String[] labels=new String[found.size()];
        for(int n=0;n<found.size();n++){YahooLookup.Candidate c=found.get(n);labels[n]=c.name+"\n希望小売価格 "+yen(c.price)+" / "+(c.taxIncluded==null?"税区分不明":c.taxIncluded?"税込表示":"税抜表示");}
        new ScanDialogBuilder(this).setTitle("容量・入数が同じ商品を選択")
            .setItems(labels,(d,w)->{YahooLookup.Candidate c=found.get(w);confirmCandidate(jan,c);})
            .setNegativeButton("戻る",null).setNeutralButton("セブンで検索",(d,w)->promptSeven(jan,keyword,null)).show();
    }
    private void confirmCandidate(String jan,YahooLookup.Candidate c) {
        new ScanDialogBuilder(this).setTitle("1個分の価格を確認")
            .setMessage(c.name+"\n\n希望小売価格 "+yen(c.price)+"\n\n通販の箱売り・複数個セットが含まれる場合があります。次の画面で1個分の価格と税区分を確認してください。")
            .setPositiveButton("価格を確認",(d,w)->edit(jan,c.name,c.price,c.taxIncluded,null))
            .setNeutralButton("掲載元",(d,w)->{openUrl(c.url);confirmCandidate(jan,c);})
            .setNegativeButton("戻る",null).show();
    }
    private void edit(String jan,String name,Long price,Boolean taxIncluded,Item existing) {
        LinearLayout body=vertical();body.setPadding(dp(20),dp(8),dp(20),dp(8));
        EditText nameInput=field("商品名",name,false),priceInput=field("1個の価格（円）",price==null?"":price.toString(),true);
        body.addView(nameInput);body.addView(priceInput);
        Spinner tax=new Spinner(this);
        String[] options={"税区分を選択してください","税込価格（そのまま）","税抜価格 → 8%を加算","税抜価格 → 10%を加算"};
        tax.setAdapter(new ArrayAdapter<>(this,android.R.layout.simple_spinner_dropdown_item,options));
        tax.setSelection(Boolean.TRUE.equals(taxIncluded)?1:0);body.addView(tax);
        body.addView(text("食品の持ち帰り等は8%、酒類・日用品等は10%。税抜からの換算は1個ごとに端数切り捨ての概算です。",11,MUTED));
        CheckBox remember=new MaterialCheckBox(this);remember.setText("この価格を手入力価格として端末に保存");remember.setTextColor(INK);remember.setEnabled(!jan.isEmpty());body.addView(remember);
        body.addView(text(jan.isEmpty()?"バーコードなしの商品":"JAN "+jan,11,MUTED));
        ScrollView scroll=new ScrollView(this);scroll.addView(body);
        AlertDialog dialog=new ScanDialogBuilder(this).setTitle(existing==null?"商品を追加":"商品を修正").setView(scroll)
            .setNegativeButton("戻る",null).setPositiveButton(existing==null?"追加する":"保存する",null).create();
        dialog.setOnShowListener(d->dialog.getButton(-1).setOnClickListener(v->{
            String enteredName=nameInput.getText().toString().trim();if(enteredName.isEmpty()){nameInput.setError("商品名を入力してください");return;}
            long enteredPrice;
            try {enteredPrice=Long.parseLong(priceInput.getText().toString().trim());if(enteredPrice<0||enteredPrice>1_000_000)throw new NumberFormatException();}
            catch(NumberFormatException e){priceInput.setError("0〜1,000,000円を入力してください");return;}
            int selected=tax.getSelectedItemPosition();if(selected==0){message("税区分を選択してください。");return;}
            long included=Pricing.inclusive(enteredPrice,selected==2?8:selected==3?10:0);
            Item result=new Item(jan,enteredName,included,1,"確認済みの概算価格");
            if(remember.isChecked()) {
                try {JSONObject db=new JSONObject(prefs.getString("manual","{}"));db.put(jan,result.json());prefs.edit().putString("manual",db.toString()).apply();}
                catch(JSONException e){message("商品の保存に失敗しました。");return;}
            }
            if(existing!=null){existing.name=enteredName;existing.price=included;existing.cents=included*100;existing.source="手動修正";existing.sourceUrl="";existing.region="";existing.checkedAt=0;save();refresh();}
            else add(result);
            enqueueAutoShare(result,"manual");
            dialog.dismiss();
        }));dialog.show();
    }
    private void add(Item i) {
        for(Item old:cart) if(!i.jan.isEmpty()&&old.jan.equals(i.jan)&&old.cents==i.cents) {
            if(old.qty>=999){message("数量は999個までです。");return;}
            old.qty++;if(!i.sourceUrl.isEmpty()){old.source=i.source;old.sourceUrl=i.sourceUrl;old.region=i.region;old.checkedAt=i.checkedAt;}save();refresh();return;
        }
        cart.add(i);save();refresh();
    }
    private void settings() {
        LinearLayout body=vertical();body.setPadding(dp(20),dp(8),dp(20),dp(8));
        body.addView(button("保存した手入力商品を削除",false,()->new ScanDialogBuilder(this).setTitle("保存済み商品を削除？").setMessage("買い物中の一覧は残ります。")
            .setNegativeButton("戻る",null).setPositiveButton("削除",(d,w)->{prefs.edit().remove("manual").apply();message("保存済み商品を削除しました。");}).show()));
        body.addView(button("セブンの登録商品を管理",false,this::sevenLibrary));
        body.addView(button("みんなの価格データベース",false,this::communitySettings));
        MaterialSwitch autoShare=new MaterialSwitch(this);autoShare.setText("価格データを自動送信");autoShare.setMinHeight(dp(64));autoShare.setPadding(dp(16),dp(8),dp(16),dp(8));autoShare.setTextColor(INK);autoShare.setChecked(prefs.getBoolean("autoShare",true));
        autoShare.setOnCheckedChangeListener((v,on)->{prefs.edit().putBoolean("autoShare",on).apply();if(on)sendNextAuto();});body.addView(autoShare);
        body.addView(button("自動送信の未送信データ",false,this::autoShareOutbox));
        MaterialSwitch vibration=new MaterialSwitch(this);vibration.setText("読み取り時の振動");vibration.setMinHeight(dp(64));vibration.setPadding(dp(16),dp(8),dp(16),dp(8));vibration.setTextColor(INK);
        vibration.setChecked(prefs.getBoolean("scanVibrate",true));
        vibration.setOnCheckedChangeListener((v,on)->prefs.edit().putBoolean("scanVibrate",on).apply());body.addView(vibration);
        body.addView(button("Yahoo!連携（任意）",false,this::yahooSettings));
        body.addView(button("使い方",false,this::help));
        ScrollView settingsScroll=new ScrollView(this);settingsScroll.addView(body);
        new ScanDialogBuilder(this).setTitle("設定").setView(settingsScroll).setNegativeButton("閉じる",null).show();
    }
    private void yahooSettings(){
        LinearLayout body=vertical();body.setPadding(dp(20),dp(8),dp(20),dp(8));
        body.addView(text("Yahoo!連携（任意）",15,INK));EditText id=field("Client ID",prefs.getString("clientId",""),false);id.setInputType(InputType.TYPE_CLASS_TEXT|InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD);body.addView(id);
        body.addView(paragraph("共有データ・セブン検索はIDなしで使えます。Yahoo!を使う場合だけClient IDを入力してください。Client Secretは不要です。",MUTED));
        body.addView(button("入力中のIDを保存して接続テスト",false,()->connectionTest(id.getText().toString().trim())));space(body,8);
        body.addView(button("Client IDの取得ページ",false,()->openUrl("https://developer.yahoo.co.jp/start/")));space(body,8);
        new ScanDialogBuilder(this).setTitle("Yahoo!連携（任意）").setView(body).setNegativeButton("戻る",null)
            .setPositiveButton("保存",(d,w)->{prefs.edit().putString("clientId",id.getText().toString().trim()).apply();message("設定を保存しました。");}).show();
    }

    private void enqueueAutoShare(Item item,String kind){
        if(!prefs.getBoolean("autoShare",true)||communityEndpoint().isEmpty()||!Pricing.validJan(item.jan)||item.cents<=0)return;
        try{
            String endpoint=communityEndpoint();JSONObject payload=AutoShare.payload(item.jan,item.name,item.cents,kind,item.sourceUrl,CommunityApi.today());
            String fingerprint=AutoShare.fingerprint(endpoint,payload);JSONArray queue=new JSONArray(prefs.getString("autoQueue","[]"));JSONObject sent=new JSONObject(prefs.getString("autoSent","{}"));
            if(!AutoShare.contains(queue,sent,fingerprint)){
                if(queue.length()>=100){message("自動送信の未送信が100件あります。設定から確認してください。");return;}
                queue.put(new JSONObject().put("endpoint",endpoint).put("fingerprint",fingerprint).put("payload",payload));
                if(!prefs.edit().putString("autoQueue",queue.toString()).commit()){message("自動送信の待機データを保存できませんでした。");return;}
            }
            sendNextAuto();
        }catch(Exception e){message("商品は追加しましたが、自動送信データを保存できませんでした。");}
    }
    private void sendNextAuto(){
        if(autoSending||!activityResumed||isFinishing()||isDestroyed()||!prefs.getBoolean("autoShare",true)||SystemClock.elapsedRealtime()<autoRetryAt)return;
        try{
            JSONArray queue=new JSONArray(prefs.getString("autoQueue","[]"));JSONObject selected=null;
            for(int n=0;n<queue.length();n++)if(communityEndpoint().equals(queue.getJSONObject(n).getString("endpoint"))){selected=queue.getJSONObject(n);break;}
            if(selected==null)return;
            final JSONObject entry=selected;autoSending=true;
            autoWorker.execute(()->{
                String error="";try{CommunityApi.submitBinding(entry.getString("endpoint"),entry.getJSONObject("payload"));}catch(Exception e){error=CommunityApi.error(e);}
                final String outcome=error;runOnUiThread(()->{
                    autoSending=false;
                    if(outcome.isEmpty()){
                        try{
                            String fingerprint=entry.getString("fingerprint");JSONObject sent=new JSONObject(prefs.getString("autoSent","{}"));AutoShare.remember(sent,fingerprint);
                            JSONArray remaining=AutoShare.remove(new JSONArray(prefs.getString("autoQueue","[]")),fingerprint);
                            if(!prefs.edit().putString("autoQueue",remaining.toString()).putString("autoSent",sent.toString()).putString("autoError","").commit()){autoRetryAt=SystemClock.elapsedRealtime()+60000;return;}
                            sendNextAuto();
                        }catch(Exception e){prefs.edit().putString("autoError","送信結果の保存に失敗しました。同じIDで再送できます。").apply();}
                    }else{autoRetryAt=SystemClock.elapsedRealtime()+60000;prefs.edit().putString("autoError",outcome).apply();if(!isDestroyed())scanHandler.postDelayed(this::sendNextAuto,60000);}
                });
            });
        }catch(Exception e){prefs.edit().putString("autoError","未送信データを読み取れません。").apply();}
    }
    private void autoShareOutbox(){
        try{
            JSONArray queue=new JSONArray(prefs.getString("autoQueue","[]"));String error=prefs.getString("autoError","");
            if(queue.length()==0){message("未送信のデータはありません。");return;}
            String[] labels=new String[queue.length()];for(int n=0;n<labels.length;n++){JSONObject p=queue.getJSONObject(n).getJSONObject("payload");labels[n]=p.getString("name")+"\n"+yenUnit(p.getLong("unit_cents"))+" / 個（税込）";}
            new ScanDialogBuilder(this).setTitle("自動送信の未送信 "+queue.length()+"件").setItems(labels,(d,w)->{
                try{JSONObject entry=queue.getJSONObject(w),p=entry.getJSONObject("payload");
                    new ScanDialogBuilder(this).setTitle(p.getString("name")).setMessage("JAN "+p.getString("jan")+"\n"+yenUnit(p.getLong("unit_cents"))+"\n送信先 "+entry.getString("endpoint")+(error.isEmpty()?"":"\n\n"+error))
                        .setPositiveButton("再送",(x,y)->{autoRetryAt=0;sendNextAuto();}).setNeutralButton("端末から削除",(x,y)->{try{prefs.edit().putString("autoQueue",AutoShare.remove(new JSONArray(prefs.getString("autoQueue","[]")),entry.getString("fingerprint")).toString()).apply();}catch(Exception ignored){}}).setNegativeButton("閉じる",null).show();
                }catch(Exception e){message("未送信データを読み取れません。");}
            }).setPositiveButton("まとめて再送",(d,w)->{autoRetryAt=0;sendNextAuto();}).setNegativeButton("閉じる",null).show();
        }catch(Exception e){message("未送信データを読み取れません。");}
    }

    private String communityEndpoint(){
        return CommunityConfig.endpoint(prefs.getString("communityUrl",null),prefs.getBoolean("communityConfigured",false));
    }
    private String alternateLookupLabel(){return CommunityConfig.yahooEnabled(prefs.getString("clientId",""))?"別の価格を探す":"セブンで探す";}
    private void communitySettings(){
        LinearLayout body=vertical();body.setPadding(dp(16),dp(8),dp(16),dp(8));
        body.addView(paragraph("同じ共有APIのURLを設定した人同士で商品・価格を集めます。標準のCloudflareは設定なしで検索できます。投稿には管理者が発行する投稿用キーが必要です。投稿は承認後に公開されます。",MUTED));
        EditText url=field("共有API URL（https://…）",communityEndpoint(),false);body.addView(url);
        body.addView(button("標準のCloudflareに戻す",false,()->url.setText(CommunityConfig.DEFAULT_URL)));
        EditText key=field("投稿用キー（管理用キーは入力しない）",prefs.getString("communityKey",""),false);key.setInputType(InputType.TYPE_CLASS_TEXT|InputType.TYPE_TEXT_VARIATION_PASSWORD);body.addView(key);
        body.addView(button("入力中のURLに接続確認",false,()->sevenTask(()->{CommunityApi.health(url.getText().toString());return true;},ok->message("共有APIに接続できました。保存すると検索に使います。"),e->message(CommunityApi.error(e)))));
        body.addView(button("未送信の投稿を確認・再送",false,this::communityOutbox));
        body.addView(paragraph("空のURLを保存すると共有機能を停止します。残高・Client ID・買い物一覧・写真は送信しません。店舗・地域・価格・商品名・確認日が公開されます。個人情報は入力しないでください。",MUTED));
        AlertDialog d=new ScanDialogBuilder(this).setTitle("みんなの価格データベース").setView(body).setNegativeButton("閉じる",null).setPositiveButton("保存",null).create();
        d.setOnShowListener(x->d.getButton(-1).setOnClickListener(v->{try{
            String endpoint=url.getText().toString().trim();if(!endpoint.isEmpty())endpoint=CommunityApi.base(endpoint);
            String token=key.getText().toString().trim();if(!token.isEmpty()&&!token.matches("[0-9a-fA-F]{64}")){key.setError("64桁の投稿用キーを入力してください");return;}
            prefs.edit().putString("communityUrl",endpoint).putBoolean("communityConfigured",true).putString("communityKey",token).apply();d.dismiss();message("共有設定を保存しました。");
        }catch(Exception e){url.setError(CommunityApi.error(e));}}));d.show();
    }
    private void communityLookup(String jan){
        String endpoint=communityEndpoint();
        sevenTask(()->CommunityApi.lookup(endpoint,jan),prices->{
            if(prices.isEmpty()){remote(jan);return;}
            String[] labels=new String[prices.size()];for(int n=0;n<labels.length;n++){CommunityApi.Price p=prices.get(n);labels[n]=p.name+"\n"+yenUnit(p.cents)+" / 個（税込）\n"+p.context();}
            new ScanDialogBuilder(this).setTitle("共有価格を選択（店舗・容量を確認）").setItems(labels,(d,w)->{
                CommunityApi.Price p=prices.get(w);
                new ScanDialogBuilder(this).setTitle(p.binding?"共有された価格を使う？":"投稿された店頭価格を使う？").setMessage(p.name+"\nJAN "+jan+"\n"+yenUnit(p.cents)+" / 個（税込）\n"+p.context()+"\n\n他の人が登録した価格です。手元の商品・容量・価格を確認してください。")
                    .setPositiveButton("買い物に追加",(dialog,which)->{Item i=new Item(jan,p.name,p.cents/100,1,p.binding?"共有された登録価格":"共有された店頭価格");i.cents=p.cents;i.region=p.context();add(i);})
                    .setNegativeButton("戻る",(dialog,which)->communityLookup(jan)).show();
            }).setNeutralButton(alternateLookupLabel(),(d,w)->remote(jan)).setNegativeButton("戻る",null).show();
        },e->new ScanDialogBuilder(this).setTitle("共有データを取得できません").setMessage(CommunityApi.error(e)).setPositiveButton(alternateLookupLabel(),(d,w)->remote(jan)).setNeutralButton("再試行",(d,w)->communityLookup(jan)).setNegativeButton("戻る",null).show());
    }
    private void sharePrice(Item item){
        if(busy){message("通信が完了してから投稿してください。");return;}
        if(communityEndpoint().isEmpty()||prefs.getString("communityKey","").isEmpty()){communitySettings();return;}
        LinearLayout body=vertical();body.setPadding(dp(16),dp(8),dp(16),dp(8));
        body.addView(paragraph("実際の店頭表示・レシートで確認した1個分の税込価格を投稿します。通販のセット価格や取得しただけの掲載価格は投稿しないでください。",MUTED));
        body.addView(text("JAN "+item.jan,16,INK));
        EditText name=field("商品名・容量・味",item.name,false),price=field("1個の税込価格（円）",java.math.BigDecimal.valueOf(item.cents,2).toPlainString(),false);
        price.setInputType(InputType.TYPE_CLASS_NUMBER|InputType.TYPE_NUMBER_FLAG_DECIMAL);
        EditText store=field("店舗・チェーン名（必須）","",false),area=field("地域（任意・個人情報を入れない）","",false),date=field("確認日 yyyy-MM-dd",CommunityApi.today(),false);
        body.addView(name);body.addView(price);body.addView(store);body.addView(area);body.addView(date);
        CheckBox confirmed=new MaterialCheckBox(this);confirmed.setText("店頭で確認した1個分の税込価格である");confirmed.setTextColor(INK);body.addView(confirmed);
        body.addView(paragraph("投稿内容は管理者の承認後に公開されます。通信失敗時は未送信一覧から再送できます。",MUTED));
        AlertDialog d=new ScanDialogBuilder(this).setTitle("みんなに店頭価格を投稿").setView(body).setPositiveButton("この内容で投稿",null).setNegativeButton("戻る",null).create();
        d.setOnShowListener(x->d.getButton(-1).setOnClickListener(v->{try{
            if(!confirmed.isChecked()){message("店頭で確認した価格かチェックしてください。");return;}
            JSONObject payload=CommunityApi.report(item.jan,name.getText().toString(),price.getText().toString(),store.getText().toString(),area.getText().toString(),date.getText().toString().trim(),true);
            JSONArray queue=new JSONArray(prefs.getString("communityOutbox","[]"));if(queue.length()>=30){message("未送信の投稿が30件あります。設定から再送・整理してください。");return;}
            JSONObject entry=new JSONObject().put("endpoint",communityEndpoint()).put("report",payload);queue.put(entry);
            if(!prefs.edit().putString("communityOutbox",queue.toString()).commit()){message("投稿を端末に保存できませんでした。");return;}
            d.dismiss();sendCommunity(entry);
        }catch(Exception e){message("商品名・店舗名・1個の税込価格・過去90日以内の確認日を確認してください。");}}));d.show();
    }
    private void sendCommunity(JSONObject entry){
        try{
            String endpoint=entry.getString("endpoint");if(!endpoint.equals(communityEndpoint())){message("共有先が変更されています。元のURLに戻してから再送してください。");return;}
            JSONObject report=entry.getJSONObject("report");String id=report.getString("request_id"),key=prefs.getString("communityKey","");
            sevenTask(()->CommunityApi.submit(endpoint,key,report),status->{removeCommunity(id);message(status.equals("pending")?"投稿を受け付けました。承認後に他の人から検索できます。":status.equals("approved")?"この投稿はすでに公開されています。":"この投稿は管理者により非公開になっています。");},e->message(CommunityApi.error(e)+"\n投稿内容は未送信一覧に残っています。"));
        }catch(Exception e){message("未送信データを読み取れませんでした。");}
    }
    private void removeCommunity(String id){
        try{JSONArray old=new JSONArray(prefs.getString("communityOutbox","[]")),next=new JSONArray();for(int n=0;n<old.length();n++){JSONObject e=old.getJSONObject(n);if(!id.equals(e.getJSONObject("report").getString("request_id")))next.put(e);}prefs.edit().putString("communityOutbox",next.toString()).apply();}catch(Exception e){message("未送信一覧を更新できませんでした。再送しても同じ投稿IDでは重複しません。");}
    }
    private void communityOutbox(){
        try{JSONArray queue=new JSONArray(prefs.getString("communityOutbox","[]"));if(queue.length()==0){message("未送信の投稿はありません。");return;}
            String[] names=new String[queue.length()];for(int n=0;n<names.length;n++)names[n]=queue.getJSONObject(n).getJSONObject("report").getString("name");
            new ScanDialogBuilder(this).setTitle("未送信の投稿").setItems(names,(d,w)->{try{
                JSONObject entry=queue.getJSONObject(w),r=entry.getJSONObject("report");
                new ScanDialogBuilder(this).setTitle("未送信の投稿を確認").setMessage(r.getString("name")+"\n"+yenUnit(r.getLong("unit_cents"))+" / 個（税込）\n"+r.getString("store_name")+" / "+r.optString("area")+"\n確認日："+r.getString("observed_on"))
                    .setPositiveButton("再送",(dialog,which)->sendCommunity(entry)).setNeutralButton("端末から削除",(dialog,which)->removeCommunity(r.optString("request_id"))).setNegativeButton("戻る",null).show();
            }catch(Exception e){message("投稿を読み取れません。");}}).setNegativeButton("閉じる",null).show();
        }catch(Exception e){message("未送信一覧を読み取れません。");}
    }

    private SevenLookup.Product readSeven(String jan) {
        try {JSONObject all=new JSONObject(prefs.getString("sevenLinks","{}"));return all.has(jan)?SevenLookup.Product.read(all.getJSONObject(jan)):null;}
        catch(Exception e){return null;}
    }
    private void storeSeven(String jan,SevenLookup.Product p) throws JSONException {
        JSONObject all=new JSONObject(prefs.getString("sevenLinks","{}"));all.put(jan,p.json());
        JSONObject manual=new JSONObject(prefs.getString("manual","{}"));manual.remove(jan);
        prefs.edit().putString("sevenLinks",all.toString()).putString("manual",manual.toString()).apply();
    }
    private void removeSeven(String jan) {
        try {JSONObject all=new JSONObject(prefs.getString("sevenLinks","{}"));all.remove(jan);prefs.edit().putString("sevenLinks",all.toString()).apply();}
        catch(JSONException e){message("保存データを変更できませんでした。");}
    }
    private <T> void sevenTask(Callable<T> task,java.util.function.Consumer<T> success,java.util.function.Consumer<Exception> failure) {
        if(busy){message("通信中です。完了後に再試行してください。");return;}
        loading(true);long token=++requestId;
        worker.execute(()->{
            try {T value=task.call();runOnUiThread(()->{if(isFinishing()||isDestroyed()||token!=requestId)return;loading(false);success.accept(value);});}
            catch(Exception e){runOnUiThread(()->{if(isFinishing()||isDestroyed()||token!=requestId)return;loading(false);failure.accept(e);});}
        });
    }
    private String sevenError(Exception e) {
        return e instanceof java.io.IOException&&e.getMessage()!=null?e.getMessage():"セブンの商品情報を読み取れませんでした。時間を置いて再試行してください。";
    }
    private void sevenEntry() {
        EditText code=field("8桁・13桁のJAN番号","",true);
        AlertDialog d=new ScanDialogBuilder(this).setTitle("セブンの商品を登録").setView(wrap(code)).setPositiveButton("次へ",null).setNegativeButton("戻る",null).create();
        d.setOnShowListener(x->d.getButton(-1).setOnClickListener(v->{String j=code.getText().toString().trim();
            if(!Pricing.validJan(j)){code.setError("正しいJAN番号を入力してください");return;}
            d.dismiss();promptSeven(j,"",cartItem(j));
        }));d.show();
    }
    private Item cartItem(String jan){for(Item i:cart)if(i.jan.equals(jan))return i;return null;}
    private void promptSeven(String jan,String seed,Item existing) {
        if(busy){message("通信中です。");return;}
        LinearLayout body=vertical();body.setPadding(dp(20),dp(8),dp(20),dp(8));
        String cleaned=SearchTerms.clean(seed);
        EditText query=field("商品名（例：じゃがりこ）",cleaned,false);body.addView(query);
        String shorter=SearchTerms.shorter(cleaned);
        if(!shorter.isEmpty()&&!shorter.equals(cleaned))body.addView(button("短い名前で検索："+shorter,false,()->query.setText(shorter)));
        EditText url=field("またはセブン公式の商品詳細URL","",false);url.setInputType(InputType.TYPE_CLASS_TEXT|InputType.TYPE_TEXT_VARIATION_URI);body.addView(url);
        Button manualButton=button("価格を手入力",false,()->{});body.addView(manualButton);
        ScrollView scroll=new ScrollView(this);scroll.addView(body);
        AlertDialog d=new ScanDialogBuilder(this).setTitle("セブンで探す").setView(scroll).setPositiveButton("商品名で検索",null).setNeutralButton("URLから取得",null).setNegativeButton("戻る",null).create();
        manualButton.setOnClickListener(v->{d.dismiss();edit(jan,query.getText().toString().trim(),null,null,existing);});
        d.setOnShowListener(x->{d.getButton(-1).setOnClickListener(v->{
            String q=query.getText().toString().trim();if(q.isEmpty()||q.length()>150){query.setError("1〜150文字の検索語を入力してください");return;}
            d.dismiss();searchSeven(jan,q,existing);
        });d.getButton(-3).setOnClickListener(v->{
            String value;try{value=SevenLookup.productUrl(url.getText().toString());}catch(Exception e){url.setError(e.getMessage());return;}
            d.dismiss();fetchSevenForLink(jan,value,existing);
        });});d.show();
    }
    private void searchSeven(String jan,String query,Item existing) {
        sevenTask(()->SevenLookup.search(query),result->{
            if(result.links.isEmpty()) {
                new ScanDialogBuilder(this).setTitle("セブンに候補がありません")
                    .setMessage("検索語："+result.query+"\n短い商品名で再検索するか、公式商品ページのURLを登録してください。")
                    .setPositiveButton("検索語・URLを変更",(d,w)->promptSeven(jan,query,existing)).setNeutralButton("公式で探す",(d,w)->{try{openUrl(SevenLookup.searchUrl(query));}catch(Exception ignored){}}).setNegativeButton("閉じる",null).show();return;
            }
            String[] labels=new String[result.links.size()];
            for(int n=0;n<labels.length;n++){SevenLookup.Link l=result.links.get(n);labels[n]=l.name+"\n"+l.priceText+"\n"+l.region;}
            new ScanDialogBuilder(this).setTitle("商品を選択")
                .setItems(labels,(d,w)->fetchSevenForLink(jan,result.links.get(w).url,existing))
                .setNeutralButton("検索語・URLを変更",(d,w)->promptSeven(jan,query,existing)).setNegativeButton("閉じる",null).show();
        },e->new ScanDialogBuilder(this).setTitle("セブンを検索できませんでした").setMessage(sevenError(e))
            .setPositiveButton("検索語・URLを変更",(d,w)->promptSeven(jan,query,existing)).setNegativeButton("閉じる",null).show());
    }
    private void fetchSevenForLink(String jan,String url,Item existing) {
        sevenTask(()->SevenLookup.load(url),p->confirmSeven(jan,p,existing),e->new ScanDialogBuilder(this).setTitle("価格を取得できませんでした").setMessage(sevenError(e))
            .setPositiveButton("別の商品・URL",(d,w)->promptSeven(jan,"",existing)).setNegativeButton("閉じる",null).show());
    }
    private void confirmSeven(String jan,SevenLookup.Product p,Item existing) {
        LinearLayout body=vertical();body.setPadding(dp(20),dp(8),dp(20),dp(8));
        TextView productName=text(p.name,20,INK);productName.setTypeface(Typeface.DEFAULT,Typeface.BOLD);body.addView(productName);
        body.addView(text(p.priceText,20,ACCENT));body.addView(text(p.region,14,MUTED));
        body.addView(paragraph("公式掲載価格です。店頭価格と異なる場合があります。",MUTED));
        body.addView(button("公式の商品ページを開く",false,()->openUrl(p.url)));
        CheckBox check=new MaterialCheckBox(this);check.setText("手元の商品と同じ商品・容量であることを確認した");check.setTextColor(INK);body.addView(check);
        ScrollView scroll=new ScrollView(this);scroll.addView(body);
        AlertDialog d=new ScanDialogBuilder(this).setTitle("JANと公式商品をひも付け").setView(scroll).setPositiveButton(existing==null?"登録して追加":"登録して更新",null)
            .setNeutralButton("別の商品を選ぶ",(x,w)->promptSeven(jan,p.name,existing)).setNegativeButton("戻る",null).create();
        d.setOnShowListener(x->{d.getButton(-1).setEnabled(false);check.setOnCheckedChangeListener((v,on)->d.getButton(-1).setEnabled(on));
            d.getButton(-1).setOnClickListener(v->{try{storeSeven(jan,p);useSeven(jan,p,existing,"セブン公式掲載価格");d.dismiss();}catch(Exception e){message("登録を保存できませんでした。");}});
        });d.show();
    }
    private void useSeven(String jan,SevenLookup.Product p,Item existing,String source) {
        Item i=new Item(jan,p.name,p.cents/100,1,source);i.cents=p.cents;i.sourceUrl=p.url;i.checkedAt=p.checkedAt;i.region=p.region;
        if(existing!=null&&cart.contains(existing)) {
            existing.name=i.name;existing.price=i.price;existing.cents=i.cents;existing.source=i.source;existing.sourceUrl=i.sourceUrl;existing.checkedAt=i.checkedAt;existing.region=i.region;save();refresh();
        } else add(i);
        if(SevenLookup.fresh(p,System.currentTimeMillis())&&!source.contains("更新失敗"))enqueueAutoShare(i,"seven");
        status.setText(source+" · 確認 "+stamp(p.checkedAt));
    }
    private void loadSeven(String jan,boolean force,Item existing) {
        SevenLookup.Product saved=readSeven(jan);if(saved==null){promptSeven(jan,"",existing);return;}
        if(!force&&SevenLookup.fresh(saved,System.currentTimeMillis())){useSeven(jan,saved,existing,"セブン掲載価格（24時間以内の保存値）");return;}
        sevenTask(()->SevenLookup.load(saved.url),fresh->{
            // Never silently accept a renamed/reused page, a price change, or a region change.
            if(!fresh.name.equals(saved.name)||fresh.cents!=saved.cents||!fresh.region.equals(saved.region)){confirmSeven(jan,fresh,existing);return;}
            try{storeSeven(jan,fresh);useSeven(jan,fresh,existing,"セブン公式掲載価格");}catch(Exception e){message("取得結果を保存できませんでした。");}
        },e->{
            new ScanDialogBuilder(this).setTitle("セブン価格を更新できませんでした")
                .setMessage(sevenError(e)+"\n\n保存価格："+yenUnit(saved.cents)+"\n確認日時："+stamp(saved.checkedAt)+"\n古い価格の可能性があります。")
                .setPositiveButton("保存価格を使う",(d,w)->useSeven(jan,saved,existing,"セブンの保存価格（更新失敗）"))
                .setNeutralButton("ひも付けを変更",(d,w)->promptSeven(jan,saved.name,existing)).setNegativeButton("戻る",null).show();
        });
    }
    private void sevenActions(String jan,SevenLookup.Product p,Item existing) {
        new ScanDialogBuilder(this).setTitle(p.name).setItems(new String[]{"今すぐ価格を再取得","別の商品ページに変更","公式ページを開く","ひも付けを削除"},(d,w)->{
            if(w==0)loadSeven(jan,true,existing);else if(w==1)promptSeven(jan,p.name,existing);else if(w==2)openUrl(p.url);
            else new ScanDialogBuilder(this).setTitle("ひも付けを削除？").setMessage("今回の買い物一覧は残ります。次回から商品を選び直せます。")
                .setPositiveButton("削除",(x,y)->{removeSeven(jan);message("ひも付けを削除しました。");}).setNegativeButton("戻る",null).show();
        }).setNegativeButton("閉じる",null).show();
    }
    private void sevenLibrary() {
        List<String> jans=new ArrayList<>(),labels=new ArrayList<>();
        try {
            JSONObject all=new JSONObject(prefs.getString("sevenLinks","{}"));Iterator<String> keys=all.keys();
            while(keys.hasNext()){String j=keys.next();SevenLookup.Product p=readSeven(j);if(p!=null){jans.add(j);labels.add(p.name+"\nJAN "+j+" · "+yenUnit(p.cents)+"\n確認 "+stamp(p.checkedAt));}}
        }catch(Exception ignored){}
        if(jans.isEmpty()){message("セブンの登録商品はありません。");return;}
        new ScanDialogBuilder(this).setTitle("セブンの登録商品").setItems(labels.toArray(new String[0]),(d,w)->{String jan=jans.get(w);sevenActions(jan,readSeven(jan),cartItem(jan));}).setNegativeButton("閉じる",null).show();
    }
    private void connectionTest(String id) {
        if(busy){message("通信中です。完了してから再試行してください。");return;}
        if(id.isEmpty()){message("Client IDを入力してください。");return;}
        if(id.matches(".*\\s.*")){message("Client IDに空白や改行が含まれています。IDだけをコピーしてください。");return;}
        prefs.edit().putString("clientId",id).apply();
        loading(true);long token=++requestId;
        worker.execute(()->{
            String result;
            try {
                YahooLookup.testConnection(id);
                result="接続成功（HTTP 200）\n\nこのIDで商品検索APIを利用できました。商品の希望小売価格が取得できるかは別途スキャンで確認してください。";
            } catch(Exception e) { result=YahooLookup.userMessage(e); }
            String message=result;
            runOnUiThread(()->{
                if(isFinishing()||isDestroyed()||token!=requestId)return;
                loading(false);
                new ScanDialogBuilder(this).setTitle("Yahoo!接続テスト")
                    .setMessage(message+"\n\nこの画面にClient IDは表示していません。")
                    .setPositiveButton("閉じる",null).show();
            });
        });
    }
    private void help() {
        new ScanDialogBuilder(this).setTitle("使い方")
            .setMessage("① スキャンすると保存済み価格・Cloudflareの共有データを検索。未登録はセブン商品名検索へ（Yahoo!連携は任意）\n② スキャン、またはJAN番号を入力\n③ セブンは初回に商品・容量・地域とJANの一致を確認。次回から自動取得（24時間キャッシュ）\n④ 連続スキャンでは読取時に1回振動。次の商品をそのまま読めます。同じ商品は枠から約1秒外すか「同じコードを再読取」を押してください。初回の商品確認中は読取を一時停止します。\n\nセブン掲載価格と希望小売価格を区別して表示します。価格改定・地域・店頭値引き等で会計額と異なります。セブンの税込小数を保持し、合計で1円未満を切り捨てます。\n\n共有検索ではJANコードを設定先のサーバーに送信します。初期設定はCloudflareです。商品をひも付けたとき、JAN・商品名・1個の税込価格・取得元・登録日を自動送信します（設定で停止可能）。送信内容は管理者の確認後に共有検索へ表示します。カメラ画像は端末内で処理し送信しません。買い物一覧・残高・Client IDは端末内に保存されます。\n\n商品情報：Yahoo!ショッピング・セブン公式ページ。セブン利用時は検索語・商品URLを公式サイトに送信します。公式の提携アプリではありません。掲載情報は在庫や店頭価格を保証しません。")
            .setPositiveButton("閉じる",null).setNeutralButton("提供元",(d,w)->openUrl("https://shopping.yahoo.co.jp/")).show();
    }
    private void openUrl(String url) {
        Uri uri=Uri.parse(url);if(!"https".equals(uri.getScheme())){message("掲載元のURLを開けません。");return;}
        try{startActivity(new Intent(Intent.ACTION_VIEW,uri));}catch(Exception e){message("ブラウザを開けません。");}
    }
    private void message(String m) {Toast.makeText(this,m,Toast.LENGTH_LONG).show();}
    @Override protected void onResume(){super.onResume();activityResumed=true;if(prefs!=null)sendNextAuto();if(scanning&&scanCamera!=null){scanGate.pauseTracking();scanCamera.resume();queueScanResume();}}
    @Override protected void onPause(){activityResumed=false;pauseDecoder();if(scanCamera!=null)scanCamera.pause();super.onPause();}
    @Override public void onWindowFocusChanged(boolean focus){super.onWindowFocusChanged(focus);if(focus)queueScanResume();else pauseDecoder();}
    @Override protected void onSaveInstanceState(Bundle state){state.putBoolean("continuousScanning",scanning);state.putString("lastScan",lastScan);super.onSaveInstanceState(state);}
    @Override protected void onDestroy(){scanning=false;activityResumed=false;scanHandler.removeCallbacksAndMessages(null);if(scanCamera!=null)scanCamera.pauseAndWait();++requestId;worker.shutdownNow();autoWorker.shutdownNow();super.onDestroy();}
}
