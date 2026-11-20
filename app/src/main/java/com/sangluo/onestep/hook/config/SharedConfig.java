package com.sangluo.onestep.hook.config;


import android.content.Context;
import android.util.Log;

import com.crossbowffs.remotepreferences.RemotePreferenceAccessException;
import com.crossbowffs.remotepreferences.RemotePreferences;
import com.sangluo.onestep.constant.CommonConstant;
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
    // 禁用配置更新app重新布局
    public static boolean RE_LAUNCH_FLAG = false;
    // app是否运行在虚拟屏幕上
    public static boolean IS_VIRTUAL_DISPLAY_RUN = false;
    // 是否启用小布识屏副屏增强
    public static boolean USE_XIAOBU_SCREEN = false;
    public static final String START_SOFT_WARE_PACKAGE = CommonConstant.APP_PACKAGE_NAME;

    public static final String AUTHORITY = START_SOFT_WARE_PACKAGE + ".prefs";

    // Do NOT build RemotePreferences in a static initializer: the owning app's
    // ContentProvider / current context may not be ready yet (see RemotePreferences
    // docs). It is created lazily via initPrefs(), which handleLoadPackage calls.
    private static HashMap<String, Boolean> PROCESS_LOAD_STATUS = new HashMap<>();

    public static boolean loadConfig() {
        return loadConfig(AweSomeUtil.getAnyContext());
    }

    public static boolean loadConfig(Context context) {
        String processName = AweSomeUtil.getCurrentProcessName();
        Log.d(TAG, "loadConfig 调用，进程: " + processName + ", 加载状态: " + PROCESS_LOAD_STATUS.get(processName));

        if (context == null) {
            Log.w(TAG, "loadConfig skipped: context is null");
            return false;
        }
        RemotePreferences prefs = new RemotePreferences(context, AUTHORITY, CommonConstant.MODULE_PREFS_NAME, true);
        if (prefs == null) {
            Log.w(TAG, "loadConfig skipped: provider not ready, keep current/default values");
            return false;
        }
        try {
            ENABLE_DISPLAY_CONTROL = prefs.getBoolean(DisPlayStringConst.POWER_OFF_CONST, false);
            ENABLE_DISABLE_AUDIO = prefs.getBoolean(DisPlayStringConst.DISABLE_AUDIO_CONST, false);
            ENABLE_DISABLE_KEY = prefs.getBoolean(DisPlayStringConst.DISABLE_KEY_CONST, false);
            RE_LAUNCH_FLAG = prefs.getBoolean(DisPlayStringConst.RE_LAUNCH_FLAG_CONST, false);
            IS_VIRTUAL_DISPLAY_RUN = prefs.getBoolean(DisPlayStringConst.IS_VIRTUAL_DISPLAY_RUN, false);
            USE_XIAOBU_SCREEN = prefs.getBoolean(DisPlayStringConst.USE_XIAOBU_SCREEN_CONST, false);
            Log.i(TAG, "loadConfig ENABLED: "+ENABLED+", ENABLE_DISPLAY_CONTROL: "+ENABLE_DISPLAY_CONTROL
                    +", ENABLE_DISABLE_AUDIO: "+ENABLE_DISABLE_AUDIO
                    +", ENABLE_DISABLE_KEY: "+ENABLE_DISABLE_KEY
                    +", RE_LAUNCH_FLAG: "+RE_LAUNCH_FLAG
                    +", IS_VIRTUAL_DISPLAY_RUN: "+IS_VIRTUAL_DISPLAY_RUN
                    +", USE_XIAOBU_SCREEN: "+USE_XIAOBU_SCREEN
            );
            PROCESS_LOAD_STATUS.put(processName, true);
            return true;
        } catch (RemotePreferenceAccessException e) {
            // Provider unreachable / not exported / permission denied -> the "config looks all off" case.
            Log.e(TAG, "loadConfig: provider unreachable, authority=" + AUTHORITY
                    + " file=" + CommonConstant.MODULE_PREFS_NAME + " (is the app installed & provider exported?)", e);
            IS_VIRTUAL_DISPLAY_RUN = false;
        } catch (Throwable e) {
            Log.e(TAG, "配置加载失败", e);
        }
        return false;
    }

}
