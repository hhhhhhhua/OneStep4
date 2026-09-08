// IRootInputBridgeService.aidl
package com.sangluo.onestep;

import java.util.List;

interface IRootInputBridgeService {

    String handleCommand(String bridgeToken, String cmd);

    void destroy();

    void sendAppLaunchWhiteSet(in List<String> whiteList);
}