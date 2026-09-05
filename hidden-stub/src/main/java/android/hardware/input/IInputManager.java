//
// Decompiled by Jadx - 887ms
//
package android.hardware.input;

import android.hardware.lights.Light;
import android.hardware.lights.LightState;
import android.os.BadParcelableException;
import android.os.Binder;
import android.os.CombinedVibration;
import android.os.IBinder;
import android.os.IInterface;
import android.os.Parcel;
import android.os.RemoteException;
import android.os.VibrationEffect;
import android.view.InputDevice;
import android.view.InputEvent;
import android.view.InputMonitor;
import android.view.KeyCharacterMap;
import android.view.PointerIcon;
import android.view.VerifiedInputEvent;
import android.view.inputmethod.InputMethodInfo;
import android.view.inputmethod.InputMethodSubtype;

import java.util.Collections;
import java.util.List;
import java.util.Map;

public interface IInputManager extends IInterface {

    void addPortAssociation(String str, int i) throws RemoteException;

    void addUniqueIdAssociationByDescriptor(String str, String str2) throws RemoteException;

    void addUniqueIdAssociationByPort(String str, String str2) throws RemoteException;

    void cancelCurrentTouch() throws RemoteException;

    void cancelVibrate(int i, IBinder iBinder) throws RemoteException;

    void clearAllModifierKeyRemappings() throws RemoteException;

    void closeLightSession(int i, IBinder iBinder) throws RemoteException;

    void disableInputDevice(int i) throws RemoteException;

    void disableSensor(int i, int i2) throws RemoteException;

    void enableInputDevice(int i) throws RemoteException;

    boolean enableSensor(int i, int i2, int i3, int i4) throws RemoteException;

    boolean flushSensor(int i, int i2) throws RemoteException;

    HostUsiVersion getHostUsiVersionFromDisplayConfig(int i) throws RemoteException;

    InputDevice getInputDevice(int i) throws RemoteException;

    String getInputDeviceBluetoothAddress(int i) throws RemoteException;

    int[] getInputDeviceIds() throws RemoteException;

    KeyCharacterMap getKeyCharacterMap(String str) throws RemoteException;

    int getKeyCodeForKeyLocation(int i, int i2) throws RemoteException;

    LightState getLightState(int i, int i2) throws RemoteException;

    List<Light> getLights(int i) throws RemoteException;

    Map getModifierKeyRemapping() throws RemoteException;

    int getMousePointerSpeed() throws RemoteException;

    String getVelocityTrackerStrategy() throws RemoteException;

    int[] getVibratorIds(int i) throws RemoteException;

    boolean hasKeys(int i, int i2, int[] iArr, boolean[] zArr) throws RemoteException;

    boolean injectInputEvent(InputEvent inputEvent, int i) throws RemoteException;

    boolean injectInputEventToTarget(InputEvent inputEvent, int i, int i2) throws RemoteException;

    int isInTabletMode() throws RemoteException;

    int isMicMuted() throws RemoteException;

    boolean isVibrating(int i) throws RemoteException;

    InputMonitor monitorGestureInput(IBinder iBinder, String str, int i) throws RemoteException;

    void openLightSession(int i, String str, IBinder iBinder) throws RemoteException;

    void pilferPointers(IBinder iBinder) throws RemoteException;

    void remapModifierKey(int i, int i2) throws RemoteException;

    void removeAllCustomInputGestures(int i, int i2) throws RemoteException;

    void removePortAssociation(String str) throws RemoteException;

    void removeUniqueIdAssociationByDescriptor(String str) throws RemoteException;

    void removeUniqueIdAssociationByPort(String str) throws RemoteException;

    void requestPointerCapture(IBinder iBinder, boolean z) throws RemoteException;

    void resetLockedModifierState() throws RemoteException;

    void setLightStates(int i, int[] iArr, LightState[] lightStateArr, IBinder iBinder) throws RemoteException;

    boolean setPointerIcon(PointerIcon pointerIcon, int i, int i2, int i3, IBinder iBinder) throws RemoteException;

    void tryPointerSpeed(int i) throws RemoteException;

    VerifiedInputEvent verifyInputEvent(InputEvent inputEvent) throws RemoteException;

    void vibrate(int i, VibrationEffect vibrationEffect, IBinder iBinder) throws RemoteException;

    void vibrateCombined(int i, CombinedVibration combinedVibration, IBinder iBinder) throws RemoteException;

    public static class Proxy implements IInputManager {

        public Proxy(IBinder obj) {

        }

        @Override
        public void addPortAssociation(String str, int i) throws RemoteException {

        }

        @Override
        public void addUniqueIdAssociationByDescriptor(String str, String str2) throws RemoteException {

        }

        @Override
        public void addUniqueIdAssociationByPort(String str, String str2) throws RemoteException {

        }

        @Override
        public void cancelCurrentTouch() throws RemoteException {

        }

        @Override
        public void cancelVibrate(int i, IBinder iBinder) throws RemoteException {

        }

        @Override
        public void clearAllModifierKeyRemappings() throws RemoteException {

        }

        @Override
        public void closeLightSession(int i, IBinder iBinder) throws RemoteException {

        }

        @Override
        public void disableInputDevice(int i) throws RemoteException {

        }

        @Override
        public void disableSensor(int i, int i2) throws RemoteException {

        }

        @Override
        public void enableInputDevice(int i) throws RemoteException {

        }

        @Override
        public boolean enableSensor(int i, int i2, int i3, int i4) throws RemoteException {
            return false;
        }

        @Override
        public boolean flushSensor(int i, int i2) throws RemoteException {
            return false;
        }

        @Override
        public HostUsiVersion getHostUsiVersionFromDisplayConfig(int i) throws RemoteException {
            return null;
        }

        @Override
        public InputDevice getInputDevice(int i) throws RemoteException {
            return null;
        }

        @Override
        public String getInputDeviceBluetoothAddress(int i) throws RemoteException {
            return "";
        }

        @Override
        public int[] getInputDeviceIds() throws RemoteException {
            return new int[0];
        }

        @Override
        public KeyCharacterMap getKeyCharacterMap(String str) throws RemoteException {
            return null;
        }

        @Override
        public int getKeyCodeForKeyLocation(int i, int i2) throws RemoteException {
            return 0;
        }

        @Override
        public LightState getLightState(int i, int i2) throws RemoteException {
            return null;
        }

        @Override
        public List<Light> getLights(int i) throws RemoteException {
            return Collections.emptyList();
        }

        @Override
        public Map getModifierKeyRemapping() throws RemoteException {
            return Collections.emptyMap();
        }

        @Override
        public int getMousePointerSpeed() throws RemoteException {
            return 0;
        }

        @Override
        public String getVelocityTrackerStrategy() throws RemoteException {
            return "";
        }

        @Override
        public int[] getVibratorIds(int i) throws RemoteException {
            return new int[0];
        }

        @Override
        public boolean hasKeys(int i, int i2, int[] iArr, boolean[] zArr) throws RemoteException {
            return false;
        }

        @Override
        public boolean injectInputEvent(InputEvent inputEvent, int i) throws RemoteException {
            return false;
        }

        @Override
        public boolean injectInputEventToTarget(InputEvent inputEvent, int i, int i2) throws RemoteException {
            return false;
        }

        @Override
        public int isInTabletMode() throws RemoteException {
            return 0;
        }

        @Override
        public int isMicMuted() throws RemoteException {
            return 0;
        }

        @Override
        public boolean isVibrating(int i) throws RemoteException {
            return false;
        }

        @Override
        public InputMonitor monitorGestureInput(IBinder iBinder, String str, int i) throws RemoteException {
            return null;
        }

        @Override
        public void openLightSession(int i, String str, IBinder iBinder) throws RemoteException {

        }

        @Override
        public void pilferPointers(IBinder iBinder) throws RemoteException {

        }

        @Override
        public void remapModifierKey(int i, int i2) throws RemoteException {

        }

        @Override
        public void removeAllCustomInputGestures(int i, int i2) throws RemoteException {

        }

        @Override
        public void removePortAssociation(String str) throws RemoteException {

        }

        @Override
        public void removeUniqueIdAssociationByDescriptor(String str) throws RemoteException {

        }

        @Override
        public void removeUniqueIdAssociationByPort(String str) throws RemoteException {

        }

        @Override
        public void requestPointerCapture(IBinder iBinder, boolean z) throws RemoteException {

        }

        @Override
        public void resetLockedModifierState() throws RemoteException {

        }

        @Override
        public void setLightStates(int i, int[] iArr, LightState[] lightStateArr, IBinder iBinder) throws RemoteException {

        }

        @Override
        public boolean setPointerIcon(PointerIcon pointerIcon, int i, int i2, int i3, IBinder iBinder) throws RemoteException {
            return false;
        }

        @Override
        public void tryPointerSpeed(int i) throws RemoteException {

        }

        @Override
        public VerifiedInputEvent verifyInputEvent(InputEvent inputEvent) throws RemoteException {
            return null;
        }

        @Override
        public void vibrate(int i, VibrationEffect vibrationEffect, IBinder iBinder) throws RemoteException {

        }

        @Override
        public void vibrateCombined(int i, CombinedVibration combinedVibration, IBinder iBinder) throws RemoteException {

        }

        @Override
        public IBinder asBinder() {
            return null;
        }
    }

    public static abstract class Stub extends Binder implements IInputManager {
        public static final String DESCRIPTOR = "android.hardware.input.IInputManager";
        static final int TRANSACTION_addCustomInputGesture = 75;
        static final int TRANSACTION_addPortAssociation = 40;
        static final int TRANSACTION_addUniqueIdAssociationByDescriptor = 42;
        static final int TRANSACTION_addUniqueIdAssociationByPort = 44;
        static final int TRANSACTION_cancelCurrentTouch = 57;
        static final int TRANSACTION_cancelVibrate = 31;
        static final int TRANSACTION_clearAllModifierKeyRemappings = 23;
        static final int TRANSACTION_closeLightSession = 56;
        static final int TRANSACTION_disableInputDevice = 5;
        static final int TRANSACTION_disableSensor = 50;
        static final int TRANSACTION_enableInputDevice = 4;
        static final int TRANSACTION_enableSensor = 49;
        static final int TRANSACTION_flushSensor = 51;
        static final int TRANSACTION_getAppLaunchBookmarks = 79;
        static final int TRANSACTION_getBatteryState = 36;
        static final int TRANSACTION_getCustomInputGestures = 78;
        static final int TRANSACTION_getHostUsiVersionFromDisplayConfig = 66;
        static final int TRANSACTION_getInputDevice = 2;
        static final int TRANSACTION_getInputDeviceBluetoothAddress = 62;
        static final int TRANSACTION_getInputDeviceIds = 3;
        static final int TRANSACTION_getInputGesture = 74;
        static final int TRANSACTION_getKeyCharacterMap = 8;
        static final int TRANSACTION_getKeyCodeForKeyLocation = 7;
        static final int TRANSACTION_getKeyGlyphMap = 69;
        static final int TRANSACTION_getKeyboardLayout = 17;
        static final int TRANSACTION_getKeyboardLayoutForInputDevice = 18;
        static final int TRANSACTION_getKeyboardLayoutListForInputDevice = 21;
        static final int TRANSACTION_getKeyboardLayouts = 16;
        static final int TRANSACTION_getLightState = 53;
        static final int TRANSACTION_getLights = 52;
        static final int TRANSACTION_getModifierKeyRemapping = 24;
        static final int TRANSACTION_getMousePointerSpeed = 9;
        static final int TRANSACTION_getSensorList = 46;
        static final int TRANSACTION_getTouchCalibrationForInputDevice = 14;
        static final int TRANSACTION_getVelocityTrackerStrategy = 1;
        static final int TRANSACTION_getVibratorIds = 32;
        static final int TRANSACTION_hasKeys = 6;
        static final int TRANSACTION_injectInputEvent = 11;
        static final int TRANSACTION_injectInputEventToTarget = 12;
        static final int TRANSACTION_isInTabletMode = 26;
        static final int TRANSACTION_isMicMuted = 28;
        static final int TRANSACTION_isVibrating = 33;
        static final int TRANSACTION_monitorGestureInput = 39;
        static final int TRANSACTION_openLightSession = 55;
        static final int TRANSACTION_pilferPointers = 63;
        static final int TRANSACTION_registerBatteryListener = 58;
        static final int TRANSACTION_registerInputDevicesChangedListener = 25;
        static final int TRANSACTION_registerKeyEventActivityListener = 60;
        static final int TRANSACTION_registerKeyGestureEventListener = 70;
        static final int TRANSACTION_registerKeyGestureHandler = 72;
        static final int TRANSACTION_registerKeyboardBacklightListener = 64;
        static final int TRANSACTION_registerSensorListener = 47;
        static final int TRANSACTION_registerStickyModifierStateListener = 67;
        static final int TRANSACTION_registerTabletModeChangedListener = 27;
        static final int TRANSACTION_registerVibratorStateListener = 34;
        static final int TRANSACTION_remapModifierKey = 22;
        static final int TRANSACTION_removeAllCustomInputGestures = 77;
        static final int TRANSACTION_removeCustomInputGesture = 76;
        static final int TRANSACTION_removePortAssociation = 41;
        static final int TRANSACTION_removeUniqueIdAssociationByDescriptor = 43;
        static final int TRANSACTION_removeUniqueIdAssociationByPort = 45;
        static final int TRANSACTION_requestPointerCapture = 38;
        static final int TRANSACTION_resetLockedModifierState = 80;
        static final int TRANSACTION_setKeyboardLayoutForInputDevice = 20;
        static final int TRANSACTION_setKeyboardLayoutOverrideForInputDevice = 19;
        static final int TRANSACTION_setLightStates = 54;
        static final int TRANSACTION_setPointerIcon = 37;
        static final int TRANSACTION_setTouchCalibrationForInputDevice = 15;
        static final int TRANSACTION_tryPointerSpeed = 10;
        static final int TRANSACTION_unregisterBatteryListener = 59;
        static final int TRANSACTION_unregisterKeyEventActivityListener = 61;
        static final int TRANSACTION_unregisterKeyGestureEventListener = 71;
        static final int TRANSACTION_unregisterKeyGestureHandler = 73;
        static final int TRANSACTION_unregisterKeyboardBacklightListener = 65;
        static final int TRANSACTION_unregisterSensorListener = 48;
        static final int TRANSACTION_unregisterStickyModifierStateListener = 68;
        static final int TRANSACTION_unregisterVibratorStateListener = 35;
        static final int TRANSACTION_verifyInputEvent = 13;
        static final int TRANSACTION_vibrate = 29;
        static final int TRANSACTION_vibrateCombined = 30;

        public static IInputManager asInterface(IBinder obj) {
            if (obj == null) {
                return null;
            }
            IInterface iin = obj.queryLocalInterface(DESCRIPTOR);
            if (iin != null && (iin instanceof IInputManager)) {
                return (IInputManager) iin;
            }
            return new Proxy(obj);
        }

        @Override
        public IBinder asBinder() {
            return this;
        }

        public static String getDefaultTransactionName(int transactionCode) {
            switch (transactionCode) {
                case TRANSACTION_getVelocityTrackerStrategy:
                    return "getVelocityTrackerStrategy";
                case TRANSACTION_getInputDevice:
                    return "getInputDevice";
                case TRANSACTION_getInputDeviceIds:
                    return "getInputDeviceIds";
                case TRANSACTION_enableInputDevice:
                    return "enableInputDevice";
                case TRANSACTION_disableInputDevice:
                    return "disableInputDevice";
                case TRANSACTION_hasKeys:
                    return "hasKeys";
                case TRANSACTION_getKeyCodeForKeyLocation:
                    return "getKeyCodeForKeyLocation";
                case TRANSACTION_getKeyCharacterMap:
                    return "getKeyCharacterMap";
                case TRANSACTION_getMousePointerSpeed:
                    return "getMousePointerSpeed";
                case TRANSACTION_tryPointerSpeed:
                    return "tryPointerSpeed";
                case TRANSACTION_injectInputEvent:
                    return "injectInputEvent";
                case TRANSACTION_injectInputEventToTarget:
                    return "injectInputEventToTarget";
                case TRANSACTION_verifyInputEvent:
                    return "verifyInputEvent";
                case TRANSACTION_getTouchCalibrationForInputDevice:
                    return "getTouchCalibrationForInputDevice";
                case TRANSACTION_setTouchCalibrationForInputDevice:
                    return "setTouchCalibrationForInputDevice";
                case TRANSACTION_getKeyboardLayouts:
                    return "getKeyboardLayouts";
                case TRANSACTION_getKeyboardLayout:
                    return "getKeyboardLayout";
                case TRANSACTION_getKeyboardLayoutForInputDevice:
                    return "getKeyboardLayoutForInputDevice";
                case TRANSACTION_setKeyboardLayoutOverrideForInputDevice:
                    return "setKeyboardLayoutOverrideForInputDevice";
                case TRANSACTION_setKeyboardLayoutForInputDevice:
                    return "setKeyboardLayoutForInputDevice";
                case TRANSACTION_getKeyboardLayoutListForInputDevice:
                    return "getKeyboardLayoutListForInputDevice";
                case TRANSACTION_remapModifierKey:
                    return "remapModifierKey";
                case TRANSACTION_clearAllModifierKeyRemappings:
                    return "clearAllModifierKeyRemappings";
                case TRANSACTION_getModifierKeyRemapping:
                    return "getModifierKeyRemapping";
                case TRANSACTION_registerInputDevicesChangedListener:
                    return "registerInputDevicesChangedListener";
                case TRANSACTION_isInTabletMode:
                    return "isInTabletMode";
                case TRANSACTION_registerTabletModeChangedListener:
                    return "registerTabletModeChangedListener";
                case TRANSACTION_isMicMuted:
                    return "isMicMuted";
                case TRANSACTION_vibrate:
                    return "vibrate";
                case TRANSACTION_vibrateCombined:
                    return "vibrateCombined";
                case TRANSACTION_cancelVibrate:
                    return "cancelVibrate";
                case TRANSACTION_getVibratorIds:
                    return "getVibratorIds";
                case TRANSACTION_isVibrating:
                    return "isVibrating";
                case TRANSACTION_registerVibratorStateListener:
                    return "registerVibratorStateListener";
                case TRANSACTION_unregisterVibratorStateListener:
                    return "unregisterVibratorStateListener";
                case TRANSACTION_getBatteryState:
                    return "getBatteryState";
                case TRANSACTION_setPointerIcon:
                    return "setPointerIcon";
                case TRANSACTION_requestPointerCapture:
                    return "requestPointerCapture";
                case TRANSACTION_monitorGestureInput:
                    return "monitorGestureInput";
                case TRANSACTION_addPortAssociation:
                    return "addPortAssociation";
                case TRANSACTION_removePortAssociation:
                    return "removePortAssociation";
                case TRANSACTION_addUniqueIdAssociationByDescriptor:
                    return "addUniqueIdAssociationByDescriptor";
                case TRANSACTION_removeUniqueIdAssociationByDescriptor:
                    return "removeUniqueIdAssociationByDescriptor";
                case TRANSACTION_addUniqueIdAssociationByPort:
                    return "addUniqueIdAssociationByPort";
                case TRANSACTION_removeUniqueIdAssociationByPort:
                    return "removeUniqueIdAssociationByPort";
                case TRANSACTION_getSensorList:
                    return "getSensorList";
                case TRANSACTION_registerSensorListener:
                    return "registerSensorListener";
                case TRANSACTION_unregisterSensorListener:
                    return "unregisterSensorListener";
                case TRANSACTION_enableSensor:
                    return "enableSensor";
                case TRANSACTION_disableSensor:
                    return "disableSensor";
                case TRANSACTION_flushSensor:
                    return "flushSensor";
                case TRANSACTION_getLights:
                    return "getLights";
                case TRANSACTION_getLightState:
                    return "getLightState";
                case TRANSACTION_setLightStates:
                    return "setLightStates";
                case TRANSACTION_openLightSession:
                    return "openLightSession";
                case TRANSACTION_closeLightSession:
                    return "closeLightSession";
                case TRANSACTION_cancelCurrentTouch:
                    return "cancelCurrentTouch";
                case TRANSACTION_registerBatteryListener:
                    return "registerBatteryListener";
                case TRANSACTION_unregisterBatteryListener:
                    return "unregisterBatteryListener";
                case TRANSACTION_registerKeyEventActivityListener:
                    return "registerKeyEventActivityListener";
                case TRANSACTION_unregisterKeyEventActivityListener:
                    return "unregisterKeyEventActivityListener";
                case TRANSACTION_getInputDeviceBluetoothAddress:
                    return "getInputDeviceBluetoothAddress";
                case TRANSACTION_pilferPointers:
                    return "pilferPointers";
                case TRANSACTION_registerKeyboardBacklightListener:
                    return "registerKeyboardBacklightListener";
                case TRANSACTION_unregisterKeyboardBacklightListener:
                    return "unregisterKeyboardBacklightListener";
                case TRANSACTION_getHostUsiVersionFromDisplayConfig:
                    return "getHostUsiVersionFromDisplayConfig";
                case TRANSACTION_registerStickyModifierStateListener:
                    return "registerStickyModifierStateListener";
                case TRANSACTION_unregisterStickyModifierStateListener:
                    return "unregisterStickyModifierStateListener";
                case TRANSACTION_getKeyGlyphMap:
                    return "getKeyGlyphMap";
                case TRANSACTION_registerKeyGestureEventListener:
                    return "registerKeyGestureEventListener";
                case TRANSACTION_unregisterKeyGestureEventListener:
                    return "unregisterKeyGestureEventListener";
                case TRANSACTION_registerKeyGestureHandler:
                    return "registerKeyGestureHandler";
                case TRANSACTION_unregisterKeyGestureHandler:
                    return "unregisterKeyGestureHandler";
                case TRANSACTION_getInputGesture:
                    return "getInputGesture";
                case TRANSACTION_addCustomInputGesture:
                    return "addCustomInputGesture";
                case TRANSACTION_removeCustomInputGesture:
                    return "removeCustomInputGesture";
                case TRANSACTION_removeAllCustomInputGestures:
                    return "removeAllCustomInputGestures";
                case TRANSACTION_getCustomInputGestures:
                    return "getCustomInputGestures";
                case TRANSACTION_getAppLaunchBookmarks:
                    return "getAppLaunchBookmarks";
                case TRANSACTION_resetLockedModifierState:
                    return "resetLockedModifierState";
                default:
                    return null;
            }
        }

        public String getTransactionName(int transactionCode) {
            return getDefaultTransactionName(transactionCode);
        }

    }
}
