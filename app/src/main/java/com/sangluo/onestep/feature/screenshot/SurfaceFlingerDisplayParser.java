package com.sangluo.onestep.feature.screenshot;

/**
 * Maps a logical display name to the SurfaceFlinger display token accepted by
 * {@code screencap -d}. Tokens are the numeric ids printed by
 * {@code dumpsys SurfaceFlinger --display-id}, not the logical display ids.
 */
public final class SurfaceFlingerDisplayParser {
    private SurfaceFlingerDisplayParser() {
    }

    /** SurfaceFlinger display token for {@code displayName}, or null when absent. */
    public static String tokenFor(String dump, String displayName) {
        if (dump == null || displayName == null || displayName.isEmpty()) {
            return null;
        }
        String needle = "displayName=\"" + displayName + "\"";
        String[] lines = dump.split("\n");
        for (String rawLine : lines) {
            String line = rawLine.trim();
            if (!line.startsWith("Display ") || !line.contains(needle)) {
                continue;
            }
            String[] parts = line.split("\\s+");
            if (parts.length >= 2 && !parts[1].isEmpty()) {
                return parts[1];
            }
        }
        return null;
    }
}
