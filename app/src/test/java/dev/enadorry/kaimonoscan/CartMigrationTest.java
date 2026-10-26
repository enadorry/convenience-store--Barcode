package dev.enadorry.kaimonoscan;
import org.junit.Test;
import org.json.JSONObject;
import static org.junit.Assert.*;
public class CartMigrationTest {
 @Test public void oldCartWithoutNewFieldsStillLoads() throws Exception {
  JSONObject legacy=new JSONObject().put("jan","4909411089368").put("name","コーヒー").put("price",213).put("qty",2).put("source","手入力");
  var i=MainActivity.Item.read(legacy);
  assertEquals(21300,i.cents);assertEquals(2,i.qty);assertEquals("",i.sourceUrl);
 }
 @Test public void fractionalPricesAndProvenanceSurviveRestart() throws Exception {
  var i=new MainActivity.Item("4909411089368","コーヒー",182,2,"セブン掲載価格");
  i.cents=18252;i.checkedAt=1000;i.region="販売地域：九州";i.sourceUrl="https://www.sej.co.jp/products/a/item/460833/";
  var restored=MainActivity.Item.read(new JSONObject(i.json().toString()));
  assertEquals(36504,Pricing.fractionalSubtotal(restored.cents,restored.qty));
  assertEquals(i.sourceUrl,restored.sourceUrl);assertEquals(i.checkedAt,restored.checkedAt);assertEquals(i.region,restored.region);
 }
}
