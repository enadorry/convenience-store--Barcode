package dev.enadorry.kaimonoscan;
import org.junit.Test;
import org.json.*;
import static org.junit.Assert.*;

public class AutoShareTest {
 private JSONObject payload() throws Exception{return AutoShare.payload("4901234567894","お菓子 60g",18252,"manual","",CommunityApi.today());}
 @Test public void sendsOnlyMappingWithExactUnitPriceAndNoPersonalFields() throws Exception {
  JSONObject p=payload();assertEquals(7,p.length());assertEquals(18252,p.getLong("unit_cents"));assertFalse(p.has("qty"));assertFalse(p.has("walletBalance"));assertFalse(p.has("communityKey"));assertFalse(p.has("shelf_confirmed"));assertEquals("manual",p.getString("source_kind"));
 }
 @Test public void dedupeIgnoresRequestUuidButNotPriceOrDestination() throws Exception {
  JSONObject a=payload(),b=payload();String key=AutoShare.fingerprint("https://one.example",a);
  assertEquals(key,AutoShare.fingerprint("https://one.example",b));
  assertNotEquals(key,AutoShare.fingerprint("https://two.example",b));
  b.put("unit_cents",19000);assertNotEquals(key,AutoShare.fingerprint("https://one.example",b));
 }
 @Test public void queuedRetryRetainsUuidAndAcknowledgementRemovesOnlyThatEntry() throws Exception {
  JSONObject p=payload();String key=AutoShare.fingerprint("https://one.example",p);
  JSONArray queue=new JSONArray().put(new JSONObject().put("fingerprint",key).put("payload",p)).put(new JSONObject().put("fingerprint","other"));
  assertTrue(AutoShare.contains(queue,new JSONObject(),key));assertEquals(p.getString("request_id"),new JSONArray(queue.toString()).getJSONObject(0).getJSONObject("payload").getString("request_id"));
  JSONObject sent=new JSONObject();AutoShare.remember(sent,key);queue=AutoShare.remove(queue,key);assertEquals(1,queue.length());assertTrue(AutoShare.contains(queue,sent,key));
 }
 @Test public void invalidOrZeroPriceAndNonJanNeverBecomeContributions() throws Exception {
  for(String jan:new String[]{"","4901234567890"})try{AutoShare.payload(jan,"test",10000,"manual","",CommunityApi.today());fail();}catch(IllegalArgumentException expected){}
  try{AutoShare.payload("4901234567894","test",0,"manual","",CommunityApi.today());fail();}catch(IllegalArgumentException expected){}
 }
 @Test public void approvedBindingsStayDistinctFromShelfReports() throws Exception {
  JSONObject row=payload().put("id","11111111-1111-4111-8111-111111111111");
  JSONObject response=new JSONObject().put("api_version",1).put("product",JSONObject.NULL).put("reports",new JSONArray()).put("bindings",new JSONArray().put(row));
  var prices=CommunityApi.parse(response.toString(),row.getString("jan"));assertEquals(1,prices.size());assertTrue(prices.get(0).binding);assertTrue(prices.get(0).context().contains("店舗未確認"));assertEquals(18252,prices.get(0).cents);
  row.put("jan","12345670");try{CommunityApi.parse(response.toString(),"4901234567894");fail();}catch(java.io.IOException expected){}
 }
}
