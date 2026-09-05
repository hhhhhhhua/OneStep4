package com.sangluo.onestep.utils;

import android.hardware.input.InputManager;
import android.os.Binder;
import android.util.Log;
import android.view.InputDevice;

import java.io.BufferedReader;
import java.io.FileReader;
import java.io.InputStreamReader;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class DeviceUtil {

    private static final String TAG = "OneStep40";

    public static void wakePointerHardware() {
        long token = Binder.clearCallingIdentity();
        try {
            // 遍历所有 event 节点，发送 1 像素微移脉冲 (EV_REL REL_X 1 + EV_SYN 0 0)
            String cmd = "for dev in /dev/input/event*; do sendevent $dev 2 0 1 2>/dev/null && sendevent $dev 0 0 0 2>/dev/null; done";
            String cmd2 = "for dev in /dev/input/event*; do sendevent $dev 2 0 -1 2>/dev/null && sendevent $dev 0 0 0 2>/dev/null; done";
            Process process = Runtime.getRuntime().exec(new String[]{"sh", "-c", cmd});
            process.waitFor();
            Runtime.getRuntime().exec(new String[]{"sh", "-c", cmd2});
            process.waitFor();
            Log.d(TAG, "已完成所有输入节点的广播唤醒");
        } catch (Exception e) {
            Log.e(TAG, "盲发唤醒失败: ", e);
        } finally {
            Binder.restoreCallingIdentity(token);
        }
    }

    /**
     * 通过 Shizuku 向 Linux 硬件节点直接注入相对位移
     * @param dx X轴相对位移 (正数向右，负数向左)
     * @param dy Y轴相对位移 (正数向下，负数向上)
     */
    public static void injectRealHardwareMove(int dx, int dy) {

        long token = Binder.clearCallingIdentity();
        try {
            // 拼接 sendevent 指令：遍历所有 event 节点，写入 EV_REL (X/Y) 和 EV_SYN
            // 这样无需精准匹配哪一个是鼠标，全部节点广播注入，只有鼠标节点会响应位移
            String cmd = String.format(
                    "for dev in /dev/input/event*; do " +
                            "sendevent $dev 2 0 %d 2>/dev/null; " +
                            "sendevent $dev 2 1 %d 2>/dev/null; " +
                            "sendevent $dev 0 0 0 2>/dev/null; " +
                            "done",
                    dx, dy
            );

            Process process = Runtime.getRuntime().exec(new String[]{"sh", "-c", cmd});
            process.waitFor();
            Log.d(TAG, "成功注入硬件位移: dx=" + dx + ", dy=" + dy);
        } catch (Exception e) {
            Log.e(TAG, "硬件位移注入失败", e);
        } finally {
            Binder.restoreCallingIdentity(token);
        }
    }

    public static void setInputDeviceEnabled(int deviceId, boolean disabled) {
        long token = Binder.clearCallingIdentity();
        try {
            Object inputManager = SysServiceObjUtil.getInputManager2();
            if (inputManager == null) return;

            // 调用 InputManagerService.disableInputDevice(deviceId) 或 enableInputDevice
            String methodName = disabled ? "disableInputDevice" : "enableInputDevice";
            Method method = inputManager.getClass().getMethod(methodName, int.class);
            method.invoke(inputManager, deviceId);

            Log.d(TAG, "物理设备 " + deviceId + " 状态切换: disabled=" + disabled);
        } catch (Throwable e) {
            Log.e(TAG, "切换设备状态失败", e);
        } finally {
            Binder.restoreCallingIdentity(token);
        }
    }

    public static List<Integer> getMouseDeviceIdList() {
        Object im = null; // 或 Context.INPUT_SERVICE
        try {
            im = SysServiceObjUtil.getInputManager2();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
        List<Integer> list = new ArrayList<>();
        if (im == null) return list;
        Method injectMethod = null;
        int[] deviceIds = null;
        try {
            injectMethod =
                    im.getClass().getMethod(
                            "getInputDeviceIds"
                    );
            deviceIds = (int[]) injectMethod.invoke(im);
        } catch (NoSuchMethodException | IllegalAccessException | InvocationTargetException e) {
            Log.e(TAG, "getInputDeviceIds e: "+e.getMessage());
            throw new RuntimeException(e);
        }
        Method injectMethod2 = null;
        for (int id : deviceIds) {
            InputDevice device = null;
            try {
                injectMethod2 =
                        im.getClass().getMethod(
                                "getInputDevice",
                                int.class
                        );
                device = (InputDevice) injectMethod2.invoke(im, id);
            } catch (NoSuchMethodException | IllegalAccessException | InvocationTargetException e) {
                Log.e(TAG, "getInputDevice e: "+e.getMessage());
                throw new RuntimeException(e);
            }
            if (device != null) {
                // 条件 1: 判定设备类别是否包含 鼠标/轨迹球
                if ((device.getSources() & InputDevice.SOURCE_MOUSE) == InputDevice.SOURCE_MOUSE) {
                    Log.d(TAG, "MouseFix 找到鼠标设备: id=" + id + ", name=" + device.getName());
                    list.add(id);
                }
            }
        }
        if (list.size() < 1){
            Log.e(TAG, "MouseFix 不能找到鼠标设备");
        }
        return list;
    }

}
