package com.sangluo.onestep.system.root;

import android.os.RemoteException;
import android.os.SystemClock;

import com.sangluo.onestep.IRootInputBridgeService;
import com.sangluo.onestep.IShizukuCommonService;
import com.sangluo.onestep.utils.ShizukuServiceUtil;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

/** Serializes commands through one long-lived su process. */
public final class PersistentRootShell {

    public ShellCommandResult run(String command) {
        return this.run(command, 10);
    }

    public ShellCommandResult run(String command, int timeoutSeconds) {
        IShizukuCommonService mRootInputBridgeService = ShizukuServiceUtil.getShizukuCommonService();
        try {
            return mRootInputBridgeService.run(command, timeoutSeconds);
        } catch (RemoteException e) {
            throw new RuntimeException(e);
        }
    }

    public boolean hasConfirmedRootAccess(){
        IShizukuCommonService mRootInputBridgeService = ShizukuServiceUtil.getShizukuCommonService();
        try {
            return mRootInputBridgeService.hasConfirmedRootAccess();
        } catch (RemoteException e) {
            throw new RuntimeException(e);
        }
    }
}
