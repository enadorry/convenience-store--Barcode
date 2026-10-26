package dev.enadorry.kaimonoscan;

import org.json.*;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.*;

/** Persistent queue operations are called on the UI thread; retries retain request_id. */
final class AutoShare {
    static JSONObject payload(String jan,String name,long cents,String kind,String url,String date) throws Exception {
        if(!Pricing.validJan(jan)||cents<1||cents>110000000||!Arrays.asList("manual","seven").contains(kind)||!CommunityApi.recentDate(date))throw new IllegalArgumentException();
        if(kind.equals("seven"))url=SevenLookup.productUrl(url);else url="";
        return new JSONObject().put("request_id",UUID.randomUUID().toString()).put("jan",jan).put("name",CommunityApi.checked(name,200,true))
            .put("unit_cents",cents).put("source_kind",kind).put("source_url",url).put("observed_on",date);
    }
    static String fingerprint(String endpoint,JSONObject p) throws Exception {
        String value=new JSONArray().put(endpoint).put(p.getString("jan")).put(p.getString("name")).put(p.getLong("unit_cents"))
            .put(p.getString("source_kind")).put(p.getString("source_url")).put(p.getString("observed_on")).toString();
        byte[] bytes=MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8));
        StringBuilder out=new StringBuilder();for(byte b:bytes)out.append(String.format(Locale.ROOT,"%02x",b&255));return out.toString();
    }
    static boolean contains(JSONArray queue,JSONObject sent,String fingerprint) throws Exception {
        if(sent.has(fingerprint))return true;
        for(int n=0;n<queue.length();n++)if(fingerprint.equals(queue.getJSONObject(n).getString("fingerprint")))return true;
        return false;
    }
    static JSONArray remove(JSONArray queue,String fingerprint) throws Exception {
        JSONArray result=new JSONArray();for(int n=0;n<queue.length();n++)if(!fingerprint.equals(queue.getJSONObject(n).getString("fingerprint")))result.put(queue.getJSONObject(n));return result;
    }
    static void remember(JSONObject sent,String fingerprint) throws Exception {
        sent.put(fingerprint,System.currentTimeMillis());
        while(sent.length()>500){String oldest=null;long time=Long.MAX_VALUE;Iterator<String> keys=sent.keys();while(keys.hasNext()){String k=keys.next();if(sent.getLong(k)<time){time=sent.getLong(k);oldest=k;}}sent.remove(oldest);}
    }
}
