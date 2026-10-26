package dev.enadorry.kaimonoscan;
import java.io.*;
import org.junit.Test;
import static org.junit.Assert.*;
public class StoreCatalogTest {
 private StoreCatalog catalog() throws Exception{return new StoreCatalog(new InputStreamReader(getClass().getResourceAsStream("/store-products.json"),"UTF-8"));}
 @Test public void catalogHasBothChainsWithoutInventedJanOrPrices() throws Exception{StoreCatalog c=catalog();assertTrue(c.products.size()>100);for(String s:new String[]{"lawson","family"}){assertTrue(c.categories(s).size()>3);assertTrue(c.search(s,"","").links.size()>10);}for(StoreCatalog.Entry e:c.products)assertEquals(e.url,StoreLookup.productUrl(e.store,e.url));}
 @Test public void searchAndBrowseStayWithinSelectedChain() throws Exception{StoreCatalog c=catalog();for(String s:new String[]{"lawson","family"}){for(SevenLookup.Link l:c.search(s,"チョコ","").links){assertEquals(s,StoreLookup.storeForUrl(l.url));assertEquals("",l.priceText);}String cat=c.categories(s).get(0);assertFalse(c.search(s,"",cat).links.isEmpty());assertTrue(c.search(s,"絶対に該当しない検索語012345",cat).links.isEmpty());}}
 @Test public void stripsBrandAndWidthVariations(){assertEquals(StoreCatalog.normalize("ローソンオリジナル　チョコ １００ｇ"),StoreCatalog.normalize("チョコ100g"));assertEquals("カレー",StoreCatalog.normalize("ファミマル カレー"));}
}
