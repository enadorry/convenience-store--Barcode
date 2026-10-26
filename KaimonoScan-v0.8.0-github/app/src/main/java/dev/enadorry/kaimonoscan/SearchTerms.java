package dev.enadorry.kaimonoscan;

import java.text.Normalizer;
import java.util.*;
import org.jsoup.Jsoup;

/** Search-only normalization; never alters the displayed product identity or its price. */
final class SearchTerms {
    private static final String MAKERS="カルビー|湖池屋|森永製菓|江崎グリコ|グリコ|明治|ロッテ|ブルボン|亀田製菓|三幸製菓|ヤマザキビスケット|有楽製菓|不二家|東ハト|カンロ|UHA味覚糖|味覚糖|キリン|アサヒ|サントリー|コカコーラ";
    private static final String PROMO="送料無料|送料込み|送料込|まとめ買い|ケース販売|ケース売り|大特価|最安値|お買い得|セール|クーポン|ポイント|あすつく|優良配送|予約販売|訳あり|業務用|SALE|お取り寄せ";
    private static final String[] BRANDS={"堅あげポテト","ポテトチップス","じゃがりこ","かっぱえびせん","サッポロポテト","ピザポテト","チップスター","プリッツ","ポッキー","トッポ","コアラのマーチ","パイの実","チョコパイ","アルフォート","ブラックサンダー","たけのこの里","きのこの山","ハイチュウ","ぷっちょ","ピュレグミ","果汁グミ","忍者めし","タフグミ","コロロ","フェットチーネグミ","柿の種","ハッピーターン","ばかうけ","キャラメルコーン","ファイア"};
    static String clean(String title){
        if(title==null)return "";
        String q=Normalizer.normalize(Jsoup.parse(title.substring(0,Math.min(2000,title.length()))).text(),Normalizer.Form.NFKC);
        // Remove only promotional brackets; keep flavour names and percentages.
        q=q.replaceAll("(?i)[【\\[（(][^】\\]）)]*(?:"+PROMO+")[^】\\]）)]*[】\\]）)]"," ");
        q=q.replaceAll("ポイント[0-9]+倍|[0-9]+倍ポイント", " ");
        q=q.replaceAll("(?i)(?:"+PROMO+")(?:対象|商品|中|実施中)?"," ");
        q=q.replaceAll("(?:最大)?[0-9]+(?:倍|%)(?:還元|ポイント|OFF|オフ)"," ");
        q=q.replaceAll("(?:賞味期限|発送予定|発送日|配送予定)[:： ]*[0-9年/月日.\\-〜~]+"," ");
        q=q.replaceAll("[0-9]{8,14}"," ");
        q=q.replaceAll("(?i)[0-9]+(?:[.,][0-9]+)?\\s*(?:kg|ml|g|l|枚|袋|本|箱|個|粒|パック|ケース|円)(?:入り|入)?(?=$|[\\s×xX*・/、,()（）\\[\\]【】])"," ");
        q=q.replaceAll("[0-9]+\\s*(?:入り|入)(?=$|[\\s×*・/、,()（）\\[\\]【】])"," ");
        q=q.replaceAll("[【】\\[\\]()（）×*|/・、,]"," ").replaceAll("\\s+"," ").trim();
        q=q.replaceFirst("^(?:(?:"+MAKERS+")\\s*)+","").trim();
        q=q.replaceAll("(?i)(?:^|\\s)(?:PET|セット|ケース|お菓子|おやつ|食品|菓子|スナック菓子|x)(?=\\s|$)"," ");
        return q.replaceAll("\\s+"," ").trim();
    }
    static String bestSeed(List<String> names){
        String best="";
        for(String name:names){String q=clean(name);if(q.length()>=2&&(best.isEmpty()||q.length()<best.length()))best=q;}
        return best.length()>150?"":best;
    }
    static String shorter(String title){
        String q=clean(title);for(String brand:BRANDS)if(q.contains(brand))return brand;
        String best="";for(String token:q.split("\\s+"))if(token.length()>=2&&!token.matches("[0-9% .]+")&&token.length()>best.length())best=token;
        return best;
    }
    static List<String> queries(String title){
        LinkedHashSet<String> out=new LinkedHashSet<>();String clean=clean(title),shorter=shorter(title);
        if(!clean.isEmpty()&&clean.length()<=150)out.add(clean);
        if(!shorter.isEmpty()&&shorter.length()<=150)out.add(shorter);
        return new ArrayList<>(out);
    }
}
