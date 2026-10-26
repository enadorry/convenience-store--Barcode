package dev.enadorry.kaimonoscan;
import org.junit.Test;
import static org.junit.Assert.*;
public class PricingTest {
 @Test public void janChecksumAndLength() {
  assertTrue(Pricing.validJan("4901234567894"));
  assertTrue(Pricing.validJan("12345670"));
  assertFalse(Pricing.validJan("4901234567890"));
  assertFalse(Pricing.validJan("1234567"));
  assertFalse(Pricing.validJan("490123456789４"));
  assertFalse(Pricing.validJan(null));
 }
 @Test public void inclusivePricesAndRounding() {
  assertEquals(198,Pricing.inclusive(198,0));
  assertEquals(213,Pricing.inclusive(198,8));
  assertEquals(217,Pricing.inclusive(198,10));
  assertEquals(0,Pricing.inclusive(0,8));
 }
 @Test public void totalsDoNotOverflowInteger() {assertEquals(1_098_900_000L,Pricing.subtotal(1_100_000,999));}
 @Test(expected=IllegalArgumentException.class) public void invalidQuantityIsRejected(){Pricing.subtotal(100,0);}
 @Test(expected=IllegalArgumentException.class) public void invalidTaxIsRejected(){Pricing.inclusive(100,9);}
}
