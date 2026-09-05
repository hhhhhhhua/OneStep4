// IRootInputBridgeService.aidl
package com.sangluo.onestep;

import com.sangluo.onestep.system.root.ShellCommandResult;

// Declare any non-default types here with import statements

interface IRootInputBridgeService {

    String handleCommand(String bridgeToken, String cmd);

    ShellCommandResult run(String command, int timeoutSeconds);

    boolean hasConfirmedRootAccess();

    void destroy();
}