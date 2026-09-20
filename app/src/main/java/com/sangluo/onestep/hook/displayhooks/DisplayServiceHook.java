package com.sangluo.onestep.hook.displayhooks;

import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
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

    public static void hook(XC_LoadPackage.LoadPackageParam lpparam) {
        hookLogicalDisplayMapper(lpparam);
        hookDisplayDeviceRepository(lpparam);
        hookInputManagerService(lpparam);
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

                        Log.d(TAG, "HDMI已连接! DISPLAY_CONTROL=" + SharedConfig.ENABLE_DISPLAY_CONTROL);
                        HDMI_CONNECTED = true;
                    }
                    
                    @Override
                    protected void onAfter(MethodHookParam param) {
                        Log.d(TAG, "=== handleDisplayDeviceAdded after 被调用 ===");
                        
                        if (!SharedConfig.ENABLED) {
                            return;
                        }

                        Context context = SysServiceObjUtil.getCurrentContext();
                        PackageManager packageManager = context.getPackageManager();
                        Intent intent = packageManager.getLaunchIntentForPackage(SharedConfig.START_SOFT_WARE_PACKAGE);
                        context.startActivity(intent);

                        // 禁用HDMI音频
                        if (SharedConfig.ENABLE_DISABLE_AUDIO) {
                            Log.d(TAG, "禁用HDMI音频, 延时600ms执行");
                            new Thread(() -> {
                                try {
                                    Thread.sleep(600);
                                    manageAudioService(lpparam, 0);
                                } catch (InterruptedException e) {
                                    Log.e(TAG, "[!] 延时执行被中断: " + e);
                                }
                            }).start();
                        }

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
                        Log.d(TAG, "=== handleDisplayDeviceRemoved 被调用 ===");
                        HDMI_CONNECTED = false;
                        powerControl(lpparam, 2);
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
                new XC_MethodHook() {
                    @Override
                    protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
                        if (!HDMI_CONNECTED) {
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

}
