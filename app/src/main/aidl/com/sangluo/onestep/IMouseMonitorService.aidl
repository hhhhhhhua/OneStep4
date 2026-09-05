package com.sangluo.onestep;

import com.sangluo.onestep.IMouseCallback;

interface IMouseMonitorService {

    void startMonitor(int displayId, int width, int height, IMouseCallback callback);

    void stopMonitor();

    void setMousePosition(int displayId, float x, float y);

    void wakeUpMouse();

    void destroy();
}