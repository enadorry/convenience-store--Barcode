package dev.enadorry.kaimonoscan;

import org.json.*;
import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.math.BigDecimal;
import java.util.*;

final class CommunityApi {
    static String base(String input) throws IOException {
        try {
            URI u=new URI(input.trim());
            if(!"https".equalsIgnoreCase(u.getScheme())||u.getHost()==null||u.getRawUserInfo()!=null||u.getRawQuery()!=null||u.getRawFragment()!=null||!(u.getPath().isEmpty()||u.getPath().equals("/")))throw new Exception();
            return "https://"+u.getRawAuthority();
        }catch(Exception e){throw new IOException("共有APIのURLは https://ホスト名 の形式で入力してください。");}
    }
    static String today(){SimpleDateFormat f=new SimpleDateFormat("yyyy-MM-dd",Locale.ROOT);f.setTimeZone(TimeZone.getTimeZone("Asia/Tokyo"));return f.format(new Date());}
    static boolean recentDate(String value){
        try{if(!value.matches("[0-9]{4}-[0-9]{2}-[0-9]{2}"))return false;
            SimpleDateFormat f=new SimpleDateFormat("yyyy-MM-dd",Locale.ROOT);f.setLenient(false);f.setTimeZone(TimeZone.getTimeZone("Asia/Tokyo"));
            long date=f.parse(value).getTime(),now=f.parse(today()).getTime();return date<=now&&date>=now-90L*86400000;
        }catch(Exception e){return false;}
    }
    static long cents(String value){
        String normalized=java.text.Normalizer.normalize(value,java.text.Normalizer.Form.NFKC).trim();
        if(!normalized.matches("(?:[0-9]+|[0-9]{1,3}(?:,[0-9]{3})+)(?:\\.[0-9]{1,2})?"))throw new IllegalArgumentException();
        long c=new BigDecimal(normalized.replace(",", "")).movePointRight(2).longValueExact();
        if(c<1||c>110_000_000)throw new IllegalArgumentException();return c;
    }
    static String checked(String value,int max,boolean required){
        String v=value.trim();if((required&&v.isEmpty())||v.length()>max||v.matches("(?s).*[\\x00-\\x1f\\x7f].*"))throw new IllegalArgumentException();return v;
    }
    static JSONObject report(String jan,String name,String price,String store,String area,String date,boolean confirmed) throws JSONException {
        if(!Pricing.validJan(jan)||!recentDate(date)||!confirmed)throw new IllegalArgumentException();
        return new JSONObject().put("request_id",UUID.randomUUID().toString()).put("jan",jan).put("name",checked(name,200,true)).put("unit_cents",cents(price))
            .put("store_name",checked(store,120,true)).put("area",checked(area,120,false)).put("observed_on",date).put("shelf_confirmed",true);
    }
    static final class Price {
        String id,jan,name,store,area,date;long cents;boolean binding;
        String context(){return store+(area.isEmpty()?"":" / "+area)+"\n"+(binding?"登録日：":"店頭確認：")+date;}
    }
    static List<Price> parse(String body,String jan) throws Exception {
        JSONObject root=new JSONObject(body);if(root.optInt("api_version")!=1)throw new IOException("共有APIのバージョンが対応していません。");
        JSONArray rows=root.getJSONArray("reports");JSONObject product=root.optJSONObject("product");
        if(rows.length()>20||(rows.length()>0&&(product==null||!jan.equals(product.optString("jan")))))throw new IOException("共有データの形式が不正です。");
        ArrayList<Price> out=new ArrayList<>();
        for(int n=0;n<rows.length();n++){
            JSONObject row=rows.getJSONObject(n);Price p=new Price();p.id=row.getString("id");UUID.fromString(p.id);p.jan=jan;
            p.name=checked(row.getString("name"),200,true);p.store=checked(row.getString("store_name"),120,true);p.area=checked(row.getString("area"),120,false);p.date=row.getString("observed_on");
            if(!recentDate(p.date))continue;
            p.cents=new BigDecimal(row.get("unit_cents").toString()).longValueExact();Pricing.fractionalSubtotal(p.cents,1);if(p.cents==0)throw new IllegalArgumentException();out.add(p);
        }
        JSONArray bindings=root.optJSONArray("bindings");
        if(bindings!=null){if(bindings.length()>20)throw new IOException("共有データが多すぎます。");
            for(int n=0;n<bindings.length();n++){
                JSONObject row=bindings.getJSONObject(n);if(!jan.equals(row.getString("jan")))throw new IOException("JANが一致しません。");
                Price p=new Price();p.binding=true;p.jan=jan;p.id=row.getString("id");UUID.fromString(p.id);
                p.name=checked(row.getString("name"),200,true);p.cents=new BigDecimal(row.get("unit_cents").toString()).longValueExact();Pricing.fractionalSubtotal(p.cents,1);if(p.cents==0)throw new IllegalArgumentException();
                p.date=row.getString("observed_on");if(!recentDate(p.date))continue;
                String kind=row.getString("source_kind");
                if(kind.equals("seven")){SevenLookup.productUrl(row.getString("source_url"));p.store="セブン掲載価格の共有値";}
                else if(kind.equals("manual"))p.store="手入力の共有値（店舗未確認）";
                else throw new IOException("共有価格の取得元が不明です。");
                p.area="";out.add(p);
            }
        }
        return out;
    }
    static List<Price> lookup(String base,String jan) throws Exception {
        if(!Pricing.validJan(jan))throw new IllegalArgumentException();return parse(request(base,"/v1/products/"+jan,"GET","",null),jan);
    }
    static void health(String url) throws Exception {if(new JSONObject(request(url,"/health","GET","",null)).optInt("api_version")!=1)throw new IOException("対応する共有APIではありません。");}
    static String submit(String base,String token,JSONObject report) throws Exception {
        if(!token.matches("[0-9a-fA-F]{64}"))throw new IOException("投稿用キーを設定してください。");
        JSONObject r=new JSONObject(request(base,"/v1/reports","POST",token,report));String status=r.getString("status");
        if(!Arrays.asList("pending","approved","rejected").contains(status))throw new IOException("投稿結果を確認できませんでした。");UUID.fromString(r.getString("id"));return status;
    }
    static String submitBinding(String endpoint,JSONObject payload) throws Exception {
        JSONObject r=new JSONObject(request(endpoint,"/v1/bindings","POST","",payload));
        UUID.fromString(r.getString("id"));String status=r.getString("status");
        if(!Arrays.asList("pending","approved","rejected").contains(status))throw new IOException("送信結果を確認できませんでした。");return status;
    }
    static String error(Exception e){return e instanceof IOException&&e.getMessage()!=null?e.getMessage():"共有データを処理できませんでした。入力と接続先を確認してください。";}
    private static String request(String endpoint,String path,String method,String token,JSONObject body) throws Exception {
        HttpURLConnection c=(HttpURLConnection)new URL(base(endpoint)+path).openConnection();c.setInstanceFollowRedirects(false);c.setConnectTimeout(12000);c.setReadTimeout(15000);c.setRequestMethod(method);
        c.setRequestProperty("Accept","application/json");if(!token.isEmpty())c.setRequestProperty("Authorization","Bearer "+token);
        try {
            if(body!=null){byte[] bytes=body.toString().getBytes(StandardCharsets.UTF_8);if(bytes.length>8192)throw new IOException("投稿内容が大きすぎます。");
                c.setDoOutput(true);c.setFixedLengthStreamingMode(bytes.length);c.setRequestProperty("Content-Type","application/json");try(OutputStream out=c.getOutputStream()){out.write(bytes);}}
            int code=c.getResponseCode();if(code<200||code>=300){
                if(code==404&&path.equals("/v1/bindings"))throw new IOException("Cloudflareサーバーをv0.7.5に更新してください。未送信データは端末に残っています。");
                if(code==401||code==403)throw new IOException("共有APIの認証に失敗しました。投稿用キーを確認してください。");
                if(code==409)throw new IOException("同じ投稿IDの内容・投稿者が一致しません。未送信データを確認してください。");
                if(code==429)throw new IOException("投稿の上限です。時間を置いて再送してください。");
                if(code==400||code==413||code==415)throw new IOException("投稿内容を受け付けられませんでした。価格・日付・文字数を確認してください。");
                throw new IOException("共有APIに接続できませんでした（HTTP "+code+"）。");
            }
            try(InputStream in=c.getInputStream();ByteArrayOutputStream out=new ByteArrayOutputStream()){
                byte[] buffer=new byte[4096];int n;while((n=in.read(buffer))!=-1){if(out.size()+n>131072)throw new IOException("共有データが大きすぎます。");out.write(buffer,0,n);}return out.toString("UTF-8");
            }
        }catch(java.net.SocketTimeoutException e){throw new IOException("共有APIがタイムアウトしました。投稿は未送信一覧から同じIDで再送できます。");}
        finally{c.disconnect();}
    }
}
