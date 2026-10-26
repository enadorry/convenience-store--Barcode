package dev.enadorry.kaimonoscan;
import org.junit.Test;
import static org.junit.Assert.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
import org.json.*;
import java.util.*;

public class PBLookupTest {
 private PBLookup load() throws Exception {try(Reader reader=new InputStreamReader(getClass().getResourceAsStream("/pb-names.json"),StandardCharsets.UTF_8)){return new PBLookup(reader);}}
 @Test public void uniqueName() throws Exception {PBLookup p=load();assertEquals("ゼロサイダートリプル 500ml",p.find("4514603469110").get(0).name);}
 @Test public void unknownAndInvalid() throws Exception {PBLookup p=load();assertTrue(p.find("4909411089368").isEmpty());assertTrue(p.find("bad").isEmpty());assertTrue(p.find("4514603469111").isEmpty());}
 @Test public void ambiguousNamesRemainSeparate() throws Exception {List<PBLookup.Name> names=load().find("4573166972635");assertEquals(2,names.size());assertNotEquals(names.get(0).productId,names.get(1).productId);}
 @Test public void returnedListCannotBeMutated() throws Exception {List<PBLookup.Name> names=load().find("4514603469110");try{names.clear();fail();}catch(UnsupportedOperationException expected){}}
 @Test public void removeCapacityAndMultipacks(){assertEquals("ホットケーキミックス",PBLookup.query("ホットケーキミックス200g×3"));assertEquals("ゼロサイダートリプル",PBLookup.query("ゼロサイダートリプル ５００ｍｌ"));}
 @Test public void preserveFlavorNumbers(){assertEquals("2色サンドケーキ",PBLookup.query("2色サンドケーキ2本入"));assertEquals("3種のチーズ",PBLookup.query("3種のチーズ 90g"));}
 @Test public void snapshotIdentityAndNoPrices() throws Exception {
  String source;try(InputStream in=getClass().getResourceAsStream("/pb-names.json")){source=new String(in.readAllBytes(),StandardCharsets.UTF_8);}
  JSONArray products=new JSONObject(source).getJSONArray("products");Set<String> jans=new HashSet<>();assertEquals(2401,products.length());
  for(int i=0;i<products.length();i++){JSONObject p=products.getJSONObject(i);assertEquals(4,p.length());assertTrue(Pricing.validJan(p.getString("jan")));assertFalse(p.getString("jan").startsWith("2"));assertFalse(p.has("price"));assertFalse(p.has("cents"));jans.add(p.getString("jan"));}
  assertEquals(2400,jans.size());
 }
 @Test public void invalidAndStoreCodesExcluded() throws Exception {PBLookup p=new PBLookup(new StringReader("{\"products\":[{\"jan\":\"bad\",\"name\":\"test\"},{\"jan\":\"2000000000008\",\"name\":\"test\"}]}"));assertTrue(p.find("2000000000008").isEmpty());}
}
