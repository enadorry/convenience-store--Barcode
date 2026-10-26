package dev.enadorry.kaimonoscan;
import org.junit.Test;
import static org.junit.Assert.*;
public class LabelTextTest {
 @Test public void selectsExplicitTaxIncludedInsteadOfBase(){LabelText.Result r=LabelText.parse("おにぎり 鮭\n本体価格198円(税込213.84円)");assertEquals("おにぎり 鮭",r.names.get(0));assertEquals(2,r.prices.size());assertEquals(21384,r.preferred().cents);assertEquals("excluded",r.prices.get(0).tax);}
 @Test public void splitPriceAndTaxLines(){LabelText.Result r=LabelText.parse("牛乳\n税込\n213.84\n円");assertEquals(21384,r.preferred().cents);}
 @Test public void unknownTaxIsNotAutomaticallySelected(){LabelText.Result r=LabelText.parse("食パン\n198円");assertEquals(19800,r.prices.get(0).cents);assertNull(r.preferred());}
 @Test public void multipleIncludedPricesRequireSelection(){assertNull(LabelText.parse("税込108円\n税込216円").preferred());}
 @Test public void discountStickerNotMisreadAsProductPrice(){LabelText.Result r=LabelText.parse("お弁当\n税込500円\n50円引き\n20%OFF");assertEquals(1,r.prices.size());assertEquals(50000,r.preferred().cents);}
 @Test public void bulkAndPerWeightPricesAreSkipped(){assertTrue(LabelText.parse("2個で300円\n100g当たり128円").prices.isEmpty());}
 @Test public void janAndDateAreNotPrices(){assertTrue(LabelText.parse("4909411089368\n2026/10/26\n賞味期限2026.10.27").prices.isEmpty());}
 @Test public void fullWidthYenAndComma(){assertEquals(123450,LabelText.parse("税込￥１,２３４.５０").preferred().cents);}
 @Test public void nameAndPriceOnSameLine(){LabelText.Result r=LabelText.parse("牛乳 198円(税込213.84円)");assertEquals("牛乳",r.names.get(0));assertEquals(21384,r.preferred().cents);}
 @Test public void duplicatePricesAndEmptyInput(){assertEquals(1,LabelText.parse("税込108円\n税込108円").prices.size());assertTrue(LabelText.parse(null).names.isEmpty());assertTrue(LabelText.parse("").prices.isEmpty());}
 @Test public void unreasonableNumberNotPartiallyParsed(){assertTrue(LabelText.parse("12345678円").prices.isEmpty());assertTrue(LabelText.parse("4909411089368円").prices.isEmpty());}
 @Test public void taxConversionPreservesFraction(){assertEquals(21384,LabelText.addTax(19800,8));assertEquals(21780,LabelText.addTax(19800,10));}
 @Test public void inputPriceIsBounded(){assertEquals(21384,LabelText.parsePrice("２１３.８４"));for(String value:new String[]{"1e6","-1","NaN","1.234","1100001"})try{LabelText.parsePrice(value);fail(value);}catch(IllegalArgumentException expected){}}
 @Test public void separatedCurrencyAndAmount(){assertEquals(19800,LabelText.parse("牛乳\n¥\n198").prices.get(0).cents);assertEquals(19800,LabelText.parse("牛乳\n税込\n¥\n198").preferred().cents);}
}
