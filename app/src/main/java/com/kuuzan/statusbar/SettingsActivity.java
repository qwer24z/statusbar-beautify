package com.kuuzan.statusbar;

import android.app.Activity;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.ImageDecoder;
import android.graphics.Outline;
import android.graphics.Typeface;
import android.net.Uri;
import android.os.Environment;
import android.os.Bundle;
import android.util.Base64;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewOutlineProvider;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.Toast;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;

/**
 * 模块的设置页：选图、看预览、调圆角 / 暗化 / 模糊。
 * 用的是系统自带的 ActivityResult 老接口，不引 androidx，APK 能小一点。
 */
public class SettingsActivity extends Activity {

    private static final int REQ_PICK = 1001;

    private SharedPreferences prefs;
    private int sbH;
    private Bitmap source;

    private ImageView preview;
    private TextView imageState;
    private CheckBox enabledBox;
    private SeekBar cornerBar;
    private SeekBar dimBar;
    private SeekBar blurBar;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        prefs = getSharedPreferences(Prefs.FILE, MODE_PRIVATE);
        sbH = statusBarHeight();
        setContentView(buildUi());
        loadStored();
    }

    // ---------------- 界面 ----------------

    private View buildUi() {
        ScrollView scroll = new ScrollView(this);
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        int pad = dp(20);
        box.setPadding(pad, pad, pad, dp(36));
        scroll.addView(box, new ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        TextView title = new TextView(this);
        title.setText("状态栏美化");
        title.setTextSize(22);
        title.setTypeface(null, Typeface.BOLD);
        box.addView(title);

        TextView sub = new TextView(this);
        sub.setText("挑一张图铺进系统状态栏，圆角、暗化、模糊在这儿调");
        sub.setTextSize(13);
        sub.setAlpha(0.6f);
        LinearLayout.LayoutParams subLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        subLp.topMargin = dp(4);
        box.addView(sub, subLp);

        preview = new ImageView(this);
        preview.setScaleType(ImageView.ScaleType.CENTER_CROP);
        LinearLayout.LayoutParams prevLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, sbH);
        prevLp.topMargin = dp(18);
        box.addView(preview, prevLp);

        imageState = new TextView(this);
        imageState.setTextSize(12);
        imageState.setAlpha(0.7f);
        LinearLayout.LayoutParams stLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        stLp.topMargin = dp(6);
        box.addView(imageState, stLp);

        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        LinearLayout.LayoutParams rowLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        rowLp.topMargin = dp(14);
        box.addView(row, rowLp);

        Button pick = new Button(this);
        pick.setText("选择图片");
        pick.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                pickImage();
            }
        });
        row.addView(pick, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        Button clear = new Button(this);
        clear.setText("清空");
        clear.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                source = null;
                prefs.edit().remove(Prefs.KEY_IMAGE).remove(Prefs.KEY_URI).apply();
                preview.setImageDrawable(null);
                updateImageState();
            }
        });
        LinearLayout.LayoutParams clearLp = new LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        clearLp.leftMargin = dp(8);
        row.addView(clear, clearLp);

        enabledBox = new CheckBox(this);
        enabledBox.setText("启用");
        enabledBox.setChecked(prefs.getBoolean(Prefs.KEY_ENABLED, true));
        enabledBox.setOnCheckedChangeListener((btn, checked) ->
                prefs.edit().putBoolean(Prefs.KEY_ENABLED, checked).apply());
        LinearLayout.LayoutParams enLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        enLp.topMargin = dp(16);
        box.addView(enabledBox, enLp);

        cornerBar = addSlider(box, "圆角 %d dp", 40,
                prefs.getInt(Prefs.KEY_CORNER, Prefs.DEF_CORNER));
        dimBar = addSlider(box, "暗化 %d %%", 80,
                prefs.getInt(Prefs.KEY_DIM, Prefs.DEF_DIM));
        blurBar = addSlider(box, "模糊 %d dp", 25,
                prefs.getInt(Prefs.KEY_BLUR, Prefs.DEF_BLUR));

        TextView hint = new TextView(this);
        hint.setText("改完去 LSPosed 里勾上作用域「系统界面」，再重启系统界面或重启手机才生效。"
                + "AOSP / 类原生直接能用；ColorOS 这种改了类名的走窗口级兜底，不确定时看 LSPosed 日志里 SBBeautify 的命中记录。");
        hint.setTextSize(12);
        hint.setAlpha(0.55f);
        LinearLayout.LayoutParams hintLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        hintLp.topMargin = dp(24);
        box.addView(hint, hintLp);

        return scroll;
    }

    private SeekBar addSlider(LinearLayout parent, String fmt, int max, int progress) {
        TextView label = new TextView(this);
        label.setTextSize(14);
        label.setText(String.format(fmt, progress));
        LinearLayout.LayoutParams labelLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        labelLp.topMargin = dp(16);
        parent.addView(label, labelLp);

        SeekBar bar = new SeekBar(this);
        bar.setMax(max);
        bar.setProgress(progress);
        bar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int value, boolean fromUser) {
                label.setText(String.format(fmt, value));
                onSlidersChanged();
            }

            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {
            }

            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {
            }
        });
        parent.addView(bar, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        return bar;
    }

    // ---------------- 数据 ----------------

    private void loadStored() {
        String b64 = prefs.getString(Prefs.KEY_IMAGE, null);
        if (b64 != null && !b64.isEmpty()) {
            try {
                byte[] bytes = Base64.decode(b64, Base64.NO_WRAP);
                source = BitmapFactory.decodeByteArray(bytes, 0, bytes.length);
            } catch (Throwable ignored) {
                source = null;
            }
        }
        ensureMediaFile();
        updateImageState();
        renderPreview();
    }

    private void onSlidersChanged() {
        prefs.edit()
                .putInt(Prefs.KEY_CORNER, cornerBar.getProgress())
                .putInt(Prefs.KEY_DIM, dimBar.getProgress())
                .putInt(Prefs.KEY_BLUR, blurBar.getProgress())
                .apply();
        renderPreview();
    }

    private void renderPreview() {
        if (source == null) {
            preview.setImageDrawable(null);
            preview.setBackgroundColor(0x22000000);
            return;
        }
        int width = getResources().getDisplayMetrics().widthPixels;
        Bitmap out = RenderUtil.render(source, width, sbH,
                dimBar.getProgress(), dp(blurBar.getProgress()));
        preview.setImageBitmap(out);
        applyCorner(preview, dp(cornerBar.getProgress()));
    }

    private void applyCorner(View view, int cornerPx) {
        if (cornerPx <= 0) {
            view.setClipToOutline(false);
            return;
        }
        view.setOutlineProvider(new ViewOutlineProvider() {
            @Override
            public void getOutline(View v, Outline outline) {
                float r = Math.min(cornerPx, Math.min(v.getWidth(), v.getHeight()) / 2f);
                outline.setRoundRect(0, 0, v.getWidth(), v.getHeight(), r);
            }
        });
        view.setClipToOutline(true);
        view.invalidateOutline();
    }

    private void pickImage() {
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType("image/*");
        intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION
                | Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION);
        startActivityForResult(intent, REQ_PICK);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode != REQ_PICK || resultCode != RESULT_OK || data == null || data.getData() == null) {
            return;
        }
        final Uri uri = data.getData();
        try {
            getContentResolver().takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION);
        } catch (Throwable ignored) {
        }
        prefs.edit().putString(Prefs.KEY_URI, uri.toString()).apply();
        Toast.makeText(this, "读图中", Toast.LENGTH_SHORT).show();
        new Thread(new Runnable() {
            @Override
            public void run() {
                final Bitmap bm = decodeScaled(uri);
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        if (bm == null) {
                            Toast.makeText(SettingsActivity.this, "这张图打不开", Toast.LENGTH_SHORT).show();
                            return;
                        }
                        source = bm;
                        saveImage(bm);
                        renderPreview();
                    }
                });
            }
        }).start();
    }

    private Bitmap decodeScaled(Uri uri) {
        try {
            ImageDecoder.Source src = ImageDecoder.createSource(getContentResolver(), uri);
            Bitmap bm = ImageDecoder.decodeBitmap(src, (decoder, info, source) -> {
                int ow = info.getSize().getWidth();
                int oh = info.getSize().getHeight();
                if (ow > 1080) {
                    float k = 1080f / ow;
                    decoder.setTargetSize(1080, Math.max(1, Math.round(oh * k)));
                } else if (oh > 1440) {
                    float k = 1440f / oh;
                    decoder.setTargetSize(Math.max(1, Math.round(ow * k)), 1440);
                }
                decoder.setAllocator(ImageDecoder.ALLOCATOR_SOFTWARE);
            });
            if (bm == null) {
                return null;
            }
            return bm.copy(Bitmap.Config.ARGB_8888, true);
        } catch (Throwable t) {
            return null;
        }
    }

    private void updateImageState() {
        String b64 = prefs.getString(Prefs.KEY_IMAGE, null);
        if (b64 == null || b64.isEmpty()) {
            imageState.setText("图片状态：未选择，状态栏会是一层淡蓝");
        } else {
            imageState.setText("图片状态：已保存约 " + (b64.length() * 3 / 4 / 1024) + " KB");
        }
    }

    private void saveImage(Bitmap bm) {
        try {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            bm.compress(Bitmap.CompressFormat.JPEG, 88, out);
            prefs.edit().putString(Prefs.KEY_IMAGE,
                    Base64.encodeToString(out.toByteArray(), Base64.NO_WRAP)).apply();
            String back = prefs.getString(Prefs.KEY_IMAGE, null);
            Toast.makeText(this, back == null ? "保存失败"
                    : "已保存 " + (back.length() * 3 / 4 / 1024) + " KB", Toast.LENGTH_SHORT).show();
        } catch (Throwable t) {
            Toast.makeText(this, "保存失败：" + t, Toast.LENGTH_LONG).show();
        }
        writeMediaFile(bm);
        updateImageState();
    }

    /** 另存一份到 /sdcard/Android/media，SystemUI 直接读这个文件，不靠配置同步 */
    private void writeMediaFile(Bitmap bm) {
        try {
            File dir = new File(Environment.getExternalStorageDirectory(),
                    "Android/media/" + Prefs.MODULE_PKG);
            if (!dir.exists() && !dir.mkdirs()) {
                return;
            }
            File out = new File(dir, "statusbar.jpg");
            FileOutputStream fos = new FileOutputStream(out);
            try {
                bm.compress(Bitmap.CompressFormat.JPEG, 90, fos);
            } finally {
                fos.close();
            }
        } catch (Throwable t) {
            Toast.makeText(this, "写 media 文件失败：" + t, Toast.LENGTH_LONG).show();
        }
    }

    private void ensureMediaFile() {
        if (source == null) {
            return;
        }
        try {
            File f = new File(new File(Environment.getExternalStorageDirectory(),
                    "Android/media/" + Prefs.MODULE_PKG), "statusbar.jpg");
            if (!f.exists() || f.length() <= 0) {
                writeMediaFile(source);
            }
        } catch (Throwable ignored) {
        }
    }

    // ---------------- 杂项 ----------------

    private int statusBarHeight() {
        int id = getResources().getIdentifier("status_bar_height", "dimen", "android");
        int h = id > 0 ? getResources().getDimensionPixelSize(id) : 0;
        return h > 0 ? h : dp(24);
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }
}
