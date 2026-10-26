package dev.enadorry.kaimonoscan;
import org.junit.Test;
import org.json.JSONObject;
import static org.junit.Assert.*;
public class DiscountTest {
 @Test public void amountKeepsBaseFraction(){assertEquals(15252,Discount.unit(18252,"yen",30));}
 @Test public void percentRoundsPerUnitToYen(){assertEquals(15800,Discount.unit(19800,"percent",20));assertEquals(31600,Pricing.fractionalSubtotal(Discount.unit(19800,"percent",20),2));}
 @Test public void noneKeepsFractionalCents(){assertEquals(18252,Discount.unit(18252,"none",0));assertEquals(18252,Discount.unit(18252,"percent",0));}
 @Test public void hundredPercentAndFinalPrice(){assertEquals(0,Discount.unit(20000,"percent",100));assertEquals(14800,Discount.unit(20000,"price",148));}
 @Test public void invalidDiscountRejected(){for(String type:new String[]{"bad","percent","yen","price"})try{Discount.unit(10000,type,101);fail(type);}catch(IllegalArgumentException expected){}try{Discount.unit(10000,"yen",-1);fail();}catch(IllegalArgumentException expected){}}
 @Test public void oldCartMigratesWithoutDiscount() throws Exception {MainActivity.Item item=MainActivity.Item.read(new JSONObject("{\"jan\":\"4909411089368\",\"name\":\"コーヒー\",\"price\":200,\"qty\":1}"));assertEquals(20000,item.netCents());assertFalse(item.discounted());assertFalse(item.labelPrice);}
 @Test public void serializedDiscountDoesNotOverwriteOriginal() throws Exception {MainActivity.Item item=new MainActivity.Item("4909411089368","コーヒー",200,2,"手入力");item.discountType="percent";item.discountValue=50;item.labelPrice=true;MainActivity.Item restored=MainActivity.Item.read(item.json());assertEquals(20000,restored.cents);assertEquals(10000,restored.netCents());assertEquals(2,restored.qty);assertTrue(restored.labelPrice);}
 @Test public void discountedAndRegularLinesMustNotMerge(){MainActivity.Item a=new MainActivity.Item("4909411089368","コーヒー",200,1,"手入力"),b=new MainActivity.Item(a.jan,a.name,200,1,a.source);assertTrue(a.samePrice(b));b.discountType="yen";b.discountValue=30;assertFalse(a.samePrice(b));b.discountType="none";b.discountValue=0;b.labelPrice=true;assertFalse(a.samePrice(b));}
}
