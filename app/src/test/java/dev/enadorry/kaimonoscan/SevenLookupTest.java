package dev.enadorry.kaimonoscan;
import org.junit.Test;
import org.json.JSONObject;
import java.io.IOException;
import java.util.List;
import static org.junit.Assert.*;
public class SevenLookupTest {
 static final String URL="https://www.sej.co.jp/products/a/item/460833/";
 static final String DETAIL="<div class='item_ttl'><h1>テストコーヒー ６００ｍｌ</h1></div><div class='item_price'><p>169円（税込182.52円）</p></div><div class='item_region'>販売地域：九州</div>";
 @Test public void parseRealPagePriceFormatPreservesFractionsAndSource() throws Exception {
  var p=SevenLookup.parseProduct(DETAIL,URL,12345);assertEquals(18252,p.cents);assertEquals(URL,p.url);assertEquals(12345,p.checkedAt);assertEquals("販売地域：九州",p.region);
 }
 @Test public void hiddenUnavailableTemplateDoesNotMaskVisibleProduct() throws Exception {
  var p=SevenLookup.parseProduct("<div style='display: none'><p class='blacklist_error'>現在この商品の情報を表示できません。</p></div>"+DETAIL,URL,100);
  assertEquals(18252,p.cents);
 }
 @Test(expected=IOException.class) public void visibleUnavailablePageIsRejectedDespiteLeftoverPrice() throws Exception {
  SevenLookup.parseProduct("<p class='blacklist_error'>現在この商品の情報を表示できません。</p>"+DETAIL,URL,100);
 }
 @Test(expected=IOException.class) public void missingPriceNeverUsesRandomNumbersOrZero() throws Exception {
  SevenLookup.parseProduct("<div class='item_ttl'><h1>600ml コーヒー</h1></div><p>JAN4909411089368 販売地域 2026</p>",URL,1);
 }
 @Test(expected=IOException.class) public void multipleVisiblePricesAreNotGuessed() throws Exception {
  SevenLookup.parseProduct(DETAIL+"<div class='item_price'>200円（税込216円）</div>",URL,1);
 }
 @Test public void onlyOfficialProductDetailUrlsAreAccepted() throws Exception {
  assertEquals(URL,SevenLookup.productUrl(URL+"?utm_source=test#price"));
  for(String bad:List.of("http://www.sej.co.jp/products/a/item/1/","https://www.sej.co.jp.evil.test/products/a/item/1/","https://www.sej.co.jp@evil.test/products/a/item/1/","https://www.sej.co.jp/products/a/item/../1/","https://www.sej.co.jp/products/a/item/%31/","https://127.0.0.1/products/a/item/1/","https://www.sej.co.jp/products/a/itemresult/")) {
   try{SevenLookup.productUrl(bad);fail(bad);}catch(IOException expected){}
  }
 }
 @Test public void resultCardsAreDeduplicatedAndExternalLinksRejected() throws Exception {
  String card="<div class='list_inner'><div class='item_ttl'><a href='/products/a/item/460833/'>コーヒー</a></div><div class='item_price'>169円（税込182.52円）</div></div>";
  var links=SevenLookup.parseSearch("<h1>商品の検索結果</h1>"+card+card+"<div class='list_inner'><div class='item_ttl'><a href='https://evil.test/products/a/item/1/'>偽物</a></div></div>");
  assertEquals(1,links.size());assertEquals(URL,links.get(0).url);
 }
 @Test(expected=IOException.class) public void nonSearchHtmlIsAnErrorRatherThanNoResults() throws Exception {
  SevenLookup.parseSearch("<h1>メンテナンス中</h1>");
 }
 @Test public void cachedProductRoundTripsAndExpiresAt24Hours() throws Exception {
  var p=SevenLookup.parseProduct(DETAIL,URL,1000);var restored=SevenLookup.Product.read(new JSONObject(p.json().toString()));
  assertEquals(p.cents,restored.cents);assertTrue(SevenLookup.fresh(restored,1000+SevenLookup.CACHE_MS-1));
  assertFalse(SevenLookup.fresh(restored,1000+SevenLookup.CACHE_MS));assertFalse(SevenLookup.fresh(restored,999));
 }
 @Test public void shorterFallbackRetainsUsefulKeyword() {
  assertEquals("ファイア",SevenLookup.fallbackQuery("キリン ファイア ワンデイ ブラック 600ml PET 24本"));
 }
 @Test public void fractionalAmountsAccumulateBeforeYenRounding() {
  long hundredths=Pricing.fractionalSubtotal(18252,2);
  assertEquals(36504,hundredths);assertEquals(365,hundredths/100);
  assertEquals(18252*999L,Pricing.fractionalSubtotal(18252,999));
 }
}
