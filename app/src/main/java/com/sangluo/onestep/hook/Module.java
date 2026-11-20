package com.sangluo.onestep.hook;


import android.app.Application;
import android.content.Context;
import android.util.Log;

import com.sangluo.onestep.hook.displayhooks.DisplayServiceHook;
import com.sangluo.onestep.xiaobu.XiaobuDisplay;

import de.robv.android.xposed.IXposedHookLoadPackage;
import de.robv.android.xposed.IXposedHookZygoteInit;
import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;
import de.robv.android.xposed.callbacks.XC_LoadPackage;

public class Module implements IXposedHookLoadPackage, IXposedHookZygoteInit {

    private static final String TAG = "OneStep4 EnhanceMirror";

    @Override
    public void initZygote(StartupParam startupParam) throws Throwable {
        Log.d(TAG, "Module initialized at: " + startupParam.modulePath);
    }

    @Override
    public void handleLoadPackage(XC_LoadPackage.LoadPackageParam lpparam) throws Throwable {
        if (XiaobuDisplay.TARGET_PACKAGE.equals(lpparam.packageName)) {
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
        if (!lpparam.packageName.equals("android")) {
            return;
        }

        Log.d(TAG, "Hooking system_server process");

        try {
            DisplayServiceHook.hook(lpparam);
            Log.d(TAG, "DisplayServiceHook loaded");
        } catch (Throwable t) {
            Log.e(TAG, "DisplayServiceHook failed", t);
        }
        
    }
}
