package com.kuuzan.statusbar;

import android.content.Context;

import java.io.File;
import java.io.FileOutputStream;

/**
 * 诊断用：既写 LSPosed 日志，也往系统界面自己的私有目录写一份，
 * 方便没有日志功能的管理器里查。
 */
final class Diag {

    private static final String FALLBACK_PATH = "/data/data/com.android.systemui/files/sbbeautify.log";
    private static final Object LOCK = new Object();

    private Diag() {
    }

    private static final java.util.concurrent.atomic.AtomicInteger PUSHED =
            new java.util.concurrent.atomic.AtomicInteger();

    static void log(String msg) {
        try {
            de.robv.android.xposed.XposedBridge.log("SBBeautify: " + msg);
        } catch (Throwable ignored) {
        }
        if (PUSHED.getAndIncrement() < 400) {
            pushToApp(msg);
        }
        synchronized (LOCK) {
            try (FileOutputStream out = new FileOutputStream(logFile(), true)) {
                out.write((System.currentTimeMillis() + " " + msg + "\n").getBytes());
            } catch (Throwable ignored) {
            }
        }
    }

    private static void pushToApp(String msg) {
        try {
            android.content.Context ctx = android.app.AndroidAppHelper.currentApplication();
            if (ctx == null) {
                return;
            }
            android.os.Bundle b = new android.os.Bundle();
            b.putString("line", msg);
            ctx.getContentResolver().call(ConfigProvider.URI, "log", null, b);
        } catch (Throwable ignored) {
        }
    }

    static String logPath() {
        return logFile().getAbsolutePath();
    }

    private static File logFile() {
        try {
            Context ctx = android.app.AndroidAppHelper.currentApplication();
            if (ctx != null) {
                File dir = ctx.getFilesDir();
                if (dir != null) {
                    return new File(dir, "sbbeautify.log");
                }
            }
        } catch (Throwable ignored) {
        }
        return new File(FALLBACK_PATH);
    }
}
