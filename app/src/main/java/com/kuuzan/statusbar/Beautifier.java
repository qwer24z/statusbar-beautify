package com.kuuzan.statusbar;

import android.content.Context;
import android.content.res.Resources;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Outline;
import android.graphics.Paint;
import android.graphics.drawable.BitmapDrawable;
import android.os.Environment;
import android.util.Base64;
import android.view.Gravity;
import android.view.View;
import android.view.ViewOutlineProvider;

import java.io.File;
import java.util.Collections;
import java.util.Set;
import java.util.WeakHashMap;

import de.robv.android.xposed.XSharedPreferences;

/**
 * 跑在 com.android.systemui 进程里，把图铺成状态栏那层 View 的背景。
 * 图片三条路：media 目录里 App 存的文件 → ContentProvider 的 Base64 → XSharedPreferences。
 * 一条都拿不到就画品红/青棋盘格，这个颜色不会被误认成系统主题色。
 */
final class Beautifier {

    private static final String TAG = "SBBeautify";
    private static final String MEDIA_IMAGE = "statusbar.jpg";

    private static final Set<View> DONE = Collections.newSetFromMap(new WeakHashMap<View, Boolean>());
    private static final Set<View> DONE_HEADER = Collections.newSetFromMap(new WeakHashMap<View, Boolean>());

    private Beautifier() {
    }

    static void attach(final View root) {
        if (root == null || DONE.contains(root)) {
            return;
        }
        DONE.add(root);
        Diag.log("attach " + root.getClass().getName() + " " + root.getWidth() + "x" + root.getHeight());
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
            if (cfg != null && !cfg.enabled) {
                Diag.log("模块被关闭");
                return;
            }
            Bitmap src = loadImage(cfg);
            if (src == null) {
                root.setBackground(new BitmapDrawable(root.getResources(), checker(root.getWidth(), root.getHeight())));
                root.setClipToOutline(false);
                Diag.log("拿不到图，画棋盘格");
                return;
            }
            int dim = cfg == null ? 0 : Math.max(0, Math.min(80, cfg.dim));
            int blurPx = cfg == null ? 0
                    : Math.round(Math.max(0, cfg.blur) * Resources.getSystem().getDisplayMetrics().density);
            int cornerPx = cfg == null ? 0
                    : Math.round(Math.max(0, cfg.corner) * Resources.getSystem().getDisplayMetrics().density);

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
                    + " from=" + imageFrom);
        } catch (Throwable t) {
            Diag.log("install 失败 " + t);
        }
    }

    /** 控制中心顶部的标头（OplusQuickStatusBarHeader 之类），把图铺在它背后 */
    static void attachHeader(final View root) {
        if (root == null || DONE_HEADER.contains(root)) {
            return;
        }
        DONE_HEADER.add(root);
        Diag.log("标头 attach " + root.getClass().getName()
                + " " + root.getWidth() + "x" + root.getHeight());
        if (root.getWidth() > 0 && root.getHeight() > 0) {
            installHeader(root);
            return;
        }
        root.addOnLayoutChangeListener(new View.OnLayoutChangeListener() {
            @Override
            public void onLayoutChange(View v, int left, int top, int right, int bottom,
                                       int oldLeft, int oldTop, int oldRight, int oldBottom) {
                if (v.getWidth() > 0 && v.getHeight() > 0) {
                    v.removeOnLayoutChangeListener(this);
                    installHeader(v);
                }
            }
        });
    }

    private static void installHeader(View root) {
        try {
            Cfg cfg = Cfg.load();
            if (cfg != null && !cfg.enabled) {
                Diag.log("标头：模块被关闭");
                return;
            }
            Bitmap src = loadImage(cfg);
            if (src == null) {
                Diag.log("标头：拿不到图，跳过");
                return;
            }
            int dim = cfg == null ? 0 : Math.max(0, Math.min(80, cfg.dim));
            int blurPx = cfg == null ? 0
                    : Math.round(Math.max(0, cfg.blur) * Resources.getSystem().getDisplayMetrics().density);
            int cornerDp = cfg == null ? 24 : Math.max(24, cfg.corner);
            final int cornerPx = Math.round(cornerDp * Resources.getSystem().getDisplayMetrics().density);

            Bitmap bmp = RenderUtil.render(src, root.getWidth(), root.getHeight(), dim, blurPx);
            if (bmp == null) {
                return;
            }
            BitmapDrawable drawable = new BitmapDrawable(root.getResources(), bmp);
            drawable.setGravity(Gravity.FILL);
            root.setBackground(drawable);
            root.setOutlineProvider(new ViewOutlineProvider() {
                @Override
                public void getOutline(View v, Outline outline) {
                    float r = Math.min(cornerPx, Math.min(v.getWidth(), v.getHeight()) / 2f);
                    outline.setRoundRect(0, 0, v.getWidth(), v.getHeight(), r);
                }
            });
            root.setClipToOutline(true);
            root.invalidateOutline();
            Diag.log("标头铺图完成 " + root.getWidth() + "x" + root.getHeight()
                    + " corner=" + cornerPx);
        } catch (Throwable t) {
            Diag.log("标头 install 失败 " + t);
        }
    }

    private static String imageFrom = "?";

    private static Bitmap loadImage(Cfg cfg) {
        Bitmap file = loadFromMedia();
        if (file != null) {
            imageFrom = "media-file";
            Diag.log("用 media 文件里的图 " + file.getWidth() + "x" + file.getHeight());
            return file;
        }
        if (cfg != null && cfg.image != null && !cfg.image.isEmpty()) {
            try {
                byte[] bytes = Base64.decode(cfg.image, Base64.NO_WRAP);
                Bitmap bmp = BitmapFactory.decodeByteArray(bytes, 0, bytes.length);
                if (bmp != null) {
                    imageFrom = "config(" + cfg.via + ")";
                    Diag.log("用配置里的图 " + bmp.getWidth() + "x" + bmp.getHeight());
                    return bmp;
                }
            } catch (Throwable t) {
                Diag.log("配置里的图解码失败 " + t);
            }
        }
        return null;
    }

    static File mediaImageFile() {
        return new File(new File(Environment.getExternalStorageDirectory(),
                "Android/media/" + Prefs.MODULE_PKG), MEDIA_IMAGE);
    }

    private static Bitmap loadFromMedia() {
        try {
            File f = mediaImageFile();
            if (!f.exists() || f.length() <= 0) {
                return null;
            }
            return BitmapFactory.decodeFile(f.getAbsolutePath());
        } catch (Throwable t) {
            Diag.log("读 media 文件失败 " + t);
            return null;
        }
    }

    private static Bitmap checker(int w, int h) {
        Bitmap bmp = Bitmap.createBitmap(Math.max(1, w), Math.max(1, h), Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(bmp);
        Paint paint = new Paint();
        int cell = Math.max(10, Math.min(w, h) / 5);
        for (int y = 0; y < h; y += cell) {
            for (int x = 0; x < w; x += cell) {
                boolean even = ((x / cell) + (y / cell)) % 2 == 0;
                paint.setColor(even ? 0xFFFF00FF : 0xFF00FFFF);
                canvas.drawRect(x, y, x + cell, y + cell, paint);
            }
        }
        return bmp;
    }

    private static final class Cfg {
        boolean enabled = true;
        String image;
        int corner = Prefs.DEF_CORNER;
        int dim = Prefs.DEF_DIM;
        int blur = Prefs.DEF_BLUR;
        String via = "?";

        static Cfg load() {
            Cfg fromProvider = loadFromProvider();
            if (fromProvider != null) {
                fromProvider.via = "provider";
                if (fromProvider.image != null && !fromProvider.image.isEmpty()) {
                    return fromProvider;
                }
            }
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
