package com.sangluo.onestep;

import com.crossbowffs.remotepreferences.RemotePreferenceProvider;

public class SharedPerfsProvider extends RemotePreferenceProvider {

    private static final String TAG = "OneStep4 ShellExecutor";
    private static final String PRETAG = "[ShellExecutor] ";
    private static final int SYSTEM_UID = 1000;

    public SharedPerfsProvider() {
        super(
                "com.sangluo.onestepc16.prefs",
                new String[] {"enhance_mirror_prefs"}
        );
    }

}
