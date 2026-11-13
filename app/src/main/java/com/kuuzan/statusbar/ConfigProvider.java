package com.kuuzan.statusbar;

import android.content.ContentProvider;
import android.content.ContentValues;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.database.MatrixCursor;
import android.net.Uri;

/**
 * 把模块的配置喂给 SystemUI 进程。
 * XSharedPreferences 在部分 LSPosed 版本上读不到，走 provider 就不吃文件权限这一套。
 */
public class ConfigProvider extends ContentProvider {

    public static final String AUTHORITY = "com.kuuzan.statusbar.config";
    public static final Uri URI = Uri.parse("content://" + AUTHORITY + "/config");

    public static final String[] COLUMNS = {
            "enabled", "image", "corner", "dim", "blur"
    };

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
