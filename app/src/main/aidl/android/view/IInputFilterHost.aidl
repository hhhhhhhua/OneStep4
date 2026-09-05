package android.view;

import android.view.InputEvent;

/** @hide */
oneway interface IInputFilterHost {
    void sendInputEvent(in InputEvent event, int policyFlags);
}