package dev.enadorry.kaimonoscan;
import org.junit.Test;
import static org.junit.Assert.*;
import java.nio.charset.StandardCharsets;
/** Minimal unmodified title/normal-price HTML blocks retrieved 2026-10-26 from the test URLs. */
public class StoreLiveFixtureTest {
 private String fixture(String name) throws Exception{return new String(getClass().getResourceAsStream("/"+name).readAllBytes(),StandardCharsets.UTF_8);}
 @Test public void lawsonOfficialMarkup() throws Exception{SevenLookup.Product p=StoreLookup.parse("lawson",fixture("lawson-product.html"),StoreLookupTest.L,123);assertEquals("プレミアムおにぎり　いくら醤油漬",p.name);assertEquals(32200,p.cents);}
 @Test public void familyOfficialMarkup() throws Exception{SevenLookup.Product p=StoreLookup.parse("family",fixture("family-product.html"),StoreLookupTest.F,123);assertEquals("30種類以上のスパイスが決め手のこだわりカレー",p.name);assertEquals(46500,p.cents);}
}
