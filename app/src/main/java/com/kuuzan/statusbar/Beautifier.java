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
import android.view.ViewGroup;
import android.view.ViewOutlineProvider;
import android.widget.TextView;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Set;
import java.util.WeakHashMap;

import de.robv.android.xposed.XSharedPreferences;

/**
 * 两个地方铺图：
 *  1) 状态栏那层 View（窗口级 DecorView / 类名层命中的 View）
 *  2) 控制中心顶部的标头
 * 标头三种找法：类名 QuickStatusBarHeader、LayoutInflater 造出来的同名 View、
 * 从下拉窗口里按 qs_carrier_text 往上爬到标头容器。
 */
final class Beautifier {

    private static final String TAG = "SBBeautify";
    private static final String MEDIA_IMAGE = "statusbar.jpg";

    private static final Set<View> DONE = Collections.newSetFromMap(new WeakHashMap<View, Boolean>());
    private static final Set<View> DONE_HEADER = Collections.newSetFromMap(new WeakHashMap<View, Boolean>());
    private static final Set<View> DONE_SHADE = Collections.newSetFromMap(new WeakHashMap<View, Boolean>());

    private Beautifier() {
    }

    // ---------------- 状态栏 ----------------

    static void attach(final View root) {
        if (root == null || DONE.contains(root)) {
            return;
        }
        DONE.add(root);
        Diag.log("attach " + root.getClass().getName() + " " + root.getWidth() + "x" + root.getHeight());
        whenLaidOut(root, new Runnable() {
            @Override
            public void run() {
                paintStatusBar(root);
            }
        });
    }

    private static void paintStatusBar(View root) {
        try {
            Cfg cfg = Cfg.load();
            if (cfg != null && !cfg.enabled) {
                Diag.log("模块被关闭");
                return;
            }
            if (cfg != null && !cfg.status) {
                Diag.log("状态栏开关关闭，跳过");
                return;
            }
            Bitmap src = loadImage(cfg);
            if (src == null) {
                root.setBackground(new BitmapDrawable(root.getResources(), checker(root.getWidth(), root.getHeight())));
                root.setClipToOutline(false);
                Diag.log("拿不到图，画棋盘格");
                return;
            }
            int cornerPx = cfg == null ? 0
                    : Math.round(Math.max(0, cfg.corner) * Resources.getSystem().getDisplayMetrics().density);
            Bitmap bmp = renderFor(src, root, cfg);
            if (bmp == null) {
                return;
            }
            BitmapDrawable drawable = new BitmapDrawable(root.getResources(), bmp);
            drawable.setGravity(Gravity.FILL);
            root.setBackground(drawable);
            clipRound(root, cornerPx);
            Diag.log("状态栏铺图完成 " + root.getWidth() + "x" + root.getHeight() + " from=" + imageFrom);
        } catch (Throwable t) {
            Diag.log("状态栏 install 失败 " + t);
        }
    }

    // ---------------- 标头 ----------------

    /** 类名 / LayoutInflater 命中的标头 View */
    static void attachHeader(final View root) {
        if (root == null || DONE_HEADER.contains(root)) {
            return;
        }
        DONE_HEADER.add(root);
        Diag.log("标头 attach " + root.getClass().getName()
                + " " + root.getWidth() + "x" + root.getHeight());
        whenLaidOut(root, new Runnable() {
            @Override
            public void run() {
                paintHeader(root, "class");
            }
        });
    }

    /** 下拉窗口整棵树，按资源 id 爬上去找标头 */
    static void attachShade(final View decor) {
        if (decor == null || DONE_SHADE.contains(decor)) {
            return;
        }
        DONE_SHADE.add(decor);
        Diag.log("下拉窗口 attach " + decor.getClass().getName());
        whenLaidOut(decor, new Runnable() {
            @Override
            public void run() {
                View header = findHeaderByText(decor);
                if (header == null) {
                    header = findHeaderByTree(decor);
                }
                if (header == null) {
                    Diag.log("下拉树里没找到标头");
                    return;
                }
                Diag.log("下拉树命中标头 " + header.getClass().getName()
                        + " " + header.getWidth() + "x" + header.getHeight());
                paintHeader(header, "tree");
            }
        });
    }

    /** 用「11月14日 星期六」这类日期文字当锚点，往上爬到标头容器 */
    private static View findHeaderByText(View decor) {
        try {
            ArrayList<View> all = new ArrayList<>();
            collectViews(decor, all);
            Diag.log("下拉树共 " + all.size() + " 个 View");
            for (View v : all) {
                if (!(v instanceof TextView) || v.getVisibility() != View.VISIBLE) {
                    continue;
                }
                CharSequence cs = ((TextView) v).getText();
                if (cs == null) {
                    continue;
                }
                String text = cs.toString().trim();
                boolean looksLikeDate = text.matches(".*\\d{1,2}月\\d{1,2}日.*") || text.contains("星期");
                if (!looksLikeDate) {
                    continue;
                }
                Diag.log("日期锚点: " + text);
                return climbToHeader(decor, v);
            }
        } catch (Throwable t) {
            Diag.log("文字找标头出错 " + t);
        }
        return null;
    }

    private static void collectViews(View v, ArrayList<View> out) {
        out.add(v);
        if (v instanceof ViewGroup) {
            ViewGroup g = (ViewGroup) v;
            for (int i = 0; i < g.getChildCount(); i++) {
                collectViews(g.getChildAt(i), out);
            }
        }
    }

    private static View climbToHeader(View decor, View anchor) {
        int decorW = decor.getWidth();
        int screenH = decor.getResources().getDisplayMetrics().heightPixels;
        int maxH = (int) (screenH * 0.45f);
        int minH = (int) (screenH * 0.15f);
        View best = null;
        View cur = anchor;
        View lastUnderLimit = null;
        while (cur != null && cur != decor) {
            int h = cur.getHeight();
            if (cur.getWidth() >= decorW * 0.85f && h > 0) {
                if (h <= maxH) {
                    lastUnderLimit = cur;
                } else {
                    break;
                }
            }
            if (!(cur.getParent() instanceof View)) {
                break;
            }
            cur = (View) cur.getParent();
        }
        if (lastUnderLimit != null && lastUnderLimit.getHeight() >= minH) {
            best = lastUnderLimit;
        } else {
            best = lastUnderLimit;
        }
        return best;
    }

    private static View findHeaderByTree(View decor) {
        try {
            Resources res = decor.getResources();
            String[] ids = {"qs_carrier_text", "quick_settings_header", "qs_header", "header_clock"};
            for (String name : ids) {
                int id = res.getIdentifier(name, "id", "com.android.systemui");
                if (id == 0) {
                    continue;
                }
                View anchor = decor.findViewById(id);
                if (anchor == null) {
                    continue;
                }
                int decorW = decor.getWidth();
                int screenH = res.getDisplayMetrics().heightPixels;
                int maxH = (int) (screenH * 0.5f);
                View best = null;
                View cur = anchor;
                while (cur != null && cur != decor) {
                    if (cur.getWidth() >= decorW * 0.9f && cur.getHeight() > 0 && cur.getHeight() <= maxH) {
                        best = cur;
                    }
                    if (!(cur.getParent() instanceof View)) {
                        break;
                    }
                    cur = (View) cur.getParent();
                }
                if (best != null) {
                    return best;
                }
            }
        } catch (Throwable t) {
            Diag.log("找标头出错 " + t);
        }
        return null;
    }

    private static void paintHeader(View root, String how) {
        try {
            Cfg cfg = Cfg.load();
            if (cfg != null && !cfg.enabled) {
                Diag.log("标头：模块被关闭");
                return;
            }
            if (cfg != null && !cfg.header) {
                Diag.log("标头：开关关闭，跳过");
                return;
            }
            Bitmap src = loadImage(cfg);
            if (src == null) {
                Diag.log("标头：没图，跳过");
                return;
            }
            int cornerDp = cfg == null ? 24 : Math.max(24, cfg.corner);
            int cornerPx = Math.round(cornerDp * Resources.getSystem().getDisplayMetrics().density);
            Bitmap bmp = renderFor(src, root, cfg);
            if (bmp == null) {
                return;
            }
            BitmapDrawable drawable = new BitmapDrawable(root.getResources(), bmp);
            drawable.setGravity(Gravity.FILL);
            // Iconify 的思路：别跟系统抢 setBackground（会被 ColorOS 的主题逻辑盖掉），
            // 铺在 foreground 上，画在背景之上、子 View 之下，正好是标头图
            try {
                root.setForeground(drawable);
                clipRound(root, cornerPx);
                Diag.log("标头铺图完成(" + how + ",foreground) "
                        + root.getWidth() + "x" + root.getHeight());
            } catch (Throwable t) {
                root.setBackground(drawable);
                clipRound(root, cornerPx);
                Diag.log("标头铺图完成(" + how + ",background) "
                        + root.getWidth() + "x" + root.getHeight());
            }
        } catch (Throwable t) {
            Diag.log("标头 install 失败 " + t);
        }
    }

    // ---------------- 公共 ----------------

    private static void whenLaidOut(final View v, final Runnable action) {
        if (v.getWidth() > 0 && v.getHeight() > 0) {
            action.run();
            return;
        }
        v.addOnLayoutChangeListener(new View.OnLayoutChangeListener() {
            @Override
            public void onLayoutChange(View view, int left, int top, int right, int bottom,
                                       int oldLeft, int oldTop, int oldRight, int oldBottom) {
                if (view.getWidth() > 0 && view.getHeight() > 0) {
                    view.removeOnLayoutChangeListener(this);
                    action.run();
                }
            }
        });
    }

    private static Bitmap renderFor(Bitmap src, View root, Cfg cfg) {
        int dim = cfg == null ? 0 : Math.max(0, Math.min(80, cfg.dim));
        int blurPx = cfg == null ? 0
                : Math.round(Math.max(0, cfg.blur) * Resources.getSystem().getDisplayMetrics().density);
        return RenderUtil.render(src, root.getWidth(), root.getHeight(), dim, blurPx);
    }

    private static void clipRound(View root, final int cornerPx) {
        if (cornerPx <= 0) {
            root.setClipToOutline(false);
            return;
        }
        root.setOutlineProvider(new ViewOutlineProvider() {
            @Override
            public void getOutline(View v, Outline outline) {
                float r = Math.min(cornerPx, Math.min(v.getWidth(), v.getHeight()) / 2f);
                outline.setRoundRect(0, 0, v.getWidth(), v.getHeight(), r);
            }
        });
        root.setClipToOutline(true);
        root.invalidateOutline();
    }

    private static String imageFrom = "?";

    private static Bitmap loadImage(Cfg cfg) {
        Bitmap file = loadFromMedia();
        if (file != null) {
            imageFrom = "media-file";
            return file;
        }
        if (cfg != null && cfg.image != null && !cfg.image.isEmpty()) {
            try {
                byte[] bytes = Base64.decode(cfg.image, Base64.NO_WRAP);
                Bitmap bmp = BitmapFactory.decodeByteArray(bytes, 0, bytes.length);
                if (bmp != null) {
                    imageFrom = "config(" + cfg.via + ")";
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
        boolean status = Prefs.DEF_STATUS;
        boolean header = Prefs.DEF_HEADER;
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
                    cfg.status = c.getInt(5) != 0;
                    cfg.header = c.getInt(6) != 0;
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
                cfg.status = p.getBoolean(Prefs.KEY_STATUS, Prefs.DEF_STATUS);
                cfg.header = p.getBoolean(Prefs.KEY_HEADER, Prefs.DEF_HEADER);
                return cfg;
            } catch (Throwable t) {
                Diag.log("XSharedPreferences 读配置失败 " + t);
                return null;
            }
        }
    }
}
