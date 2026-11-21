package com.sangluo.onestep.feature.tasks;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Extracts the packages whose recent tasks still own a live process.
 *
 * <p>{@code dumpsys activity recents} prints one block per recent task. A task whose
 * block contains {@code mRootProcess} is still running in the background; tasks without
 * it are merely remembered entries. The order of the dump (newest first) is preserved.</p>
 */
public final class RunningTaskCatalog {
    private static final String TASK_PREFIX = "* Recent #";
    private static final String ROOT_PROCESS = "mRootProcess=";
    private static final Pattern TASK_PACKAGE = Pattern.compile("A=\\d+:([^\\s}]+)");
    private static final Pattern AFFINITY_PACKAGE = Pattern.compile("affinity=\\d+:([^\\s}]+)");

    private RunningTaskCatalog() {
    }

    /** Running task packages, newest first, without duplicates. */
    public static List<String> packagesFromRecentsDump(String dump) {
        List<String> result = new ArrayList<>();
        if (dump == null || dump.isEmpty()) {
            return result;
        }
        Set<String> seen = new LinkedHashSet<>();
        String packageName = "";
        boolean hasRootProcess = false;
        for (String rawLine : dump.split("\n")) {
            String line = rawLine.trim();
            if (line.startsWith(TASK_PREFIX) && line.contains("Task{")) {
                addIfRunning(seen, packageName, hasRootProcess);
                packageName = extractPackage(line);
                hasRootProcess = false;
                continue;
            }
            if (line.startsWith(ROOT_PROCESS)) {
                hasRootProcess = true;
            } else if (packageName.isEmpty() && line.startsWith("affinity=")) {
                packageName = match(AFFINITY_PACKAGE, line);
            }
        }
        addIfRunning(seen, packageName, hasRootProcess);
        result.addAll(seen);
        return result;
    }

    private static void addIfRunning(Set<String> result, String packageName,
                                     boolean hasRootProcess) {
        if (hasRootProcess && packageName != null && !packageName.isEmpty()) {
            result.add(packageName);
        }
    }

    private static String extractPackage(String line) {
        String matched = match(TASK_PACKAGE, line);
        return matched.isEmpty() ? match(AFFINITY_PACKAGE, line) : matched;
    }

    private static String match(Pattern pattern, String line) {
        Matcher matcher = pattern.matcher(line);
        return matcher.find() ? matcher.group(1) : "";
    }
}
