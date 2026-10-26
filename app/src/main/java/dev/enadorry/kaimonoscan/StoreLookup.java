package dev.enadorry.kaimonoscan;

import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.math.BigDecimal;
import java.text.Normalizer;
import java.util.*;
import java.util.regex.*;
import org.jsoup.Jsoup;
import org.jsoup.nodes.*;

/** Public official pages only. No JANs are inferred from product URL IDs. */
final class StoreLookup {
    private static long lastRequest;
    static boolean validStore(String s){return "seven".equals(s)||"lawson".equals(s)||"family".equals(s);}
    static String name(String s){return "lawson".equals(s)?"ローソン":"family".equals(s)?"ファミマ":"セブン";}
    static String key(String s,String jan){if(!validStore(s)||!Pricing.validJan(jan))throw new IllegalArgumentException("店舗・JANが不正です");return s.equals("seven")?jan:s+":"+jan;}
    static String janForKey(String s,String key){String j=s.equals("seven")?key:key.startsWith(s+":")?key.substring(s.length()+1):"";return Pricing.validJan(j)?j:"";}
    static String storeForUrl(String value) throws IOException {
        try {String host=new URI(value).getHost();if("www.sej.co.jp".equals(host))return "seven";if("www.lawson.co.jp".equals(host))return "lawson";if("www.family.co.jp".equals(host))return "family";}catch(Exception ignored){}
        throw new IOException("対応する店舗の公式商品URLを入力してください。");
    }
    static String productUrl(String store,String value) throws IOException {
        try {
            URI u=new URI(value.trim());
            if(!validStore(store)||!"https".equals(u.getScheme())||u.getUserInfo()!=null||u.getPort()!=-1||u.getRawQuery()!=null||u.getRawFragment()!=null||!store.equals(storeForUrl(value.trim())))throw new Exception();
            if(store.equals("seven"))return SevenLookup.productUrl(value);
            String pattern=store.equals("lawson")?"/recommend/original/detail/[0-9]+_[0-9]+\\.html":"/goods/[a-z0-9_-]+/[0-9]+\\.html";
            if(!u.getRawPath().matches(pattern))throw new Exception();return u.toString();
        }catch(Exception e){throw new IOException(name(store)+"公式の商品詳細URLを入力してください。");}
    }
    static SevenLookup.Product load(String store,String url) throws Exception {
        String safe=productUrl(store,url);if(store.equals("seven"))return SevenLookup.load(safe);
        return parse(store,fetch(store,safe),safe,System.currentTimeMillis());
    }
    private static String fetch(String store,String value) throws Exception {
        String next=value;
        for(int n=0;n<4;n++){
            URL u=new URL(productUrl(store,next));
            synchronized(StoreLookup.class){long delay=1200-(System.nanoTime()/1_000_000-lastRequest);if(delay>0)Thread.sleep(delay);lastRequest=System.nanoTime()/1_000_000;}
            HttpURLConnection c=(HttpURLConnection)u.openConnection();c.setInstanceFollowRedirects(false);c.setConnectTimeout(12000);c.setReadTimeout(15000);c.setRequestProperty("User-Agent","KaimonoScan/0.10 (personal price reader)");c.setRequestProperty("Accept-Language","ja");
            try{
                int code=c.getResponseCode();
                if(code==301||code==302||code==303||code==307||code==308){String location=c.getHeaderField("Location");if(location==null)throw new IOException("移動先がありません。");next=new URL(u,location).toString();continue;}
                if(code!=200)throw new IOException("公式ページを取得できません（HTTP "+code+"）。");
                StringBuilder b=new StringBuilder();try(Reader r=new InputStreamReader(c.getInputStream(),StandardCharsets.UTF_8)){char[] buf=new char[4096];int len;while((len=r.read(buf))!=-1){b.append(buf,0,len);if(b.length()>2_000_000)throw new IOException("ページが大きすぎます。");}}return b.toString();
            }finally{c.disconnect();}
        }throw new IOException("公式ページの移動回数が多すぎます。");
    }
    private static boolean hidden(Element el){for(Element e=el;e!=null;e=e.parent())if(e.hasAttr("hidden")||e.attr("style").replaceAll("\\s+","").toLowerCase(Locale.ROOT).contains("display:none"))return true;return false;}
    static SevenLookup.Product parse(String store,String html,String url,long now) throws Exception {
        String safe=productUrl(store,url);if(store.equals("seven"))return SevenLookup.parseProduct(html,safe,now);
        Document doc=Jsoup.parse(html,safe);
        List<Element> names=new ArrayList<>(),prices=new ArrayList<>();
        for(Element e:doc.select(store.equals("lawson")?".rightBlock h2.ttl":"h1.ly-mod-ttl-main"))if(!hidden(e))names.add(e);
        if(store.equals("lawson")){
            for(Element e:doc.select(".rightBlock dl.price"))if(!hidden(e)&&e.select("dt").text().contains("標準価格"))prices.add(e);
        }else for(Element e:doc.select(".ly-goodsinfo-area .ly-kakaku-usual"))if(!hidden(e))prices.add(e);
        if(names.size()!=1||prices.size()!=1||names.get(0).text().trim().isEmpty())throw new IOException("商品名・通常価格を一意に取得できません。値札を読むか手入力してください。");
        String raw=prices.get(0).text();String normalized=Normalizer.normalize(raw,Normalizer.Form.NFKC).replaceAll("\\s+","");
        Pattern pattern=Pattern.compile(store.equals("lawson")?"([0-9,]+(?:\\.[0-9]{1,2})?)円\\(税込(?:み)?\\)":"税込([0-9,]+(?:\\.[0-9]{1,2})?)円");
        Matcher m=pattern.matcher(normalized);if(!m.find())throw new IOException("税込の通常価格を読み取れません。");
        long cents;try{cents=new BigDecimal(m.group(1).replace(",","")).movePointRight(2).longValueExact();}catch(Exception e){throw new IOException("価格が不正です。");}
        if(m.find()||cents<=0||cents>110_000_000)throw new IOException("価格を特定できません。");
        SevenLookup.Product p=new SevenLookup.Product();p.store=store;p.name=names.get(0).text().trim();p.url=safe;p.cents=cents;p.priceText=raw;p.checkedAt=now;
        p.region="販売地域は公式ページで確認";
        if(store.equals("family")){String region=doc.select(".ly-ttl-icn-area .ly-reg-tag").text().replace("発売地域","").trim();if(!region.isEmpty())p.region=region;}
        else for(Element e:doc.select(".rightBlock p"))if(e.text().contains("地域")&&e.text().length()<180){p.region=e.text();break;}
        return p;
    }
}
