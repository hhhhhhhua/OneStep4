package com.sangluo.onestep.ui.floatingball;

import com.sangluo.onestep.model.LauncherApp;

/** One entry of the floating ball's single ring. */
public final class FloatingBallItem {
    public enum Kind {
        /** Layer switch control, always at ring index 0. */
        SWITCH,
        /** App already hosted in one of the OneStep slots. */
        OPENED_APP,
        /** User-picked app from the second layer. */
        PINNED_APP,
        /** Opens the app picker. */
        ADD
    }

    public final Kind kind;
    public final LauncherApp app;
    /** Slot holding {@link #app}; only meaningful for {@link Kind#OPENED_APP}. */
    public final int slot;
    /** True for the slot that currently owns the main pane focus. */
    public final boolean activeMain;
    /** True when a pinned app is already open in some slot. */
    public final boolean alreadyOpen;

    private FloatingBallItem(Kind kind, LauncherApp app, int slot,
                             boolean activeMain, boolean alreadyOpen) {
        this.kind = kind;
        this.app = app;
        this.slot = slot;
        this.activeMain = activeMain;
        this.alreadyOpen = alreadyOpen;
    }

    public static FloatingBallItem layerSwitch() {
        return new FloatingBallItem(Kind.SWITCH, null, -1, false, false);
    }

    public static FloatingBallItem openedApp(LauncherApp app, int slot, boolean activeMain) {
        return new FloatingBallItem(Kind.OPENED_APP, app, slot, activeMain, true);
    }

    public static FloatingBallItem pinnedApp(LauncherApp app, boolean alreadyOpen) {
        return new FloatingBallItem(Kind.PINNED_APP, app, -1, false, alreadyOpen);
    }

    public static FloatingBallItem addButton() {
        return new FloatingBallItem(Kind.ADD, null, -1, false, false);
    }

    public String instanceKey() {
        return app == null ? "" : app.instanceKey();
    }

    public String label() {
        return app == null ? "" : app.label;
    }
}
