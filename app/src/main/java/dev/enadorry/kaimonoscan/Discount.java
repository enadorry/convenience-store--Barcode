package dev.enadorry.kaimonoscan;

/** A discount belongs to this shopping line, never to the persistent JAN price. */
final class Discount {
    static long unit(long base,String type,long value){
        Pricing.fractionalSubtotal(base,1);
        if(value<0)throw new IllegalArgumentException("割引を確認してください。");
        switch(type){
            case "none":if(value!=0)throw new IllegalArgumentException();return base;
            case "yen":if(value>1_100_000||value*100>base)throw new IllegalArgumentException("円引きは商品価格以下にしてください。");return base-value*100;
            case "percent":if(value>100)throw new IllegalArgumentException("0〜100％を入力してください。");return value==0?base:(base*(100-value)/10000)*100;
            case "price":if(value>1_100_000||value*100>base)throw new IllegalArgumentException("割引後の価格は元の価格以下にしてください。");return value*100;
            default:throw new IllegalArgumentException("割引の種類を確認してください。");
        }
    }
    static String label(String type,long value){
        return type.equals("yen")?value+"円引き":type.equals("percent")?value+"％引き":type.equals("price")?"割引価格":"割引";
    }
}
