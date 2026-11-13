package com.kuuzan.statusbar;

import android.content.Context;
import android.content.res.Resources;
import android.database.Cursor;
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
 * 配置优先走 ContentProvider（跨进程可靠），读不到再退回 XSharedPreferences。
 * 各种失败都用不同底色标出来，没日志也能看状态栏颜色判断。
 */
final class Beautifier {

    /** 洋红：连配置都拿不到 */
    private static final int COLOR_NO_CONFIG = 0x99FF00FF;
    /** 淡蓝：配置拿到了，但没选图 */
    private static final int COLOR_NO_IMAGE = 0x5500C8FF;
    /** 橙色：图拿到了，解码失败 */
    private static final int COLOR_DECODE_FAIL = 0x99FF9800;

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
            Cfg cfg = Cfg.load();
            if (cfg == null) {
                root.setBackground(new ColorDrawable(COLOR_NO_CONFIG));
                Diag.log("配置读不到，刷洋红");
                return;
            }
            Diag.log("配置 enabled=" + cfg.enabled + " imageLen=" + cfg.imageLen()
                    + " via=" + cfg.via);
            if (!cfg.enabled) {
                return;
            }
            if (cfg.image == null || cfg.image.isEmpty()) {
                root.setBackground(new ColorDrawable(COLOR_NO_IMAGE));
                Diag.log("无图，刷淡蓝");
                return;
            }

            byte[] bytes = Base64.decode(cfg.image, Base64.NO_WRAP);
            Bitmap src = BitmapFactory.decodeByteArray(bytes, 0, bytes.length);
            if (src == null) {
                root.setBackground(new ColorDrawable(COLOR_DECODE_FAIL));
                Diag.log("解码失败，刷橙色");
                return;
            }

            float density = Resources.getSystem().getDisplayMetrics().density;
            int blurPx = Math.round(Math.max(0, cfg.blur) * density);
            int cornerPx = Math.round(Math.max(0, cfg.corner) * density);

            Bitmap bmp = RenderUtil.render(src, root.getWidth(), root.getHeight(),
                    Math.max(0, Math.min(80, cfg.dim)), blurPx);
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
                    + " dim=" + cfg.dim + " blur=" + blurPx + " corner=" + cornerPx);
        } catch (Throwable t) {
            Diag.log("install 失败 " + t);
        }
    }

    private static final class Cfg {
        boolean enabled = true;
        String image;
        int corner = Prefs.DEF_CORNER;
        int dim = Prefs.DEF_DIM;
        int blur = Prefs.DEF_BLUR;
        String via = "?";

        int imageLen() {
            return image == null ? 0 : image.length();
        }

        static Cfg load() {
            Cfg fromProvider = loadFromProvider();
            if (fromProvider != null) {
                fromProvider.via = "provider";
                if (fromProvider.image != null && !fromProvider.image.isEmpty()) {
                    return fromProvider;
                }
            }
            // provider 通了但没图时，再试一次老通道，两条路都走满
            Cfg fromPrefs = loadFromXSharedPrefs();
            if (fromPrefs != null) {
                fromPrefs.via = "xsharedprefs";
                if (fromPrefs.image != null && !fromPrefs.image.isEmpty()) {
                    return fromPrefs;
                }
            }
            return fromProvider != null ? fromProvider : fromPrefs;
        }

        static Cfg loadFromProvider() {
            try {
                Context ctx = android.app.AndroidAppHelper.currentApplication();
                if (ctx == null) {
                    Diag.log("provider: 拿不到 SystemUI 的 Context");
                    return null;
                }
                Cursor c = ctx.getContentResolver().query(ConfigProvider.URI, null, null, null, null);
                if (c == null) {
                    return null;
                }
                try {
                    if (!c.moveToFirst()) {
                        return null;
                    }
                    Cfg cfg = new Cfg();
                    cfg.enabled = c.getInt(0) != 0;
                    cfg.image = c.getString(1);
                    cfg.corner = c.getInt(2);
                    cfg.dim = c.getInt(3);
                    cfg.blur = c.getInt(4);
                    return cfg;
                } finally {
                    c.close();
                }
            } catch (Throwable t) {
                Diag.log("provider 读配置失败 " + t);
                return null;
            }
        }

        static Cfg loadFromXSharedPrefs() {
            try {
                XSharedPreferences p = new XSharedPreferences(Prefs.MODULE_PKG, Prefs.FILE);
                Cfg cfg = new Cfg();
                cfg.enabled = p.getBoolean(Prefs.KEY_ENABLED, true);
                cfg.image = p.getString(Prefs.KEY_IMAGE, null);
                cfg.corner = p.getInt(Prefs.KEY_CORNER, Prefs.DEF_CORNER);
                cfg.dim = p.getInt(Prefs.KEY_DIM, Prefs.DEF_DIM);
                cfg.blur = p.getInt(Prefs.KEY_BLUR, Prefs.DEF_BLUR);
                return cfg;
            } catch (Throwable t) {
                Diag.log("XSharedPreferences 读配置失败 " + t);
                return null;
            }
        }
    }
}
