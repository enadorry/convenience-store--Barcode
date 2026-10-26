package dev.enadorry.kaimonoscan;

import org.jsoup.Jsoup;
import org.jsoup.nodes.*;
import org.json.*;
import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.math.BigDecimal;
import java.text.Normalizer;
import java.util.*;
import java.util.regex.*;

/** Reads only public SEJ product/search pages; no accounts, cookies, or private APIs. */
final class SevenLookup {
    static final long CACHE_MS=24L*60*60*1000;
    private static long lastRequest;
    static final class Product {
        String name, url, region, priceText;
        long cents, checkedAt;
        JSONObject json() throws JSONException {
            return new JSONObject().put("name",name).put("url",url).put("region",region)
                .put("priceText",priceText).put("cents",cents).put("checkedAt",checkedAt);
        }
        static Product read(JSONObject o) throws Exception {
            Product p=new Product();p.name=o.getString("name");p.url=productUrl(o.getString("url"));
            p.region=o.optString("region","");p.priceText=o.optString("priceText","");
            p.cents=o.getLong("cents");p.checkedAt=o.getLong("checkedAt");
            if(p.name.isEmpty()||p.cents<=0||p.cents>110_000_000||p.checkedAt<=0)throw new IOException("保存データが不正です。");
            return p;
        }
    }
    static final class Link {
        String name,url,priceText,region;
        Link(String n,String u,String p,String r){name=n;url=u;priceText=p;region=r;}
    }
    static final class SearchResult {
        String query;
        List<Link> links;
        SearchResult(String q,List<Link> l){query=q;links=l;}
    }
    static boolean fresh(Product p,long now) {return now>=p.checkedAt && now-p.checkedAt<CACHE_MS;}
    static String productUrl(String value) throws IOException {
        try {
            URI u=new URI(value.trim());
            if(!"https".equalsIgnoreCase(u.getScheme())||!"www.sej.co.jp".equalsIgnoreCase(u.getHost())||u.getUserInfo()!=null||u.getPort()!=-1)
                throw new Exception();
            if(!u.getRawPath().matches("/products/a/item/[0-9]{1,12}/?"))throw new Exception();
            return "https://www.sej.co.jp"+u.getRawPath().replaceAll("/$","")+"/";
        } catch(Exception e){throw new IOException("セブン公式の商品詳細URL（https://www.sej.co.jp/products/a/item/数字/）を入力してください。");}
    }
    private static boolean allowedPage(URL u) {
        return "https".equals(u.getProtocol())&&"www.sej.co.jp".equalsIgnoreCase(u.getHost())&&u.getUserInfo()==null&&u.getPort()==-1
            && (u.getPath().matches("/products/a/item/[0-9]{1,12}/?")||u.getPath().equals("/products/a/itemresult/"));
    }
    private static String fetch(String value) throws Exception {
        URL u=new URL(value);
        for(int redirect=0;redirect<4;redirect++) {
            if(!allowedPage(u))throw new IOException("対応していないページへの移動を中止しました。公式の商品詳細URLを確認してください。");
            synchronized(SevenLookup.class) {
                long delay=1200-(System.nanoTime()/1_000_000-lastRequest);
                if(delay>0)Thread.sleep(delay);
                lastRequest=System.nanoTime()/1_000_000;
            }
            HttpURLConnection c=(HttpURLConnection)u.openConnection();c.setInstanceFollowRedirects(false);
            c.setConnectTimeout(12000);c.setReadTimeout(15000);
            c.setRequestProperty("User-Agent","KaimonoScan/0.2.0 (personal shopping price reader)");
            c.setRequestProperty("Accept-Language","ja");
            try {
                int code=c.getResponseCode();
                if(code==301||code==302||code==303||code==307||code==308) {
                    String location=c.getHeaderField("Location");if(location==null)throw new IOException("移動先を取得できません。");
                    u=new URL(u,location);continue;
                }
                if(code!=200)throw new IOException("セブン公式ページを取得できません（HTTP "+code+"）。公式URLで試すか、時間を置いて再試行してください。");
                StringBuilder s=new StringBuilder();
                try(Reader r=new InputStreamReader(c.getInputStream(),StandardCharsets.UTF_8)) {
                    char[] a=new char[4096];int n;
                    while((n=r.read(a))!=-1){s.append(a,0,n);if(s.length()>2_000_000)throw new IOException("ページが大きすぎるため取得を中止しました。");}
                }
                return s.toString();
            } finally {c.disconnect();}
        }
        throw new IOException("公式ページの移動回数が多すぎます。商品URLを確認してください。");
    }
    static Product load(String url) throws Exception {String safe=productUrl(url);return parseProduct(fetch(safe),safe,System.currentTimeMillis());}
    static SearchResult search(String query) throws Exception {
        List<String> queries=SearchTerms.queries(query);
        if(queries.isEmpty())throw new IOException("商品名を入力してください。容量・送料の文だけでは検索できません。");
        String used="";List<Link> links=Collections.emptyList();
        for(String q:queries){used=q;links=parseSearch(fetch(searchUrl(q)));if(!links.isEmpty())break;}
        return new SearchResult(used,links);
    }
    static String searchUrl(String query) throws UnsupportedEncodingException {
        return "https://www.sej.co.jp/products/a/itemresult/?key="+URLEncoder.encode(query,"UTF-8")+"&limit=100&p=1";
    }
    static String fallbackQuery(String query) {
        return SearchTerms.shorter(query);
    }
    static List<Link> parseSearch(String html) throws IOException {
        Document doc=Jsoup.parse(html,"https://www.sej.co.jp");
        if(!doc.select("h1").text().contains("検索結果"))throw new IOException("商品検索ページを読み取れません。公式商品URLを登録してください。");
        List<Link> out=new ArrayList<>();Set<String> seen=new HashSet<>();
        // Product cards, not arbitrary page-wide anchors or unrelated prices.
        for(Element card:doc.select(".list_inner")) {
            if(hidden(card))continue;
            Element anchor=card.selectFirst(".item_ttl a[href]");if(anchor==null)continue;
            String url;
            try{url=productUrl(anchor.absUrl("href"));}catch(IOException e){continue;}
            String name=anchor.text().trim();if(name.isEmpty()||!seen.add(url))continue;
            Element price=card.selectFirst(".item_price"),region=card.selectFirst(".item_region");
            out.add(new Link(name,url,price==null?"価格は詳細で確認":price.text(),region==null?"地域不明":region.text()));
            if(out.size()>=30)break;
        }
        // A changed page structure must not be reported as an empty product search.
        if(out.isEmpty()&&doc.selectFirst(".list_inner .item_price")!=null)
            throw new IOException("検索結果の構造が変わっている可能性があります。商品詳細URLを登録してください。");
        return out;
    }
    private static boolean hidden(Element el) {
        for(Element e=el;e!=null;e=e.parent()) {
            if(e.hasAttr("hidden")||e.attr("style").toLowerCase(Locale.ROOT).replaceAll("\\s+","").contains("display:none"))return true;
        }
        return false;
    }
    static Product parseProduct(String html,String url,long now) throws IOException {
        String safe=productUrl(url);Document doc=Jsoup.parse(html,safe);
        for(Element err:doc.select(".blacklist_error"))if(!hidden(err))throw new IOException("この商品の情報は現在公開されていません。保存済みの価格は更新しません。");
        Element title=doc.selectFirst(".item_ttl h1");if(title==null)throw new IOException("商品名を読み取れません。商品詳細ページか確認してください。");
        List<Element> prices=new ArrayList<>();for(Element p:doc.select(".item_price"))if(!hidden(p))prices.add(p);
        if(prices.size()!=1)throw new IOException("1個分の価格を特定できません。手入力で確認してください。");
        String text=Normalizer.normalize(prices.get(0).text(),Normalizer.Form.NFKC).replaceAll("\\s+","");
        Matcher m=Pattern.compile("([0-9,]+)円\\(税込(?:価格)?([0-9,]+(?:\\.[0-9]{1,2})?)円\\)").matcher(text);
        if(!m.find())throw new IOException("税込価格を読み取れません。ページ形式が変わった可能性があります。");
        BigDecimal amount;
        try {amount=new BigDecimal(m.group(2).replace(",",""));}catch(Exception e){throw new IOException("価格の形式が不正です。");}
        long cents;try{cents=amount.movePointRight(2).longValueExact();}catch(Exception e){throw new IOException("価格の桁数が不正です。");}
        if(m.find()||cents<=0||cents>110_000_000)throw new IOException("価格を一意に特定できません。");
        Product p=new Product();p.name=title.text().trim();if(p.name.isEmpty())throw new IOException("商品名がありません。");
        p.url=safe;p.cents=cents;p.checkedAt=now;p.priceText=prices.get(0).text();
        Element region=doc.selectFirst(".item_region");p.region=region==null?"販売地域不明":region.text();return p;
    }
}
