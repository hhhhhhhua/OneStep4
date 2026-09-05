package com.sangluo.onestep;

import com.sangluo.onestep.system.root.ShellCommandResult;

interface IShizukuCommonService {
    IBinder getService(String serviceName);

    ShellCommandResult run(String command, int timeoutSeconds);

    boolean hasConfirmedRootAccess();

    void destroy();
}