package com.sangluo.onestep.shizuku;

import android.os.RemoteException;
import android.util.Log;

import com.sangluo.onestep.IEscCallback;
import com.sangluo.onestep.IShizukuKeyService;

import java.io.BufferedReader;
import java.io.InputStreamReader;

public class ShizukuKeyService extends IShizukuKeyService.Stub {

    private static final String TAG = "OneStep40";
    private IEscCallback mCallback;

    // 防抖机制：记录上一次触发的时间戳
    private long mLastTriggerTime = 0;
    private static final long DEBOUNCE_INTERVAL_MS = 300; // 300毫秒防抖间隔

    public ShizukuKeyService() {
        // 启动前清理旧的 getevent 进程，防止重复监听
        killOldGeteventProcess();
        startKernelInputListener();
    }

    private void killOldGeteventProcess() {
        try {
            // 杀掉当前用户下残留的 getevent 进程
            Runtime.getRuntime().exec("pkill -f getevent");
            Thread.sleep(100); // 稍微等待 100ms 确保旧进程完全退出
        } catch (Exception ignored) {}
    }

    private void startKernelInputListener() {
        new Thread(() -> {
            Log.d(TAG, "正在启动内核级 input 事件监听器 (getevent)...");
            Process process = null;
            BufferedReader reader = null;
            try {
                // -l 参数输出文字格式，-q 参数去除多余的前缀打印
                process = Runtime.getRuntime().exec("getevent -l -q");
                reader = new BufferedReader(new InputStreamReader(process.getInputStream()));

                String line;
                while ((line = reader.readLine()) != null) {
//                    Log.d(TAG, line);
                    // 只捕获 KEY_ESC 且状态为 DOWN 的事件
                    if (line.contains("KEY_ESC") && line.contains("DOWN")) {
                        dispatchEscEvent("KEY_ESC");
                    }
                    else if (line.contains("KEY_F1") && line.contains("DOWN")) {
                        dispatchEscEvent("KEY_F1");
                    }
                    else if (line.matches(".*KEY_[1-7].*DOWN.*")) {
                        dispatchEscEvent(line);
                    }
                }
            } catch (Throwable e) {
                Log.e(TAG, "getevent 监听异常: ", e);
                e.printStackTrace();
            } finally {
                if (reader != null) {
                    try { reader.close(); } catch (Exception ignored) {}
                }
                if (process != null) {
                    process.destroy();
                }
            }
        }, "KernelInputMonitorThread").start();
    }

    /**
     * 统一控制事件分发（带防抖逻辑）
     */
    private synchronized void dispatchEscEvent(String key_type) {
        long currentTime = System.currentTimeMillis();
        if (currentTime - mLastTriggerTime > DEBOUNCE_INTERVAL_MS) {
            mLastTriggerTime = currentTime;
            Log.d(TAG, "【全局按键触发成功！】");
            if (mCallback != null) {
                try {
                    mCallback.onEscPressed(key_type);
                } catch (RemoteException e) {
                    Log.e(TAG, "回调主 App 失败", e);
                }
            }
        }
    }

    @Override
    public void setCallback(IEscCallback callback) throws RemoteException {
        this.mCallback = callback;
        Log.d(TAG, "主 App 回调接口绑定成功！");
    }

    @Override
    public void destroy() {
        System.exit(0);
    }
}