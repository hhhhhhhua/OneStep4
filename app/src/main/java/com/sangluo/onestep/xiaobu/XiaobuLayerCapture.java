package com.sangluo.onestep.xiaobu;

import android.content.ComponentName;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Point;
import android.graphics.Rect;
import android.hardware.display.DisplayManager;
import android.util.Log;
import android.view.Display;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.List;

/** Fast in-process layer capture used as a fallback when the shell capture is unavailable. */
final class XiaobuLayerCapture {
    private static final String TAG = "OneStepXiaobu";
    private static final String SELF = "com.coloros.colordirectservice";
    private static final String SYSTEM_UI = "com.android.systemui";

    private XiaobuLayerCapture() {}

    static Bitmap capture(Context context, int display) {
        if (context == null || display <= 0) return null;
        try {
            DisplayManager manager = (DisplayManager) context.getSystemService(Context.DISPLAY_SERVICE);
            Display target = manager == null ? null : manager.getDisplay(display);
            if (target == null) return null;
            Point size = new Point();
            target.getRealSize(size);
            if (size.x <= 0 || size.y <= 0) return null;

            Class<?> screenClass = Class.forName("com.oplus.screenshot.OplusScreenCapture");
            Object screen = screenClass.getMethod("getInstance").invoke(null);
            if (screen == null) return null;

            Object window = topWindow(display, size.x, size.y);
            if (window == null) return null;
            Object surface = field(window, "mSurfaceControl");
            if (surface == null) return null;

            Class<?> builderClass = Class.forName(
                    "com.oplus.screenshot.OplusScreenCapture$LayerCaptureArgs$Builder");
            Constructor<?> constructor = builderClass.getConstructor(
                    Class.forName("android.view.SurfaceControl"));
            Object builder = constructor.newInstance(surface);
            builder = invoke(builder, "setSourceCrop", new Class<?>[]{Rect.class},
                    new Rect(0, 0, size.x, size.y));
            builder = invoke(builder, "setFrameScale", new Class<?>[]{float.class}, 1.0f);
            builder = invoke(builder, "setPixelFormat", new Class<?>[]{int.class}, 1);
            Object args = invoke(builder, "build", new Class<?>[0]);
            Class<?> argsClass = Class.forName(
                    "com.oplus.screenshot.OplusScreenCapture$LayerCaptureArgs");
            Object buffer = screenClass.getMethod("captureLayers", argsClass).invoke(screen, args);
            if (buffer == null) return null;
            return (Bitmap) buffer.getClass().getMethod("asBitmap").invoke(buffer);
        } catch (Throwable error) {
            Log.w(TAG, "layer capture failed", error);
            return null;
        }
    }

    private static Object topWindow(int display, int width, int height) {
        try {
            Object manager = Class.forName("android.view.OplusWindowManager")
                    .getConstructor().newInstance();
            List<?> windows = null;
            try {
                Object value = invoke(manager, "getVisibleWindowInfo", new Class<?>[]{int.class}, display);
                if (value instanceof List) windows = (List<?>) value;
            } catch (Throwable ignored) { }
            if (windows == null) return null;
            Object best = null;
            long bestArea = -1;
            for (Object window : windows) {
                if (window == null) continue;
                Object surface = field(window, "mSurfaceControl");
                ComponentName component = (ComponentName) field(window, "componentName");
                String pkg = string(window, "packageName");
                String name = string(window, "windowName");
                Rect bounds = (Rect) field(window, "mBounds");
                if (surface == null || component == null || pkg == null || bounds == null) continue;
                if (SELF.equals(pkg) || SYSTEM_UI.equals(pkg) || pkg.startsWith("com.oplus.systemui")) continue;
                if (name != null && (name.contains("StatusBar") || name.contains("NavigationBar")
                        || name.contains("ScreenDecor") || name.contains("OneStepSideInputShield")
                        || name.contains("OneStep floating ball"))) continue;
                if (Math.abs(bounds.width() - width) > 8 || Math.abs(bounds.height() - height) > 8) continue;
                long area = (long) bounds.width() * bounds.height();
                if (area > bestArea) {
                    bestArea = area;
                    best = window;
                }
            }
            return best;
        } catch (Throwable ignored) {
            return null;
        }
    }

    private static Object invoke(Object target, String name, Class<?>[] types, Object... args)
            throws ReflectiveOperationException {
        Method method = target.getClass().getMethod(name, types);
        return method.invoke(target, args);
    }

    private static Object field(Object target, String name) {
        try {
            Field field = target.getClass().getField(name);
            return field.get(target);
        } catch (Throwable ignored) { }
        try {
            Field field = target.getClass().getDeclaredField(name);
            field.setAccessible(true);
            return field.get(target);
        } catch (Throwable ignored) {
            return null;
        }
    }

    private static String string(Object target, String name) {
        Object value = field(target, name);
        return value instanceof String ? (String) value : null;
    }
}
