package com.sangluo.onestep;

import com.crossbowffs.remotepreferences.RemotePreferenceProvider;
import com.sangluo.onestep.constant.CommonConstant;

public class SharedPerfsProvider extends RemotePreferenceProvider {

    private static final String TAG = "OneStep4 ShellExecutor";
    private static final String PRETAG = "[ShellExecutor] ";
    private static final int SYSTEM_UID = 1000;

    public SharedPerfsProvider() {
        super(
                CommonConstant.APP_PACKAGE_NAME + ".prefs",
                new String[] {"enhance_mirror_prefs"}
        );
    }

}
