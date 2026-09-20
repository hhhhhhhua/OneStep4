package com.sangluo.onestep.utils;

import android.annotation.SuppressLint;
import android.content.Context;
import android.util.Log;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.lang.reflect.Method;

import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;

public class AweSomeUtil {  

    private static final String TAG = "OneStep4 AweSomeUtil";

    private static Context sSystemContext;

    /**
     * 自动识别环境获取 Context
     * 1. 优先尝试获取 ActivityThread 的 systemContext (系统进程/Hook环境)
     * 2. 失败则尝试获取当前应用的 Application (App 进程)
     */
    @SuppressLint("PrivateApi")
    public static Context getAnyContext() {
        if (sSystemContext != null) return sSystemContext;

        try {
            // 1. 尝试反射 ActivityThread (兼容 system_server 和普通进程)
            Class<?> activityThreadClass = Class.forName("android.app.ActivityThread");
            Method currentActivityThreadMethod = activityThreadClass.getDeclaredMethod("currentActivityThread");
            currentActivityThreadMethod.setAccessible(true);
            Object activityThread = currentActivityThreadMethod.invoke(null);

            if (activityThread != null) {
                // 尝试 getSystemContext (通常在 system_server 中最稳)
                Method getSystemContextMethod = activityThreadClass.getDeclaredMethod("getSystemContext");
                getSystemContextMethod.setAccessible(true);
                sSystemContext = (Context) getSystemContextMethod.invoke(activityThread);
            }
            
            // 2. 如果还是空，尝试获取当前 Application (普通 App 进程)
            if (sSystemContext == null && activityThread != null) {
                Method getApplicationMethod = activityThreadClass.getDeclaredMethod("getApplication");
                getApplicationMethod.setAccessible(true);
                sSystemContext = (Context) getApplicationMethod.invoke(activityThread);
            }
        } catch (Exception e) {
            Log.e(TAG, "反射获取 Context 失败: " + e.getMessage());
        }

        return sSystemContext;
    }

    /**
     * 获取系统上下文
     * @return 系统上下文
     */
    public static Context getSystemContext() {
        try {
            if (sSystemContext != null) return sSystemContext;

            // 反射调用 ActivityThread.currentActivityThread().getSystemContext()
            Object activityThread = XposedHelpers.callStaticMethod(
                    XposedHelpers.findClass("android.app.ActivityThread", null), 
                    "currentActivityThread"
            );
            return (Context) XposedHelpers.callMethod(activityThread, "getSystemContext");
        } catch (Throwable e) {
            Log.e(TAG + "获取系统Context失败", e.getMessage());
            return null;
        }
    }

    /**
     * 获取当前进程名
     * @return 当前进程名
     */
    public static String getCurrentProcessName() {
        try {
            Object activityThread = XposedHelpers.callStaticMethod(
                    XposedHelpers.findClass("android.app.ActivityThread", null), "currentActivityThread");
            if (activityThread == null) {
                return getProcessNameFromProc();
            }
            String processName = (String) XposedHelpers.callMethod(activityThread, "getProcessName");
            if (processName != null && !processName.isEmpty()) {
                return processName;
            }
            return getProcessNameFromProc();
        } catch (Throwable e) {
            try {
                return getProcessNameFromProc();
            } catch (Exception ex) {
                Log.e(TAG + "获取进程名失败", ex.getMessage());
                return "unknown";
            }
        }
    }
    
    private static String getProcessNameFromProc() {
        try {
            BufferedReader reader = new BufferedReader(new InputStreamReader(new java.io.FileInputStream("/proc/self/cmdline")));
            String processName = reader.readLine();
            reader.close();
            return processName != null ? processName.trim() : "unknown";
        } catch (Exception e) {
            return "unknown";
        }
    }


}