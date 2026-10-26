package dev.enadorry.kaimonoscan;

import org.json.*;
import java.io.*;
import java.text.Normalizer;
import java.util.*;

/** Name candidates only. Prices always come from the selected Seven product page. */
final class PBLookup {
    static final class Name {
        final String jan, productId, name, url;
        Name(String j,String id,String n,String u){jan=j;productId=id;name=n;url=u;}
    }
    private final Map<String,List<Name>> index=new HashMap<>();
    PBLookup(Reader input) throws Exception {
        StringBuilder data=new StringBuilder();char[] buffer=new char[4096];int n;
        while((n=input.read(buffer))!=-1){data.append(buffer,0,n);if(data.length()>2_000_000)throw new IOException("PB索引が大きすぎます。");}
        JSONArray products=new JSONObject(data.toString()).getJSONArray("products");
        for(int i=0;i<products.length();i++){
            JSONObject p=products.getJSONObject(i);String jan=p.getString("jan"),name=p.getString("name");
            if(!Pricing.validJan(jan)||jan.startsWith("2")||name.trim().isEmpty())continue;
            index.computeIfAbsent(jan,k->new ArrayList<>()).add(new Name(jan,p.getString("productId"),name,p.getString("url")));
        }
    }
    List<Name> find(String jan){
        if(!Pricing.validJan(jan)||jan.startsWith("2"))return Collections.emptyList();
        return Collections.unmodifiableList(index.getOrDefault(jan,Collections.emptyList()));
    }
    static String query(String name){
        String q=Normalizer.normalize(name,Normalizer.Form.NFKC)
            .replaceAll("(?i)[0-9]+(?:\\.[0-9]+)?\\s*(?:kg|ml|g|l|枚|個|本|袋|粒|食|切|ロール)(?:\\s*[×x*]\\s*[0-9]+(?:\\.[0-9]+)?\\s*(?:kg|ml|g|l|枚|個|本|袋|粒|食|切|ロール)?)*(?:入り|入)?"," ")
            .replaceAll("(?i)セブンプレミアム|SEVEN PREMIUM"," ");
        return SearchTerms.clean(q);
    }
}
