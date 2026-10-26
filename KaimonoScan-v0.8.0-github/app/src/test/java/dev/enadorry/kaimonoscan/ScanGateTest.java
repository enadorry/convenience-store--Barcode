package dev.enadorry.kaimonoscan;
import org.junit.Test;
import static org.junit.Assert.*;
public class ScanGateTest {
 private static final String A="4909411089368",B="4901234567894";
 @Test public void repeatedFramesNeverAddSameHeldBarcodeTwice(){
  ScanGate g=new ScanGate();assertTrue(g.accept(A,1000));
  for(long t=1100;t<20000;t+=100){assertFalse(g.accept(A,t));g.frame(false,t);}
 }
 @Test public void differentProductsAreAcceptedWithoutClosingCamera(){
  ScanGate g=new ScanGate();assertTrue(g.accept(A,1000));assertTrue(g.accept(B,1500));
 }
 @Test public void cooldownDoesNotPermanentlySuppressNewProduct(){
  ScanGate g=new ScanGate();assertTrue(g.accept(A,1000));assertFalse(g.accept(B,1100));assertTrue(g.accept(B,1400));
 }
 @Test public void elapsedTimeAloneDoesNotRearmBarcode(){
  ScanGate g=new ScanGate();assertTrue(g.accept(A,1000));assertFalse(g.accept(A,10000));
 }
 @Test public void observedEmptyFramesRearmAfterRelease(){
  ScanGate g=new ScanGate();assertTrue(g.accept(A,1000));
  assertFalse(g.frame(false,1300));assertFalse(g.frame(false,1800));assertTrue(g.frame(false,2400));
  assertTrue(g.accept(A,2500));
 }
 @Test public void possibleBarcodePointsBreakEmptyFrameSequence(){
  ScanGate g=new ScanGate();g.accept(A,1000);g.frame(false,1300);g.frame(true,2000);g.frame(false,2200);g.frame(false,2300);
  assertFalse(g.accept(A,2400));
 }
 @Test public void pausingForDialogCannotCountAsCameraRelease(){
  ScanGate g=new ScanGate();g.accept(A,1000);g.frame(false,1300);g.pauseTracking();g.frame(false,10000);
  assertFalse(g.accept(A,10100));
 }
 @Test public void explicitRepeatAllowsOneMoreScanThenBlocksDuplicates(){
  ScanGate g=new ScanGate();g.accept(A,1000);g.allowRepeat();assertTrue(g.accept(A,1500));assertFalse(g.accept(A,1600));
 }
 @Test public void invalidCodesCannotProduceSuccessFeedback(){
  ScanGate g=new ScanGate();assertFalse(g.accept("4909411089360",1000));assertFalse(g.accept("",2000));assertFalse(g.accept(null,3000));assertTrue(g.accept(A,4000));
 }
 @Test public void alternatingVisibleCodesAreStillCountedOnlyOnceEach(){
  ScanGate g=new ScanGate();g.accept(A,1000);g.accept(B,1500);
  assertFalse(g.accept(A,2000));assertFalse(g.accept(B,2500));
 }
 @Test public void recreationDoesNotImmediatelyRescanLastCountedBarcode(){
  ScanGate g=new ScanGate();g.restorePrevious(A,1000);assertFalse(g.accept(A,1500));assertTrue(g.accept(B,2000));
 }
}
