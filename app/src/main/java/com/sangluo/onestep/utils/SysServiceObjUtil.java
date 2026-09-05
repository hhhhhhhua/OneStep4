package com.sangluo.onestep.utils;

import android.os.IBinder;

import java.lang.reflect.InvocationTargetException;

import rikka.shizuku.ShizukuBinderWrapper;
import rikka.shizuku.SystemServiceHelper;

public class SysServiceObjUtil {

    public static Object getInputManager() throws ClassNotFoundException, NoSuchMethodException, InvocationTargetException, IllegalAccessException {

        IBinder systemService = SystemServiceHelper.getSystemService("input");

        if (systemService == null) {

            return null;

        }

        return Class.forName("android.hardware.input.IInputManager$Stub").getMethod("asInterface", IBinder.class).invoke(null, new ShizukuBinderWrapper(systemService));

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
}
