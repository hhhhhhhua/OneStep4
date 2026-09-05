package com.sangluo.onestep;

interface IShizukuCommonService {
    IBinder getService(String serviceName);

    void destroy();
}