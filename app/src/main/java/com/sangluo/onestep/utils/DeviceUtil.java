package com.sangluo.onestep.utils;

import static android.view.InputDevice.KEYBOARD_TYPE_ALPHABETIC;
import static android.view.InputDevice.KEYBOARD_TYPE_NON_ALPHABETIC;

import android.content.Context;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.hardware.input.IInputManager;
import android.hardware.input.InputManager;
import android.os.Binder;
import android.os.IBinder;
import android.os.RemoteException;
import android.util.Log;
import android.view.Display;
import android.view.InputDevice;
import android.view.KeyEvent;

import com.sangluo.onestep.IRootInputBridgeService;

import java.io.BufferedReader;
import java.io.FileReader;
import java.io.InputStreamReader;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import rikka.shizuku.ShizukuBinderWrapper;
import rikka.shizuku.SystemServiceHelper;

public class DeviceUtil {

    private static final String TAG = "OneStep40";
    /** WindowManager display IME policy that hosts the IME on this display. */
    private static final int DISPLAY_IME_POLICY_LOCAL = 0;

    public static void bindDeviceToVirtualDisplay(IInputManager inputManager, int type , int displayId){
        List<InputDevice> keyboradDevices = getKeyboradDevices(inputManager);
        List<InputDevice> mouseDevices = getMouseDevices(inputManager);
        ArrayList<InputDevice> inputDevices = new ArrayList<>();
        switch (type){
            case 0: // 所有设备
                inputDevices.addAll(keyboradDevices);
                inputDevices.addAll(mouseDevices);
                break;
            case 1: // 键盘
                inputDevices.addAll(keyboradDevices);
                break;
            case 2: // 鼠标
                inputDevices.addAll(mouseDevices);
                break;
            default:
        }
        for (InputDevice inputDevice : inputDevices) {
            try {
                int associatedDisplayId = getAssociatedDisplayId(inputDevice);
                if (associatedDisplayId == displayId){
                    Log.d(TAG, "设备：" + inputDevice.getName() + "已经绑定到displayid: " + displayId);
                    continue;
                }
                inputManager.removeUniqueIdAssociationByDescriptor(inputDevice.getDescriptor());
                Display displayObj = ExtDisplayUtil.getDisplayObj(SysServiceObjUtil.getCurrentContext(), displayId);
                inputManager.addUniqueIdAssociationByDescriptor(inputDevice.getDescriptor(), ExtDisplayUtil.getDisplayUniqueId(displayObj));
                Log.d(TAG, "绑定设备：" + inputDevice.getName() + "到displayid: " + displayId + "成功");
            } catch (RemoteException e) {
                Log.d(TAG, "绑定设备：" + inputDevice + "到displayid: " + displayId + "失败");
                throw new RuntimeException(e);
            }
        }
    }

    /**
     * The extended display defaults to the fallback IME policy, which draws the keyboard on
     * the phone (display 0). Switch it to local so text input appears on this display.
     */
    public static void ensureLocalImePolicy(int displayId) {
        new Thread(() -> {
            try {
                IRootInputBridgeService service =
                        ShizukuServiceUtil.getRootInputBridgeService();
                if (service == null) {
                    ShizukuServiceUtil.waitServiceInitOver();
                    service = ShizukuServiceUtil.getRootInputBridgeService();
                }
                if (service == null) {
                    Log.w(TAG, "IME policy bridge unavailable for display " + displayId);
                    return;
                }
                String response = service.handleCommand(bridgeToken(),
                        "imePolicy " + displayId + " " + DISPLAY_IME_POLICY_LOCAL);
                Log.i(TAG, "Display IME policy display=" + displayId
                        + " response=" + response);
            } catch (RemoteException | RuntimeException error) {
                Log.w(TAG, "Cannot apply local IME policy for display " + displayId, error);
            }
        }, "onestep-screenshot-ime-policy").start();
    }


    private static String bridgeToken() {
        try {
            Context anyContext = AweSomeUtil.getAnyContext();
            PackageInfo info = anyContext.getPackageManager().getPackageInfo(anyContext.getPackageName(), 0);
            return info.getLongVersionCode() + ":" + info.lastUpdateTime + ":"
                    + anyContext.getApplicationInfo().sourceDir;
        } catch (PackageManager.NameNotFoundException | RuntimeException error) {
            return "unknown";
        }
    }

    /**
     * 获取当前设备绑定的displayid
     * @param inputDevice
     * @return
     */
    private static int getAssociatedDisplayId(InputDevice inputDevice) {
        try {
            Method method = InputDevice.class.getDeclaredMethod(
                    "getAssociatedDisplayId"
            );
            method.setAccessible(true);
            return (int) method.invoke(inputDevice);
        } catch (Exception e) {
            e.printStackTrace();
            return Display.DEFAULT_DISPLAY;
        }
    }

    public static List<InputDevice> getKeyboradDevices(IInputManager inputManager){
        try{
            List<InputDevice> list = new ArrayList<>();
            try {
                int[] inputDeviceIds = inputManager.getInputDeviceIds();
//                Log.d(TAG, "----------设备总数：" + inputDeviceIds.length);
                for (int inputDeviceId : inputDeviceIds) {
                    InputDevice inputDevice = inputManager.getInputDevice(inputDeviceId);
//                    Log.d(TAG, "----------设备：" + inputDevice.toString());
                    if (inputDevice.isExternal() && isKeyboardDevice(inputDevice)){
//                        Log.d(TAG, "找到键盘：" + inputDevice.toString());
                        list.add(inputDevice);
                    }
                }
            } catch (RemoteException e) {
                throw new RuntimeException(e);
            }
            return list;
        }catch (Exception e){
            Log.e(TAG, "getKeyboradDevices失败：", e);
            throw new RuntimeException(e);
        }
    }

    public static List<InputDevice> getMouseDevices(IInputManager inputManager){
        List<InputDevice> list = new ArrayList<>();
        try {
            int[] inputDeviceIds = inputManager.getInputDeviceIds();
            for (int inputDeviceId : inputDeviceIds) {
                InputDevice inputDevice = inputManager.getInputDevice(inputDeviceId);
                if (inputDevice.isExternal() && isMouseDevice(inputDevice)){
//                    Log.d(TAG, "找到鼠标：" + inputDevice.toString());
                    list.add(inputDevice);
                }
            }
        } catch (RemoteException e) {
            Log.e(TAG, "getMouseDevices失败：", e);
            throw new RuntimeException(e);
        }
        return list;
    }

    private static boolean isKeyboardDevice(InputDevice device) {
        if (device == null) {
            return false;
        }

        int sources = device.getSources();

        boolean hasKeyboard =
                (sources & InputDevice.SOURCE_KEYBOARD) != 0;

        String name = device.getName();

        return name != null && name.toLowerCase().contains("keyboard") && hasKeyboard;
    }

    private static boolean isMouseDevice(InputDevice device) {
        if (device == null) {
            return false;
        }

        int sources = device.getSources();

        boolean hasMouse =
                (sources & InputDevice.SOURCE_MOUSE) != 0;

        String name = device.getName();

        return name != null && name.toLowerCase().contains("mouse") && hasMouse;
    }

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
