package com.sangluo.onestep.xiaobu;

import android.app.ActivityManager;
import android.app.Application;
import android.content.ComponentName;
import android.content.Context;
import android.graphics.Point;
import android.graphics.Rect;
import android.hardware.display.DisplayManager;
import android.util.Log;
import android.view.Display;
import com.sangluo.onestep.hook.config.SharedConfig;
import java.lang.reflect.Field;
import java.util.List;

/**
 * Shared state for the "使用小布识屏" option migrated from the xiaobu module. The target display is
 * detected as the display that currently hosts the OneStep window, so display ids may change
 * between boots without breaking the routing.
 */
public final class XiaobuDisplay {
    public static final String TARGET_PACKAGE = "com.coloros.colordirectservice";
    public static final String ONESTEP_PACKAGE = "com.sangluo.onestepc16";
    public static final String ENABLED_KEY = "onestep_xiaobu_enabled";
    private static final String TAG = "OneStepXiaobu";

    private static volatile Application application;
    private static volatile boolean installed;
    private static volatile boolean routingEnabled;
    private static volatile int cachedId = -1;
    private static volatile String cachedName = "";

    private XiaobuDisplay() {}

    public static void install(Application app) {
        if (installed || app == null) return;
        installed = true;
        application = app;
        // Install unconditionally: the OneStep process may not exist yet when this package
        // starts. The SharedConfig switch and the display are checked by every hook at runtime.
        XiaobuDisplayHook.install(app.getClassLoader());
        routingEnabled = true;
        Log.i(TAG, "xiaobu hooks installed; switch checked at runtime");
    }

    private static volatile boolean configLoaded;

    static void ensureConfig(Context context) {
        if (configLoaded) return;
        Context source = context != null ? context : application;
        if (source == null) return;
        try {
            if (SharedConfig.loadConfig(source)) configLoaded = true;
        } catch (Throwable error) {
            Log.w(TAG, "shared config load failed", error);
        }
    }

    static boolean enabled(Context context) {
        ensureConfig(context);
        return SharedConfig.ENABLED && SharedConfig.USE_XIAOBU_SCREEN;
    }

    static Application application() {
        return application;
    }

    static boolean routingEnabled() {
        return routingEnabled;
    }

    static boolean isTargeted() {
        Application app = application;
        return app != null && enabled(app);
    }

    static int displayId(Context context) {
        if (context == null || !enabled(context)) return -1;
        try {
            DisplayManager manager = (DisplayManager) context.getSystemService(Context.DISPLAY_SERVICE);
            if (manager == null) return -1;
            int cached = cachedId;
            if (cached > 0) {
                Display display = manager.getDisplay(cached);
                if (display != null && (cachedName.length() == 0
                        || cachedName.equals(display.getName()))) {
                    return cached;
                }
            }
            Display visible = displayHostingOneStep(manager);
            if (visible != null) {
                remember(visible);
                return visible.getDisplayId();
            }
            int activityDisplay = displayForOneStepTask(context);
            if (activityDisplay > 0) {
                Display display = manager.getDisplay(activityDisplay);
                if (display != null) {
                    remember(display);
                    return activityDisplay;
                }
            }
        } catch (Throwable error) {
            Log.w(TAG, "display lookup failed", error);
        }
        return -1;
    }

    static String displayName(Context context) {
        try {
            DisplayManager manager = (DisplayManager) context.getSystemService(Context.DISPLAY_SERVICE);
            Display display = manager == null ? null : manager.getDisplay(displayId(context));
            return display == null ? null : display.getName();
        } catch (Throwable ignored) {
            return null;
        }
    }

    static Rect displayRect(Context context) {
        int id = displayId(context);
        if (id <= 0) return null;
        try {
            DisplayManager manager = (DisplayManager) context.getSystemService(Context.DISPLAY_SERVICE);
            Display display = manager == null ? null : manager.getDisplay(id);
            if (display == null) return null;
            Point size = new Point();
            display.getRealSize(size);
            return size.x > 0 && size.y > 0 ? new Rect(0, 0, size.x, size.y) : null;
        } catch (Throwable ignored) {
            return null;
        }
    }

    static ComponentName topComponent(Context context) {
        if (context == null) return null;
        int display = displayId(context);
        if (display <= 0) return null;
        try {
            DisplayManager manager = (DisplayManager) context.getSystemService(Context.DISPLAY_SERVICE);
            Display target = manager == null ? null : manager.getDisplay(display);
            if (target == null) return null;
            Point size = new Point();
            target.getRealSize(size);
            Object value = Class.forName("android.view.OplusWindowManager")
                    .getConstructor().newInstance();
            Object windows = value.getClass().getMethod("getVisibleWindowInfo", int.class)
                    .invoke(value, display);
            if (!(windows instanceof List)) return null;
            ComponentName best = null;
            long bestArea = -1;
            for (Object window : (List<?>) windows) {
                if (window == null) continue;
                ComponentName component = (ComponentName) field(window, "componentName");
                String pkg = (String) field(window, "packageName");
                Object surface = field(window, "mSurfaceControl");
                Rect bounds = (Rect) field(window, "mBounds");
                if (component == null || pkg == null || surface == null || bounds == null) continue;
                if (TARGET_PACKAGE.equals(pkg) || "com.android.systemui".equals(pkg)
                        || pkg.startsWith("com.oplus.systemui")) continue;
                if (size.x > 0 && size.y > 0
                        && (Math.abs(bounds.width() - size.x) > 8
                        || Math.abs(bounds.height() - size.y) > 8)) continue;
                long area = (long) bounds.width() * bounds.height();
                if (area > bestArea) {
                    bestArea = area;
                    best = component;
                }
            }
            return best;
        } catch (Throwable ignored) {
            return null;
        }
    }

    private static Display displayHostingOneStep(DisplayManager manager) {
        try {
            for (Display display : manager.getDisplays()) {
                if (display == null || display.getDisplayId() == 0) continue;
                if (hostsOneStep(display)) return display;
            }
        } catch (Throwable ignored) { }
        return null;
    }

    /** Display that currently hosts the OneStep MainActivity task. */
    private static int displayForOneStepTask(Context context) {
        try {
            ActivityManager manager = (ActivityManager) context.getSystemService(Context.ACTIVITY_SERVICE);
            if (manager == null) return -1;
            List<ActivityManager.RunningTaskInfo> tasks = manager.getRunningTasks(Integer.MAX_VALUE);
            if (tasks == null) return -1;
            int best = -1;
            for (ActivityManager.RunningTaskInfo task : tasks) {
                ComponentName top = (ComponentName) field(task, "topActivity");
                ComponentName base = (ComponentName) field(task, "baseActivity");
                boolean matches = isOneStepMain(top) || isOneStepMain(base);
                if (!matches) continue;
                int display = displayOf(task);
                if (display <= 0) continue;
                if (isOneStepMain(top)) return display;
                if (best < 0) best = display;
            }
            return best;
        } catch (Throwable ignored) {
            return -1;
        }
    }

    private static boolean isOneStepMain(ComponentName component) {
        return component != null && ONESTEP_PACKAGE.equals(component.getPackageName())
                && component.getClassName() != null
                && component.getClassName().contains("MainActivity");
    }

    private static int displayOf(ActivityManager.RunningTaskInfo task) {
        try {
            return ((Number) field(task, "displayId")).intValue();
        } catch (Throwable ignored) {
            return -1;
        }
    }

    private static boolean hostsOneStep(Display display) {
        try {
            Object manager = Class.forName("android.view.OplusWindowManager")
                    .getConstructor().newInstance();
            Object value = manager.getClass().getMethod("getVisibleWindowInfo", int.class)
                    .invoke(manager, display.getDisplayId());
            if (!(value instanceof List)) return false;
            for (Object window : (List<?>) value) {
                if (window == null) continue;
                Object pkg = field(window, "packageName");
                if (ONESTEP_PACKAGE.equals(pkg)) return true;
            }
        } catch (Throwable ignored) { }
        return false;
    }

    private static void remember(Display display) {
        cachedId = display.getDisplayId();
        String name = display.getName();
        cachedName = name == null ? "" : name;
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
