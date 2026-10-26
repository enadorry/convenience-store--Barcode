package dev.enadorry.kaimonoscan;
import org.junit.Test;
import static org.junit.Assert.*;
import java.io.*;
import org.json.*;

public class StoreLookupTest {
 static final String L="https://www.lawson.co.jp/recommend/original/detail/1527632_1996.html";
 static final String F="https://www.family.co.jp/goods/obento/0730648.html";
 static String lawson(String price){return "<div class='rightBlock'><h2 class='ttl'>おにぎり</h2><dl class='price'><dt>ローソン標準価格</dt><dd>"+price+"</dd></dl></div>";}
 static String family(String price){return "<h1 class='ly-mod-ttl-main'>カレー</h1><div class='ly-goodsinfo-area'><span class='ly-kakaku-usual'>"+price+"</span><span class='ly-kakaku-sale'>税込100円</span></div>";}
 @Test public void separatesStoresAndKeepsLegacySevenKey(){String j="4909411089368";assertEquals(j,StoreLookup.key("seven",j));assertNotEquals(StoreLookup.key("lawson",j),StoreLookup.key("family",j));assertEquals("",StoreLookup.janForKey("seven","lawson:"+j));assertEquals(j,StoreLookup.janForKey("lawson","lawson:"+j));assertEquals("",StoreLookup.janForKey("family","lawson:"+j));}
 @Test public void validatesExactStoreHostAndProductPath() throws Exception {
  assertEquals(L,StoreLookup.productUrl("lawson",L));assertEquals(F,StoreLookup.productUrl("family",F));
  for(String bad:new String[]{L.replace("https:","http:"),L+"?x=1",L+"#price",L.replace("www.lawson.co.jp","www.lawson.co.jp.evil.test"),L.replace("www.lawson.co.jp","user@www.lawson.co.jp"),L.replace("www.lawson.co.jp","www.lawson.co.jp:443"),L.replace("1527632_1996.html","../1527632_1996.html"),"https://www.lawson.co.jp/recommend/original/",F})assertThrows(IOException.class,()->StoreLookup.productUrl("lawson",bad));
  assertThrows(IOException.class,()->StoreLookup.productUrl("family",L));
 }
 @Test public void lawsonReadsStandardTaxInclusivePriceOnly() throws Exception{SevenLookup.Product p=StoreLookup.parse("lawson","<div>セール100円</div>"+lawson("<span>322</span><span>円(税込)</span>"),L,123);assertEquals(32200,p.cents);assertEquals("lawson",p.store);assertEquals("おにぎり",p.name);}
 @Test public void familyIgnoresSaleAndPointsPrices() throws Exception{SevenLookup.Product p=StoreLookup.parse("family",family("431円<span>（税込</span>465円<span>）</span>"),F,123);assertEquals(46500,p.cents);assertEquals("family",p.store);}
 @Test public void refusesAmbiguousOrMissingNormalPrices(){assertThrows(IOException.class,()->StoreLookup.parse("lawson",lawson("322円(税込) 400円(税込)"),L,123));assertThrows(IOException.class,()->StoreLookup.parse("family",family("431円（税抜）"),F,123));assertThrows(IOException.class,()->StoreLookup.parse("family",family("431円（税込465円）")+family("431円（税込465円）"),F,123));assertThrows(IOException.class,()->StoreLookup.parse("lawson",lawson("322円(税込)").replace("標準価格","クーポン価格"),L,123));}
 @Test public void hiddenPricesDoNotCount() throws Exception{assertEquals(32200,StoreLookup.parse("lawson",lawson("322円(税込)")+"<div hidden>"+lawson("100円(税込)")+"</div>",L,123).cents);}
 @Test public void productRoundTripAndLegacyMigration() throws Exception{SevenLookup.Product p=StoreLookup.parse("family",family("431円（税込465円）"),F,123);SevenLookup.Product copy=SevenLookup.Product.read(p.json());assertEquals("family",copy.store);assertEquals(F,copy.url);JSONObject old=new JSONObject().put("name","コーヒー").put("url","https://www.sej.co.jp/products/a/item/123/").put("cents",10000).put("checkedAt",123);assertEquals("seven",SevenLookup.Product.read(old).store);assertThrows(IOException.class,()->SevenLookup.Product.read(p.json().put("store","seven")));}
 @Test public void newStoreSharingNeverMasqueradesAsSeven() throws Exception{JSONObject p=AutoShare.payload("4909411089368","コーヒー",20000,"manual",L,CommunityApi.today());assertEquals("manual",p.getString("source_kind"));assertEquals("",p.getString("source_url"));}
}
