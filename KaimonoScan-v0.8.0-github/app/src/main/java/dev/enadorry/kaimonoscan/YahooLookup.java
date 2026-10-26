package dev.enadorry.kaimonoscan;

import org.json.*;
import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.util.*;

final class YahooLookup {
    static final class Candidate {
        String name, url;
        long price;
        Boolean taxIncluded;
    }
    static final class ApiFailure extends IOException {
        final int status;
        ApiFailure(int status, String detail) { super(detail); this.status = status; }
    }
    // Expose only classified, known error reasons. Never show raw response bodies,
    // request URLs, Client IDs or echoed credentials in the diagnostic screen.
    static ApiFailure failure(int status, String body, String authHeader) {
        String content=((body == null ? "" : body)+" "+(authHeader == null ? "" : authHeader)).toLowerCase(Locale.ROOT);
        String reason;
        if (content.contains("insufficient_scope"))
            reason="理由: insufficient_scope（利用権限不足）。Yahoo!のアプリ登録内容・利用可能なAPIを確認してください。";
        else if (content.contains("invalid_appid") || content.contains("invalid appid") || content.contains("invalid_client"))
            reason="理由: Client IDが無効と判定されました。管理画面からClient IDを再コピーして保存してください。";
        else if (status == 429)
            reason="理由: 利用回数の制限。しばらく待ってから再試行してください。";
        else if (content.contains("your request was forbidden"))
            reason="理由: Your Request was Forbidden。IDの不一致や接続許可などが考えられます。この返答だけでは特定できません。";
        else if (status == 401 || status == 403)
            reason="理由: Yahoo!側で認証またはアクセスが拒否されました。この返答だけではIDの入力ミスと利用権限不足を区別できません。";
        else if (status >= 500)
            reason="理由: Yahoo!側で処理できませんでした。一時的な障害や利用制限の可能性があります。";
        else reason="理由: 商品検索のリクエストが受け付けられませんでした。";
        return new ApiFailure(status,"Yahoo! API: HTTP "+status+"\n\n"+reason);
    }
    static String userMessage(Exception e) {
        if (e instanceof ApiFailure) return e.getMessage();
        if (e instanceof java.net.SocketTimeoutException) return "Yahoo! APIへの接続がタイムアウトしました。ネット接続を確認して再試行してください。";
        if (e instanceof javax.net.ssl.SSLException) return "Yahoo! APIとの暗号化通信に失敗しました。端末の日時やネットワーク設定を確認してください。";
        if (e instanceof JSONException) return "Yahoo! APIから正常な商品データを取得できませんでした。";
        return "Yahoo! APIに通信できませんでした。ネット接続を確認してください。";
    }
    static List<Candidate> search(String id, String jan) throws Exception {
        return parse(request(id,"jan_code="+jan+"&results=30"), jan);
    }
    static final class SearchResult {
        List<Candidate> prices;
        List<String> names;
        List<String> hints=new ArrayList<>();
        String notice="";
    }
    interface Requester {String get(String parameters) throws Exception;}
    static SearchResult lookup(String id,String jan) throws Exception {
        return lookupWith(jan,p->request(id,p));
    }
    static SearchResult lookupWith(String jan,Requester request) throws Exception {
        if(!Pricing.validJan(jan))throw new IllegalArgumentException("Invalid JAN");
        String body=request.get("jan_code="+jan+"&results=50&sale_end_from=0");
        SearchResult r=new SearchResult();r.prices=parse(body,jan);r.names=parseNames(body,jan);
        if(!r.names.isEmpty()||!r.prices.isEmpty())return r;
        try {
            body=request.get("query="+jan+"&results=50&sale_end_from=0");
            r.prices=parse(body,jan);r.names=parseNames(body,jan);r.hints=parseHints(body);
        }catch(Exception e){r.notice="追加の商品名検索に失敗しました。\n"+userMessage(e);}
        return r;
    }
    static List<String> parseHints(String body) throws JSONException {
        LinkedHashSet<String> hints=new LinkedHashSet<>();JSONArray hits=new JSONObject(body).optJSONArray("hits");
        if(hits!=null)for(int i=0;i<hits.length();i++){
            JSONObject hit=hits.optJSONObject(i);if(hit==null||!hit.optString("janCode", "").trim().isEmpty())continue;
            String name=hit.optString("name", "").trim();if(!SearchTerms.clean(name).isEmpty())hints.add(name);
            if(hints.size()>=10)break;
        }
        return new ArrayList<>(hints);
    }
    static List<String> parseNames(String body,String jan) throws JSONException {
        Set<String> names=new LinkedHashSet<>();JSONArray hits=new JSONObject(body).optJSONArray("hits");
        if(hits!=null)for(int i=0;i<hits.length();i++) {
            JSONObject h=hits.optJSONObject(i);if(h!=null&&jan.equals(h.optString("janCode"))) {
                String name=h.optString("name","").trim();if(!name.isEmpty())names.add(name);
            }
        }
        return new ArrayList<>(names);
    }
    static void testConnection(String id) throws Exception {
        JSONObject result=new JSONObject(request(id,"query=water&results=1"));
        if (result.optJSONArray("hits") == null) throw new JSONException("Missing hits");
    }
    private static String request(String id, String parameters) throws Exception {
        String endpoint = "https://shopping.yahooapis.jp/ShoppingWebService/V3/itemSearch?appid="
            + URLEncoder.encode(id, "UTF-8") + "&" + parameters;
        HttpURLConnection c = (HttpURLConnection) new URL(endpoint).openConnection();
        c.setConnectTimeout(12000); c.setReadTimeout(15000);
        try {
            int status = c.getResponseCode();
            if (status != 200) {
                String error="";
                try { error=readLimited(c.getErrorStream(), 16000); } catch (IOException ignored) {}
                throw failure(status,error,c.getHeaderField("WWW-Authenticate"));
            }
            return readLimited(c.getInputStream(),2_000_000);
        } finally { c.disconnect(); }
    }
    private static String readLimited(InputStream stream, int limit) throws IOException {
        if (stream == null) return "";
        StringBuilder body = new StringBuilder();
        try (Reader r = new InputStreamReader(stream, StandardCharsets.UTF_8)) {
            char[] buffer = new char[4096]; int n;
            while (body.length()<limit && (n=r.read(buffer,0,Math.min(buffer.length,limit-body.length()))) != -1)
                body.append(buffer,0,n);
        }
        return body.toString();
    }
    static List<Candidate> parse(String body, String jan) throws JSONException {
        List<Candidate> out = new ArrayList<>();
        JSONArray hits = new JSONObject(body).optJSONArray("hits");
        if (hits == null) return out;
        Set<String> seen = new HashSet<>();
        for (int i=0; i<hits.length(); i++) {
            JSONObject h=hits.optJSONObject(i); if (h==null) continue;
            if (!jan.equals(h.optString("janCode"))) continue;
            JSONObject label=h.optJSONObject("priceLabel"); if (label==null) continue;
            long fixed=label.optLong("fixedPrice", 0);
            if (fixed<=0 || fixed>1_000_000) continue;
            Candidate item=new Candidate(); item.name=h.optString("name", "商品");
            item.price=fixed; item.url=h.optString("url", "");
            item.taxIncluded=label.has("taxable") && !label.isNull("taxable") ? label.optBoolean("taxable") : null;
            if (seen.add(item.name + "|" + fixed + "|" + item.taxIncluded)) out.add(item);
        }
        return out;
    }
}
