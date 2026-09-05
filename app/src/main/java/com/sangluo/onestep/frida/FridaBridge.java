package com.sangluo.onestep.frida;

import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;

import com.sangluo.onestep.MainActivity;
import com.sangluo.onestep.utils.DeviceUtil;
import com.sangluo.onestep.utils.ShizukuServiceUtil;

import rikka.shizuku.Shizuku;

/**
 * 供frida hook使用，提供java间接调用frida的能力
 */
public class FridaBridge {

    /**
     * 外接设备绑定到指定的display上
     * frida函数：startFixedPollingMonitorSync
     * @param displayId
     */
    public static void startFixedPollingMonitorSyncBridge(int displayId){
        ShizukuServiceUtil.wakeUpMouse();
    }

    /**
     * 获取 hdmiDisplayId
     * frida函数：getHdmiDisplayId
     */
    public static Integer getHdmiDisplayIdBridge(){
        return null;
    }


    /**
     * 获取 service
     * frida函数：getServiceByNameBridge
     */
    public static Object getServiceByNameBridge(String serviceName){
        return null;
    }

}



