package com.sangluo.onestep.data.apps;

import java.util.Collection;

/**
 * Decides which installed activities belong to the floating ball's secondary picker.
 *
 * <p>Some apps publish an entry point with an explicit {@code ACTION_MAIN} filter but never a
 * launcher icon: Xposed module settings pages, OEM feature screens, hidden maintenance pages.
 * {@code LauncherApps} therefore never reports them, so they cannot be picked through the
 * normal app catalog. These entries stay out of the desktop grid and the top app strip; they
 * are only offered next to the catalog when the user explicitly adds apps to the ball.</p>
 */
public final class UnlistedEntryPolicy {
    private UnlistedEntryPolicy() {
    }

    /**
     * True when a queried activity is a usable picker entry: publicly reachable, enabled, not
     * owned by OneStep itself, and not already covered by the package's launcher icon.
     */
    public static boolean isCandidate(boolean exported, boolean enabled,
                                      String packageName, String ownerPackage,
                                      Collection<String> launcherPackages) {
        if (!exported || !enabled || packageName == null || packageName.isEmpty()) {
            return false;
        }
        if (packageName.equals(ownerPackage)) {
            return false;
        }
        return launcherPackages == null || !launcherPackages.contains(packageName);
    }

    /**
     * True when the candidate is the better entry for its package. One row per package keeps
     * the picker readable; ties fall back to the class name so repeated scans stay stable.
     */
    public static boolean prefer(int candidatePriority, boolean candidateDefault,
                                 String candidateClass, int currentPriority,
                                 boolean currentDefault, String currentClass) {
        if (candidatePriority != currentPriority) {
            return candidatePriority > currentPriority;
        }
        if (candidateDefault != currentDefault) {
            return candidateDefault;
        }
        if (candidateClass == null) {
            return false;
        }
        return currentClass == null || candidateClass.compareTo(currentClass) < 0;
    }
}
