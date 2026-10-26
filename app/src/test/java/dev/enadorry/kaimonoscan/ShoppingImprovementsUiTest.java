package dev.enadorry.kaimonoscan;
import android.view.*;
import android.widget.*;
import androidx.appcompat.app.AlertDialog;
import org.junit.*;
import org.junit.runner.RunWith;
import org.robolectric.*;
import org.robolectric.android.controller.ActivityController;
import org.robolectric.annotation.Config;
import org.json.*;
import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class) @Config(sdk=34)
public class ShoppingImprovementsUiTest {
 private ActivityController<MainActivity> controller;
 @After public void close(){if(controller!=null)controller.pause().stop().destroy();}
 private MainActivity create(){controller=Robolectric.buildActivity(MainActivity.class).setup();return controller.get();}
 private View find(View v,String text){if(v instanceof TextView&&text.contentEquals(((TextView)v).getText()))return v;if(v instanceof EditText&&text.contentEquals(((EditText)v).getHint()))return v;if(v instanceof ViewGroup)for(int i=0;i<((ViewGroup)v).getChildCount();i++){View r=find(((ViewGroup)v).getChildAt(i),text);if(r!=null)return r;}return null;}
 private AlertDialog dialog(){org.robolectric.Shadows.shadowOf(android.os.Looper.getMainLooper()).idle();return (AlertDialog)org.robolectric.shadows.ShadowDialog.getLatestDialog();}
 @Test public void discountOneOfThreeAndRescanKeepsRegularPrice() throws Exception {
  android.content.SharedPreferences prefs=RuntimeEnvironment.getApplication().getSharedPreferences("shopping",0);MainActivity.Item item=new MainActivity.Item("4909411089368","コーヒー",200,3,"手入力");prefs.edit().putString("cart",new JSONArray().put(item.json()).toString()).putLong("walletBalance",1000).commit();
  MainActivity a=create();find(a.getWindow().getDecorView(),"割引").performClick();AlertDialog d=dialog();find(d.getWindow().getDecorView(),"半額").performClick();d.getButton(-1).performClick();
  JSONArray cart=new JSONArray(prefs.getString("cart","[]"));assertEquals(2,cart.length());assertEquals(2,cart.getJSONObject(0).getInt("qty"));assertEquals(1,cart.getJSONObject(1).getInt("qty"));assertEquals(20000,cart.getJSONObject(1).getLong("cents"));assertNotNull(find(a.getWindow().getDecorView(),"¥500"));
  java.lang.reflect.Method lookup=MainActivity.class.getDeclaredMethod("lookup",String.class);lookup.setAccessible(true);lookup.invoke(a,"4909411089368");cart=new JSONArray(prefs.getString("cart","[]"));assertEquals(3,cart.getJSONObject(0).getInt("qty"));assertEquals(1,cart.getJSONObject(1).getInt("qty"));assertNotNull(find(a.getWindow().getDecorView(),"¥700"));
 }
 @Test public void labelReviewRequiresConfirmationAndDoesNotSaveSaleAsRegular() throws Exception {
  MainActivity a=create();java.lang.reflect.Method review=MainActivity.class.getDeclaredMethod("reviewLabel",String.class,String.class,MainActivity.Item.class);review.setAccessible(true);review.invoke(a,"4909411089368","牛乳\n本体198円(税込213.84円)",null);AlertDialog d=dialog();assertFalse(d.getButton(-1).isEnabled());((CheckBox)find(d.getWindow().getDecorView(),"商品名・1個の税込価格を確認した")).setChecked(true);d.getButton(-1).performClick();
  android.content.SharedPreferences prefs=a.getSharedPreferences("shopping",0);JSONObject item=new JSONArray(prefs.getString("cart","[]")).getJSONObject(0);assertEquals("4909411089368",item.getString("jan"));assertEquals(21384,item.getLong("cents"));assertTrue(item.getBoolean("labelPrice"));assertEquals("{}",prefs.getString("manual","{}"));assertEquals("[]",prefs.getString("autoQueue","[]"));
 }
 @Test public void missingTaxPriceRequiresUserInput() throws Exception {
  MainActivity a=create();java.lang.reflect.Method review=MainActivity.class.getDeclaredMethod("reviewLabel",String.class,String.class,MainActivity.Item.class);review.setAccessible(true);review.invoke(a,"4909411089368","牛乳\n198円",null);AlertDialog d=dialog();EditText price=(EditText)find(d.getWindow().getDecorView(),"1個の税込価格（円）");assertEquals("",price.getText().toString());((CheckBox)find(d.getWindow().getDecorView(),"商品名・1個の税込価格を確認した")).setChecked(true);d.getButton(-1).performClick();assertTrue(d.isShowing());assertNotNull(price.getError());
 }
 @Test public void lookupFormOffersLabelCamera() throws Exception {
  MainActivity a=create();java.lang.reflect.Method prompt=MainActivity.class.getDeclaredMethod("promptSeven",String.class,String.class,MainActivity.Item.class);prompt.setAccessible(true);prompt.invoke(a,"4909411089368","コーヒー",null);assertNotNull(find(dialog().getWindow().getDecorView(),"値札を読む"));
 }
 @Test public void rememberedLabelPriceKeepsFractionalYenOnReuse() throws Exception {
  android.content.SharedPreferences prefs=RuntimeEnvironment.getApplication().getSharedPreferences("shopping",0);MainActivity.Item saved=new MainActivity.Item("4909411089368","牛乳",213,1,"値札確認");saved.cents=21384;prefs.edit().putString("manual",new JSONObject().put(saved.jan,saved.json()).toString()).commit();
  MainActivity a=create();java.lang.reflect.Method lookup=MainActivity.class.getDeclaredMethod("lookup",String.class);lookup.setAccessible(true);lookup.invoke(a,saved.jan);dialog().getButton(-1).performClick();org.robolectric.Shadows.shadowOf(android.os.Looper.getMainLooper()).idle();assertEquals(21384,new JSONArray(prefs.getString("cart","[]")).getJSONObject(0).getLong("cents"));
 }
}
