// IRootInputBridgeService.aidl
package com.sangluo.onestep;

// Declare any non-default types here with import statements

interface IRootInputBridgeService {

    String handleCommand(String bridgeToken, String cmd);

    void destroy();
}