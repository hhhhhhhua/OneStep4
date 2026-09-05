package com.sangluo.onestep.utils;

import android.content.ComponentName;
import android.content.ServiceConnection;
import android.hardware.input.InputManager;
import android.os.Binder;
import android.os.IBinder;
import android.os.RemoteException;
import android.os.SystemClock;
import android.util.Log;

import com.sangluo.onestep.IMouseCallback;
import com.sangluo.onestep.IMouseMonitorService;
import com.sangluo.onestep.IRootInputBridgeService;
import com.sangluo.onestep.IShizukuCommonService;
import com.sangluo.onestep.MainActivity;
import com.sangluo.onestep.OneStepApplication;
import com.sangluo.onestep.frida.FridaBridge;
import com.sangluo.onestep.shizuku.MouseMonitorUserService;
import com.sangluo.onestep.shizuku.RootInputBridgeService;
import com.sangluo.onestep.shizuku.ShizukuCommonService;
import com.sangluo.onestep.system.root.PersistentRootShell;

import java.lang.reflect.InvocationTargetException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;

import rikka.shizuku.Shizuku;

public class ShizukuServiceUtil {

    private static final String TAG = "OneStep40";
    private static long mLastTriggerTime = SystemClock.uptimeMillis();
    private static final PersistentRootShell persistentRootShell = new PersistentRootShell();

    private static IMouseMonitorService mShizukuService;
    private static IShizukuCommonService mShizukuCommonService;
    private static IRootInputBridgeService mRootInputBridgeService;
    private static Shizuku.UserServiceArgs args = new Shizuku.UserServiceArgs(
            new ComponentName("com.sangluo.onestepc16", MouseMonitorUserService.class.getName())
    )
            .daemon(true)
            .processNameSuffix("mouse_monitor_single")
            .version(1);
    private static Shizuku.UserServiceArgs args2 = new Shizuku.UserServiceArgs(
            new ComponentName("com.sangluo.onestepc16", ShizukuCommonService.class.getName())
    )
            .daemon(true)
            .processNameSuffix("common_single")
            .version(1);
    private static Shizuku.UserServiceArgs args3 = new Shizuku.UserServiceArgs(
            new ComponentName("com.sangluo.onestepc16", RootInputBridgeService.class.getName())
    )
            .daemon(true)
            .processNameSuffix("root_input_bridge")
            .version(3);
    private static ServiceConnection suCoon = new ServiceConnection() {
        @Override
        public void onServiceConnected(ComponentName name, IBinder service) {
            mShizukuService = IMouseMonitorService.Stub.asInterface(service);
        }

        @Override
        public void onServiceDisconnected(ComponentName name) {
            // 处理断开连接
            Shizuku.unbindUserService(args, suCoon, true);
        }
    };
    private static ServiceConnection ccCoon = new ServiceConnection() {
        @Override
        public void onServiceConnected(ComponentName name, IBinder service) {
            mShizukuCommonService = IShizukuCommonService.Stub.asInterface(service);
        }
        @Override
        public void onServiceDisconnected(ComponentName name) {
            // 处理断开连接
            Shizuku.unbindUserService(args2, ccCoon, true);
        }
    };
    private static ServiceConnection ssCoon = new ServiceConnection() {
        @Override
        public void onServiceConnected(ComponentName name, IBinder service) {
            Log.d(TAG + "SHIZUKU_TEST",
                    "C: onServiceConnected " +
                            SystemClock.uptimeMillis());
            mRootInputBridgeService = IRootInputBridgeService.Stub.asInterface(service);
//            lock.notifyAll();
        }
        @Override
        public void onServiceDisconnected(ComponentName name) {
            // 处理断开连接
            Shizuku.unbindUserService(args3, ssCoon, true);
        }
    };

    private static final Object lock = new Object();
    public static void bindShizukuService() {

        // 绑定注册 Shizuku UserService
        Shizuku.bindUserService(args, suCoon);

        // 绑定注册 Shizuku UserService
        Shizuku.bindUserService(args2, ccCoon);

        // 绑定注册 Shizuku UserService
        int status = Shizuku.peekUserService(args3, ssCoon);
        Shizuku.bindUserService(args3, ssCoon);
        // 这个service比较耗时，第一次添加等待机制
        if (status != 0){
            /*synchronized (lock) {
                while (mRootInputBridgeService == null) {
                    try {
                        lock.wait();
                    } catch (InterruptedException e) {
                        throw new RuntimeException(e);
                    }
                }
            }*/
        }
        MainActivity.startEscListener();

        System.out.println("1");
    }

    public static void bindShizukuMouseMonitorUserServiceByDisplayId(int displayId, int width, int height){
        Log.d(TAG, "Shizuku 注册mouse监听器： " + displayId);
        // 绑定 UserService 参数
        Shizuku.UserServiceArgs args = new Shizuku.UserServiceArgs(
                new ComponentName("com.sangluo.onestepc16", MouseMonitorUserService.class.getName()))
                .daemon(true)
                .processNameSuffix("mouse_monitor")
                .version(1);

        // 绑定服务
        Shizuku.bindUserService(args, new ServiceConnection() {
            @Override
            public void onServiceConnected(ComponentName name, IBinder service) {
                IMouseMonitorService mShizukuServiceInner = IMouseMonitorService.Stub.asInterface(service);
                try {
                    mShizukuServiceInner.startMonitor(displayId, width, height, new IMouseCallback.Stub() {

                        @Override
                        public void onMouseEdge(int displayId, float x, float y) {
                            // 这里运行在你的主 App 进程中，处理边缘触发逻辑
//                            Log.d(TAG, "收到 Shizuku 回调：鼠标触碰边缘 " + displayId + " x: " + x + " y: " + y);
                            long currentTime = SystemClock.uptimeMillis();
                            // 如果距离上一次触发未满 300ms，直接拦截丢弃
                            if (currentTime - mLastTriggerTime < 300) {
                                return;
                            }
                            mLastTriggerTime = currentTime;

                            Integer hdmiDisplayId = ExtDisplayUtil.getHdmiDisplayId(OneStepApplication.getContext());
                            if (hdmiDisplayId == null){
                                hdmiDisplayId = 0;
                            }
//                            FridaBridge.startFixedPollingMonitorSyncBridge(hdmiDisplayId);
                            DeviceUtil.bindDeviceToVirtualDisplay(SysServiceObjUtil.getInputManager(), 0, hdmiDisplayId);
//                            float centerX = width / 2.0f;
//                            float centerY = height / 2.0f;
//
//                            float offsetX = x - centerX;
//                            float offsetY = y - centerY;
//                            ShizukuServiceUtil.moveCursor(hdmiDisplayId, offsetX * 1/6, offsetY * 1/5);
//                            DeviceUtil.moveCursorSmooth(displayId, x, y, centerX, centerY);
                        }
                    });
                } catch (RemoteException e) {
                    e.printStackTrace();
                } catch (Exception e) {
                    throw new RuntimeException(e);
                }
            }

            @Override
            public void onServiceDisconnected(ComponentName name) {
                // 处理断开连接
            }
        });
    }

    public static void moveCursor(int displayId, float x, float y) {
        if (mShizukuService != null) {
            try {
                // 跨进程发送指令给 Shizuku 执行
//                mShizukuService.wakeUpMouse();
                mShizukuService.setMousePosition(displayId, x, y);
            } catch (RemoteException e) {
                Log.e(TAG, "setMousePosition e2: "+e.getMessage());
                e.printStackTrace();
            }
        } else {
            Log.e(TAG, "Shizuku 服务未连接！");
        }
    }

    public static void wakeUpMouse() {
        if (mShizukuService != null) {
            try {
                // 跨进程发送指令给 Shizuku 执行
                mShizukuService.wakeUpMouse();
            } catch (RemoteException e) {
                Log.e(TAG, "wakeUpMouse e2: "+e.getMessage());
                e.printStackTrace();
            }
        } else {
            Log.e(TAG, "Shizuku 服务未连接！");
        }
    }

    public static IBinder getService(String serviceName){
        try {
            return mShizukuCommonService.getService(serviceName);
        } catch (RemoteException e) {
            Log.e(TAG, "getService ex: ", e);
            throw new RuntimeException(e);
        }
    }

    public static IRootInputBridgeService getRootInputBridgeService() {
        return mRootInputBridgeService;
    }
}
