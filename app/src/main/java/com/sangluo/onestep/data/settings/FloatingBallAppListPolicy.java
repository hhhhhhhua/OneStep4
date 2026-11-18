package com.sangluo.onestep.data.settings;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Reconciles the floating ball's second layer (user-picked launcher apps) with the
 * live launcher list. Unlike the top app strip this list is explicit: nothing is
 * auto-selected, uninstalled entries are dropped, and the result never exceeds the
 * configured cap.
 */
public final class FloatingBallAppListPolicy {
    private FloatingBallAppListPolicy() {
    }

    /** Saved keys filtered against {@code availableKeys}, de-duplicated and capped. */
    public static List<String> reconcile(List<String> availableKeys, List<String> savedKeys,
                                         int maxCount) {
        int limit = Math.max(0, maxCount);
        if (limit == 0 || savedKeys == null || savedKeys.isEmpty()) {
            return Collections.emptyList();
        }
        Set<String> available = new HashSet<>();
        if (availableKeys != null) {
            for (String key : availableKeys) {
                if (key != null && !key.isEmpty()) {
                    available.add(key);
                }
            }
        }
        List<String> result = new ArrayList<>(Math.min(limit, savedKeys.size()));
        for (String key : savedKeys) {
            if (result.size() >= limit) {
                break;
            }
            if (key != null && !key.isEmpty() && available.contains(key)
                    && !result.contains(key)) {
                result.add(key);
            }
        }
        return Collections.unmodifiableList(result);
    }

    /** Adds or removes one key, refusing to grow past {@code maxCount}. */
    public static List<String> toggle(List<String> current, String key, int maxCount) {
        int limit = Math.max(0, maxCount);
        LinkedHashSet<String> keys = new LinkedHashSet<>();
        if (current != null) {
            for (String existing : current) {
                if (existing != null && !existing.isEmpty()) {
                    keys.add(existing);
                }
            }
        }
        if (key == null || key.isEmpty()) {
            return Collections.unmodifiableList(new ArrayList<>(keys));
        }
        if (keys.remove(key)) {
            return Collections.unmodifiableList(new ArrayList<>(keys));
        }
        if (keys.size() >= limit) {
            return Collections.unmodifiableList(new ArrayList<>(keys));
        }
        keys.add(key);
        return Collections.unmodifiableList(new ArrayList<>(keys));
    }

    public static boolean canAdd(List<String> current, String key, int maxCount) {
        if (key == null || key.isEmpty()) {
            return false;
        }
        if (current != null && current.contains(key)) {
            return true;
        }
        int size = current == null ? 0 : current.size();
        return size < Math.max(0, maxCount);
    }

    /** Reorders {@code orderedKeys} so it starts with {@code ordered} (drag-sorted keys). */
    public static List<String> applyOrder(List<String> ordered, List<String> fallback) {
        LinkedHashSet<String> keys = new LinkedHashSet<>();
        if (ordered != null) {
            for (String key : ordered) {
                if (key != null && !key.isEmpty()) {
                    keys.add(key);
                }
            }
        }
        if (fallback != null) {
            for (String key : fallback) {
                if (key != null && !key.isEmpty()) {
                    keys.add(key);
                }
            }
        }
        return Collections.unmodifiableList(new ArrayList<>(keys));
    }
}
