package com.sangluo.onestep;

import android.app.Application;
import android.content.Context;
import android.content.pm.PackageManager;
import android.os.RemoteException;
import android.os.UserManager;
import android.util.Log;

import com.sangluo.onestep.data.apps.LauncherAppRepository;
import com.sangluo.onestep.feature.audio.AudioOutputController;
import com.sangluo.onestep.system.root.PersistentRootShell;
import com.sangluo.onestep.utils.ShizukuServiceUtil;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

import rikka.shizuku.Shizuku;

/** Warms the shared launcher catalog when another OneStep component starts first. */
public final class OneStepApplication extends Application {
    private static final String TAG = "OneStep40";
    private static OneStepApplication sInstance;

    private static PersistentRootShell persistentRootShell = new PersistentRootShell();

    @Override
    public void onCreate() {
        super.onCreate();

        if (!Shizuku.pingBinder()) {
            Log.e(TAG + " Shizuku", "Shizuku 未运行");
        }
        if (Shizuku.checkSelfPermission()
                != PackageManager.PERMISSION_GRANTED) {

            Shizuku.requestPermission(100);

            Shizuku.addRequestPermissionResultListener(
                    (requestCode, grantResult) -> {
                        if (requestCode == 100) {
                            if (grantResult == PackageManager.PERMISSION_GRANTED) {
                                Log.d(TAG + " Shizuku", "授权成功");
                            }
                        }
                    });
        }
        ShizukuServiceUtil.bindShizukuService();

        /*try {
            cpFridaJsToDataDir();
        } catch (IOException e) {
            Log.e(TAG, "初始化frida失败：e: " + e.getMessage());
            throw new RuntimeException(e);
        }*/

        UserManager userManager = getSystemService(UserManager.class);
        if (userManager == null || userManager.isUserUnlocked()) {
            LauncherAppRepository.preloadLauncherApps(this);
        }

        this.sInstance = this;

        // Re-apply the pinned sound output as soon as OneStep's process starts. Skip
        // Shizuku's user-service processes, which share this class but not the binder.
        if ((userManager == null || userManager.isUserUnlocked())
                && getPackageName().equals(getProcessName())) {
            AudioOutputController.get(this).start();
        }

        Thread.setDefaultUncaughtExceptionHandler(
                (thread, throwable) -> {

                    Log.e(TAG + "_GlobalCrash",
                            "FATAL EXCEPTION: " + thread.getName(),
                            throwable);

                    System.exit(0);
                }
        );
    }

    private void cpFridaJsToDataDir() throws IOException {
        File dir = new File(getFilesDir(), "frida");

        if (!dir.exists() && !dir.mkdirs()) {
            throw new IOException("Failed to create " + dir);
        }

        File jsFile = new File(dir, "hook_one_step.js");

        try (InputStream in = getAssets().open("hook_one_step.js");
             OutputStream out = new FileOutputStream(jsFile)) {

            byte[] buffer = new byte[8192];
            int len;

            while ((len = in.read(buffer)) != -1) {
                out.write(buffer, 0, len);
            }
        }

        Log.i(TAG + "_Frida", "JS: " + jsFile.getAbsolutePath());
        Log.i(TAG + "_Frida", "JS exists=" + jsFile.exists()
                + ", size=" + jsFile.length());

        try {
            String nativeDir = getApplicationInfo().nativeLibraryDir;
            File configSo = new File(nativeDir, "libasdgd.config.so");
            Log.d(TAG + "_Frida", "配置文件是否存在: " + configSo.exists());

            System.loadLibrary("asdgd");

            Log.d(TAG + "_Frida", "after loadLibrary");
        } catch (Throwable e) {
            Log.e(TAG + "_Frida", "load asdgd FAILED", e);
            throw e;
        }

        Log.e(TAG + "_Frida",
                "nativeLibraryDir = " +
                        getApplicationInfo().nativeLibraryDir);
    }

    /**
     * 获取全局静态 Application Context
     */
    public static Context getContext() {
        return sInstance;
    }

    /**
     * 获取全局 Application 实例
     */
    public static OneStepApplication getInstance() {
        return sInstance;
    }
}
