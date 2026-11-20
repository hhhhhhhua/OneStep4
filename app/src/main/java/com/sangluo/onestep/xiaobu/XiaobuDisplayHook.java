package com.sangluo.onestep.xiaobu;

import android.app.ActivityManager;
import android.app.ActivityOptions;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.Rect;
import android.os.Bundle;
import android.util.Log;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.List;
import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedBridge;

/**
 * Multi-display hooks for 小布识屏, migrated from the xiaobu-screen-enhancer module. The code only
 * runs when the OneStep "使用小布识屏" option is enabled and a OneStep display is present.
 */
final class XiaobuDisplayHook {
    private static final String TAG = "OneStepXiaobu";
    private static final String COLLECTION_ACTION = "com.oplus.infocollection.COLLECTION";
    private static final ThreadLocal<ActivityOptions> PENDING_OPTIONS = new ThreadLocal<ActivityOptions>();
    private static final ThreadLocal<Boolean> FALLBACK = new ThreadLocal<Boolean>();

    private XiaobuDisplayHook() {}

    static void install(ClassLoader loader) {
        boolean capture = installCapture(loader);
        boolean launch = installStartActivity(loader);
        boolean recorded = installOptionsRecorder(loader);
        if (!capture || !launch) {
            Log.w(TAG, "display routing unavailable on this host version");
            return;
        }
        XiaobuEntryBypass.install(loader);
        XiaobuOcrHook.install(loader);
        installReplaceGuard(loader);
        installTopComponent(loader);
        installTaskArea(loader);
        installNativeResultRect(loader);
        installActivityLaunch(loader);
        Log.i(TAG, recorded
                ? "multi-display collection routing installed"
                : "multi-display collection routing installed without launch animation");
    }

    // ---------------------------------------------------------------- capture

    private static boolean installCapture(ClassLoader loader) {
        try {
            Class<?> capture = Class.forName("wb0", false, loader);
            Method run = capture.getDeclaredMethod("a", Context.class, String.class, Rect.class,
                    boolean.class, int.class);
            if (!Modifier.isStatic(run.getModifiers()) || run.getReturnType() != Bitmap.class) {
                throw new NoSuchMethodException("Unknown capture contract");
            }
            XposedBridge.hookMethod(run, new XC_MethodHook() {
                @Override protected void beforeHookedMethod(MethodHookParam hook) {
                    Log.i(TAG, "capture hook entry arg1=" + hook.args[1]
                            + " preloadFlag=" + hook.args[3] + " routing=" + XiaobuDisplay.routingEnabled());
                    if (!XiaobuDisplay.routingEnabled()) return;
                    if (hook.args[1] != null) return;
                    Context context = (Context) hook.args[0];
                    int display = XiaobuDisplay.displayId(context);
                    if (display <= 0) return;
                    boolean preload = !Boolean.TRUE.equals(hook.args[3]);
                    if (preload) {
                        Bitmap fast = XiaobuLayerCapture.capture(context, display);
                        if (fast != null) hook.setResult(fast);
                        return;
                    }
                    Bitmap bitmap = XiaobuCaptureBridge.capture(context, display);
                    if (bitmap == null) bitmap = XiaobuLayerCapture.capture(context, display);
                    if (bitmap == null) {
                        hook.setResult(null);
                        return;
                    }
                    Log.i(TAG, "display capture display " + display + " bitmap="
                            + bitmap.getWidth() + "x" + bitmap.getHeight());
                    hook.setResult(bitmap);
                }
            });
            return true;
        } catch (Throwable error) {
            Log.w(TAG, "capture hook unavailable", error);
            return false;
        }
    }

    private static void installReplaceGuard(ClassLoader loader) {
        try {
            Class<?> parse = Class.forName("com.oplus.infocollection.collection.core.a", false, loader);
            Class<?> info = Class.forName("y34", false, loader);
            Class<?> continuation = Class.forName(
                    "kotlin.coroutines.jvm.internal.ContinuationImpl", false, loader);
            Method replace = parse.getDeclaredMethod("A", Context.class, info, continuation);
            XposedBridge.hookMethod(replace, new XC_MethodHook() {
                @Override protected void beforeHookedMethod(MethodHookParam hook) {
                    if (!XiaobuDisplay.routingEnabled() || !XiaobuDisplay.isTargeted()) return;
                    if (hook.args[1] != null) hook.setResult(hook.args[1]);
                }
            });
        } catch (Throwable error) {
            Log.w(TAG, "bitmap replacement guard unavailable", error);
        }
    }

    private static void installTopComponent(ClassLoader loader) {
        try {
            Class<?> common = Class.forName("com.coloros.direct.setting.util.CommonUtils", false, loader);
            Method top = common.getDeclaredMethod("getTopComponentName");
            if (!Modifier.isStatic(top.getModifiers()) || top.getReturnType() != ComponentName.class) {
                throw new NoSuchMethodException("Unknown top component contract");
            }
            XposedBridge.hookMethod(top, new XC_MethodHook() {
                @Override protected void beforeHookedMethod(MethodHookParam hook) {
                    Log.i(TAG, "top component hook entry");
                    if (!XiaobuDisplay.routingEnabled() || !XiaobuDisplay.isTargeted()) return;
                    ComponentName component = XiaobuDisplay.topComponent(XiaobuDisplay.application());
                    if (component != null) hook.setResult(component);
                }
            });
        } catch (Throwable error) {
            Log.w(TAG, "top component hook unavailable", error);
        }
        try {
            Class<?> helper = Class.forName("z66", false, loader);
            Method analyze = helper.getDeclaredMethod("a", Context.class);
            if (!Modifier.isStatic(analyze.getModifiers())) {
                throw new NoSuchMethodException("Unknown window stack contract");
            }
            XposedBridge.hookMethod(analyze, new XC_MethodHook() {
                @Override protected void afterHookedMethod(MethodHookParam hook) {
                    if (!XiaobuDisplay.routingEnabled() || !XiaobuDisplay.isTargeted()) return;
                    Object result = hook.getResult();
                    if (result == null) return;
                    Context context = (Context) hook.args[0];
                    ComponentName top = XiaobuDisplay.topComponent(
                            context != null ? context : XiaobuDisplay.application());
                    if (top == null) return;
                    try {
                        Field component = result.getClass().getDeclaredField("a");
                        if (top.equals(component.get(result))) return;
                        Field expand = result.getClass().getDeclaredField("b");
                        Field secure = result.getClass().getDeclaredField("c");
                        Field windows = result.getClass().getDeclaredField("d");
                        Object replaced = result.getClass().getConstructor(
                                ComponentName.class, boolean.class, boolean.class, List.class)
                                .newInstance(top, expand.getBoolean(result), secure.getBoolean(result),
                                        (List<?>) windows.get(result));
                        hook.setResult(replaced);
                    } catch (Throwable error) {
                        Log.w(TAG, "focus activity replacement failed", error);
                    }
                }
            });
        } catch (Throwable error) {
            Log.w(TAG, "focus activity hook unavailable", error);
        }
        try {
            Class<?> insets = Class.forName("xe5", false, loader);
            Method method = insets.getDeclaredMethod("b", Context.class);
            if (!Modifier.isStatic(method.getModifiers()) || method.getReturnType() != Rect.class) {
                throw new NoSuchMethodException("Unknown inset contract");
            }
            XposedBridge.hookMethod(method, new XC_MethodHook() {
                @Override protected void beforeHookedMethod(MethodHookParam hook) {
                    if (!XiaobuDisplay.routingEnabled() || !XiaobuDisplay.isTargeted()) return;
                    hook.setResult(new Rect());
                }
            });
        } catch (Throwable error) {
            Log.w(TAG, "inset hook unavailable", error);
        }
        try {
            Class<?> helper = Class.forName("z66", false, loader);
            Method method = helper.getDeclaredMethod("h", Context.class, String.class);
            if (!Modifier.isStatic(method.getModifiers()) || method.getReturnType() != ComponentName.class) {
                throw new NoSuchMethodException("Unknown window helper contract");
            }
            XposedBridge.hookMethod(method, new XC_MethodHook() {
                @Override protected void beforeHookedMethod(MethodHookParam hook) {
                    if (!XiaobuDisplay.routingEnabled() || !XiaobuDisplay.isTargeted()) return;
                    Context context = (Context) hook.args[0];
                    ComponentName component = XiaobuDisplay.topComponent(
                            context != null ? context : XiaobuDisplay.application());
                    if (component != null) hook.setResult(component);
                }
            });
        } catch (Throwable error) {
            Log.w(TAG, "window helper hook unavailable", error);
        }
    }

    private static void installTaskArea(ClassLoader loader) {
        try {
            Class<?> startInfo = Class.forName(
                    "com.oplus.infocollection.data.CollectionStartInfo", false, loader);
            Method getter = startInfo.getDeclaredMethod("getTaskArea");
            XposedBridge.hookMethod(getter, new XC_MethodHook() {
                @Override protected void beforeHookedMethod(MethodHookParam hook) {
                    if (!XiaobuDisplay.routingEnabled() || !XiaobuDisplay.isTargeted()) return;
                    Rect rect = XiaobuDisplay.displayRect(XiaobuDisplay.application());
                    if (rect != null) hook.setResult(rect);
                }
            });
        } catch (Throwable error) {
            Log.w(TAG, "task area hook unavailable", error);
        }
    }

    private static void installNativeResultRect(ClassLoader loader) {
        try {
            Class<?> layout = Class.forName(
                    "com.oplus.infocollection.view.CollectionInfoLayout", false, loader);
            Class<?> store = Class.forName("im0", false, loader);
            Method setInfo = layout.getDeclaredMethod("setInfo", store);
            XposedBridge.hookMethod(setInfo, new XC_MethodHook() {
                @Override protected void beforeHookedMethod(MethodHookParam hook) {
                    if (!XiaobuDisplay.routingEnabled() || !XiaobuDisplay.isTargeted()) return;
                    if (hook.args[0] == null) return;
                    Rect target = XiaobuDisplay.displayRect(XiaobuDisplay.application());
                    if (target == null) return;
                    try {
                        Object graphics = field(hook.args[0], "d");
                        if (graphics == null) return;
                        Object first = field(graphics, "a");
                        Object second = field(graphics, "b");
                        if (first instanceof Rect) {
                            ((Rect) first).set(0, 0, target.width(), target.height());
                        }
                        if (second instanceof Rect) {
                            ((Rect) second).set(0, 0, target.width(), target.height());
                        }
                    } catch (Throwable error) {
                        Log.w(TAG, "native result rect failed", error);
                    }
                }
            });
        } catch (Throwable error) {
            Log.w(TAG, "native result rect unavailable", error);
        }
        try {
            Class<?> helper = Class.forName("hy5", false, loader);
            Method insets = helper.getDeclaredMethod("i", Rect.class);
            XposedBridge.hookMethod(insets, new XC_MethodHook() {
                @Override protected void beforeHookedMethod(MethodHookParam hook) {
                    if (!XiaobuDisplay.routingEnabled() || !XiaobuDisplay.isTargeted()) return;
                    Rect input = (Rect) hook.args[0];
                    Rect target = XiaobuDisplay.displayRect(XiaobuDisplay.application());
                    if (input == null || target == null) return;
                    if (input.width() != target.width() || input.height() != target.height()) return;
                    hook.setResult(new Rect(0, 0, target.width(), target.height()));
                }
            });
        } catch (Throwable error) {
            Log.w(TAG, "native result inset unavailable", error);
        }
    }

    // ---------------------------------------------------------------- launch

    private static boolean installOptionsRecorder(ClassLoader loader) {
        boolean hooked = false;
        try {
            Class<?> options = Class.forName("android.app.ActivityOptions", false, loader);
            XC_MethodHook recorder = new XC_MethodHook() {
                @Override protected void afterHookedMethod(MethodHookParam hook) {
                    if (hook.getResult() instanceof ActivityOptions) {
                        PENDING_OPTIONS.set((ActivityOptions) hook.getResult());
                    }
                }
            };
            try {
                Method three = options.getDeclaredMethod("makeCustomAnimation", Context.class, int.class, int.class);
                XposedBridge.hookMethod(three, recorder);
                hooked = true;
            } catch (Throwable ignored) { }
            try {
                Method four = options.getDeclaredMethod("makeCustomAnimation", Context.class, int.class,
                        int.class, int.class);
                XposedBridge.hookMethod(four, recorder);
                hooked = true;
            } catch (Throwable ignored) { }
        } catch (Throwable error) {
            Log.w(TAG, "animation recorder unavailable", error);
        }
        return hooked;
    }

    private static boolean installStartActivity(ClassLoader loader) {
        try {
            Class<?> wrapper = Class.forName("android.content.ContextWrapper", false, loader);
            Method start = wrapper.getDeclaredMethod("startActivity", Intent.class, Bundle.class);
            if (start.getReturnType() != void.class) throw new NoSuchMethodException("Unknown startActivity");
            XposedBridge.hookMethod(start, new XC_MethodHook() {
                @Override protected void beforeHookedMethod(MethodHookParam hook) throws Throwable {
                    launchOnTarget(hook, hook.thisObject, (Intent) hook.args[0], 1);
                }
            });
            return true;
        } catch (Throwable error) {
            Log.w(TAG, "collection launch hook unavailable", error);
            return false;
        }
    }

    private static void installActivityLaunch(ClassLoader loader) {
        try {
            Class<?> activity = Class.forName("android.app.Activity", false, loader);
            Method start = activity.getDeclaredMethod("startActivityForResult",
                    Intent.class, int.class, Bundle.class);
            XposedBridge.hookMethod(start, new XC_MethodHook() {
                @Override protected void beforeHookedMethod(MethodHookParam hook) throws Throwable {
                    launchOnTarget(hook, hook.thisObject, (Intent) hook.args[0], 2);
                }
            });
        } catch (Throwable error) {
            Log.w(TAG, "activity launch hook unavailable", error);
        }
    }

    private static void launchOnTarget(XC_MethodHook.MethodHookParam hook, Object ownerObject,
            Intent intent, int optionsIndex) throws Throwable {
        Log.i(TAG, "launch hook entry action=" + (intent == null ? null : intent.getAction())
                + " routing=" + XiaobuDisplay.routingEnabled());
        if (hook == null || intent == null || !(ownerObject instanceof Context)) return;
        if (Boolean.TRUE.equals(FALLBACK.get())) return;
        if (!XiaobuDisplay.routingEnabled() || !XiaobuDisplay.isTargeted()) return;
        Context context = (Context) ownerObject;
        int display = XiaobuDisplay.displayId(context);
        if (display <= 0) return;
        boolean collection = COLLECTION_ACTION.equals(intent.getAction());
        if (!collection && contextDisplay(ownerObject) != display) return;
        Bundle redirected = collection ? optionsFor(display) : basicOptions(display);
        if (redirected == null) return;
        try {
            Object[] args = hook.args.clone();
            args[optionsIndex] = redirected;
            Method original = originalMethod(hook);
            if (original == null) return;
            invokeOriginal(original, ownerObject, args);
            hook.setResult(null);
        } catch (Throwable error) {
            Throwable cause = error instanceof InvocationTargetException
                    ? ((InvocationTargetException) error).getCause() : error;
            if (cause instanceof NoSuchMethodException) {
                Log.i(TAG, "native launch options unavailable; using display context");
            } else {
                Log.w(TAG, "launch on display " + display + " failed", cause);
            }
            if (launchOnDisplayContext(context, intent, display)) {
                hook.setResult(null);
            } else if (cause instanceof SecurityException && launchInTask(context, intent, display)) {
                hook.setResult(null);
            }
        }
    }

    private static int contextDisplay(Object owner) {
        try {
            if (owner instanceof android.app.Activity) {
                android.app.Activity activity = (android.app.Activity) owner;
                if (android.os.Build.VERSION.SDK_INT >= 30) {
                    android.view.Display display = activity.getDisplay();
                    if (display != null) return display.getDisplayId();
                }
                return activity.getWindowManager().getDefaultDisplay().getDisplayId();
            }
            if (owner instanceof Context) {
                Context context = (Context) owner;
                if (android.os.Build.VERSION.SDK_INT >= 30) {
                    android.view.Display display = context.getDisplay();
                    if (display != null) return display.getDisplayId();
                }
                Object manager = context.getSystemService(Context.WINDOW_SERVICE);
                if (manager instanceof android.view.WindowManager) {
                    return ((android.view.WindowManager) manager).getDefaultDisplay().getDisplayId();
                }
            }
        } catch (Throwable ignored) { }
        return -1;
    }

    private static Bundle basicOptions(int display) {
        try {
            ActivityOptions options = ActivityOptions.makeBasic();
            options.setLaunchDisplayId(display);
            return options.toBundle();
        } catch (Throwable error) {
            Log.w(TAG, "collection options building failed", error);
            return null;
        }
    }

    private static Bundle optionsFor(int display) {
        try {
            ActivityOptions options = PENDING_OPTIONS.get();
            PENDING_OPTIONS.remove();
            if (options == null) options = ActivityOptions.makeBasic();
            options.setLaunchDisplayId(display);
            return options.toBundle();
        } catch (Throwable error) {
            Log.w(TAG, "collection options building failed", error);
            return null;
        }
    }

    private static boolean launchOnDisplayContext(Context context, Intent intent, int display) {
        Boolean previous = FALLBACK.get();
        FALLBACK.set(Boolean.TRUE);
        try {
            android.hardware.display.DisplayManager manager =
                    (android.hardware.display.DisplayManager) context.getSystemService(Context.DISPLAY_SERVICE);
            android.view.Display target = manager == null ? null : manager.getDisplay(display);
            if (target == null) return false;
            context.createDisplayContext(target).startActivity(intent);
            Log.i(TAG, "collection launched with display context " + display);
            return true;
        } catch (Throwable error) {
            Log.w(TAG, "display context launch unavailable", error);
            return false;
        } finally {
            if (previous == null) FALLBACK.remove(); else FALLBACK.set(previous);
        }
    }

    private static boolean launchInTask(Context context, Intent intent, int display) {
        try {
            Class<?> flexible = Class.forName("com.oplus.flexiblewindow.FlexibleWindowManager",
                    false, context.getClassLoader());
            Object manager = flexible.getMethod("getInstance").invoke(null);
            Method start = null;
            for (Method method : flexible.getMethods()) {
                Class<?>[] types = method.getParameterTypes();
                if ("startActivityInTask".equals(method.getName()) && types.length == 2
                        && types[0] == Intent.class && types[1] == int.class) {
                    start = method;
                    break;
                }
            }
            if (start == null || manager == null) return false;
            int taskId = taskOnDisplay(context, display);
            if (taskId < 0) return false;
            start.invoke(manager, intent, taskId);
            Log.i(TAG, "collection launched in task " + taskId + " on display " + display);
            return true;
        } catch (Throwable error) {
            Log.w(TAG, "display task launch unavailable", error);
            return false;
        }
    }

    private static int taskOnDisplay(Context context, int display) {
        try {
            ActivityManager manager = (ActivityManager) context.getSystemService(Context.ACTIVITY_SERVICE);
            if (manager == null) return -1;
            List<ActivityManager.RunningTaskInfo> tasks = manager.getRunningTasks(Integer.MAX_VALUE);
            if (tasks == null) return -1;
            for (ActivityManager.RunningTaskInfo task : tasks) {
                if (displayOf(task) == display) return task.id;
            }
        } catch (Throwable ignored) { }
        return -1;
    }

    private static int displayOf(ActivityManager.RunningTaskInfo task) {
        try {
            Field field = Class.forName("android.app.TaskInfo").getDeclaredField("displayId");
            field.setAccessible(true);
            return field.getInt(task);
        } catch (Throwable ignored) {
            return -1;
        }
    }

    private static void invokeOriginal(Method method, Object owner, Object[] args) throws Throwable {
        Method invoke = Class.forName("de.robv.android.xposed.XposedBridge")
                .getMethod("invokeOriginalMethod", Method.class, Object.class, Object[].class);
        try {
            invoke.invoke(null, method, owner, args);
        } catch (InvocationTargetException error) {
            throw error.getCause();
        }
    }

    private static Method originalMethod(XC_MethodHook.MethodHookParam hook) {
        try {
            Field field = hook.getClass().getField("method");
            Object value = field.get(hook);
            return value instanceof Method ? (Method) value : null;
        } catch (Throwable ignored) {
            return null;
        }
    }

    private static Object field(Object target, String name) {
        try {
            Field field = target.getClass().getDeclaredField(name);
            field.setAccessible(true);
            return field.get(target);
        } catch (Throwable ignored) {
            return null;
        }
    }
}
