package com.sangluo.onestep.system.input;

import android.graphics.Rect;
import android.net.LocalSocket;
import android.net.LocalSocketAddress;
import android.os.RemoteException;
import android.os.SystemClock;
import android.text.TextUtils;
import android.util.Log;
import android.content.ComponentName;
import android.view.KeyEvent;
import android.view.MotionEvent;

import com.sangluo.onestep.IRootInputBridgeService;
import com.sangluo.onestep.MotionEventCodec;
import com.sangluo.onestep.model.PinnedTaskState;
import com.sangluo.onestep.shizuku.RootInputBridgeService;
import com.sangluo.onestep.utils.ShizukuServiceUtil;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.Closeable;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.util.Locale;

/** Typed client for commands served by the privileged input bridge socket. */
public final class RootInputBridgeClient implements AutoCloseable {
    private static final String TAG = "OneStep40";
    private static final String SOCKET_PREFIX = "onestep_input_bridge_";
    private static final String HELLO_PREFIX = RootInputBridgeService.HELLO_RESPONSE_PREFIX;
    private static final int CONNECT_LOG_THROTTLE_MS = 2000;
    private static final int HANDSHAKE_TIMEOUT_MS = 250;
    private static final int POLICY_TIMEOUT_MS = 800;

    private LocalSocket socket;
    private BufferedReader reader;
    private BufferedWriter writer;
    private String connectedBridgeToken;
    private long lastFailureLogUptime;

    public synchronized boolean sendMotion(String bridgeToken, int displayId,
                                           MotionEvent event, long traceId) {
        try {
            String command = "motionEvent " + displayId + " " + traceId + " "
                    + MotionEventCodec.encode(event);
            return sendLine(bridgeToken, command);
        } catch (RuntimeException e) {
            logFailure("encode motion failed", e);
            return false;
        }
    }

    public synchronized boolean sendKeyEvent(String bridgeToken, int displayId,
                                             KeyEvent event, long traceId) {
        try {
            String command = "KeyEvent " + displayId + " " + traceId + " "
                    + MotionEventCodec.encode(event);
            return sendLine(bridgeToken, command);
        } catch (RuntimeException e) {
            logFailure("encode motion failed", e);
            return false;
        }
    }

    public synchronized boolean sendKey(String bridgeToken, int displayId, int keyCode) {
        return sendLine(bridgeToken,
                String.format(Locale.US, "key %d %d", displayId, keyCode));
    }

    public synchronized boolean focusHostedDisplay(String bridgeToken, int displayId) {
        String response = sendRequestOnDedicatedConnection(
                bridgeToken, "focusHostedDisplay " + displayId);
        if (TextUtils.isEmpty(response)) {
            return false;
        }
        try {
            String[] parts = response.trim().split("\\s+");
            return parts.length == 4
                    && "focusHostedDisplay".equals(parts[0])
                    && Integer.parseInt(parts[1]) == displayId
                    && Boolean.parseBoolean(parts[2])
                    && Boolean.parseBoolean(parts[3]);
        } catch (NumberFormatException e) {
            return false;
        }
    }

    public boolean removeTask(String bridgeToken, int taskId) {
        String response = sendRequestOnDedicatedConnection(
                bridgeToken, "removeTask " + taskId);
        if (TextUtils.isEmpty(response)) {
            return false;
        }
        try {
            String[] parts = response.trim().split("\\s+");
            return parts.length == 3
                    && "removeTask".equals(parts[0])
                    && Integer.parseInt(parts[1]) == taskId
                    && Boolean.parseBoolean(parts[2]);
        } catch (NumberFormatException e) {
            return false;
        }
    }

    public boolean moveTaskToDisplay(
            String bridgeToken, int taskId, int displayId, ComponentName component) {
        if (component == null) {
            return false;
        }
        String response = sendRequestOnDedicatedConnection(
                bridgeToken, "moveTaskToDisplay " + taskId + " " + displayId
                        + " " + component.flattenToString());
        if (TextUtils.isEmpty(response)) {
            return false;
        }
        try {
            String[] parts = response.trim().split("\\s+");
            return parts.length == 4
                    && "moveTaskToDisplay".equals(parts[0])
                    && Integer.parseInt(parts[1]) == taskId
                    && Integer.parseInt(parts[2]) == displayId
                    && Boolean.parseBoolean(parts[3]);
        } catch (NumberFormatException e) {
            return false;
        }
    }

    public Integer setDisplayImePolicy(String bridgeToken, int displayId, int policy) {
        String command = String.format(Locale.US, "imePolicy %d %d", displayId, policy);
        String response = sendRequestOnDedicatedConnection(bridgeToken, command);
        if (TextUtils.isEmpty(response)) {
            return null;
        }
        try {
            String[] parts = response.trim().split("\\s+");
            if (parts.length != 5 || !"imePolicy".equals(parts[0])
                    || Integer.parseInt(parts[1]) != displayId
                    || Integer.parseInt(parts[2]) != policy) {
                logFailure("invalid IME policy response", new IOException(response));
                return null;
            }
            int actualPolicy = Integer.parseInt(parts[3]);
            if (!Boolean.parseBoolean(parts[4])) {
                Log.w(TAG, "Root bridge did not apply IME policy: display=" + displayId
                        + ", requested=" + policy + ", actual=" + actualPolicy);
            }
            return actualPolicy >= 0 ? actualPolicy : null;
        } catch (NumberFormatException e) {
            logFailure("invalid IME policy response", e);
            return null;
        }
    }

    public boolean setDisplayRotationAuto(String bridgeToken, int displayId) {
        String response = sendRequestOnDedicatedConnection(
                bridgeToken, "displayRotationAuto " + displayId);
        if (TextUtils.isEmpty(response)) {
            return false;
        }
        try {
            String[] parts = response.trim().split("\\s+");
            return parts.length == 3
                    && "displayRotationAuto".equals(parts[0])
                    && Integer.parseInt(parts[1]) == displayId
                    && Boolean.parseBoolean(parts[2]);
        } catch (NumberFormatException e) {
            logFailure("invalid automatic display rotation response", e);
            return false;
        }
    }

    public boolean setDisplayLandscapeRotation(String bridgeToken, int displayId, int rotation) {
        String response = sendRequestOnDedicatedConnection(bridgeToken,
                "displayLandscapeRotation " + displayId + " " + rotation);
        if (TextUtils.isEmpty(response)) {
            return false;
        }
        try {
            String[] parts = response.trim().split("\\s+");
            return parts.length == 4
                    && "displayLandscapeRotation".equals(parts[0])
                    && Integer.parseInt(parts[1]) == displayId
                    && Integer.parseInt(parts[2]) == rotation
                    && Boolean.parseBoolean(parts[3]);
        } catch (NumberFormatException e) {
            logFailure("invalid landscape display rotation response", e);
            return false;
        }
    }

    public PinnedTaskState getPinnedTaskState(String bridgeToken) {
        String response = sendRequestOnDedicatedConnection(bridgeToken, "pipState");
        if (TextUtils.isEmpty(response)) {
            return null;
        }
        String[] parts = response.trim().split("\\s+");
        if (parts.length == 2 && "pipState".equals(parts[0]) && "none".equals(parts[1])) {
            return new PinnedTaskState(false, -1, null);
        }
        if (parts.length != 7 || !"pipState".equals(parts[0])
                || !"active".equals(parts[1])) {
            return null;
        }
        try {
            int taskId = Integer.parseInt(parts[2]);
            Rect bounds = new Rect(Integer.parseInt(parts[3]), Integer.parseInt(parts[4]),
                    Integer.parseInt(parts[5]), Integer.parseInt(parts[6]));
            return taskId > 0 && !bounds.isEmpty()
                    ? new PinnedTaskState(true, taskId, bounds) : null;
        } catch (NumberFormatException e) {
            logFailure("invalid PiP state response", e);
            return null;
        }
    }

    public boolean dockPinnedTask(String bridgeToken, int taskId, Rect targetBounds,
                                  Rect restoreBounds) {
        String command = String.format(Locale.US,
                "pipDock %d %d %d %d %d %d %d %d %d",
                taskId, targetBounds.left, targetBounds.top,
                targetBounds.right, targetBounds.bottom,
                restoreBounds.left, restoreBounds.top,
                restoreBounds.right, restoreBounds.bottom);
        return parsePipOperationResponse(
                sendRequestOnDedicatedConnection(bridgeToken, command), "pipDock", taskId);
    }

    public boolean undockPinnedTask(String bridgeToken, int taskId, Rect restoreBounds) {
        String command = String.format(Locale.US, "pipUndock %d %d %d %d %d",
                taskId, restoreBounds.left, restoreBounds.top,
                restoreBounds.right, restoreBounds.bottom);
        return parsePipOperationResponse(
                sendRequestOnDedicatedConnection(bridgeToken, command), "pipUndock", taskId);
    }

    @Override
    public synchronized void close() {
        closeConnection();
    }

    private boolean parsePipOperationResponse(String response, String operation, int taskId) {
        if (TextUtils.isEmpty(response)) {
            return false;
        }
        String[] parts = response.trim().split("\\s+");
        try {
            return parts.length == 3 && operation.equals(parts[0])
                    && Integer.parseInt(parts[1]) == taskId
                    && Boolean.parseBoolean(parts[2]);
        } catch (NumberFormatException e) {
            logFailure("invalid " + operation + " response", e);
            return false;
        }
    }

    private boolean sendLine(String bridgeToken, String line) {
        sendRequestOnDedicatedConnection(bridgeToken, line);
        return true;
    }

    private static IRootInputBridgeService getRootInputBridgeService() {
        IRootInputBridgeService mRootInputBridgeService = ShizukuServiceUtil.getRootInputBridgeService();
        return mRootInputBridgeService;
    }

    private String sendRequestOnDedicatedConnection(String bridgeToken, String line) {
        try {
            return getRootInputBridgeService().handleCommand(bridgeToken, line);
        } catch (RemoteException e) {
            throw new RuntimeException(e);
        }
    }

    private LocalSocket openSocket(int timeoutMs) throws IOException {
        LocalSocket result = new LocalSocket();
        try {
            result.connect(new LocalSocketAddress(SOCKET_PREFIX + android.os.Process.myUid(),
                    LocalSocketAddress.Namespace.ABSTRACT));
            result.setSoTimeout(timeoutMs);
            return result;
        } catch (IOException | RuntimeException e) {
            closeQuietly(result);
            throw e;
        }
    }

    private static void writeLine(BufferedWriter target, String line) throws IOException {
        target.write(line);
        target.write('\n');
        target.flush();
    }

    private void logFailure(String stage, Exception e) {
        long now = SystemClock.uptimeMillis();
        if (now - lastFailureLogUptime < CONNECT_LOG_THROTTLE_MS) {
            return;
        }
        lastFailureLogUptime = now;
        Log.w(TAG, "Direct input bridge " + stage + ": "
                + e.getClass().getSimpleName()
                + (TextUtils.isEmpty(e.getMessage()) ? "" : " " + e.getMessage()));
    }

    private synchronized void closeConnection() {
        closeQuietly(reader);
        closeQuietly(writer);
        closeQuietly(socket);
        reader = null;
        writer = null;
        socket = null;
        connectedBridgeToken = null;
    }

    private static void closeQuietly(Closeable closeable) {
        if (closeable == null) {
            return;
        }
        try {
            closeable.close();
        } catch (IOException ignored) {
        }
    }
}
