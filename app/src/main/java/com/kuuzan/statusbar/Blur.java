package com.kuuzan.statusbar;

import android.graphics.Bitmap;

/**
 * 方框模糊，跑三遍近似高斯。
 * 只在状态栏那块小图（宽 <= 屏幕宽，高 <= 状态栏高）上用，几十毫秒。
 */
public final class Blur {

    private Blur() {
    }

    public static void blur(Bitmap bmp, int radius) {
        if (bmp == null || radius <= 0) {
            return;
        }
        int w = bmp.getWidth();
        int h = bmp.getHeight();
        if (w < 3 || h < 3) {
            return;
        }
        int r = Math.max(1, Math.min(radius, Math.min(w, h) / 3));
        int[] pix = new int[w * h];
        bmp.getPixels(pix, 0, w, 0, 0, w, h);
        for (int pass = 0; pass < 3; pass++) {
            horizontal(pix, w, h, r);
            vertical(pix, w, h, r);
        }
        bmp.setPixels(pix, 0, w, 0, 0, w, h);
    }

    private static void horizontal(int[] pix, int w, int h, int r) {
        int[] row = new int[w];
        int div = 2 * r + 1;
        for (int y = 0; y < h; y++) {
            int base = y * w;
            System.arraycopy(pix, base, row, 0, w);

            int sa = 0, sr = 0, sg = 0, sb = 0;
            for (int i = -r; i <= r; i++) {
                int p = row[clamp(i, w)];
                sa += p >>> 24;
                sr += (p >> 16) & 0xFF;
                sg += (p >> 8) & 0xFF;
                sb += p & 0xFF;
            }
            for (int x = 0; x < w; x++) {
                pix[base + x] = ((sa / div) << 24) | ((sr / div) << 16) | ((sg / div) << 8) | (sb / div);
                int out = row[clamp(x - r, w)];
                int in = row[clamp(x + r + 1, w)];
                sa += (in >>> 24) - (out >>> 24);
                sr += ((in >> 16) & 0xFF) - ((out >> 16) & 0xFF);
                sg += ((in >> 8) & 0xFF) - ((out >> 8) & 0xFF);
                sb += (in & 0xFF) - (out & 0xFF);
            }
        }
    }

    private static void vertical(int[] pix, int w, int h, int r) {
        int[] col = new int[h];
        int div = 2 * r + 1;
        for (int x = 0; x < w; x++) {
            for (int y = 0; y < h; y++) {
                col[y] = pix[y * w + x];
            }

            int sa = 0, sr = 0, sg = 0, sb = 0;
            for (int i = -r; i <= r; i++) {
                int p = col[clamp(i, h)];
                sa += p >>> 24;
                sr += (p >> 16) & 0xFF;
                sg += (p >> 8) & 0xFF;
                sb += p & 0xFF;
            }
            for (int y = 0; y < h; y++) {
                pix[y * w + x] = ((sa / div) << 24) | ((sr / div) << 16) | ((sg / div) << 8) | (sb / div);
                int out = col[clamp(y - r, h)];
                int in = col[clamp(y + r + 1, h)];
                sa += (in >>> 24) - (out >>> 24);
                sr += ((in >> 16) & 0xFF) - ((out >> 16) & 0xFF);
                sg += ((in >> 8) & 0xFF) - ((out >> 8) & 0xFF);
                sb += (in & 0xFF) - (out & 0xFF);
            }
        }
    }

    private static int clamp(int v, int n) {
        if (v < 0) {
            return 0;
        }
        return v >= n ? n - 1 : v;
    }
}
