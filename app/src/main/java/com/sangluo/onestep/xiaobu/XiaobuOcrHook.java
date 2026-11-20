package com.sangluo.onestep.xiaobu;

import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;

/** Starts native local OCR without first waiting for the cloud response. */
final class XiaobuOcrHook {
    static void install(ClassLoader loader) {
        try {
            String observer;
            String waitMethod;
            Class<?> type;
            try {
                observer = "com.oplus.aiunit.ocr.client.schedule.CloudResultObserver";
                type = Class.forName(observer, false, loader);
                waitMethod = "c";
            } catch (ClassNotFoundException renamed) {
                observer = "com.oplus.aiunit.ocr.client.schedule.a";
                type = Class.forName(observer, false, loader);
                waitMethod = "b";
                if (type.getDeclaredField("a").getType() != java.util.concurrent.CountDownLatch.class) {
                    throw new NoSuchFieldException("Unknown cloud observer latch");
                }
            }
            if (type.getDeclaredMethod(waitMethod, long.class).getReturnType() != void.class) {
                throw new NoSuchMethodException("Unknown cloud wait implementation");
            }
            XposedHelpers.findAndHookMethod(observer, loader, waitMethod, long.class,
                    new XC_MethodHook() {
                        @Override protected void beforeHookedMethod(MethodHookParam hook) {
                            if (!XiaobuDisplay.routingEnabled() || !XiaobuDisplay.isTargeted()) return;
                            long original = (Long) hook.args[0];
                            if (original > 0) {
                                hook.args[0] = 0L;
                                XposedBridge.log("XiaobuLauncherHook: cloud pre-wait " + original
                                        + "s bypassed; native local OCR starts immediately");
                            }
                        }
                    });
            XposedBridge.log("XiaobuLauncherHook: nonblocking cloud OCR hook installed");
        } catch (Throwable error) {
            XposedBridge.log("XiaobuLauncherHook: native OCR scheduling retained: " + error);
        }
    }
}
