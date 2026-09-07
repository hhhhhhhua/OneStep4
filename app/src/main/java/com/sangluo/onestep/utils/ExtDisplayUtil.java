package com.sangluo.onestep.utils;

import android.annotation.SuppressLint;
import android.content.Context;
import android.hardware.display.DisplayManager;
import android.os.Build;
import android.os.IBinder;
import android.util.Log;
import android.view.Display;

import com.sangluo.onestep.OneStepApplication;

import java.lang.reflect.Method;

import rikka.shizuku.SystemServiceHelper;

public class ExtDisplayUtil {

    private static final String TAG = "OneStep40";

    public static final int TYPE_UNKNOWN = 0;

    /**
     * Display type: Physical display connected through an internal port.
     * @hide
     */
    public static final int TYPE_INTERNAL = 1;

    /**
     * Display type: Physical display connected through an external port.
     * @hide
     */
    public static final int TYPE_EXTERNAL = 2;

    /**
     * Display type: WiFi display.
     * @hide
     */
    public static final int TYPE_WIFI = 3;

    /**
     * Display type: Overlay display.
     * @hide
     */
    public static final int TYPE_OVERLAY = 4;

    /**
     * Display type: Virtual display.
     * @hide
     */
    public static final int TYPE_VIRTUAL = 5;

    public static Display getDisplayObj(Context context, int displayId){
        DisplayManager displayManager = (DisplayManager)context.getSystemService(Context.DISPLAY_SERVICE);
        return displayManager.getDisplay(displayId);
    }

    public static String getDisplayUniqueId(Display display) {
        try {
            @SuppressLint("BlockedPrivateApi") Method method = Display.class.getDeclaredMethod("getUniqueId");
            method.setAccessible(true);
            return (String) method.invoke(display);
        } catch (Exception e) {
            Log.e(TAG, "getUniqueId failed", e);
            return null;
        }
    }

    /**
     * 获取外接屏幕 DisplayId
     * @return
     */
    public static Integer getHdmiDisplayId(Context context) {
        try{
            DisplayManager displayManager = (DisplayManager) context.getSystemService(Context.DISPLAY_SERVICE);
            Display[] displays = displayManager.getDisplays();
            if (displays != null && displays.length > 0){
                for (int i = 0; i < displays.length; i++) {
                    Display d = displays[i];
                    boolean internal = true;
                    if (Build.VERSION.SDK_INT_FULL >= Build.VERSION_CODES_FULL.BAKLAVA_1) {
                        internal = d.isInternal();
                    }else{
                        if (d.getDisplayId() != 0){
                            internal = false;
                        }
                    }

                    String s = "[" + i + "]" +
                            " displayId=" + d.getDisplayId() +
                            " name=" + d.getName() +
                            " state=" + d.getState() +
                            " size=" + d.getWidth() + "x" + d.getHeight();
                    Log.i(TAG, s);

                    if (!internal && !d.getName().contains("OneStepSlot")){
                        return d.getDisplayId();
                    }
                }
            }
        }catch (Exception e){
            Log.e(TAG, "getHdmiDisplayId e: "+e.getMessage());
            e.printStackTrace();
        }
        return null;
    }

    public static String disyPlaytypeToString(int type) {
        switch (type) {
            case TYPE_UNKNOWN:
                return "UNKNOWN";
            case TYPE_INTERNAL:
                return "INTERNAL";
            case TYPE_EXTERNAL:
                return "EXTERNAL";
            case TYPE_WIFI:
                return "WIFI";
            case TYPE_OVERLAY:
                return "OVERLAY";
            case TYPE_VIRTUAL:
                return "VIRTUAL";
            default:
                return Integer.toString(type);
        }
    }
}
