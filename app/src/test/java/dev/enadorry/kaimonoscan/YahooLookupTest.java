package dev.enadorry.kaimonoscan;
import org.junit.Test;
import static org.junit.Assert.*;
public class YahooLookupTest {
 @Test public void retailSalePriceNeverSubstitutesForFixedPrice() throws Exception {
  assertTrue(YahooLookup.parse("{\"hits\":[{\"janCode\":\"4901234567894\",\"price\":99,\"priceLabel\":{\"defaultPrice\":99,\"fixedPrice\":0}}]}","4901234567894").isEmpty());
 }
 @Test public void mismatchedOrMissingJanIsRejected() throws Exception {
  assertTrue(YahooLookup.parse("{\"hits\":[{\"janCode\":\"12345670\",\"priceLabel\":{\"fixedPrice\":100}},{\"priceLabel\":{\"fixedPrice\":100}}]}","4901234567894").isEmpty());
 }
 @Test public void fixedPriceAndUnknownTaxArePreserved() throws Exception {
  var items=YahooLookup.parse("{\"hits\":[{\"name\":\"飲み物\",\"janCode\":\"4901234567894\",\"price\":99,\"priceLabel\":{\"fixedPrice\":180}}]}","4901234567894");
  assertEquals(1,items.size());assertEquals(180,items.get(0).price);assertNull(items.get(0).taxIncluded);
 }
 @Test public void duplicateHitsDoNotCreateDuplicateChoices() throws Exception {
  String hit="{\"name\":\"飲み物\",\"janCode\":\"4901234567894\",\"priceLabel\":{\"fixedPrice\":180,\"taxable\":false}}";
  var items=YahooLookup.parse("{\"hits\":["+hit+","+hit+"]}","4901234567894");
  assertEquals(1,items.size());assertEquals(Boolean.FALSE,items.get(0).taxIncluded);
 }
 @Test public void scopeFailureIsDistinguishedWithoutEchoingCredentials() {
  String secret="do-not-display-this-client-id";
  var error=YahooLookup.failure(403,"appid="+secret,"Bearer error=\"insufficient_scope\"");
  assertTrue(error.getMessage().contains("HTTP 403"));
  assertTrue(error.getMessage().contains("insufficient_scope"));
  assertFalse(error.getMessage().contains(secret));
 }
 @Test public void forbiddenDoesNotAssertThatIdIsWrong() {
  var error=YahooLookup.failure(403,"Your Request was Forbidden",null);
  assertTrue(error.getMessage().contains("特定できません"));
 }
 @Test public void rateLimitAndUnknownAuthHaveSeparateMessages() {
  assertTrue(YahooLookup.failure(429,"",null).getMessage().contains("利用回数"));
  assertTrue(YahooLookup.failure(401,"",null).getMessage().contains("HTTP 401"));
 }
 @Test public void arbitraryServerMessagesAndRequestUrlsAreNeverDisplayed() {
  String text=YahooLookup.failure(403,"https://example.test/?appid=secret-value",null).getMessage();
  assertFalse(text.contains("secret-value"));assertFalse(text.contains("https://"));
 }
 @Test public void namesSurviveMissingMsrpWithoutUsingSalePrice() throws Exception {
  String body="{\"hits\":[{\"janCode\":\"4909411089368\",\"name\":\"ファイア 600ml\",\"price\":99}]}";
  assertTrue(YahooLookup.parse(body,"4909411089368").isEmpty());
  assertEquals("ファイア 600ml",YahooLookup.parseNames(body,"4909411089368").get(0));
 }

 @Test public void emptyJanSearchRetriesKeywordJanAndKeepsExactIdentity() throws Exception {
  java.util.List<String> calls=new java.util.ArrayList<>();
  var r=YahooLookup.lookupWith("4901234567894",p->{calls.add(p);return calls.size()==1?"{\"hits\":[]}":"{\"hits\":[{\"janCode\":\"4901234567894\",\"name\":\"お菓子\",\"priceLabel\":{\"fixedPrice\":180}}]}";});
  assertEquals(2,calls.size());assertTrue(calls.get(0).contains("jan_code="));assertTrue(calls.get(0).contains("sale_end_from=0"));assertTrue(calls.get(1).startsWith("query=4901234567894"));assertEquals(1,r.prices.size());
 }
 @Test public void unnamedJanHitsAreOnlyNameHintsNeverPrices() throws Exception {
  var r=YahooLookup.lookupWith("4901234567894",p->p.startsWith("jan_code")?"{\"hits\":[]}":"{\"hits\":[{\"name\":\"カルビー じゃがりこ 60g 12個\",\"priceLabel\":{\"fixedPrice\":2000}}]}");
  assertTrue(r.prices.isEmpty());assertTrue(r.names.isEmpty());assertEquals(1,r.hints.size());
 }
 @Test public void explicitOtherJanIsNotEvenNameHint() throws Exception {
  assertTrue(YahooLookup.parseHints("{\"hits\":[{\"name\":\"違う商品\",\"janCode\":\"12345670\"}]}").isEmpty());
 }
 @Test public void exactNameWithoutMsrpDoesNotPerformUnnecessaryRetry() throws Exception {
  int[] calls={0};var r=YahooLookup.lookupWith("4901234567894",p->{calls[0]++;return "{\"hits\":[{\"name\":\"じゃがりこ\",\"janCode\":\"4901234567894\"}]}";});
  assertEquals(1,calls[0]);assertEquals(1,r.names.size());assertTrue(r.prices.isEmpty());
 }
 @Test public void fallbackFailureIsReportedWithoutLosingManualRoute() throws Exception {
  var r=YahooLookup.lookupWith("4901234567894",p->{if(p.startsWith("query"))throw new java.io.IOException("sensitive request");return "{\"hits\":[]}";});
  assertTrue(r.prices.isEmpty());assertTrue(r.notice.contains("追加"));assertFalse(r.notice.contains("sensitive"));
 }
 @Test public void primaryAuthenticationErrorDoesNotTriggerFallback() throws Exception {
  int[] calls={0};try{YahooLookup.lookupWith("4901234567894",p->{calls[0]++;throw YahooLookup.failure(401,"",null);});fail();}catch(YahooLookup.ApiFailure e){assertEquals(401,e.status);assertEquals(1,calls[0]);}
 }
}
