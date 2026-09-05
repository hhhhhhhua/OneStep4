package com.sangluo.onestep.shizuku;

import android.os.IBinder;
import android.util.Log;

import com.sangluo.onestep.IShizukuCommonService;
import com.sangluo.onestep.RootVirtualDisplayBridge;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;

public class ShizukuCommonService extends IShizukuCommonService.Stub{

    private static final String TAG = "OneStep40";

    @Override
    public IBinder getService(String serviceName){
        try {
            Class<?> serviceManager = Class.forName("android.os.ServiceManager");
            Method getService = serviceManager.getDeclaredMethod("getService", String.class);
            getService.setAccessible(true);
            Object result = getService.invoke(null, serviceName);
            if (!(result instanceof IBinder)) {
                Log.e(TAG, "root display service unavailable");
                throw new IllegalStateException("root display service unavailable: " + result);
            }
            return (IBinder)result;
        } catch (ClassNotFoundException | NoSuchMethodException | IllegalAccessException
                 | InvocationTargetException e) {
            Log.e(TAG, "cannot resolve root display service", e);
            throw new IllegalStateException("cannot resolve root display service", e);
        }
    }

    @Override
    public void destroy() {
        System.exit(0);
    }
}
