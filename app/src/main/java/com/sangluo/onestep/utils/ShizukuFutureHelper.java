package com.sangluo.onestep.utils;

import android.content.ComponentName;
import android.content.ServiceConnection;
import android.os.IBinder;
import android.os.IInterface;
import android.util.Log;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Function;

import rikka.shizuku.Shizuku;

public class ShizukuFutureHelper {

    private static final String TAG = "OneStep4";

    public static <T extends IInterface> T forceSyncBindInMainThread(
            Shizuku.UserServiceArgs args,
            Function<IBinder, T> stubAsInterface,
            long timeoutMs) {

        // 1. 使用原子引用，用于在异步回调和主线程之间跨线程安全传递对象
        final AtomicReference<T> serviceRef = new AtomicReference<>(null);

        // 2. 创建一个计数为 1 的同步倒计时锁
        final CountDownLatch latch = new CountDownLatch(1);

        // 3. 构造底层的 ServiceConnection
        ServiceConnection connection = new ServiceConnection() {
            @Override
            public void onServiceConnected(ComponentName name, IBinder service) {
                if (service != null) {
                    try {
                        // 在系统的 Binder 线程池中同步转换并塞入引用
                        T interfaceInstance = stubAsInterface.apply(service);
                        serviceRef.set(interfaceInstance);
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                }
                // 关键：无论成功还是失败，只要 Binder 握手完成，立刻释放主线程的锁
                latch.countDown();
            }

            @Override
            public void onServiceDisconnected(ComponentName name) {
                serviceRef.set(null);
            }
        };

        // 4. 发起 Shizuku 绑定请求
        // 核心原理：虽然 Shizuku 会在主线程安排回调，但由于系统底层 Binder 驱动是并发多线程的，
        // 当主线程在下面被 latch.await 卡住时，系统的 Binder 线程会强行切入并执行锁释放，从而彻底规避死锁。
        Shizuku.bindUserService(args, connection);

        try {
            // 5. 核心阻塞点：让主线程在 onCreate 中合法地“死等”指定的毫秒数
            boolean success = latch.await(timeoutMs, TimeUnit.MILLISECONDS);
            if (!success) {
                Log.e("ShizukuSync", "主线程强行同步绑定服务超时！");
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }

        // 6. 像普通的同步方法一样，直接一行代码返回泛型结果
        return serviceRef.get();
    }

}
