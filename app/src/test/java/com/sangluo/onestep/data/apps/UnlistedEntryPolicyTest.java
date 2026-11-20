package com.sangluo.onestep.data.apps;

import org.junit.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class UnlistedEntryPolicyTest {
    private static final String OWNER = "com.sangluo.onestepc16";
    private static final List<String> LAUNCHER_PACKAGES =
            Arrays.asList("com.android.chrome", "com.tencent.mm");

    @Test
    public void acceptsAnExportedEnabledPageWithoutALauncherIcon() {
        assertTrue(UnlistedEntryPolicy.isCandidate(true, true,
                "dev.codex.xiaobu.launcherhook", OWNER, LAUNCHER_PACKAGES));
    }

    @Test
    public void rejectsPagesTheSystemCannotStart() {
        assertFalse(UnlistedEntryPolicy.isCandidate(false, true,
                "dev.codex.xiaobu.launcherhook", OWNER, LAUNCHER_PACKAGES));
        assertFalse(UnlistedEntryPolicy.isCandidate(true, false,
                "dev.codex.xiaobu.launcherhook", OWNER, LAUNCHER_PACKAGES));
    }

    @Test
    public void rejectsPackagesTheCatalogAlreadyShows() {
        assertFalse(UnlistedEntryPolicy.isCandidate(true, true,
                "com.tencent.mm", OWNER, LAUNCHER_PACKAGES));
    }

    @Test
    public void rejectsOneStepItselfAndMissingPackages() {
        assertFalse(UnlistedEntryPolicy.isCandidate(true, true, OWNER, OWNER,
                LAUNCHER_PACKAGES));
        assertFalse(UnlistedEntryPolicy.isCandidate(true, true, "", OWNER,
                LAUNCHER_PACKAGES));
        assertFalse(UnlistedEntryPolicy.isCandidate(true, true, null, OWNER,
                LAUNCHER_PACKAGES));
    }

    @Test
    public void treatsAMissingLauncherCatalogAsEmpty() {
        assertTrue(UnlistedEntryPolicy.isCandidate(true, true,
                "dev.codex.xiaobu.launcherhook", OWNER, null));
        assertTrue(UnlistedEntryPolicy.isCandidate(true, true,
                "dev.codex.xiaobu.launcherhook", OWNER, Collections.emptyList()));
    }

    @Test
    public void prefersHigherPriorityThenDefaultThenStableClassName() {
        assertTrue(UnlistedEntryPolicy.prefer(10, false, "A", 0, true, "B"));
        assertTrue(UnlistedEntryPolicy.prefer(0, true, "Z", 0, false, "A"));
        assertTrue(UnlistedEntryPolicy.prefer(0, false, "A", 0, false, "B"));
        assertFalse(UnlistedEntryPolicy.prefer(0, false, "B", 0, false, "A"));
        assertFalse(UnlistedEntryPolicy.prefer(0, false, "A", 0, false, "A"));
        assertFalse(UnlistedEntryPolicy.prefer(0, false, null, 0, false, "A"));
        assertTrue(UnlistedEntryPolicy.prefer(0, false, "A", 0, false, null));
    }
}
