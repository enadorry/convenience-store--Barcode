package dev.enadorry.kaimonoscan;

import android.graphics.Canvas;
import android.graphics.ColorFilter;
import android.graphics.Paint;
import android.graphics.PixelFormat;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.drawable.Drawable;

/** Keeps the bloom inside the button's measured bounds to avoid clipped siblings. */
final class EmberDrawable extends Drawable {
    private final Paint paint=new Paint(Paint.ANTI_ALIAS_FLAG);
    private final float density;
    EmberDrawable(float density){this.density=density;paint.setColor(0xfff45500);}
    @Override public void draw(Canvas canvas){
        Rect b=getBounds();float inset=12*density;
        RectF face=new RectF(b.left+inset,b.top+inset,b.right-inset,b.bottom-inset);
        paint.setShadowLayer(15*density,0,0,0x99f55600);
        canvas.drawRoundRect(face,20*density,20*density,paint);
    }
    @Override public void setAlpha(int alpha){paint.setAlpha(alpha);invalidateSelf();}
    @Override public void setColorFilter(ColorFilter filter){paint.setColorFilter(filter);invalidateSelf();}
    @Override public int getOpacity(){return PixelFormat.TRANSLUCENT;}
}
