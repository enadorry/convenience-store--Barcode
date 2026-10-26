package dev.enadorry.kaimonoscan;
import org.junit.Test;
import static org.junit.Assert.*;
import java.util.List;
public class SearchTermsTest {
 @Test public void candyPromotionAndBulkPacksAreRemoved(){assertEquals("じゃがりこ サラダ",SearchTerms.clean("【送料無料】カルビー じゃがりこ サラダ 60g×12個 ケース販売"));}
 @Test public void attachedSizesAndFullWidthNormalize(){assertEquals("ポテトチップス うすしお味",SearchTerms.clean("カルビー ポテトチップス うすしお味 ６０ｇ×１２袋"));}
 @Test public void flavorAndCacaoArePreserved(){assertEquals("チョコレート効果 カカオ72%",SearchTerms.clean("明治 チョコレート効果 カカオ72% 75g"));assertEquals("じゃがりこ のり塩バター味",SearchTerms.clean("じゃがりこ【のり塩バター味】52g"));}
 @Test public void numericProductNamesAreNotPackCounts(){assertEquals("1本満足バー シリアルチョコ",SearchTerms.clean("1本満足バー シリアルチョコ 1本"));assertEquals("ガリガリ君ソーダ",SearchTerms.clean("ガリガリ君ソーダ 7本入り"));}
 @Test public void promotionalBracketDoesNotBecomeKeyword(){assertEquals("ブラックサンダー",SearchTerms.clean("【ポイント10倍 送料無料】有楽製菓 ブラックサンダー 20個"));}
 @Test public void htmlEntitiesAndTagsAreDecoded(){assertEquals("ポッキー チョコレート",SearchTerms.clean("<b>グリコ</b>&nbsp;ポッキー チョコレート 2袋"));}
 @Test public void queryBroadeningIsBoundedAndDeduplicated(){assertEquals(List.of("じゃがりこ サラダ","じゃがりこ"),SearchTerms.queries("カルビー じゃがりこ サラダ 60g 12個 送料無料"));assertEquals(List.of("ポッキー"),SearchTerms.queries("ポッキー"));}
 @Test public void oldCoffeeFallbackRemainsCompatible(){assertEquals("ファイア",SearchTerms.shorter("キリン ファイア ワンデイ ブラック 600ml PET 24本"));}
 @Test public void shortestCleanedSeedAvoidsStoreSalesCopy(){assertEquals("じゃがりこ サラダ",SearchTerms.bestSeed(List.of("【送料無料】カルビー じゃがりこ サラダ 60g×12個","カルビー じゃがりこ サラダ まとめ買い 12個")));}
 @Test public void emptyAndOnlyPromotionsDoNotSearch(){assertEquals("",SearchTerms.clean(null));assertTrue(SearchTerms.queries("【送料無料】12袋 ケース").isEmpty());}
}
