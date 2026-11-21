package com.sangluo.onestep.feature.audio;

import android.media.AudioDeviceInfo;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

public class AudioOutputTypesTest {
    @Test
    public void outputTypesUseTheShellCommandTokens() {
        assertEquals("BUILTIN_SPEAKER",
                AudioOutputTypes.commandName(AudioDeviceInfo.TYPE_BUILTIN_SPEAKER));
        assertEquals("BUILTIN_EARPIECE",
                AudioOutputTypes.commandName(AudioDeviceInfo.TYPE_BUILTIN_EARPIECE));
        assertEquals("BLUETOOTH_A2DP",
                AudioOutputTypes.commandName(AudioDeviceInfo.TYPE_BLUETOOTH_A2DP));
        assertEquals("USB_HEADSET",
                AudioOutputTypes.commandName(AudioDeviceInfo.TYPE_USB_HEADSET));
        assertEquals("HDMI",
                AudioOutputTypes.commandName(AudioDeviceInfo.TYPE_HDMI));
    }

    @Test
    public void unselectableTypesHaveNoCommandToken() {
        assertNull(AudioOutputTypes.commandName(AudioDeviceInfo.TYPE_TELEPHONY));
        assertNull(AudioOutputTypes.commandName(AudioDeviceInfo.TYPE_REMOTE_SUBMIX));
        assertNull(AudioOutputTypes.commandName(AudioDeviceInfo.TYPE_BUILTIN_MIC));
        assertNull(AudioOutputTypes.commandName(AudioDeviceInfo.TYPE_UNKNOWN));
    }

    @Test
    public void earpieceIsHiddenFromThePicker() {
        assertTrue(AudioOutputTypes.isHiddenOutput(AudioDeviceInfo.TYPE_BUILTIN_EARPIECE));
        assertFalse(AudioOutputTypes.isHiddenOutput(AudioDeviceInfo.TYPE_BUILTIN_SPEAKER));
        assertFalse(AudioOutputTypes.isHiddenOutput(AudioDeviceInfo.TYPE_BLUETOOTH_A2DP));
    }

    @Test
    public void labelsAreUserFacing() {
        assertEquals("扬声器", AudioOutputTypes.label(AudioDeviceInfo.TYPE_BUILTIN_SPEAKER));
        assertEquals("蓝牙音频", AudioOutputTypes.label(AudioDeviceInfo.TYPE_BLUETOOTH_A2DP));
        assertEquals("音频设备", AudioOutputTypes.label(AudioDeviceInfo.TYPE_UNKNOWN));
    }
}
