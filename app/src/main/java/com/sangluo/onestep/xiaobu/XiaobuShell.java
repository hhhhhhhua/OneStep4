package com.sangluo.onestep.xiaobu;

import android.os.RemoteException;

import com.sangluo.onestep.IShizukuCommonService;
import com.sangluo.onestep.system.root.ShellCommandResult;
import com.sangluo.onestep.utils.ShizukuServiceUtil;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.util.concurrent.TimeUnit;

/** Privileged shell: prefers the OneStep Shizuku user service, then falls back to su. */
final class XiaobuShell {
    static final class Result {
        final int code;
        final String output;

        Result(int code, String output) {
            this.code = code;
            this.output = output == null ? "" : output;
        }

        boolean ok() {
            return code == 0;
        }
    }

    private XiaobuShell() {}

    static Result run(String command) {
        IShizukuCommonService service = ShizukuServiceUtil.getShizukuCommonService();
        if (service != null) {
            ShellCommandResult result = null;
            try {
                result = service.run(command, 10);
            } catch (RemoteException e) {
                throw new RuntimeException(e);
            }
            return new Result(result.exitCode, result.output);
        }
        throw new RuntimeException("IShizukuCommonService is null");
    }
}
