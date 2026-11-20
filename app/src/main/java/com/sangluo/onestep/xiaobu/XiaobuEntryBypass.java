package com.sangluo.onestep.xiaobu;

import android.content.Context;
import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedBridge;

/** Lets the routed 识屏 run on pages the host normally rejects (launcher/desktop/blacklist). */
final class XiaobuEntryBypass {
    private static final String TAG = "OneStepXiaobu";

    private XiaobuEntryBypass() {}

    static void install(ClassLoader loader) {
        try {
            Class<?> helper = Class.forName("xk0", false, loader);
            java.lang.reflect.Method launcher = helper.getDeclaredMethod("a");
            if (launcher.getReturnType() != boolean.class) {
                throw new NoSuchMethodException("Unknown launcher top check");
            }
            XposedBridge.hookMethod(launcher, new XC_MethodHook() {
                @Override protected void beforeHookedMethod(MethodHookParam hook) {
                    if (!XiaobuDisplay.routingEnabled() || !XiaobuDisplay.isTargeted()) return;
                    hook.setResult(false);
                }
            });
            XposedBridge.log("OneStepXiaobu: launcher top gate bypass installed");
        } catch (Throwable error) {
            XposedBridge.log("OneStepXiaobu: launcher top gate unavailable: " + error);
        }
        try {
            Class<?> common = Class.forName("com.oplus.infocollection.common.a", false, loader);
            java.lang.reflect.Method method = common.getDeclaredMethod("a");
            if (method.getReturnType() != boolean.class) {
                throw new NoSuchMethodException("Unknown launcher gate");
            }
            XposedBridge.hookMethod(method, new XC_MethodHook() {
                @Override protected void beforeHookedMethod(MethodHookParam hook) {
                    if (!XiaobuDisplay.routingEnabled() || !XiaobuDisplay.isTargeted()) return;
                    hook.setResult(false);
                }
            });
            XposedBridge.log("OneStepXiaobu: launcher gate bypass installed");
        } catch (Throwable error) {
            XposedBridge.log("OneStepXiaobu: launcher gate bypass unavailable: " + error);
        }
        try {
            Class<?> handler = Class.forName(
                    "com.coloros.colordirectservice.identify.entrance.DirectEntranceHandler", false, loader);
            Class<?> info = Class.forName(
                    "com.coloros.colordirectservice.identify.beans.DirectStartInfo", false, loader);
            Class<?> continuation = Class.forName("kotlin.coroutines.Continuation", false, loader);
            java.lang.reflect.Method method = handler.getDeclaredMethod(
                    "j", Context.class, info, boolean.class, continuation);
            XposedBridge.hookMethod(method, new XC_MethodHook() {
                @Override protected void beforeHookedMethod(MethodHookParam hook) {
                    if (!XiaobuDisplay.routingEnabled() || !XiaobuDisplay.isTargeted()) return;
                    if (hook.args.length > 1 && hook.args[1] != null) hook.setResult(Boolean.TRUE);
                }
            });
            XposedBridge.log("OneStepXiaobu: app blacklist bypass installed");
        } catch (Throwable error) {
            XposedBridge.log("OneStepXiaobu: app blacklist bypass unavailable: " + error);
        }
        try {
            Class<?> utils = Class.forName(
                    "com.oplus.infocollection.utils.WindowManagerUtils", false, loader);
            Class<?> info = Class.forName("com.oplus.app.OplusWindowInfo", false, loader);
            java.lang.reflect.Method method = utils.getDeclaredMethod("i", info);
            final boolean primitive = method.getReturnType() == boolean.class;
            XposedBridge.hookMethod(method, new XC_MethodHook() {
                @Override protected void beforeHookedMethod(MethodHookParam hook) {
                    if (!XiaobuDisplay.routingEnabled() || !XiaobuDisplay.isTargeted()) return;
                    hook.setResult(primitive ? Boolean.FALSE : null);
                }
            });
            XposedBridge.log("OneStepXiaobu: secure window precheck bypass installed");
        } catch (Throwable error) {
            XposedBridge.log("OneStepXiaobu: secure window precheck bypass unavailable: " + error);
        }
    }
}
