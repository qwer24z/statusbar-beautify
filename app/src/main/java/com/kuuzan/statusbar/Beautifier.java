package com.kuuzan.statusbar;

import android.content.res.Resources;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Outline;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.ColorDrawable;
import android.util.Base64;
import android.view.Gravity;
import android.view.View;
import android.view.ViewOutlineProvider;

import java.util.Collections;
import java.util.Set;
import java.util.WeakHashMap;

import de.robv.android.xposed.XSharedPreferences;

/**
 * 跑在 com.android.systemui 进程里，把图铺成状态栏那层 View 的背景。
 * 没配置图片时先刷一层淡蓝，用来确认钩子到底有没有生效。
 */
final class Beautifier {

    private static final int PROBE_COLOR = 0x5500C8FF;
    private static final Set<View> DONE = Collections.newSetFromMap(new WeakHashMap<View, Boolean>());

    private Beautifier() {
    }

    static void attach(final View root) {
        if (root == null || DONE.contains(root)) {
            return;
        }
        DONE.add(root);
        Diag.log("attach " + root.getClass().getName()
                + " " + root.getWidth() + "x" + root.getHeight());
        if (root.getWidth() > 0 && root.getHeight() > 0) {
            install(root);
            return;
        }
        root.addOnLayoutChangeListener(new View.OnLayoutChangeListener() {
            @Override
            public void onLayoutChange(View v, int left, int top, int right, int bottom,
                                       int oldLeft, int oldTop, int oldRight, int oldBottom) {
                if (v.getWidth() > 0 && v.getHeight() > 0) {
                    v.removeOnLayoutChangeListener(this);
                    install(v);
                }
            }
        });
    }

    private static void install(View root) {
        try {
            XSharedPreferences p = new XSharedPreferences(Prefs.MODULE_PKG, Prefs.FILE);
            boolean enabled = p.getBoolean(Prefs.KEY_ENABLED, true);
            String b64 = p.getString(Prefs.KEY_IMAGE, null);
            Diag.log("配置 enabled=" + enabled + " imageLen=" + (b64 == null ? 0 : b64.length()));

            if (!enabled) {
                return;
            }
            if (b64 == null || b64.isEmpty()) {
                // 没有图：刷一层淡蓝，证明钩子通了、只是缺图或读不到配置
                root.setBackground(new ColorDrawable(PROBE_COLOR));
                Diag.log("无图，刷了测试底色 " + Integer.toHexString(PROBE_COLOR));
                return;
            }

            byte[] bytes = Base64.decode(b64, Base64.NO_WRAP);
            Bitmap src = BitmapFactory.decodeByteArray(bytes, 0, bytes.length);
            if (src == null) {
                Diag.log("图片解码失败");
                return;
            }

            float density = Resources.getSystem().getDisplayMetrics().density;
            int dim = Math.max(0, Math.min(80, p.getInt(Prefs.KEY_DIM, Prefs.DEF_DIM)));
            int blurPx = Math.round(Math.max(0, p.getInt(Prefs.KEY_BLUR, Prefs.DEF_BLUR)) * density);
            int cornerPx = Math.round(Math.max(0, p.getInt(Prefs.KEY_CORNER, Prefs.DEF_CORNER)) * density);

            Bitmap bmp = RenderUtil.render(src, root.getWidth(), root.getHeight(), dim, blurPx);
            if (bmp == null) {
                return;
            }
            BitmapDrawable drawable = new BitmapDrawable(root.getResources(), bmp);
            drawable.setGravity(Gravity.FILL);
            root.setBackground(drawable);

            if (cornerPx > 0) {
                final int corner = cornerPx;
                root.setOutlineProvider(new ViewOutlineProvider() {
                    @Override
                    public void getOutline(View v, Outline outline) {
                        float r = Math.min(corner, Math.min(v.getWidth(), v.getHeight()) / 2f);
                        outline.setRoundRect(0, 0, v.getWidth(), v.getHeight(), r);
                    }
                });
                root.setClipToOutline(true);
                root.invalidateOutline();
            } else {
                root.setClipToOutline(false);
            }
            Diag.log("铺图完成 " + root.getWidth() + "x" + root.getHeight()
                    + " dim=" + dim + " blur=" + blurPx + " corner=" + cornerPx);
        } catch (Throwable t) {
            Diag.log("install 失败 " + t);
        }
    }
}
