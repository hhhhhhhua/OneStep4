package com.sangluo.onestep.feature.audio;

import android.media.AudioDeviceInfo;

/**
 * Maps {@link AudioDeviceInfo} output types to the names accepted by
 * {@code cmd audio set-preferred-output-device} and to user-facing labels.
 */
public final class AudioOutputTypes {
    private AudioOutputTypes() {
    }

    /**
     * Type token understood by the privileged audio shell command, or {@code null} for
     * types that cannot be selected (telephony, remote submix, inputs, unknowns).
     */
    public static String commandName(int type) {
        switch (type) {
            case AudioDeviceInfo.TYPE_BUILTIN_EARPIECE:
                return "BUILTIN_EARPIECE";
            case AudioDeviceInfo.TYPE_BUILTIN_SPEAKER:
                return "BUILTIN_SPEAKER";
            case AudioDeviceInfo.TYPE_BUILTIN_SPEAKER_SAFE:
                return "BUILTIN_SPEAKER_SAFE";
            case AudioDeviceInfo.TYPE_WIRED_HEADSET:
                return "WIRED_HEADSET";
            case AudioDeviceInfo.TYPE_WIRED_HEADPHONES:
                return "WIRED_HEADPHONES";
            case AudioDeviceInfo.TYPE_LINE_ANALOG:
                return "LINE_ANALOG";
            case AudioDeviceInfo.TYPE_LINE_DIGITAL:
                return "LINE_DIGITAL";
            case AudioDeviceInfo.TYPE_AUX_LINE:
                return "AUX_LINE";
            case AudioDeviceInfo.TYPE_BLUETOOTH_SCO:
                return "BLUETOOTH_SCO";
            case AudioDeviceInfo.TYPE_BLUETOOTH_A2DP:
                return "BLUETOOTH_A2DP";
            case AudioDeviceInfo.TYPE_HDMI:
                return "HDMI";
            case AudioDeviceInfo.TYPE_HDMI_ARC:
                return "HDMI_ARC";
            case AudioDeviceInfo.TYPE_HDMI_EARC:
                return "HDMI_EARC";
            case AudioDeviceInfo.TYPE_USB_DEVICE:
                return "USB_DEVICE";
            case AudioDeviceInfo.TYPE_USB_ACCESSORY:
                return "USB_ACCESSORY";
            case AudioDeviceInfo.TYPE_USB_HEADSET:
                return "USB_HEADSET";
            case AudioDeviceInfo.TYPE_DOCK:
                return "DOCK";
            case AudioDeviceInfo.TYPE_DOCK_ANALOG:
                return "DOCK_ANALOG";
            case AudioDeviceInfo.TYPE_HEARING_AID:
                return "HEARING_AID";
            case AudioDeviceInfo.TYPE_IP:
                return "IP";
            case AudioDeviceInfo.TYPE_BLE_HEADSET:
                return "BLE_HEADSET";
            case AudioDeviceInfo.TYPE_BLE_SPEAKER:
                return "BLE_SPEAKER";
            case AudioDeviceInfo.TYPE_BLE_BROADCAST:
                return "BLE_BROADCAST";
            default:
                return null;
        }
    }

    /** Outputs that exist but should not appear in the picker (the earpiece). */
    public static boolean isHiddenOutput(int type) {
        return type == AudioDeviceInfo.TYPE_BUILTIN_EARPIECE;
    }

    /** Short Chinese label shown next to the device product name. */
    public static String label(int type) {
        switch (type) {
            case AudioDeviceInfo.TYPE_BUILTIN_EARPIECE:
                return "听筒";
            case AudioDeviceInfo.TYPE_BUILTIN_SPEAKER:
                return "扬声器";
            case AudioDeviceInfo.TYPE_BUILTIN_SPEAKER_SAFE:
                return "扬声器（安全音量）";
            case AudioDeviceInfo.TYPE_WIRED_HEADSET:
                return "有线耳机（带麦）";
            case AudioDeviceInfo.TYPE_WIRED_HEADPHONES:
                return "有线耳机";
            case AudioDeviceInfo.TYPE_LINE_ANALOG:
                return "模拟音频输出";
            case AudioDeviceInfo.TYPE_LINE_DIGITAL:
                return "数字音频输出";
            case AudioDeviceInfo.TYPE_AUX_LINE:
                return "AUX 音频输出";
            case AudioDeviceInfo.TYPE_BLUETOOTH_SCO:
                return "蓝牙通话音频";
            case AudioDeviceInfo.TYPE_BLUETOOTH_A2DP:
                return "蓝牙音频";
            case AudioDeviceInfo.TYPE_HDMI:
                return "HDMI";
            case AudioDeviceInfo.TYPE_HDMI_ARC:
                return "HDMI ARC";
            case AudioDeviceInfo.TYPE_HDMI_EARC:
                return "HDMI eARC";
            case AudioDeviceInfo.TYPE_USB_DEVICE:
                return "USB 音频设备";
            case AudioDeviceInfo.TYPE_USB_ACCESSORY:
                return "USB 音频配件";
            case AudioDeviceInfo.TYPE_USB_HEADSET:
                return "USB 耳机";
            case AudioDeviceInfo.TYPE_DOCK:
                return "音频底座";
            case AudioDeviceInfo.TYPE_DOCK_ANALOG:
                return "模拟音频底座";
            case AudioDeviceInfo.TYPE_HEARING_AID:
                return "助听器";
            case AudioDeviceInfo.TYPE_IP:
                return "网络音频";
            case AudioDeviceInfo.TYPE_BLE_HEADSET:
                return "蓝牙 LE 耳机";
            case AudioDeviceInfo.TYPE_BLE_SPEAKER:
                return "蓝牙 LE 音箱";
            case AudioDeviceInfo.TYPE_BLE_BROADCAST:
                return "蓝牙 LE 广播";
            default:
                return "音频设备";
        }
    }
}
