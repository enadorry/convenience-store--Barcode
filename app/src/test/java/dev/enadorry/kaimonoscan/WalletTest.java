package dev.enadorry.kaimonoscan;
import org.junit.Test;
import static org.junit.Assert.*;
public class WalletTest {
 @Test public void acceptsYenAndGroupedOrFullWidthInput(){assertEquals(12000,Wallet.parse(" ¥12,000 "));assertEquals(12000,Wallet.parse("１２，０００円"));assertEquals(0,Wallet.parse("0"));}
 @Test public void rejectsAmbiguousOrInvalidInput(){for(String input:new String[]{"", "-1", "12.5", "1,20", "残高1200", "1000 2000", "1000000000", "99999999999999999999999999999"})try{Wallet.parse(input);fail(input);}catch(IllegalArgumentException expected){}}
 @Test public void subtractsFinalRoundedCartAndHandlesInsufficientFunds(){long cents=Pricing.fractionalSubtotal(18252,2);assertEquals(635,Wallet.remaining(1000,cents/100));assertEquals(-365,Wallet.remaining(0,cents/100));assertEquals(0,Wallet.remaining(365,365));}
 @Test public void paymentUsesActualRatherThanEstimatedPrice(){assertEquals(600,Wallet.paid(1000,400));assertEquals(0,Wallet.paid(1000,1000));assertEquals(1000,Wallet.paid(1000,0));}
 @Test(expected=IllegalArgumentException.class)public void rejectsPaymentAboveSnapshot(){Wallet.paid(100,101);}
 @Test(expected=IllegalArgumentException.class)public void rejectsNegativePayment(){Wallet.paid(100,-1);}
 @Test public void calculatesLargeCartWithoutIntegerOverflow(){assertEquals(-1098899000L,Wallet.remaining(1000,Pricing.fractionalSubtotal(110000000,999)/100));}
}
