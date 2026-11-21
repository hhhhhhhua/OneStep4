package com.sangluo.onestep.feature.audio;

import android.content.Context;
import android.media.AudioAttributes;
import android.media.AudioDeviceCallback;
import android.media.AudioDeviceInfo;
import android.media.AudioManager;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.text.TextUtils;
import android.util.Log;

import com.sangluo.onestep.data.settings.OneStepSettingsStore;
import com.sangluo.onestep.system.root.PersistentRootShell;
import com.sangluo.onestep.system.root.ShellCommandResult;
import com.sangluo.onestep.utils.ShizukuServiceUtil;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Lists the phone's audio outputs, switches the media strategy through the privileged
 * {@code cmd audio} command and keeps a pinned choice in force.
 *
 * <p>Pinning is enforced on device add/remove and by a light poll of the routed device:
 * while the pinned output is connected it is re-applied whenever the system picks
 * something else. When the pinned output disappears the preference is released so the
 * system default takes over; reconnecting it pins it again.</p>
 */
public final class AudioOutputController {
    private static final String TAG = "OneStep40_AudioOutput";
    private static final long ENFORCE_INTERVAL_MS = 4000L;
    private static final int COMMAND_TIMEOUT_SECONDS = 8;
    private static final long COMMAND_RETRY_DELAY_MS = 30_000L;
    private static final int MEDIA_API_LEVEL = 33;

    public interface CommandRunner {
        ShellCommandResult run(String command, int timeoutSeconds);
    }

    public interface Listener {
        void onAudioOutputStateChanged();
    }

    public interface ResultCallback {
        void onResult(boolean success, String message);
    }

    private static AudioOutputController instance;

    public static synchronized AudioOutputController get(Context context) {
        if (instance == null) {
            Context appContext = context == null ? null : context.getApplicationContext();
            if (appContext == null) {
                appContext = context;
            }
            instance = new AudioOutputController(appContext,
                    new PersistentRootShell()::run);
        }
        return instance;
    }

    private final AudioManager audioManager;
    private final OneStepSettingsStore settingsStore;
    private final CommandRunner runner;
    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private final List<Listener> listeners = new CopyOnWriteArrayList<>();
    private final AudioAttributes mediaAttributes = new AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_MEDIA)
            .build();
    private final Runnable enforceRunnable = this::enforceTick;

    private boolean started;
    private volatile Boolean commandSupported;
    private volatile String lastAppliedKey;
    private boolean autoReleasedForMissingPin;
    private long nextCommandRetryUptime;

    private final AudioDeviceCallback deviceCallback = new AudioDeviceCallback() {
        @Override
        public void onAudioDevicesAdded(AudioDeviceInfo[] addedDevices) {
            handleDeviceListChanged();
        }

        @Override
        public void onAudioDevicesRemoved(AudioDeviceInfo[] removedDevices) {
            handleDeviceListChanged();
        }
    };

    private AudioOutputController(Context context, CommandRunner runner) {
        this.audioManager = (AudioManager) context.getSystemService(Context.AUDIO_SERVICE);
        this.settingsStore = new OneStepSettingsStore(context);
        this.runner = runner;
    }

    /** Registers the device callback and re-applies the pinned output at process start. */
    public void start() {
        if (started) {
            return;
        }
        started = true;
        if (audioManager != null) {
            try {
                audioManager.registerAudioDeviceCallback(deviceCallback, mainHandler);
            } catch (RuntimeException error) {
                Log.w(TAG, "Unable to register audio device callback", error);
            }
        }
        refreshPinnedAsync(true);
        if (getPinnedOutput() != null) {
            startEnforcement();
        }
    }

    public void addListener(Listener listener) {
        if (listener != null && !listeners.contains(listener)) {
            listeners.add(listener);
        }
    }

    public void removeListener(Listener listener) {
        listeners.remove(listener);
    }

    /** Connected and selectable outputs, de-duplicated by type/address. */
    public List<AudioOutputDevice> connectedOutputs() {
        List<AudioOutputDevice> result = new ArrayList<>();
        if (audioManager == null) {
            return result;
        }
        AudioDeviceInfo[] infos;
        try {
            infos = audioManager.getDevices(AudioManager.GET_DEVICES_OUTPUTS);
        } catch (RuntimeException error) {
            Log.w(TAG, "Unable to list audio outputs", error);
            return result;
        }
        LinkedHashMap<String, AudioOutputDevice> unique = new LinkedHashMap<>();
        for (AudioDeviceInfo info : infos) {
            if (AudioOutputTypes.isHiddenOutput(info.getType())) {
                continue;
            }
            AudioOutputDevice device = AudioOutputDevice.from(info);
            if (device != null) {
                unique.putIfAbsent(device.key(), device);
            }
        }
        result.addAll(unique.values());
        return result;
    }

    /** The output the media strategy is routed to right now, or {@code null}. */
    public AudioOutputDevice currentOutput() {
        if (audioManager == null || Build.VERSION.SDK_INT < MEDIA_API_LEVEL) {
            return null;
        }
        try {
            List<AudioDeviceInfo> devices =
                    audioManager.getAudioDevicesForAttributes(mediaAttributes);
            for (AudioDeviceInfo info : devices) {
                AudioOutputDevice device = AudioOutputDevice.from(info);
                if (device != null) {
                    return device;
                }
            }
        } catch (RuntimeException error) {
            Log.w(TAG, "Unable to read current media output", error);
        }
        return null;
    }

    public AudioOutputDevice getPinnedOutput() {
        int type = settingsStore.loadPinnedAudioOutputType();
        String typeName = settingsStore.loadPinnedAudioOutputTypeName();
        if (type <= 0 || AudioOutputTypes.isHiddenOutput(type)
                || TextUtils.isEmpty(typeName)) {
            return null;
        }
        return new AudioOutputDevice(type, typeName, AudioOutputTypes.label(type),
                settingsStore.loadPinnedAudioOutputAddress(),
                settingsStore.loadPinnedAudioOutputName());
    }

    public boolean isCommandSupported() {
        return commandSupported == null || commandSupported;
    }

    /** Switches now without keeping the choice pinned. */
    public void selectOnce(AudioOutputDevice device, ResultCallback callback) {
        if (device == null) {
            postResult(callback, false, "未选择音频设备");
            return;
        }
        settingsStore.clearPinnedAudioOutput();
        stopEnforcement();
        notifyStateChanged();
        executor.execute(() -> {
            CommandOutcome outcome = applyPreferred(device, true);
            notifyStateChangedOnMain();
            postResult(callback, outcome.success,
                    outcome.success ? "已切换到 " + device.name : outcome.message);
        });
    }

    /** Switches and pins the device, moving any previous pin. */
    public void pin(AudioOutputDevice device, ResultCallback callback) {
        if (device == null) {
            postResult(callback, false, "未选择音频设备");
            return;
        }
        settingsStore.savePinnedAudioOutput(device.type, device.typeName,
                device.address, device.name);
        autoReleasedForMissingPin = false;
        notifyStateChanged();
        startEnforcement();
        executor.execute(() -> {
            CommandOutcome outcome = applyPreferred(device, true);
            if (outcome.success) {
                lastAppliedKey = device.key();
            }
            notifyStateChangedOnMain();
            postResult(callback, outcome.success,
                    outcome.success ? "已固定 " + device.name : outcome.message);
        });
    }

    /** Releases the pin and lets the system choose the output again. */
    public void unpin(ResultCallback callback) {
        settingsStore.clearPinnedAudioOutput();
        lastAppliedKey = null;
        autoReleasedForMissingPin = false;
        stopEnforcement();
        notifyStateChanged();
        executor.execute(() -> {
            CommandOutcome outcome = releasePreference(true);
            notifyStateChangedOnMain();
            postResult(callback, outcome.success,
                    outcome.success ? "已取消固定，恢复系统默认输出" : outcome.message);
        });
    }

    /** Non-forced re-apply; cheap when the pinned output is already active. */
    public void refreshPinned() {
        refreshPinnedAsync(false);
    }

    // -------------------------------------------------------------- enforcement

    private void handleDeviceListChanged() {
        // Called on the main thread by the callback's handler.
        notifyStateChanged();
        refreshPinnedAsync(true);
    }

    private void refreshPinnedAsync(boolean force) {
        executor.execute(() -> {
            AudioOutputDevice pinned = getPinnedOutput();
            if (pinned == null) {
                lastAppliedKey = null;
                autoReleasedForMissingPin = false;
                return;
            }
            AudioOutputDevice connected = findConnected(pinned);
            if (connected == null) {
                if (!autoReleasedForMissingPin) {
                    releasePreference(false);
                    autoReleasedForMissingPin = true;
                }
                lastAppliedKey = null;
                return;
            }
            autoReleasedForMissingPin = false;
            if (!force && pinned.key().equalsIgnoreCase(lastAppliedKey)) {
                return;
            }
            CommandOutcome outcome = applyPreferred(connected, false);
            if (outcome.success) {
                lastAppliedKey = pinned.key();
            }
        });
    }

    private void startEnforcement() {
        mainHandler.removeCallbacks(enforceRunnable);
        mainHandler.postDelayed(enforceRunnable, ENFORCE_INTERVAL_MS);
    }

    private void stopEnforcement() {
        mainHandler.removeCallbacks(enforceRunnable);
    }

    /**
     * Rejects a system-side switch: when the routed device stops matching the pin it is
     * re-applied. Device additions/removals already trigger an immediate reconcile.
     */
    private void enforceTick() {
        AudioOutputDevice pinned = getPinnedOutput();
        if (pinned == null) {
            return;
        }
        if (Boolean.FALSE.equals(commandSupported)
                || SystemClock.uptimeMillis() < nextCommandRetryUptime) {
            mainHandler.postDelayed(enforceRunnable, ENFORCE_INTERVAL_MS);
            return;
        }
        AudioOutputDevice current = currentOutput();
        // Before API 33 there is no public routed-device query; device add/remove
        // callbacks still keep the pin applied.
        if (current != null && !pinned.matchesKey(current.key())) {
            refreshPinnedAsync(true);
        }
        mainHandler.postDelayed(enforceRunnable, ENFORCE_INTERVAL_MS);
    }

    private AudioOutputDevice findConnected(AudioOutputDevice pinned) {
        for (AudioOutputDevice device : connectedOutputs()) {
            if (pinned.matchesKey(device.key())) {
                return device;
            }
        }
        return null;
    }

    // ---------------------------------------------------------------- commands

    private CommandOutcome applyPreferred(AudioOutputDevice device, boolean bypassRetry) {
        if (Boolean.FALSE.equals(commandSupported)) {
            return new CommandOutcome(false, "当前系统不支持声音输出切换");
        }
        ShellCommandResult result = execute(AudioOutputRoutingClient.preferredCommand(
                device.typeName, device.address), bypassRetry);
        boolean success = AudioOutputRoutingClient.preferredApplied(result);
        commandSupported = success ? Boolean.TRUE : commandSupported;
        if (!success && isUnsupportedResult(result)) {
            commandSupported = Boolean.FALSE;
        }
        return new CommandOutcome(success, success ? "" : failureText(result));
    }

    private CommandOutcome releasePreference(boolean bypassRetry) {
        ShellCommandResult result = execute(AudioOutputRoutingClient.autoCommand(),
                bypassRetry);
        boolean success = AudioOutputRoutingClient.preferredCleared(result);
        if (!success && isUnsupportedResult(result)) {
            commandSupported = Boolean.FALSE;
        }
        return new CommandOutcome(success, success ? "" : failureText(result));
    }

    private ShellCommandResult execute(String command, boolean bypassRetry) {
        if (!bypassRetry && SystemClock.uptimeMillis() < nextCommandRetryUptime) {
            return null;
        }
        try {
            ShellCommandResult result = runner.run(command, COMMAND_TIMEOUT_SECONDS);
            updateCommandRetry(result);
            return result;
        } catch (RuntimeException first) {
            // The Shizuku user service may still be binding right after process start.
            ShizukuServiceUtil.waitServiceInitOver();
            try {
                ShellCommandResult result = runner.run(command, COMMAND_TIMEOUT_SECONDS);
                updateCommandRetry(result);
                return result;
            } catch (RuntimeException second) {
                Log.w(TAG, "Audio route command failed", second);
                deferCommandRetry();
                return null;
            }
        }
    }

    private void updateCommandRetry(ShellCommandResult result) {
        if (isTransportFailure(result)) {
            nextCommandRetryUptime = SystemClock.uptimeMillis() + COMMAND_RETRY_DELAY_MS;
        } else {
            nextCommandRetryUptime = 0L;
        }
    }

    /**
     * A non-zero exit from {@code cmd audio} usually means the requested device is not
     * usable right now, which the user can retry immediately. Only shell-level failures
     * (root/Shizuku unavailable, timeouts) get the retry cooldown.
     */
    private boolean isTransportFailure(ShellCommandResult result) {
        if (result == null) {
            return true;
        }
        if (result.isSuccess()) {
            return false;
        }
        String output = result.output == null ? "" : result.output.toLowerCase();
        return output.isEmpty() || output.contains("start su failed")
                || output.contains("shell exited") || output.contains("timeout")
                || output.contains("interrupted") || output.contains("permission denied");
    }

    private void deferCommandRetry() {
        nextCommandRetryUptime = SystemClock.uptimeMillis() + COMMAND_RETRY_DELAY_MS;
    }

    private boolean isUnsupportedResult(ShellCommandResult result) {
        if (result == null) {
            return false;
        }
        String output = result.output == null ? "" : result.output.toLowerCase();
        // Device-specific errors (for example an invalid address) must not disable the
        // whole feature; only a missing/unsupported cmd audio service does.
        return output.contains("can't find service") || output.contains("unknown command")
                || output.contains("is not a shell command")
                || output.contains("inaccessible");
    }

    private String failureText(ShellCommandResult result) {
        if (Boolean.FALSE.equals(commandSupported)) {
            return "当前系统不支持声音输出切换";
        }
        String detail = AudioOutputRoutingClient.failureMessage(result);
        return "切换失败：" + detail;
    }

    // ----------------------------------------------------------------- helpers

    private void notifyStateChanged() {
        for (Listener listener : listeners) {
            try {
                listener.onAudioOutputStateChanged();
            } catch (RuntimeException error) {
                Log.w(TAG, "Audio output listener failed", error);
            }
        }
    }

    private void notifyStateChangedOnMain() {
        mainHandler.post(this::notifyStateChanged);
    }

    private void postResult(ResultCallback callback, boolean success, String message) {
        if (callback == null) {
            return;
        }
        mainHandler.post(() -> callback.onResult(success, message));
    }

    private static final class CommandOutcome {
        final boolean success;
        final String message;

        CommandOutcome(boolean success, String message) {
            this.success = success;
            this.message = message;
        }
    }
}
