package com.kuuzan.statusbar;

import android.content.res.Resources;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Outline;
import android.graphics.drawable.BitmapDrawable;
import android.util.Base64;
import android.view.Gravity;
import android.view.View;
import android.view.ViewOutlineProvider;

import java.util.Collections;
import java.util.Set;
import java.util.WeakHashMap;

import de.robv.android.xposed.XSharedPreferences;
import de.robv.android.xposed.XposedBridge;

/**
 * 跑在 com.android.systemui 进程里，把图铺成状态栏那层 View 的背景。
 */
final class Beautifier {

    private static final String TAG = "SBBeautify";
    private static final Set<View> DONE = Collections.newSetFromMap(new WeakHashMap<View, Boolean>());

    private Beautifier() {
    }

    static void attach(final View root) {
        if (root == null || DONE.contains(root)) {
            return;
        }
        DONE.add(root);
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
            Cfg cfg = Cfg.load();
            if (cfg == null) {
                return;
            }
            Bitmap bmp = RenderUtil.render(cfg.image, root.getWidth(), root.getHeight(), cfg.dim, cfg.blurPx);
            if (bmp == null) {
                return;
            }
            BitmapDrawable drawable = new BitmapDrawable(root.getResources(), bmp);
            drawable.setGravity(Gravity.FILL);
            root.setBackground(drawable);

            final int corner = cfg.cornerPx;
            if (corner > 0) {
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
        } catch (Throwable t) {
            XposedBridge.log(TAG + ": 安装背景失败 " + t);
        }
    }

    private static final class Cfg {
        Bitmap image;
        int dim;
        int blurPx;
        int cornerPx;

        static Cfg load() {
            try {
                XSharedPreferences p = new XSharedPreferences(Prefs.MODULE_PKG, Prefs.FILE);
                if (!p.getBoolean(Prefs.KEY_ENABLED, true)) {
                    return null;
                }
                String b64 = p.getString(Prefs.KEY_IMAGE, null);
                if (b64 == null || b64.isEmpty()) {
                    return null;
                }
                byte[] bytes = Base64.decode(b64, Base64.NO_WRAP);
                Bitmap src = BitmapFactory.decodeByteArray(bytes, 0, bytes.length);
                if (src == null) {
                    return null;
                }
                float density = Resources.getSystem().getDisplayMetrics().density;
                Cfg c = new Cfg();
                c.image = src;
                c.dim = Math.max(0, Math.min(80, p.getInt(Prefs.KEY_DIM, Prefs.DEF_DIM)));
                c.blurPx = Math.round(Math.max(0, p.getInt(Prefs.KEY_BLUR, Prefs.DEF_BLUR)) * density);
                c.cornerPx = Math.round(Math.max(0, p.getInt(Prefs.KEY_CORNER, Prefs.DEF_CORNER)) * density);
                return c;
            } catch (Throwable t) {
                XposedBridge.log(TAG + ": 读配置失败 " + t);
                return null;
            }
        }
    }
}
