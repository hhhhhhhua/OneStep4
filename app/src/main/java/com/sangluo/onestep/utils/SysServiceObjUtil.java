package com.sangluo.onestep.utils;

import android.content.Context;
import android.hardware.display.DisplayManager;
import android.hardware.input.IInputManager;
import android.os.IBinder;
import android.util.Log;
import android.view.Display;

import com.sangluo.onestep.OneStepApplication;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;

import rikka.shizuku.ShizukuBinderWrapper;
import rikka.shizuku.SystemServiceHelper;

public class SysServiceObjUtil {
    private static final String TAG = "OneStep4";

    public static IInputManager getInputManager(){

        IInputManager inputManager =
                IInputManager.Stub.asInterface(
                        new ShizukuBinderWrapper(
                                SystemServiceHelper.getSystemService("input")
                        )
                );
        return inputManager;
    }

    public static IInputManager getInputManagerByShizukuService(){
        Class<?> serviceManager =
                null;
        try {
            serviceManager = Class.forName("android.os.ServiceManager");
            Method getService =
                    serviceManager.getMethod(
                            "getService",
                            String.class
                    );

            IBinder binder = (IBinder) getService.invoke(
                    null,
                    "input"
            );
            IInputManager inputManager =
                    IInputManager.Stub.asInterface(binder);
            return inputManager;
        } catch (Exception ex) {
            Log.e(TAG, "获取inputManager getInputManagerByShizukuService失败：", ex);
            throw new RuntimeException(ex);
        }
    }

    public static Object getInputManager2() throws Exception {
        // 1. 直接通过 SystemServiceHelper 拿到原生的 input IBinder
        IBinder systemService = SystemServiceHelper.getSystemService("input");

        if (systemService == null) {
            return null;
        }

        // 2. ❌ 不要使用 new ShizukuBinderWrapper(systemService)
        // 2. ⭕ 直接把 systemService 传给 asInterface
        return Class.forName("android.hardware.input.IInputManager$Stub")
                .getMethod("asInterface", IBinder.class)
                .invoke(null, systemService);
    }

    public static Context getCurrentContext() {
        try {
            Class<?> activityThread =
                    Class.forName("android.app.ActivityThread");

            Method currentApplication =
                    activityThread.getDeclaredMethod("currentApplication");

            currentApplication.setAccessible(true);

            return (Context) currentApplication.invoke(null);

        } catch (Throwable e) {
            Log.e(TAG, "获取Context失败", e);
            return null;
        }
    }
}
