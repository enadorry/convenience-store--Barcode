package dev.enadorry.kaimonoscan;
import android.view.*;
import android.widget.*;
import android.content.SharedPreferences;
import androidx.appcompat.app.AlertDialog;
import org.junit.*;
import org.junit.runner.RunWith;
import org.robolectric.*;
import org.robolectric.android.controller.ActivityController;
import org.robolectric.annotation.Config;
import org.json.*;
import static org.junit.Assert.*;
@RunWith(RobolectricTestRunner.class) @Config(sdk=34)
public class StoreUiTest {
 private ActivityController<MainActivity> controller;
 private SharedPreferences prefs(){return RuntimeEnvironment.getApplication().getSharedPreferences("shopping",0);}
 @Before public void before(){prefs().edit().clear().putBoolean("autoShare",false).commit();}
 @After public void close(){if(controller!=null)controller.pause().stop().destroy();}
 private MainActivity create(){controller=Robolectric.buildActivity(MainActivity.class).setup();return controller.get();}
 private View find(View v,String text){if(v instanceof TextView&&text.contentEquals(((TextView)v).getText()))return v;if(v instanceof EditText&&text.contentEquals(((EditText)v).getHint()))return v;if(v instanceof ViewGroup)for(int i=0;i<((ViewGroup)v).getChildCount();i++){View r=find(((ViewGroup)v).getChildAt(i),text);if(r!=null)return r;}return null;}
 private Object call(MainActivity a,String name,Class<?>[] types,Object... args) throws Exception{java.lang.reflect.Method m=MainActivity.class.getDeclaredMethod(name,types);m.setAccessible(true);return m.invoke(a,args);}
 private void store(MainActivity a,String s) throws Exception{call(a,"selectStore",new Class<?>[]{String.class},s);}
 private void lookup(MainActivity a,String jan) throws Exception{call(a,"lookup",new Class<?>[]{String.class},jan);}
 private AlertDialog dialog(){Shadows.shadowOf(android.os.Looper.getMainLooper()).idle();return (AlertDialog)org.robolectric.shadows.ShadowDialog.getLatestDialog();}
 @Test public void storeChooserPersistsSelection() throws Exception{MainActivity a=create();find(a.getWindow().getDecorView(),"セブン ▾").performClick();AlertDialog d=dialog();d.getListView().performItemClick(null,1,1);assertEquals("lawson",prefs().getString("store",""));assertNotNull(find(a.getWindow().getDecorView(),"ローソン ▾"));}
 @Test public void sameJanAtDifferentChainsKeepsPricesAndQuantitiesSeparate() throws Exception{
  String j="4909411089368";MainActivity.Item old=new MainActivity.Item(j,"コーヒー",100,1,"手入力");old.store="seven";MainActivity.Item saved=new MainActivity.Item(j,"コーヒー",200,1,"手入力");saved.store="lawson";
  prefs().edit().putString("cart",new JSONArray().put(old.json()).toString()).putString("manual",new JSONObject().put(StoreLookup.key("lawson",j),saved.json()).toString()).putString("store","lawson").commit();MainActivity a=create();lookup(a,j);dialog().getButton(-1).performClick();Shadows.shadowOf(android.os.Looper.getMainLooper()).idle();JSONArray cart=new JSONArray(prefs().getString("cart","[]"));assertEquals(2,cart.length());assertEquals(10000,cart.getJSONObject(0).getLong("cents"));assertEquals(20000,cart.getJSONObject(1).getLong("cents"));lookup(a,j);cart=new JSONArray(prefs().getString("cart","[]"));assertEquals(1,cart.getJSONObject(0).getInt("qty"));assertEquals(2,cart.getJSONObject(1).getInt("qty"));store(a,"seven");lookup(a,j);cart=new JSONArray(prefs().getString("cart","[]"));assertEquals(2,cart.getJSONObject(0).getInt("qty"));assertEquals(2,cart.getJSONObject(1).getInt("qty"));
 }
 @Test public void officialLinksAreIsolatedAndSevenLegacyLinkSurvives() throws Exception{
  MainActivity a=create();String j="4909411089368";SevenLookup.Product l=StoreLookup.parse("lawson",StoreLookupTest.lawson("322円(税込)"),StoreLookupTest.L,System.currentTimeMillis());store(a,"lawson");call(a,"storeSeven",new Class<?>[]{String.class,SevenLookup.Product.class},j,l);assertNotNull(call(a,"readSeven",new Class<?>[]{String.class},j));store(a,"family");assertNull(call(a,"readSeven",new Class<?>[]{String.class},j));store(a,"seven");assertNull(call(a,"readSeven",new Class<?>[]{String.class},j));
  JSONObject legacy=new JSONObject().put("name","コーヒー").put("url","https://www.sej.co.jp/products/a/item/123/").put("cents",10000).put("checkedAt",123);JSONObject links=new JSONObject(prefs().getString("sevenLinks","{}"));links.put(j,legacy);prefs().edit().putString("sevenLinks",links.toString()).commit();assertNotNull(call(a,"readSeven",new Class<?>[]{String.class},j));store(a,"lawson");assertEquals("lawson",((SevenLookup.Product)call(a,"readSeven",new Class<?>[]{String.class},j)).store);
 }
 @Test public void unknownProductsOfferCatalogAndLabelNameSearch() throws Exception{
  MainActivity a=create();store(a,"family");call(a,"promptSeven",new Class<?>[]{String.class,String.class,MainActivity.Item.class},"4909411089368","",null);assertNotNull(find(dialog().getWindow().getDecorView(),"カテゴリから探す"));dialog().dismiss();call(a,"reviewLabel",new Class<?>[]{String.class,String.class,MainActivity.Item.class},"4909411089368","コーヒー",null);assertNotNull(find(dialog().getWindow().getDecorView(),"ファミマで商品名を検索"));
 }
 @Test public void cachedOfficialPriceAddsCorrectStoreWithoutNetwork() throws Exception {
  String j="4909411089368";long now=System.currentTimeMillis();SevenLookup.Product l=StoreLookup.parse("lawson",StoreLookupTest.lawson("322円(税込)"),StoreLookupTest.L,now);SevenLookup.Product f=StoreLookup.parse("family",StoreLookupTest.family("431円（税込465円）"),StoreLookupTest.F,now);
  prefs().edit().putString("sevenLinks",new JSONObject().put(StoreLookup.key("lawson",j),l.json()).put(StoreLookup.key("family",j),f.json()).toString()).putString("store","family").commit();MainActivity a=create();lookup(a,j);JSONArray cart=new JSONArray(prefs().getString("cart","[]"));assertEquals("family",cart.getJSONObject(0).getString("store"));assertEquals(46500,cart.getJSONObject(0).getLong("cents"));store(a,"lawson");lookup(a,j);cart=new JSONArray(prefs().getString("cart","[]"));assertEquals(2,cart.length());assertEquals("lawson",cart.getJSONObject(1).getString("store"));assertEquals(32200,cart.getJSONObject(1).getLong("cents"));
 }
 @Test public void confirmationMustBeCheckedBeforeJanLinkIsSaved() throws Exception {
  MainActivity a=create();store(a,"family");String j="4909411089368";SevenLookup.Product f=StoreLookup.parse("family",StoreLookupTest.family("431円（税込465円）"),StoreLookupTest.F,System.currentTimeMillis());call(a,"confirmSeven",new Class<?>[]{String.class,SevenLookup.Product.class,MainActivity.Item.class},j,f,null);AlertDialog d=dialog();assertFalse(d.getButton(-1).isEnabled());assertEquals("{}",prefs().getString("sevenLinks","{}"));((CheckBox)find(d.getWindow().getDecorView(),"手元の商品と同じ商品・容量であることを確認した")).setChecked(true);d.getButton(-1).performClick();assertNotNull(new JSONObject(prefs().getString("sevenLinks","{}")).getJSONObject(StoreLookup.key("family",j)));assertEquals("family",new JSONArray(prefs().getString("cart","[]")).getJSONObject(0).getString("store"));
 }

 @Test public void checkoutGroupsJanAndShowsMissingCodesWithoutChangingCart() throws Exception {
  JSONArray cart=new JSONArray();cart.put(new MainActivity.Item("4909411089368","コーヒー",200,2,"手入力").json());cart.put(new MainActivity.Item("4909411089368","コーヒー",100,1,"手入力").json());cart.put(new MainActivity.Item("96385074","別の商品",100,1,"手入力").json());cart.put(new MainActivity.Item("","野菜",100,1,"手入力").json());String before=cart.toString();prefs().edit().putString("cart",before).commit();MainActivity a=create();call(a,"checkoutBarcodes",new Class<?>[]{});AlertDialog d=dialog();assertNotNull(find(d.getWindow().getDecorView(),"3個"));assertNotNull(find(d.getWindow().getDecorView(),"4909411089368"));assertNotNull(find(d.getWindow().getDecorView(),"バーコードなし：1点"));find(d.getWindow().getDecorView(),"次へ").performClick();assertNotNull(find(d.getWindow().getDecorView(),"96385074"));assertEquals(before,prefs().getString("cart",""));
 }

}
