package dev.enadorry.kaimonoscan;
import org.junit.Test;
import org.json.*;
import static org.junit.Assert.*;
public class CommunityApiTest {
 @Test public void endpointRequiresHttpsOriginAndNoEmbeddedSecrets() throws Exception {
  assertEquals("https://test.example",CommunityApi.base("https://test.example/"));
  for(String url:new String[]{"http://test.example","https://name:secret@test.example","https://test.example/api","https://test.example/?token=secret","https://test.example/#secret","https:///missing"})try{CommunityApi.base(url);fail(url);}catch(java.io.IOException expected){}
 }
 @Test public void fractionalYenAndFullWidthInputAreExact(){assertEquals(18252,CommunityApi.cents("１８２．５２"));assertEquals(120000,CommunityApi.cents("1,200"));for(String input:new String[]{"0","-1","1.001","1100000.01","1,2","1e3"})try{CommunityApi.cents(input);fail(input);}catch(Exception expected){}}
 @Test public void payloadHasOnlyExplicitReportFields() throws Exception {
  JSONObject r=CommunityApi.report("4901234567894","お菓子 60g","182.52","テスト店","長崎県",CommunityApi.today(),true);
  assertEquals(8,r.length());assertEquals(18252,r.getLong("unit_cents"));assertFalse(r.has("walletBalance"));assertFalse(r.has("clientId"));assertFalse(r.has("communityKey"));
 }
 @Test public void rejectsUnconfirmedOrUnknownDate() throws Exception {
  assertFalse(CommunityApi.recentDate("2099-01-01"));assertFalse(CommunityApi.recentDate("2026-02-30"));assertFalse(CommunityApi.recentDate("2000-01-01"));assertTrue(CommunityApi.recentDate(CommunityApi.today()));
  try{CommunityApi.report("4901234567894","お菓子","100","店","",CommunityApi.today(),false);fail();}catch(IllegalArgumentException expected){}
 }
 private String response(String jan,Object price) throws Exception {
  return new JSONObject().put("api_version",1).put("product",new JSONObject().put("jan",jan).put("name","お菓子"))
   .put("reports",new JSONArray().put(new JSONObject().put("id","11111111-1111-4111-8111-111111111111").put("name","お菓子").put("unit_cents",price).put("store_name","店").put("area","長崎県").put("observed_on",CommunityApi.today()))).toString();
 }
 @Test public void approvedReportKeepsCentsStoreAndDate() throws Exception {
  var rows=CommunityApi.parse(response("4901234567894",18252),"4901234567894");assertEquals(1,rows.size());assertEquals(18252,rows.get(0).cents);assertTrue(rows.get(0).context().contains("長崎県"));
 }
 @Test public void mismatchedJanOrFractionalIntegerCentsAreRejected() throws Exception {
  try{CommunityApi.parse(response("12345670",10000),"4901234567894");fail();}catch(java.io.IOException expected){}
  try{CommunityApi.parse(response("4901234567894",18252.5),"4901234567894");fail();}catch(ArithmeticException expected){}
 }
 @Test public void emptyCatalogIsNotAFreeProduct() throws Exception {assertTrue(CommunityApi.parse("{\"api_version\":1,\"product\":null,\"reports\":[]}","4901234567894").isEmpty());}
}
