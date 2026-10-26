package dev.enadorry.kaimonoscan;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.text.Normalizer;
import java.util.*;
import java.util.regex.*;

/** Conservative suggestions. Never decides tax or adds a shopping item without review. */
final class LabelText {
    static final class Price {
        final long cents;final String tax,display;
        Price(long c,String t){cents=c;tax=t;display=decimal(c)+"円 · "+(t.equals("included")?"税込":t.equals("excluded")?"税抜":"税区分未確認");}
    }
    static final class Result {
        final List<String> names=new ArrayList<>();final List<Price> prices=new ArrayList<>();
        Price preferred(){List<Price> incl=new ArrayList<>();for(Price p:prices)if(p.tax.equals("included"))incl.add(p);return incl.size()==1?incl.get(0):null;}
    }
    private static final String NUMBER="([0-9]{1,3}(?:,[0-9]{3})+(?:\\.[0-9]{1,2})?|[0-9]{1,6}(?:\\.[0-9]{1,2})?)";
    private static final Pattern MONEY=Pattern.compile("(?<![0-9.,])(?:[¥￥]\\s*"+NUMBER+"|"+NUMBER+"\\s*円)(?![0-9.])");
    static String decimal(long cents){return BigDecimal.valueOf(cents,2).stripTrailingZeros().toPlainString();}
    static long parsePrice(String s){
        String normalized=Normalizer.normalize(s.trim(),Normalizer.Form.NFKC).replace(",","");
        if(!normalized.matches("[0-9]{1,7}(?:\\.[0-9]{1,2})?"))throw new IllegalArgumentException("税込価格を確認してください。");
        long cents=new BigDecimal(normalized).movePointRight(2).longValueExact();Pricing.fractionalSubtotal(cents,1);return cents;
    }
    static long addTax(long cents,int rate){if(rate!=8&&rate!=10)throw new IllegalArgumentException();return BigDecimal.valueOf(cents).multiply(BigDecimal.valueOf(100+rate)).divide(BigDecimal.valueOf(100),0,RoundingMode.DOWN).longValueExact();}
    private static boolean bundle(String line){return line.matches(".*(?:[0-9]+\\s*(?:個|点|本|袋)\\s*(?:で|まとめ)|[0-9]+g\\s*(?:当たり|あたり)|100g[/当]).*");}
    private static String tax(String before,String after){
        int included=before.lastIndexOf("税込"),excluded=Math.max(before.lastIndexOf("税抜"),before.lastIndexOf("本体"));
        if(included>=0||excluded>=0)return included>excluded?"included":"excluded";
        if(after.matches("^\\s*[（(]?\\s*税込\\s*(?:[)）].*|$)"))return "included";
        if(after.matches("^\\s*[（(]?\\s*税抜\\s*(?:[)）].*|$)"))return "excluded";
        return "unknown";
    }
    static Result parse(String raw){
        Result result=new Result();String normalized=Normalizer.normalize(raw==null?"":raw.substring(0,Math.min(raw.length(),16000)),Normalizer.Form.NFKC);
        String[] lines=normalized.split("\\r?\\n");Set<String> seen=new HashSet<>();
        for(int n=0;n<lines.length;n++){
            String line=lines[n].trim();if(line.isEmpty()||bundle(line))continue;
            Matcher m=MONEY.matcher(line);boolean monetary=false;
            while(m.find()){
                monetary=true;String after=line.substring(m.end());
                if(m.start()>0)addName(result,line.substring(0,m.start()).replaceAll("(?:税込|税抜|本体)(?:価格)?[:： ]*$", "").trim());
                if(after.matches("^\\s*(?:引|値引|OFF|オフ).*"))continue;
                String number=m.group(1)!=null?m.group(1):m.group(2);
                String before=line.substring(0,m.start());if(before.trim().isEmpty()&&n>0&&lines[n-1].trim().matches("(?:税込|税抜|本体)(?:価格)?[:：]?"))before=lines[n-1];
                add(result,seen,number,tax(before,after));
            }
            if(!monetary&&line.matches(NUMBER)&&((n>0&&lines[n-1].matches(".*(?:税込|税抜|本体|[¥￥]).*"))||(n+1<lines.length&&lines[n+1].trim().equals("円")))){
                add(result,seen,line,tax(n>1&&lines[n-1].trim().matches("[¥￥]")?lines[n-2]:n>0?lines[n-1]:"",""));monetary=true;
            }
            if(monetary||line.matches(".*(?:税込|税抜|本体価格|販売価格|お買得|お買い得|円引|割引|値引|ポイント|会員|賞味|消費期限|製造|保存方法|原材料|エネルギー|栄養|バーコード|JAN).*"))continue;
            if(line.length()>1&&line.length()<=100&&line.matches(".*[\\p{IsHan}\\p{IsHiragana}\\p{IsKatakana}a-zA-Z].*")&&!line.matches(".*(?:%|OFF|[0-9]{8,}).*")){
                addName(result,line);
            }
        }
        return result;
    }
    private static void addName(Result r,String name){if(name.length()>1&&name.length()<=100&&name.matches(".*[\\p{IsHan}\\p{IsHiragana}\\p{IsKatakana}a-zA-Z].*")&&!name.matches(".*(?:税込|税抜|本体|価格|円引|割引|値引|ポイント|[0-9][ ]*円|[0-9]{8,}).*")&&!name.matches("(?i)(?:セブンプレミアム|7プレミアム|7PREMIUM|SALE|OFF)")&&r.names.size()<8&&!r.names.contains(name))r.names.add(name);}
    private static void add(Result r,Set<String> seen,String number,String tax){
        try{long cents=parsePrice(number);if(cents<=0)return;String key=cents+":"+tax;if(seen.add(key)&&r.prices.size()<16)r.prices.add(new Price(cents,tax));}catch(Exception ignored){}
    }
}
