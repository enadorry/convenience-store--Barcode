package dev.enadorry.kaimonoscan;
import org.junit.Test;
import static org.junit.Assert.*;
import com.google.zxing.*;
import com.google.zxing.common.*;
public class CheckoutBarcodeTest {
 @Test public void displayedBarsDecodeToOriginalJan() throws Exception{
  for(String jan:new String[]{"4909411089368","96385074"}){
   BitMatrix m=CheckoutBarcode.matrix(jan);int scale=4,w=m.getWidth()*scale,h=m.getHeight()*scale;int[] pixels=new int[w*h];for(int y=0;y<h;y++)for(int x=0;x<w;x++)pixels[y*w+x]=m.get(x/scale,y/scale)?0xFF000000:0xFFFFFFFF;
   Result r=new MultiFormatReader().decode(new BinaryBitmap(new HybridBinarizer(new RGBLuminanceSource(w,h,pixels))));assertEquals(jan,r.getText());assertEquals(jan.length()==13?BarcodeFormat.EAN_13:BarcodeFormat.EAN_8,r.getBarcodeFormat());
  }
 }
 @Test public void invalidJanNeverProducesBarcode(){assertThrows(IllegalArgumentException.class,()->CheckoutBarcode.matrix("4909411089369"));assertThrows(IllegalArgumentException.class,()->CheckoutBarcode.matrix(""));}
}
