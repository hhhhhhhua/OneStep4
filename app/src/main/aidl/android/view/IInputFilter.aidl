package android.view;

import android.view.IInputFilterHost;
import android.view.InputEvent;

/** @hide */
oneway interface IInputFilter {
    void install(in IInputFilterHost host);
    void uninstall();
    void filterInputEvent(in InputEvent event, int policyFlags);
}