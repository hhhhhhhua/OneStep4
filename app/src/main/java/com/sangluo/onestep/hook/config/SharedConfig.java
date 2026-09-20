package com.sangluo.onestep.hook.config;


import android.util.Log;

import com.crossbowffs.remotepreferences.RemotePreferences;
import com.sangluo.onestep.utils.SysServiceObjUtil;

public class SharedConfig {
    
    private static final String TAG = "OneStep4 LSP SharedConfig";

    public static boolean ENABLED = false;
    // 投屏时手机是否息屏
    public static boolean ENABLE_DISPLAY_CONTROL = false;
    // 是否禁用hdmi音频
    public static boolean ENABLE_DISABLE_AUDIO = false;
    // 是否禁用按键
    public static boolean ENABLE_DISABLE_KEY = false;
    // 单应用投屏要使用的软件包名
    public static String START_SOFT_WARE_PACKAGE = "com.sangluo.onestepc16";
    private static RemotePreferences prefs;

    public static void loadConfig() {
        try {
            if (prefs == null) {
                prefs = new RemotePreferences(SysServiceObjUtil.getCurrentContext(), "com.enhancemirror.prefs", "enhance_mirror_prefs");
            }

            ENABLED = prefs.getBoolean("master_switch", false);
            ENABLE_DISPLAY_CONTROL = prefs.getBoolean("enable_display_control", false);
            ENABLE_DISABLE_AUDIO = prefs.getBoolean("disable_audio", false);
            ENABLE_DISABLE_KEY = prefs.getBoolean("disable_key", false);
            Log.i(TAG, "ENABLED: "+ENABLED+", ENABLE_DISPLAY_CONTROL: "+ENABLE_DISPLAY_CONTROL+", ENABLE_DISABLE_AUDIO: "+ENABLE_DISABLE_AUDIO+", ENABLE_DISABLE_KEY: "+ENABLE_DISABLE_KEY);

        } catch (Throwable e) {
            Log.d(TAG, "配置加载失败", e);
        }

    }
}
