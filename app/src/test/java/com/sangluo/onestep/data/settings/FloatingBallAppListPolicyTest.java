package com.sangluo.onestep.data.settings;

import org.junit.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class FloatingBallAppListPolicyTest {
    private static final List<String> AVAILABLE = Arrays.asList("a", "b", "c", "d", "e");

    @Test
    public void keepsSavedOrderAndDropsUninstalledApps() {
        List<String> result = FloatingBallAppListPolicy.reconcile(
                AVAILABLE, Arrays.asList("c", "gone", "a"), 8);

        assertEquals(Arrays.asList("c", "a"), result);
    }

    @Test
    public void removesDuplicatesAndCapsTheList() {
        List<String> result = FloatingBallAppListPolicy.reconcile(
                AVAILABLE, Arrays.asList("a", "a", "b", "c", "d", "e"), 3);

        assertEquals(Arrays.asList("a", "b", "c"), result);
    }

    @Test
    public void neverAutoSelectsAppsWhenNothingWasConfigured() {
        assertEquals(Collections.emptyList(),
                FloatingBallAppListPolicy.reconcile(AVAILABLE, Collections.emptyList(), 8));
        assertEquals(Collections.emptyList(),
                FloatingBallAppListPolicy.reconcile(AVAILABLE, null, 8));
        assertEquals(Collections.emptyList(),
                FloatingBallAppListPolicy.reconcile(AVAILABLE, Arrays.asList("a"), 0));
    }

    @Test
    public void toggleAddsAndRemovesOneKey() {
        List<String> added = FloatingBallAppListPolicy.toggle(Arrays.asList("a"), "b", 8);
        assertEquals(Arrays.asList("a", "b"), added);

        List<String> removed = FloatingBallAppListPolicy.toggle(added, "a", 8);
        assertEquals(Collections.singletonList("b"), removed);
    }

    @Test
    public void toggleRefusesToGrowPastTheCap() {
        List<String> full = Arrays.asList("a", "b");
        List<String> result = FloatingBallAppListPolicy.toggle(full, "c", 2);

        assertEquals(full, result);
    }

    @Test
    public void canAddReportsCapAndDuplicateState() {
        assertTrue(FloatingBallAppListPolicy.canAdd(Arrays.asList("a"), "b", 2));
        assertTrue(FloatingBallAppListPolicy.canAdd(Arrays.asList("a"), "a", 1));
        assertFalse(FloatingBallAppListPolicy.canAdd(Arrays.asList("a", "b"), "c", 2));
        assertFalse(FloatingBallAppListPolicy.canAdd(Arrays.asList("a"), "", 2));
    }

    @Test
    public void applyOrderPutsDraggedKeysFirstWithoutLosingOthers() {
        List<String> result = FloatingBallAppListPolicy.applyOrder(
                Arrays.asList("c", "a"), Arrays.asList("a", "b", "c"));

        assertEquals(Arrays.asList("c", "a", "b"), result);
    }
}
