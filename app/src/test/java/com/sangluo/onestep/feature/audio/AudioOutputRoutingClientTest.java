package com.sangluo.onestep.feature.audio;

import com.sangluo.onestep.system.root.ShellCommandResult;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class AudioOutputRoutingClientTest {
    @Test
    public void preferredCommandCarriesTypeAndQuotedAddress() {
        assertEquals("cmd audio set-preferred-output-device BLUETOOTH_A2DP 'AA:BB:CC:DD:EE:FF'",
                AudioOutputRoutingClient.preferredCommand(
                        "BLUETOOTH_A2DP", "AA:BB:CC:DD:EE:FF"));
    }

    @Test
    public void preferredCommandOmitsAnEmptyAddress() {
        assertEquals("cmd audio set-preferred-output-device BUILTIN_SPEAKER",
                AudioOutputRoutingClient.preferredCommand("BUILTIN_SPEAKER", ""));
    }

    @Test
    public void redactedAddressesAreOmittedFromTheCommand() {
        assertEquals("cmd audio set-preferred-output-device BLUETOOTH_A2DP",
                AudioOutputRoutingClient.preferredCommand(
                        "BLUETOOTH_A2DP", "XX:XX:XX:XX:11:DF"));
        assertTrue(AudioOutputRoutingClient.isRedactedAddress("XX:XX:XX:XX:11:DF"));
        assertTrue(AudioOutputRoutingClient.isRedactedAddress(""));
        assertTrue(AudioOutputRoutingClient.isRedactedAddress("00:00:00:00:00:00"));
        assertFalse(AudioOutputRoutingClient.isRedactedAddress("AA:BB:CC:DD:EE:FF"));
    }

    @Test
    public void autoCommandReleasesThePreference() {
        assertEquals("cmd audio set-preferred-output-device AUTO",
                AudioOutputRoutingClient.autoCommand());
    }

    @Test
    public void helpTextDetectsThePreferredOutputSubcommand() {
        assertTrue(AudioOutputRoutingClient.supportsPreferredOutput(
                "Audio manager commands:\n  set-preferred-output-device TYPE"));
        assertFalse(AudioOutputRoutingClient.supportsPreferredOutput("Audio manager commands:"));
        assertFalse(AudioOutputRoutingClient.supportsPreferredOutput(null));
    }

    @Test
    public void preferredAppliedAcceptsSuccessText() {
        assertTrue(AudioOutputRoutingClient.preferredApplied(
                new ShellCommandResult(0, "successfully set preferred device")));
        assertTrue(AudioOutputRoutingClient.preferredApplied(
                new ShellCommandResult(0, "")));
    }

    @Test
    public void preferredAppliedRejectsErrors() {
        assertFalse(AudioOutputRoutingClient.preferredApplied(
                new ShellCommandResult(0, "Error: invalid output device type provided: X")));
        assertFalse(AudioOutputRoutingClient.preferredApplied(
                new ShellCommandResult(0, "can't find service: audio")));
        assertFalse(AudioOutputRoutingClient.preferredApplied(null));
    }

    @Test
    public void preferredClearedAcceptsTheNothingToClearAnswer() {
        assertTrue(AudioOutputRoutingClient.preferredCleared(
                new ShellCommandResult(0, "failed to set to default")));
        assertTrue(AudioOutputRoutingClient.preferredCleared(
                new ShellCommandResult(0, "successfully set to default")));
        assertFalse(AudioOutputRoutingClient.preferredCleared(
                new ShellCommandResult(0, "can't find service: audio")));
    }
}
