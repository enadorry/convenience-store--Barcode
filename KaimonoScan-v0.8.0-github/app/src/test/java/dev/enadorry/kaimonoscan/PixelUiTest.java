package dev.enadorry.kaimonoscan;
import android.view.*;
import android.widget.*;
import org.junit.*;
import org.junit.runner.RunWith;
import org.robolectric.*;
import org.robolectric.android.controller.ActivityController;
import org.robolectric.annotation.Config;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.materialswitch.MaterialSwitch;
import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class)
@Config(sdk=34)
public class PixelUiTest {
 private ActivityController<MainActivity> controller;
 private MainActivity create(){controller=Robolectric.buildActivity(MainActivity.class).setup();return controller.get();}
 @After public void close(){if(controller!=null)controller.pause().stop().destroy();}
 private View find(View v,String value){
  if(v instanceof TextView&&value.contentEquals(((TextView)v).getText()))return v;
  if(value.contentEquals(v.getContentDescription()==null?"":v.getContentDescription()))return v;
  if(v instanceof ViewGroup)for(int i=0;i<((ViewGroup)v).getChildCount();i++){View found=find(((ViewGroup)v).getChildAt(i),value);if(found!=null)return found;}
  return null;
 }
 @Test public void homeHasMaterialButtonsAndAccessibleSettings(){MainActivity a=create();View root=a.getWindow().getDecorView();assertTrue(find(root,"スキャン") instanceof MaterialButton);assertTrue(find(root,"JAN入力") instanceof MaterialButton);assertNotNull(find(root,"設定"));assertNotNull(find(root,"合計（税込）"));assertEquals("¥0",((TextView)find(root,"¥0")).getText().toString());}
 @Test @Config(qualifiers="night") public void darkHomeAndMaterialSettingsLaunch(){MainActivity a=create();View root=a.getWindow().getDecorView();assertTrue(android.graphics.Color.luminance(((TextView)find(root,"¥0")).getCurrentTextColor())>.5f);assertTrue(find(root,"設定").performClick());android.app.Dialog dialog=org.robolectric.shadows.ShadowDialog.getLatestDialog();assertNotNull(dialog);assertTrue(dialog.isShowing());assertTrue(find(dialog.getWindow().getDecorView(),"読み取り時の振動") instanceof MaterialSwitch);dialog.dismiss();}
 @Test @Config(qualifiers="w320dp-h640dp") public void scanButtonHasTouchTargetOnSmallScreen(){MainActivity a=create();View root=a.getWindow().getDecorView();root.measure(View.MeasureSpec.makeMeasureSpec(320,View.MeasureSpec.EXACTLY),View.MeasureSpec.makeMeasureSpec(640,View.MeasureSpec.EXACTLY));root.layout(0,0,320,640);View scan=find(root,"スキャン");assertTrue(scan.getMeasuredHeight()>=48*a.getResources().getDisplayMetrics().density);assertTrue(scan.getBottom()<=((View)scan.getParent()).getHeight());}
}
