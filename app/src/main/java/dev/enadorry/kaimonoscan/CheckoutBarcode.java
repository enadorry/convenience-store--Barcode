package dev.enadorry.kaimonoscan;

import android.graphics.Bitmap;
import com.google.zxing.BarcodeFormat;
import com.google.zxing.EncodeHintType;
import com.google.zxing.oned.EAN13Writer;
import com.google.zxing.oned.EAN8Writer;
import com.google.zxing.common.BitMatrix;
import java.util.EnumMap;

/** Full-width integer modules and white quiet zones; no price/quantity is encoded. */
final class CheckoutBarcode {
    static BitMatrix matrix(String jan){
        if(!Pricing.validJan(jan))throw new IllegalArgumentException("正しいJANコードが必要です");
        EnumMap<EncodeHintType,Object> hints=new EnumMap<>(EncodeHintType.class);hints.put(EncodeHintType.MARGIN,24);
        return jan.length()==13?new EAN13Writer().encode(jan,BarcodeFormat.EAN_13,0,100,hints):new EAN8Writer().encode(jan,BarcodeFormat.EAN_8,0,100,hints);
    }
    static Bitmap bitmap(String jan){
        BitMatrix bars=matrix(jan);int scale=6,w=bars.getWidth()*scale,h=bars.getHeight()*scale;int[] pixels=new int[w*h];
        for(int y=0;y<h;y++)for(int x=0;x<w;x++)pixels[y*w+x]=bars.get(x/scale,y/scale)?0xFF000000:0xFFFFFFFF;
        return Bitmap.createBitmap(pixels,w,h,Bitmap.Config.ARGB_8888);
    }
}
