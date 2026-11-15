package com.kuuzan.statusbar;

import android.content.ContentProvider;
import android.content.ContentValues;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.database.Cursor;
import android.database.MatrixCursor;
import android.net.Uri;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * 把模块的配置喂给 SystemUI 进程。
 * XSharedPreferences 在部分 LSPosed 版本上读不到，走 provider 就不吃文件权限这一套。
 */
public class ConfigProvider extends ContentProvider {

    public static final String AUTHORITY = "com.kuuzan.statusbar.config";
    public static final Uri URI = Uri.parse("content://" + AUTHORITY + "/config");

    public static final String[] COLUMNS = {
            "enabled", "image", "corner", "dim", "blur", "status", "header", "header_height", "fade"
    };

    /** SystemUI 那边写过来的诊断行，App 进程里读 */
    private static final List<String> LOGS =
            Collections.synchronizedList(new ArrayList<String>());

    @Override
    public Bundle call(String method, String arg, Bundle extras) {
        if ("log".equals(method)) {
            String line = extras == null ? null : extras.getString("line");
            if (line != null) {
                synchronized (LOGS) {
                    if (LOGS.size() > 400) {
                        LOGS.remove(0);
                    }
                    LOGS.add(line);
                }
            }
            return null;
        }
        if ("dump".equals(method)) {
            Bundle out = new Bundle();
            ArrayList<String> copy;
            synchronized (LOGS) {
                copy = new ArrayList<>(LOGS);
            }
            out.putStringArrayList("lines", copy);
            return out;
        }
        return null;
    }

    public static List<String> dumpLocal() {
        synchronized (LOGS) {
            return new ArrayList<>(LOGS);
        }
    }

    @Override
    public boolean onCreate() {
        return true;
    }

    @Override
    public Cursor query(Uri uri, String[] projection, String selection,
                        String[] selectionArgs, String sortOrder) {
        SharedPreferences p = getContext().getSharedPreferences(Prefs.FILE, 0);
        MatrixCursor cursor = new MatrixCursor(COLUMNS);
        cursor.addRow(new Object[]{
                p.getBoolean(Prefs.KEY_ENABLED, true) ? 1 : 0,
                p.getString(Prefs.KEY_IMAGE, null),
                p.getInt(Prefs.KEY_CORNER, Prefs.DEF_CORNER),
                p.getInt(Prefs.KEY_DIM, Prefs.DEF_DIM),
                p.getInt(Prefs.KEY_BLUR, Prefs.DEF_BLUR),
                p.getBoolean(Prefs.KEY_STATUS, Prefs.DEF_STATUS) ? 1 : 0,
                p.getBoolean(Prefs.KEY_HEADER, Prefs.DEF_HEADER) ? 1 : 0,
                p.getInt(Prefs.KEY_HEADER_HEIGHT, Prefs.DEF_HEADER_HEIGHT),
                p.getInt(Prefs.KEY_FADE, Prefs.DEF_FADE),
        });
        return cursor;
    }

    @Override
    public String getType(Uri uri) {
        return null;
    }

    @Override
    public Uri insert(Uri uri, ContentValues values) {
        return null;
    }

    @Override
    public int delete(Uri uri, String selection, String[] selectionArgs) {
        return 0;
    }

    @Override
    public int update(Uri uri, ContentValues values, String selection, String[] selectionArgs) {
        return 0;
    }
}
