package com.kuuzan.statusbar;

/** 模块与 SystemUI 两端共用的存储约定 */
public final class Prefs {

    /** 必须与 build.gradle 里的 applicationId 一致，XSharedPreferences 靠它找文件 */
    public static final String MODULE_PKG = "com.kuuzan.statusbar";
    public static final String FILE = "config";

    public static final String KEY_ENABLED = "enabled";
    public static final String KEY_IMAGE = "image";   // 处理好的图（JPEG）的 Base64
    public static final String KEY_URI = "source_uri";
    public static final String KEY_CORNER = "corner"; // dp
    public static final String KEY_DIM = "dim";       // 0-80 百分比
    public static final String KEY_BLUR = "blur";     // dp
    public static final String KEY_STATUS = "status_enabled";
    public static final String KEY_HEADER = "header_enabled";
    public static final String KEY_HEADER_HEIGHT = "header_height"; // 屏幕高度百分比

    public static final int DEF_CORNER = 16;
    public static final int DEF_DIM = 20;
    public static final int DEF_BLUR = 0;
    public static final boolean DEF_STATUS = false;
    public static final boolean DEF_HEADER = true;
    public static final int DEF_HEADER_HEIGHT = 25;

    private Prefs() {
    }
}
