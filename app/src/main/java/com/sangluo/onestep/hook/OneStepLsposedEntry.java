package com.sangluo.onestep.hook;

import android.app.Application;
import android.content.Context;
import android.util.Log;

import com.sangluo.onestep.feature.drag.ImageDragFeatureGate;
import com.sangluo.onestep.feature.drag.ImageDragSourcePolicy;
import com.sangluo.onestep.hook.displayhooks.DisplayServiceHook;
import com.sangluo.onestep.xiaobu.XiaobuDisplay;

import java.io.File;
import java.io.IOException;
import java.lang.reflect.Method;
import java.util.concurrent.atomic.AtomicBoolean;

import de.robv.android.xposed.IXposedHookLoadPackage;
import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;
import de.robv.android.xposed.callbacks.XC_LoadPackage;

/** LSPosed backend that reuses the framework's existing XposedBridge instance. */
public final class OneStepLsposedEntry implements IXposedHookLoadPackage {
    private static final String TAG = "OneStepLsposed";

    @Override
    public void handleLoadPackage(XC_LoadPackage.LoadPackageParam loadPackageParam) {
        if (XiaobuDisplay.TARGET_PACKAGE.equals(loadPackageParam.packageName)) {
            Log.d(TAG, "Hooking xiaobu display process");
            XposedHelpers.findAndHookMethod(Application.class, "attach", Context.class,
                    new XC_MethodHook() {
                        @Override protected void afterHookedMethod(MethodHookParam hook) {
                            if (hook.thisObject instanceof Application) {
                                XiaobuDisplay.install((Application) hook.thisObject);
                            }
                        }
                    });
            return;
        }
        if (!loadPackageParam.packageName.equals("android")) {
            return;
        }

        Log.d(TAG, "Hooking system_server process");

        try {
            DisplayServiceHook.hook(loadPackageParam);
            Log.d(TAG, "DisplayServiceHook loaded");
        } catch (Throwable t) {
            Log.e(TAG, "DisplayServiceHook failed", t);
        }

    }

}
