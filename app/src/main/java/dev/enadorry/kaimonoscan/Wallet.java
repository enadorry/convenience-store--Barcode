package dev.enadorry.kaimonoscan;

/** Local yen balance snapshot. Does not connect to or charge PayPay. */
public final class Wallet {
    public static final long MAX = 999_999_999L;
    private Wallet() {}
    public static long parse(String input) {
        if(input==null || input.length()>64)throw new IllegalArgumentException();
        String value=java.text.Normalizer.normalize(input,java.text.Normalizer.Form.NFKC).trim();
        value=value.replaceFirst("^[¥￥]\\s*","").replaceFirst("\\s*円$","").trim();
        if(!value.matches("(?:[0-9]+|[0-9]{1,3}(?:,[0-9]{3})+)"))throw new IllegalArgumentException();
        try {long amount=Long.parseLong(value.replace(",",""));check(amount);return amount;}
        catch(NumberFormatException e){throw new IllegalArgumentException();}
    }
    public static long remaining(long balance,long purchase) {
        check(balance);if(purchase<0)throw new IllegalArgumentException();return balance-purchase;
    }
    public static long paid(long balance,long actual) {
        long remaining=remaining(balance,actual);
        if(remaining<0)throw new IllegalArgumentException();return remaining;
    }
    private static void check(long balance){if(balance<0||balance>MAX)throw new IllegalArgumentException();}
}
