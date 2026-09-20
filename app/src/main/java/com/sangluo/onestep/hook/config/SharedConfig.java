package com.sangluo.onestep.hook.config;


import android.util.Log;

import com.crossbowffs.remotepreferences.RemotePreferenceAccessException;
import com.crossbowffs.remotepreferences.RemotePreferences;
import com.sangluo.onestep.hook.DisPlayStringConst;
import com.sangluo.onestep.utils.AweSomeUtil;

import java.util.HashMap;

public class SharedConfig {
    
    private static final String TAG = "OneStep4 LSP SharedConfig";

    // 是否启用模块
    public static boolean ENABLED = true;
    // 投屏时手机是否息屏
    public static boolean ENABLE_DISPLAY_CONTROL = false;
    // 是否禁用hdmi音频
    public static boolean ENABLE_DISABLE_AUDIO = false;
    // 是否禁用系统快捷键
    public static boolean ENABLE_DISABLE_KEY = false;
    public static String START_SOFT_WARE_PACKAGE = "com.sangluo.onestepc16";

    private static final String AUTHORITY = "com.sangluo.onestepc16.prefs";
    private static final String PREF_FILE = "enhance_mirror_prefs";

    // Do NOT build RemotePreferences in a static initializer: the owning app's
    // ContentProvider / current context may not be ready yet (see RemotePreferences
    // docs). It is created lazily via initPrefs(), which handleLoadPackage calls.
    private static HashMap<String, Boolean> PROCESS_LOAD_STATUS = new HashMap<>();

    public static void loadConfig() {
        String processName = AweSomeUtil.getCurrentProcessName();
        Log.d(TAG, "loadConfig 调用，进程: " + processName + ", 加载状态: " + PROCESS_LOAD_STATUS.get(processName));

        RemotePreferences prefs = new RemotePreferences(AweSomeUtil.getAnyContext(), AUTHORITY, PREF_FILE, true);
        if (prefs == null) {
            Log.w(TAG, "loadConfig skipped: provider not ready, keep current/default values");
            return;
        }
        try {
            ENABLE_DISPLAY_CONTROL = prefs.getBoolean(DisPlayStringConst.POWER_OFF_CONST, false);
            ENABLE_DISABLE_AUDIO = prefs.getBoolean(DisPlayStringConst.DISABLE_AUDIO_CONST, false);
            ENABLE_DISABLE_KEY = prefs.getBoolean(DisPlayStringConst.DISABLE_KEY_CONST, false);
            Log.i(TAG, "loadConfig ENABLED: "+ENABLED+", ENABLE_DISPLAY_CONTROL: "+ENABLE_DISPLAY_CONTROL+", ENABLE_DISABLE_AUDIO: "+ENABLE_DISABLE_AUDIO+", ENABLE_DISABLE_KEY: "+ENABLE_DISABLE_KEY);
            PROCESS_LOAD_STATUS.put(processName, true);
        } catch (RemotePreferenceAccessException e) {
            // Provider unreachable / not exported / permission denied -> the "config looks all off" case.
            Log.e(TAG, "loadConfig: provider unreachable, authority=" + AUTHORITY
                    + " file=" + PREF_FILE + " (is the app installed & provider exported?)", e);
        } catch (Throwable e) {
            Log.e(TAG, "配置加载失败", e);
        }
    }

}
