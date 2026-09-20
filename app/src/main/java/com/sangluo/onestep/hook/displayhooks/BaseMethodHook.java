package com.sangluo.onestep.hook.displayhooks;

import android.os.Handler;

import com.sangluo.onestep.hook.config.SharedConfig;

import de.robv.android.xposed.XC_MethodHook;

public abstract class BaseMethodHook extends XC_MethodHook {
    
    @Override
    protected final void beforeHookedMethod(MethodHookParam param) throws Throwable {
        // 在逻辑执行前，统一加载/刷新配置
        Handler handler = DisplayServiceHook.getsHdmiHandler();
        handler.post(() -> SharedConfig.loadConfig());

        // 执行具体的子类逻辑
        onBefore(param);
    }

    @Override
    protected final void afterHookedMethod(MethodHookParam param) throws Throwable {
        onAfter(param);
    }

    // 提供给子类实现的方法，替代原生的 before/after
    protected void onBefore(MethodHookParam param) throws Throwable {}
    protected void onAfter(MethodHookParam param) throws Throwable {}
}