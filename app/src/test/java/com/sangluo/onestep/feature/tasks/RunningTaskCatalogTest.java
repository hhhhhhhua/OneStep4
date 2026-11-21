package com.sangluo.onestep.feature.tasks;

import org.junit.Test;

import java.util.Arrays;
import java.util.List;

import static org.junit.Assert.assertEquals;

public class RunningTaskCatalogTest {
    @Test
    public void keepsOnlyTasksWithLiveProcessesInDumpOrder() {
        String dump = "  Recent tasks:\n"
                + "  * Recent #0: Task{1 #10 type=standard A=10001:com.example.alpha}\n"
                + "    intent={cmp=com.example.alpha/.Main}\n"
                + "    mRootProcess=ProcessRecord{123 1001:com.example.alpha/u0a1}\n"
                + "    taskId=10\n"
                + "  * Recent #1: Task{2 #11 type=standard A=10002:com.example.beta}\n"
                + "    intent={cmp=com.example.beta/.Main}\n"
                + "    taskId=11\n"
                + "  * Recent #2: Task{3 #12 type=standard A=10003:com.example.gamma}\n"
                + "    mRootProcess=ProcessRecord{456 1002:com.example.gamma/u0a2}\n"
                + "    taskId=12\n";

        List<String> packages = RunningTaskCatalog.packagesFromRecentsDump(dump);

        assertEquals(Arrays.asList("com.example.alpha", "com.example.gamma"), packages);
    }

    @Test
    public void deduplicatesPackagesAndFallsBackToAffinity() {
        String dump = "  * Recent #0: Task{1 #10 type=standard}\n"
                + "    affinity=10001:com.example.alpha\n"
                + "    mRootProcess=ProcessRecord{123 1001:com.example.alpha/u0a1}\n"
                + "  * Recent #1: Task{2 #11 type=standard A=10001:com.example.alpha}\n"
                + "    mRootProcess=ProcessRecord{123 1001:com.example.alpha/u0a1}\n";

        List<String> packages = RunningTaskCatalog.packagesFromRecentsDump(dump);

        assertEquals(Arrays.asList("com.example.alpha"), packages);
    }

    @Test
    public void ignoresEmptyAndStoppedDumps() {
        assertEquals(0, RunningTaskCatalog.packagesFromRecentsDump("").size());
        assertEquals(0, RunningTaskCatalog.packagesFromRecentsDump(null).size());
        assertEquals(0, RunningTaskCatalog.packagesFromRecentsDump(
                "  * Recent #0: Task{1 #10 type=standard A=10001:com.example.alpha}\n")
                .size());
    }
}
