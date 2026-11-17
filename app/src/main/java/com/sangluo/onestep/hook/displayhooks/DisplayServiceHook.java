package com.sangluo.onestep.hook.displayhooks;

import static com.sangluo.onestep.utils.ExtDisplayUtil.TYPE_EXTERNAL;

import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.IBinder;
import android.util.Log;
import android.view.KeyEvent;

import com.sangluo.onestep.hook.config.SharedConfig;
import com.sangluo.onestep.utils.SysServiceObjUtil;

import java.io.IOException;
import java.lang.reflect.Method;

import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedHelpers;
import de.robv.android.xposed.callbacks.XC_LoadPackage;


public class DisplayServiceHook {

    private static final String TAG = "OneStep4 LSP DisplayServiceHook";
    private static boolean HDMI_CONNECTED = false;

    private static volatile boolean installed;

    private static HandlerThread sHdmiThread;
    private static Handler sHdmiHandler;

    private static void initHdmiHandler() {
        if (sHdmiThread != null) {
            return;
        }

        synchronized (DisplayServiceHook.class) {
            if (sHdmiThread == null) {
                sHdmiThread = new HandlerThread("OneStep-HDMI");
                sHdmiThread.start();
                sHdmiHandler = new Handler(sHdmiThread.getLooper());
            }
        }
    }

    public static Handler getsHdmiHandler(){
        return sHdmiHandler;
    }

    public static void hook(XC_LoadPackage.LoadPackageParam lpparam) {
        if (installed) {
            return;
        }
        initHdmiHandler();

        hookLogicalDisplayMapper(lpparam);
        hookDisplayDeviceRepository(lpparam);
        hookInputManagerService(lpparam);
        hookInputConfigChange(lpparam);
        hookAllowToLaunchOnDisplay(lpparam);
        installed = true;
    }


    private static void hookAllowToLaunchOnDisplay(XC_LoadPackage.LoadPackageParam lpparam){
        XposedHelpers.findAndHookMethod("com.android.server.wm.ActivityTaskSupervisor", lpparam.classLoader, "isCallerAllowedToLaunchOnDisplay", int.class, int.class, int.class, android.content.pm.ActivityInfo.class, new XC_MethodHook() {
            @Override
            protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
                super.beforeHookedMethod(param);
                param.setResult(true);
            }
            @Override
            protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                super.afterHookedMethod(param);
            }
        });
    }

    private static boolean isPhysicalExternalDisplay(Object displayDevice) {
        try {
            Object info = XposedHelpers.callMethod(
                    displayDevice,
                    "getDisplayDeviceInfoLocked"
            );

            if (info == null) {
                return false;
            }

            int type = (int)XposedHelpers.getObjectField(info, "type");
            Log.d(TAG, "display device type: "+type);

            return type == TYPE_EXTERNAL;

        } catch (Throwable e) {
            Log.e(TAG, "isPhysicalExternalDisplay failed", e);
            return false;
        }
    }

    /**
     * 处理显示设备添加/移除
     */
    private static void hookDisplayDeviceRepository(XC_LoadPackage.LoadPackageParam lpparam) {
        try {
            String className = "com.android.server.display.DisplayDeviceRepository";

            /**
             * 处理显示设备添加
             */
            XposedHelpers.findAndHookMethod(
                className,
                lpparam.classLoader,
                "handleDisplayDeviceAdded",
                "com.android.server.display.DisplayDevice",
                new BaseMethodHook() {

                    @Override
                    protected void onBefore(MethodHookParam param) throws IOException {
                        Log.d(TAG, "=== handleDisplayDeviceAdded before 被调用 ===");
                        Log.d(TAG, "配置状态: ENABLED=" + SharedConfig.ENABLED);
                        
                        if (!SharedConfig.ENABLED) {
                            Log.d(TAG, "模块未启用");
                            return;
                        }

                        if (!isPhysicalExternalDisplay(param.args[0])) {
                            Log.d(TAG, "忽略：不是 HDMI/物理外接屏");
                            return;
                        }

                        Log.d(TAG, "HDMI已连接! DISPLAY_CONTROL=" + SharedConfig.ENABLE_DISPLAY_CONTROL);
                        HDMI_CONNECTED = true;
                    }
                    
                    @Override
                    protected void onAfter(MethodHookParam param) {
                        Log.d(TAG, "=== handleDisplayDeviceAdded after 被调用 ===");
                        
                        if (!SharedConfig.ENABLED) {
                            return;
                        }

                        if (!isPhysicalExternalDisplay(param.args[0])) {
                            Log.d(TAG, "忽略：不是 HDMI/物理外接屏");
                            return;
                        }

                        sHdmiHandler.post(() -> {
                            try {
                                Log.d(TAG, "开始处理 HDMI 后续操作");

                                Context context = SysServiceObjUtil.getCurrentContext();
                                PackageManager packageManager = context.getPackageManager();
                                Intent intent = packageManager.getLaunchIntentForPackage(SharedConfig.START_SOFT_WARE_PACKAGE);
                                context.startActivity(intent);
                                SharedConfig.loadConfig();

                                // 手机息屏
                                if (SharedConfig.ENABLE_DISPLAY_CONTROL) {
                                    powerControl(lpparam, 0);
                                }

                                // 音频操作再单独延迟
                                if (SharedConfig.ENABLE_DISABLE_AUDIO) {
                                    sHdmiHandler.postDelayed(() -> {
                                        try {
                                            Log.d(TAG, "执行 HDMI 音频处理");
                                            manageAudioService(lpparam, 0);
                                        } catch (Throwable e) {
                                            Log.e(TAG, "manageAudioService failed", e);
                                        }
                                    }, 1500);
                                }

                            } catch (Throwable e) {
                                Log.e(TAG, "HDMI 后处理失败", e);
                            }
                        });



                    }
                }
            );

            /**
             * 处理显示设备移除
             */
            XposedHelpers.findAndHookMethod(
                className,
                lpparam.classLoader,
                "handleDisplayDeviceRemoved",
                "com.android.server.display.DisplayDevice",
                new BaseMethodHook() {
                    @Override
                    protected void onAfter(MethodHookParam param) {
                        if (!SharedConfig.ENABLED) {
                            return;
                        }
                        if (!isPhysicalExternalDisplay(param.args[0])) {
                            Log.d(TAG, "忽略：不是 HDMI/物理外接屏");
                            return;
                        }

                        sHdmiHandler.post(() -> {
                            try{
                                forceStopPackage(SharedConfig.START_SOFT_WARE_PACKAGE, 0);
                                Log.d(TAG, "=== handleDisplayDeviceRemoved 被调用 ===");
                                HDMI_CONNECTED = false;
                                powerControl(lpparam, 2);
                            } catch (Exception e) {
                                Log.e(TAG, "HDMI handleDisplayDeviceRemoved 后处理失败", e);
                            }
                        });
                    }
                }
            );

            Log.d(TAG, "DisplayDeviceRepository hooks installed");
            
        } catch (Throwable e) {
            Log.e(TAG, "Failed to hook DisplayDeviceRepository", e);
        }
    }

    /**
     * 禁用投屏确认对话框
     */
    private static void hookLogicalDisplayMapper(XC_LoadPackage.LoadPackageParam lpparam) {
        try {
            String className = "com.android.server.display.LogicalDisplayMapper";

            XposedHelpers.findAndHookMethod(
                className,
                lpparam.classLoader,
                "setEnabledLocked",
                "com.android.server.display.LogicalDisplay",
                boolean.class,
                new BaseMethodHook() {
                    @Override
                    protected void onBefore(MethodHookParam param) {
                        Log.d(TAG, "=== setEnabledLocked 被调用 ===");
                        Log.d(TAG, "配置状态: ENABLED=" + SharedConfig.ENABLED);
                        
                        if (!SharedConfig.ENABLED) {
                            return;
                        }
                        
                        try {
                            Object display = param.args[0];
                            Boolean isEnabledLocked = (Boolean) XposedHelpers.callMethod(display, "isEnabledLocked");
                            Log.d(TAG, "  isEnabledLocked: " + isEnabledLocked);
                            
                            if (isEnabledLocked == false) {
                                param.args[1] = true;
                                Log.d(TAG, "已启用投屏确认对话框！");
                            }else{
                                param.args[1] = isEnabledLocked;
                                Log.d(TAG, "已禁用投屏确认对话框！");
                            }
                        } catch (Throwable e) {
                            Log.e(TAG, "禁用投屏确认失败", e);
                        }
                    }
                }
            );
            
            Log.d(TAG, "LogicalDisplayMapper hooks installed");
            
        } catch (Throwable e) {
            Log.e(TAG, "Failed to hook LogicalDisplayMapper", e);
        }
    }

    private static long lastTimeStamp = System.currentTimeMillis();
    private static int powerTag = 0;
    
    private static void hookInputManagerService(XC_LoadPackage.LoadPackageParam lpparam) {
        try {
            String className = "com.android.server.input.InputManagerService";
            Log.d(TAG, "[+] 开始Hook InputManagerService");

            XposedHelpers.findAndHookMethod(
                className,
                lpparam.classLoader,
                "interceptKeyBeforeDispatching",
                IBinder.class,
                KeyEvent.class,
                int.class,
                new BaseMethodHook() {
                    @Override
                    protected void onBefore(MethodHookParam param) throws Throwable {
                        if (!HDMI_CONNECTED && !SharedConfig.IS_VIRTUAL_DISPLAY_RUN) {
                            return;
                        }

                        KeyEvent keyEvent = (KeyEvent) param.args[1];
                        int keyCode = keyEvent.getKeyCode();

                        Log.v(TAG, "interceptKeyBeforeDispatching - keyCode=" + keyCode + ", HDMI_CONNECTED=" + HDMI_CONNECTED);

                        if (keyCode == 133 && lastTimeStamp + 1000 < System.currentTimeMillis()) {
                            Log.d(TAG, "[*] 检测到POWER_KEY (133), 调用powerControl");
                            Log.d(TAG, "[*] ENABLE_DISPLAY_CONTROL=" + SharedConfig.ENABLE_DISPLAY_CONTROL);
                            powerControl(lpparam, powerTag);
                            powerTag = powerTag == 0 ? 2 : 0;
                            lastTimeStamp = System.currentTimeMillis();
                            Log.d(TAG, "[*] powerControl执行完成");
                        }

                        if (SharedConfig.ENABLE_DISABLE_KEY) {
                            Log.d(TAG, "[*] 按键拦截启用, 拦截keyCode=" + keyCode);
                            param.setResult(0);
                        }
                    }
                }
            );

            Log.i(TAG, "[+] InputManagerService Hook注册成功");

        } catch (Throwable e) {
            Log.e(TAG, "Failed to hook InputManagerService", e);
        }
    }

    private static void manageAudioService(XC_LoadPackage.LoadPackageParam lpparam, int state) {
        Log.d(TAG, "[+] 开始管理音频服务...");
        
        try {
            Class<?> serviceManagerClass = XposedHelpers.findClass("android.os.ServiceManager", lpparam.classLoader);
            IBinder audioServiceBinder = (IBinder) XposedHelpers.callStaticMethod(serviceManagerClass, "getService", "audio");
            
            if (audioServiceBinder == null) {
                Log.e(TAG, "[!] 无法获取音频服务");
                return;
            }
            Log.d(TAG, "[+] 获取音频服务Binder成功");
            
            Class<?> IAudioServiceStubClass = XposedHelpers.findClass("android.media.IAudioService$Stub", lpparam.classLoader);
            Object audioService = XposedHelpers.callStaticMethod(IAudioServiceStubClass, "asInterface", audioServiceBinder);
            
            if (audioService == null) {
                Log.e(TAG, "[!] 无法获取IAudioService接口");
                return;
            }
            Log.d(TAG, "[+] 成功获取音频服务接口");
            
            Class<?> audioAttributesBuilderClass = XposedHelpers.findClass("android.media.AudioAttributes$Builder", lpparam.classLoader);
            Object attributesBuilder = XposedHelpers.callMethod(audioAttributesBuilderClass, "newInstance");
            Object attributes = XposedHelpers.callMethod(attributesBuilder, "build");
            
            Object devices = XposedHelpers.callMethod(audioService, "getDevicesForAttributes", attributes);
            
            if (devices == null) {
                Log.e(TAG, "[!] 无法获取音频设备列表");
                return;
            }
            
            int deviceCount = (Integer) XposedHelpers.callMethod(devices, "size");
            Log.d(TAG, "[+] 获取到 " + deviceCount + " 个音频设备");
            
            Class<?> audioDeviceAttributesClass = XposedHelpers.findClass("android.media.AudioDeviceAttributes", lpparam.classLoader);
            
            for (int i = 0; i < deviceCount; i++) {
                Object device = XposedHelpers.callMethod(devices, "get", i);
                int deviceType = (Integer) XposedHelpers.callMethod(device, "getType");
                
                Log.d(TAG, "[+] 设备 " + i + ": 类型=" + deviceType);
                
                if (deviceType == 9) {
                    Log.d(TAG, "[+] 找到HDMI音频设备");
                    XposedHelpers.callMethod(audioService, "setWiredDeviceConnectionState", device, state, "com.android.shell");
                    Log.i(TAG, "[+] HDMI音频切换成功, state=" + state);
                }
            }
            
        } catch (Throwable e) {
            Log.e(TAG, "[!] manageAudioService执行失败: " + e);
        }
    }

    private static void powerControl(XC_LoadPackage.LoadPackageParam lpparam, int mode) {
        Log.d(TAG, "[*] powerControl called with mode=" + mode);
        try {
            IBinder token = null;
            Class<?> surfaceControlClass = XposedHelpers.findClass("android.view.SurfaceControl", lpparam.classLoader);
            Log.d(TAG, "[*] 获取SurfaceControl类成功");

            String[] displayControlClasses = {
                "android.view.DisplayControl",
                "com.android.server.display.DisplayControl"
            };
            for (String className : displayControlClasses) {
                try {
                    Log.d(TAG, "[*] 尝试获取DisplayControl: " + className);
                    Class<?> displayControlClass = XposedHelpers.findClass(className, lpparam.classLoader);
                    long[] ids = (long[]) XposedHelpers.callStaticMethod(displayControlClass, "getPhysicalDisplayIds");
                    if (ids != null && ids.length > 0) {
                        Log.d(TAG, "[*] 获取到DisplayIds, 长度=" + ids.length);
                        token = (IBinder) XposedHelpers.callStaticMethod(displayControlClass, "getPhysicalDisplayToken", ids[0]);
                        if (token != null) {
                            Log.d(TAG, "[*] DisplayControl获取token成功");
                            break;
                        }
                    }
                } catch (Throwable e) {
                    Log.d(TAG, "[*] DisplayControl " + className + " 失败: " + e);
                }
            }

            if (token == null) {
                Log.e(TAG, "[!] 所有方法都无法获取display token");
                return;
            }

            Log.d(TAG, "[*] 成功获取display token, 调用setDisplayPowerMode(mode=" + mode + ")");
            Method setDisplayPowerMode = surfaceControlClass.getDeclaredMethod("setDisplayPowerMode", IBinder.class, int.class);
            setDisplayPowerMode.invoke(null, token, mode);
            Log.i(TAG, "[+] setDisplayPowerMode执行成功, mode=" + mode);

        } catch (Throwable e) {
            Log.e(TAG, "[!] powerControl执行失败: " + e);
        }
    }


    private static void forceStopPackage(String packageName, int userId) {
        try {
            Class<?> amClass = Class.forName("android.app.ActivityManager");

            java.lang.reflect.Method getService =
                    amClass.getDeclaredMethod("getService");
            getService.setAccessible(true);

            Object service = getService.invoke(null);

            java.lang.reflect.Method method = null;

            for (java.lang.reflect.Method m : service.getClass().getMethods()) {
                if ("forceStopPackage".equals(m.getName())
                        && m.getParameterTypes().length == 2
                        && m.getParameterTypes()[0] == String.class
                        && m.getParameterTypes()[1] == int.class) {
                    method = m;
                    break;
                }
            }

            if (method == null) {
                throw new NoSuchMethodException(
                        "forceStopPackage(String,int) not found, service="
                                + service.getClass().getName()
                );
            }

            method.setAccessible(true);
            method.invoke(service, packageName, userId);

            Log.d(TAG, "force stop success: " + packageName);

        } catch (Throwable e) {
            Log.e(TAG, "force stop failed: " + packageName, e);
        }
    }

    /**
     * 禁用relounch
     * 注：c16 键盘连接断开会导致像bilibili等app重新加载，没找出规律，故直接返回false
     * @param lpparam
     */
    private static void hookInputConfigChange(XC_LoadPackage.LoadPackageParam lpparam){
        try {
            XposedHelpers.findAndHookMethod(
                    "com.android.server.wm.ActivityRecordExtImpl",
                    lpparam.classLoader,
                    "hookShouldRelaunchLocked",
                    int.class,
                    int.class,
                    android.content.res.Configuration.class,
                    new BaseMethodHook() {

                        @Override
                        protected void onBefore(MethodHookParam param) throws Throwable {
                        }

                        @Override
                        protected void onAfter(MethodHookParam param) throws Throwable {
                            if (!HDMI_CONNECTED && !SharedConfig.IS_VIRTUAL_DISPLAY_RUN) {
                                return;
                            }

                            if (SharedConfig.RE_LAUNCH_FLAG) {
                                // 对应 Frida:
                                final Boolean result = (Boolean) param.getResult();
                                // 对应:
                                android.content.res.Configuration changesConfig =
                                        (android.content.res.Configuration) param.args[2];
                                boolean flag =
                                        Boolean.TRUE.equals(result)
                                                && changesConfig != null;
                                if (flag) {
                                    Log.i(TAG, "禁用配置更新app重新布局执行成功！");
                                    param.setResult(false);
                                }
                            }
                        }
                    }
            );

            Log.i(TAG, "[+] hookInputConfigChange Hook注册成功");

        } catch (Throwable e) {
            Log.e(TAG, "Failed to hook hookInputConfigChange", e);
        }
    }
}
