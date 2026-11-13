package com.kuuzan.statusbar;

import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;

import de.robv.android.xposed.IXposedHookLoadPackage;
import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;
import de.robv.android.xposed.callbacks.XC_LoadPackage;

/**
 * 两层找法：
 *  1) 窗口层：挂 WindowManagerImpl#addView，认 WindowManager.LayoutParams 里
 *     type == TYPE_STATUS_BAR(2000) 的那个窗口，把图铺在它的 DecorView 上。
 *     厂商改类名改不到窗口类型，这一层给 ColorOS / OriginOS 这类留后路。
 *  2) 类名层：AOSP / 类原生 的 CollapsedStatusBarFragment（11+）
 *     和 StatusBar / PhoneStatusBar 的 makeStatusBarView（10 及以前）。
 * 两条都命中也没事，两层铺的是同一张图。
 */
public class HookMain implements IXposedHookLoadPackage {

    private static final String TAG = "SBBeautify";
    private static final String SYSTEMUI = "com.android.systemui";

    private static final String[] CLASSES = {
            "com.android.systemui.statusbar.phone.CollapsedStatusBarFragment",
            "com.android.systemui.statusbar.phone.StatusBar",
            "com.android.systemui.statusbar.phone.PhoneStatusBar",
    };

    private static final String[] METHODS = {
            "onCreateView",
            "onViewCreated",
            "makeStatusBarView",
    };

    @Override
    public void handleLoadPackage(XC_LoadPackage.LoadPackageParam lp) {
        if (!isSystemUi(lp)) {
            return;
        }
        hookStatusBarWindow(lp.classLoader);
        for (String cls : CLASSES) {
            for (String method : METHODS) {
                hook(lp.classLoader, cls, method);
            }
        }
    }

    private static boolean isSystemUi(XC_LoadPackage.LoadPackageParam lp) {
        if (SYSTEMUI.equals(lp.packageName)) {
            return true;
        }
        // 有的厂商把 SystemUI 挪到自家包名下，包名里一般还留着 systemui
        String pkg = lp.packageName == null ? "" : lp.packageName.toLowerCase();
        String proc = lp.processName == null ? "" : lp.processName.toLowerCase();
        return pkg.contains("systemui") || proc.contains("systemui");
    }

    /** 窗口类型兜底：不依赖任何 SystemUI 类名 */
    private static void hookStatusBarWindow(ClassLoader cl) {
        try {
            XposedHelpers.findAndHookMethod("android.view.WindowManagerImpl", cl,
                    "addView", View.class, ViewGroup.LayoutParams.class, new XC_MethodHook() {
                        @Override
                        protected void afterHookedMethod(MethodHookParam param) {
                            Object raw = param.args[1];
                            if (!(raw instanceof WindowManager.LayoutParams)) {
                                return;
                            }
                            WindowManager.LayoutParams attrs = (WindowManager.LayoutParams) raw;
                            if (attrs.type != WindowManager.LayoutParams.TYPE_STATUS_BAR) {
                                return;
                            }
                            View decor = (View) param.args[0];
                            XposedBridge.log(TAG + ": 命中状态栏窗口，铺在 DecorView 上");
                            Beautifier.attach(decor);
                        }
                    });
        } catch (Throwable t) {
            XposedBridge.log(TAG + ": 窗口兜底挂不上 " + t);
        }
    }

    private static void hook(ClassLoader cl, String className, String methodName) {
        try {
            Class<?> clazz = XposedHelpers.findClass(className, cl);
            XposedBridge.hookAllMethods(clazz, methodName, new XC_MethodHook() {
                @Override
                protected void afterHookedMethod(MethodHookParam param) {
                    View view = null;
                    Object result = param.getResult();
                    if (result instanceof View) {
                        view = (View) result;
                    } else if (param.args != null && param.args.length > 0 && param.args[0] instanceof View) {
                        view = (View) param.args[0];
                    } else if (param.thisObject instanceof View) {
                        view = (View) param.thisObject;
                    }
                    if (view != null) {
                        XposedBridge.log(TAG + ": 命中 " + className + "#" + methodName);
                        Beautifier.attach(view);
                    }
                }
            });
        } catch (Throwable t) {
            // 类/方法不存在属于正常情况，换下一个候选
            XposedBridge.log(TAG + ": 跳过 " + className + "#" + methodName);
        }
    }
}
