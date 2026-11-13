package com.kuuzan.statusbar;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.RectF;

/** 把选好的图裁成状态栏尺寸，再做暗化 / 模糊。设置页预览和 SystemUI 里用的是同一段。 */
public final class RenderUtil {

    private RenderUtil() {
    }

    public static Bitmap render(Bitmap src, int w, int h, int dimPercent, int blurPx) {
        if (src == null || w <= 0 || h <= 0) {
            return null;
        }
        Bitmap out;
        try {
            out = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888);
        } catch (Throwable t) {
            return null;
        }
        Canvas canvas = new Canvas(out);
        canvas.drawColor(Color.BLACK);

        float sw = src.getWidth();
        float sh = src.getHeight();
        if (sw <= 0 || sh <= 0) {
            return out;
        }

        // 居中裁切，相当于 ImageView 的 CENTER_CROP
        float scale = Math.max(w / sw, h / sh);
        float cw = w / scale;
        float ch = h / scale;
        int left = Math.round((sw - cw) / 2f);
        int top = Math.round((sh - ch) / 2f);
        Rect srcRect = new Rect(
                clamp(left, 0, (int) sw - 1),
                clamp(top, 0, (int) sh - 1),
                clamp(left + Math.round(cw), 0, (int) sw),
                clamp(top + Math.round(ch), 0, (int) sh));
        if (srcRect.width() <= 0 || srcRect.height() <= 0) {
            srcRect.set(0, 0, (int) sw, (int) sh);
        }

        Paint paint = new Paint(Paint.FILTER_BITMAP_FLAG | Paint.ANTI_ALIAS_FLAG);
        canvas.drawBitmap(src, srcRect, new RectF(0, 0, w, h), paint);

        if (dimPercent > 0) {
            int alpha = Math.min(255, dimPercent * 255 / 100);
            canvas.drawColor(Color.argb(alpha, 0, 0, 0));
        }
        if (blurPx > 0) {
            Blur.blur(out, blurPx);
        }
        return out;
    }

    private static int clamp(int v, int lo, int hi) {
        if (v < lo) {
            return lo;
        }
        return v > hi ? hi : v;
    }
}
