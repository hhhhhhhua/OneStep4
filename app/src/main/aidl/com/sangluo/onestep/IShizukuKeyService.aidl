package com.sangluo.onestep;

import com.sangluo.onestep.IEscCallback;

interface IShizukuKeyService {
    void setCallback(IEscCallback callback);

    void destroy();
}