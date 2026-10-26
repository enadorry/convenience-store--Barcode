package dev.enadorry.kaimonoscan;

public final class Pricing {
    private Pricing() {}
    public static boolean validJan(String s) {
        if (s == null || !(s.length() == 8 || s.length() == 13) || !s.matches("[0-9]+")) return false;
        int sum = 0;
        for (int i = s.length() - 2, pos = 0; i >= 0; i--, pos++)
            sum += (s.charAt(i) - '0') * (pos % 2 == 0 ? 3 : 1);
        return (10 - sum % 10) % 10 == s.charAt(s.length() - 1) - '0';
    }
    public static long inclusive(long price, int rate) {
        if (price < 0 || price > 1_000_000 || !(rate == 0 || rate == 8 || rate == 10)) throw new IllegalArgumentException();
        return price * (100 + rate) / 100;
    }
    public static long subtotal(long price, int quantity) {
        if (price < 0 || price > 1_100_000 || quantity < 1 || quantity > 999) throw new IllegalArgumentException();
        return price * quantity;
    }
    public static long fractionalSubtotal(long unitHundredths,int quantity) {
        if(unitHundredths<0||unitHundredths>110_000_000||quantity<1||quantity>999)throw new IllegalArgumentException();
        return unitHundredths*quantity;
    }
}
