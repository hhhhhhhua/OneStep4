package com.sangluo.onestep.hook;


import android.util.Log;

import com.sangluo.onestep.hook.displayhooks.DisplayServiceHook;

import de.robv.android.xposed.IXposedHookLoadPackage;
import de.robv.android.xposed.IXposedHookZygoteInit;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.callbacks.XC_LoadPackage;

public class Module implements IXposedHookLoadPackage, IXposedHookZygoteInit {

    private static final String TAG = "OneStep4 EnhanceMirror";

    @Override
    public void initZygote(StartupParam startupParam) throws Throwable {
        Log.d(TAG, "Module initialized at: " + startupParam.modulePath);
    }

    @Override
    public void handleLoadPackage(XC_LoadPackage.LoadPackageParam lpparam) throws Throwable {
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
