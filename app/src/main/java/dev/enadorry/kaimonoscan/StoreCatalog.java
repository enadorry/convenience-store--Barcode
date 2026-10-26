package dev.enadorry.kaimonoscan;
import java.io.*;
import java.text.Normalizer;
import java.util.*;
import org.json.*;

/** Name/URL discovery only; current prices always come from the chosen detail page. */
final class StoreCatalog {
    static final class Entry {
        final String store,name,url,region,category;
        Entry(JSONObject o) throws Exception{store=o.getString("store");name=o.getString("name");url=StoreLookup.productUrl(store,o.getString("url"));region=o.optString("region","");category=o.optString("category","");if(store.equals("seven")||name.isEmpty())throw new IOException("不正な商品");}
    }
    final List<Entry> products=new ArrayList<>();
    StoreCatalog(Reader input) throws Exception{
        StringBuilder s=new StringBuilder();char[] a=new char[4096];int n;while((n=input.read(a))!=-1){s.append(a,0,n);if(s.length()>3_000_000)throw new IOException("商品一覧が大きすぎます。");}
        JSONArray list=new JSONObject(s.toString()).getJSONArray("products");Set<String> seen=new HashSet<>();
        for(int i=0;i<list.length();i++){Entry e=new Entry(list.getJSONObject(i));if(seen.add(e.url))products.add(e);}
    }
    static String normalize(String value){return Normalizer.normalize(value,Normalizer.Form.NFKC).toLowerCase(Locale.ROOT).replaceAll("ファミリーマート|ファミマル|ローソンオリジナル|ローソンセレクト","").replaceAll("[\\p{P}\\p{Z}\\s]","");}
    List<String> categories(String store){Set<String> set=new TreeSet<>();for(Entry e:products)if(e.store.equals(store)&&!e.category.isEmpty())set.add(e.category);return new ArrayList<>(set);}
    SevenLookup.SearchResult search(String store,String query,String category){
        SevenLookup.SearchResult result=searchOnce(store,query,category);String shorter=SearchTerms.shorter(query);
        if(result.links.isEmpty()&&!shorter.isEmpty()&&!shorter.equals(query))return searchOnce(store,shorter,category);return result;
    }
    private SevenLookup.SearchResult searchOnce(String store,String query,String category){
        String q=normalize(SearchTerms.clean(query));List<Entry> hits=new ArrayList<>();
        for(Entry e:products){if(!e.store.equals(store)||(!category.isEmpty()&&!e.category.equals(category)))continue;String name=normalize(e.name);boolean match=q.isEmpty()||name.contains(q);if(!match){String[] tokens=SearchTerms.clean(query).split("[\\s　]+");match=tokens.length>1;for(String t:tokens)if(!name.contains(normalize(t)))match=false;}if(match)hits.add(e);}
        hits.sort(Comparator.comparingInt((Entry e)->normalize(e.name).equals(q)?0:normalize(e.name).startsWith(q)?1:2).thenComparing(e->e.name));
        List<SevenLookup.Link> out=new ArrayList<>();for(Entry e:hits){out.add(new SevenLookup.Link(e.name,e.url,"",""));if(out.size()>=500)break;}return new SevenLookup.SearchResult(query,out);
    }
}
