package com.sangluo.onestep.utils;

import android.content.Context;
import android.hardware.display.DisplayManager;
import android.hardware.input.IInputManager;
import android.media.session.MediaSessionManager;
import android.os.IBinder;
import android.util.Log;
import android.view.Display;

import com.sangluo.onestep.OneStepApplication;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
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

    public static MediaSessionManager getMediaSessionManager(Context context) {
        try {
            // 1. 通过正常构造器创建对象
            Constructor<MediaSessionManager> constructor =
                    MediaSessionManager.class.getDeclaredConstructor(Context.class);
            constructor.setAccessible(true);

            MediaSessionManager manager =
                    constructor.newInstance(context);

            // 2. Shizuku 获取 media_session Binder
            IBinder binder =
                    SystemServiceHelper.getSystemService("media_session");

            // 3. Shizuku Binder 包装
            IBinder wrappedBinder =
                    new ShizukuBinderWrapper(binder);

            // 4. 反射调用 ISessionManager.Stub.asInterface()
            Class<?> stubClass = Class.forName(
                    "android.media.session.ISessionManager$Stub"
            );

            Method asInterface = stubClass.getDeclaredMethod(
                    "asInterface",
                    IBinder.class
            );
            asInterface.setAccessible(true);

            Object sessionService =
                    asInterface.invoke(null, wrappedBinder);

            // 5. 找 MediaSessionManager 内部的 ISessionManager 字段
            Field serviceField = null;

            for (Field field :
                    MediaSessionManager.class.getDeclaredFields()) {

                if (field.getType().getName().equals(
                        "android.media.session.ISessionManager")) {

                    serviceField = field;
                    break;
                }
            }

            if (serviceField == null) {
                throw new NoSuchFieldException(
                        "ISessionManager field not found"
                );
            }

            serviceField.setAccessible(true);

            // 6. 替换成 Shizuku 的 Binder
            serviceField.set(manager, sessionService);

            return manager;

        } catch (Throwable e) {
            throw new RuntimeException(e);
        }
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
