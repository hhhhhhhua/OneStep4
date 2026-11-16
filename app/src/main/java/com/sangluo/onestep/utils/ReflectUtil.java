package com.sangluo.onestep.utils;

import android.util.Log;

import com.alibaba.fastjson2.JSONObject;

import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.Arrays;

public class ReflectUtil {

    private static final String TAG = "OneStep4 ReflectUtil";

    public static Method findMethod(Class<?> clazz, String name) {
        Class<?> current = clazz;

        while (current != null) {
            try {
                Method method = current.getDeclaredMethod(name);
                method.setAccessible(true);
                return method;
            } catch (NoSuchMethodException ignored) {
                current = current.getSuperclass();
            }
        }

        try {
            throw new NoSuchMethodException(name);
        } catch (NoSuchMethodException e) {
            throw new RuntimeException(e);
        }
    }

    public static Field findField(Class<?> clazz, String name) {
        Class<?> current = clazz;

        while (current != null) {
            try {
                Field[] declaredFields = current.getDeclaredFields();
                Log.i(TAG, clazz.getName() + " ALL declaredFields: " + JSONObject.toJSONString(declaredFields));
                Field field = current.getDeclaredField(name);
                field.setAccessible(true);
                return field;
            } catch (NoSuchFieldException ignored) {
                current = current.getSuperclass();
            }
        }

        try {
            throw new NoSuchFieldException(name);
        } catch (NoSuchFieldException e) {
            throw new RuntimeException(e);
        }
    }

    public static Object invokeMethodVal(Class<?> clazz, String name, Object object){
        Method method = findMethod(
                clazz,
                name
        );

        Object info = null;
        try {
            info = method.invoke(object);
            return info;
        } catch (IllegalAccessException e) {
            throw new RuntimeException(e);
        } catch (InvocationTargetException e) {
            throw new RuntimeException(e);
        }
    }

    public static Object invokeFieldVal(Class<?> clazz, String name, Object object){
        Field field = findField(
                clazz,
                name
        );

        Object val = null;
        try {
            val = field.get(object);
            return val;
        } catch (IllegalAccessException e) {
            throw new RuntimeException(e);
        }
    }
}
