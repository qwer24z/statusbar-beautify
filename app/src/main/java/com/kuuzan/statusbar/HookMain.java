package com.kuuzan.statusbar;

import android.os.Build;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;

import de.robv.android.xposed.IXposedHookLoadPackage;
import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;
import de.robv.android.xposed.callbacks.XC_LoadPackage;

/**
 * 三层找法：
 *  1) 窗口层：WindowManagerImpl#addView 里 type == TYPE_STATUS_BAR(2000) 的窗口，铺在 DecorView
 *  2) 窗口层备胎：ViewRootImpl#setView，同样看 WindowManager.LayoutParams 的类型
 *  3) 类名层：CollapsedStatusBarFragment（11+）/ StatusBar / PhoneStatusBar（10 及以前）
 * 每一步都写诊断日志，命中与否一看就知道。
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
        Diag.log("进入进程 " + lp.packageName + " sdk=" + Build.VERSION.SDK_INT);
        hookSystemUiApp(lp.classLoader);
        hookStatusBarWindow(lp.classLoader);
        hookViewRoot(lp.classLoader);
        hookQsHeader(lp.classLoader);
        for (String cls : CLASSES) {
            for (String method : METHODS) {
                hook(lp.classLoader, cls, method);
            }
        }
        Diag.log("钩子安装完毕");
    }

    private static boolean isSystemUi(XC_LoadPackage.LoadPackageParam lp) {
        if (SYSTEMUI.equals(lp.packageName)) {
            return true;
        }
        String pkg = lp.packageName == null ? "" : lp.packageName.toLowerCase();
        String proc = lp.processName == null ? "" : lp.processName.toLowerCase();
        return pkg.contains("systemui") || proc.contains("systemui");
    }

    /** 证明模块确实进了 SystemUI 进程 */
    private static void hookSystemUiApp(ClassLoader cl) {
        try {
            Class<?> clazz = XposedHelpers.findClass("com.android.systemui.SystemUIApplication", cl);
            XposedBridge.hookAllMethods(clazz, "onCreate", new XC_MethodHook() {
                @Override
                protected void afterHookedMethod(MethodHookParam param) {
                    Diag.log("SystemUIApplication.onCreate 被调到");
                }
            });
        } catch (Throwable t) {
            Diag.log("找不到 SystemUIApplication: " + t);
        }
    }

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
                            Diag.log("WindowManagerImpl.addView 命中状态栏窗口");
                            Beautifier.attach((View) param.args[0]);
                        }
                    });
        } catch (Throwable t) {
            Diag.log("窗口兜底挂不上 " + t);
        }
    }

    private static void hookViewRoot(ClassLoader cl) {
        try {
            Class<?> clazz = XposedHelpers.findClass("android.view.ViewRootImpl", cl);
            XposedBridge.hookAllMethods(clazz, "setView", new XC_MethodHook() {
                @Override
                protected void afterHookedMethod(MethodHookParam param) {
                    Object raw = param.args.length > 1 ? param.args[1] : null;
                    if (!(raw instanceof WindowManager.LayoutParams)) {
                        return;
                    }
                    if (((WindowManager.LayoutParams) raw).type != WindowManager.LayoutParams.TYPE_STATUS_BAR) {
                        return;
                    }
                    Object view = param.args[0];
                    if (view instanceof View) {
                        Diag.log("ViewRootImpl.setView 命中状态栏窗口");
                        Beautifier.attach((View) view);
                    }
                }
            });
        } catch (Throwable t) {
            Diag.log("ViewRootImpl 兜底挂不上 " + t);
        }
    }

    /** 控制中心标头：ColorOS 是 com.oplus.systemui.qs.OplusQuickStatusBarHeader，兜底认类名含 QuickStatusBarHeader 的 View */
    private static void hookQsHeader(ClassLoader cl) {
        try {
            Class<?> viewClass = XposedHelpers.findClass("android.view.View", cl);
            XposedBridge.hookAllMethods(viewClass, "onFinishInflate", new XC_MethodHook() {
                @Override
                protected void afterHookedMethod(MethodHookParam param) {
                    Object raw = param.thisObject;
                    if (!(raw instanceof View)) {
                        return;
                    }
                    String name = raw.getClass().getName();
                    if (name.contains("QuickStatusBarHeader")) {
                        Diag.log("QS 标头命中 " + name);
                        Beautifier.attachHeader((View) raw);
                    }
                }
            });
        } catch (Throwable t) {
            Diag.log("QS 标头钩子挂不上 " + t);
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
                        Diag.log("类名层命中 " + className + "#" + methodName);
                        Beautifier.attach(view);
                    }
                }
            });
        } catch (Throwable t) {
            Diag.log("跳过 " + className + "#" + methodName);
        }
    }
}
