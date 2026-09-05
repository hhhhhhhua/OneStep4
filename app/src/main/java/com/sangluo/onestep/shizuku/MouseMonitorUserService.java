package com.sangluo.onestep.shizuku;
import android.os.Binder;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.IBinder;
import android.os.RemoteException;
import android.util.Log;
import android.view.InputDevice;
import android.view.InputEvent;
import android.view.InputEventReceiver;
import android.view.InputMonitor;
import android.view.MotionEvent;

import com.sangluo.onestep.IMouseCallback;
import com.sangluo.onestep.IMouseMonitorService;
import com.sangluo.onestep.utils.DeviceUtil;
import com.sangluo.onestep.utils.SysServiceObjUtil;

import java.lang.reflect.Method;
import java.util.List;

public class MouseMonitorUserService extends IMouseMonitorService.Stub {
    private static final String TAG = "OneStep40";
    private InputMonitor mInputMonitor;
    private InputEventReceiver mInputEventReceiver;
    private HandlerThread mHandlerThread;
    private IMouseCallback mCallback;
    private Integer WIDTH = null;
    private Integer HEIGHT = null;
    private int mRealMouseDeviceId = -1;
    @Override
    public void startMonitor(int displayId, int width, int height, IMouseCallback callback) {
        WIDTH = width;
        HEIGHT = height;
        try{
            this.mCallback = callback;

            // 1. 创建一个带 Looper 的线程用于接收输入事件
            mHandlerThread = new HandlerThread("MouseMonitorThread");
            mHandlerThread.start();
            Handler handler = new Handler(mHandlerThread.getLooper());

            // 2. 获取 InputManager (此时运行在 Shell 进程，有权限)
            // 注意：这里需要通过反射或获取 system context 来拿到 InputManager
            // 可以尝试 IInputManager.Stub.asInterface(ServiceManager.getService("input"))

            // 假设你拿到了 inputManager
            Object inputManager = SysServiceObjUtil.getInputManager2();

            // 反射获取底层 IInputManager 的 monitorGestureInput 方法
            // 参数类型为：(String name, int displayId, int gestureType)
            Method monitorMethod = inputManager.getClass().getMethod(
                    "monitorGestureInput",
                    IBinder.class,
                    String.class,
                    int.class
            );

            IBinder token = new Binder();

            // 执行反射调用，gestureType 传 0
            mInputMonitor = (InputMonitor) monitorMethod.invoke(
                    inputManager,
                    token,
                    "shizuku-mouse-edge"+displayId,
                    displayId
            );

            // 3. 注册接收器
            mInputEventReceiver = new InputEventReceiver(
                    mInputMonitor.getInputChannel(), handler.getLooper()) {

                @Override
                public void onInputEvent(InputEvent event) {
                    if (event instanceof MotionEvent) {
                        MotionEvent me = (MotionEvent) event;
                        if ((me.getSource() & InputDevice.SOURCE_MOUSE) == InputDevice.SOURCE_MOUSE) {
                            mRealMouseDeviceId = me.getDeviceId();
                        }
                        // 在这里执行你的边缘检测算法
                        checkEdgeAndCallback(me, displayId);
                    }
                    finishInputEvent(event, false);
                }
            };
        }catch (Exception e){
            Log.d(TAG, "Shizuku startMonitor异常： " + e.getMessage());
            throw new RuntimeException(e);
        }
    }

    private void checkEdgeAndCallback(MotionEvent event, int displayId) {
        // ... 你的边缘计算逻辑 ...
        // 如果到达边缘，通过 Binder 回调给主进程
        if (mCallback != null) {
            try {
                float x = event.getX();
                float y = event.getY();

                int edgeThreshold = 5; // 边缘响应阈值 5px

                boolean isLeft = x <= edgeThreshold;
                boolean isRight = x >= (WIDTH - edgeThreshold);
                boolean isTop = y <= edgeThreshold;
                boolean isBottom = y >= (HEIGHT - edgeThreshold);

                if (isLeft || isRight || isTop || isBottom) {
                    mCallback.onMouseEdge(displayId, event.getX(), event.getY());
                }
            } catch (RemoteException e) {
                e.printStackTrace();
            }
        }
    }

    @Override
    public void stopMonitor() {
        if (mInputEventReceiver != null) {
            mInputEventReceiver.dispose();
        }
        if (mInputMonitor != null) {
            mInputMonitor.dispose();
        }
        if (mHandlerThread != null) {
            mHandlerThread.quitSafely();
        }
    }

    @Override
    public void setMousePosition(int displayId, float x, float y) {
        long token = Binder.clearCallingIdentity();
        try {
            List<Integer> mouseDeviceIdList = DeviceUtil.getMouseDeviceIdList();
            for (Integer i : mouseDeviceIdList) {
                DeviceUtil.setInputDeviceEnabled(i, true);
            }
            for (Integer i : mouseDeviceIdList) {
                DeviceUtil.setInputDeviceEnabled(i, false);
            }
            DeviceUtil.injectRealHardwareMove((int) x, (int) y);
        } catch (Exception e) {
            Log.e(TAG, "setMousePosition 失败: ", e);
        } finally {
            Binder.restoreCallingIdentity(token);
        }
    }

    @Override
    public void wakeUpMouse(){
        long token = Binder.clearCallingIdentity();
        try {
            DeviceUtil.wakePointerHardware();
        } catch (Exception e) {
            Log.e(TAG, "wakeUpMouse 失败: ", e);
        } finally {
            Binder.restoreCallingIdentity(token);
        }
    }

    @Override
    public void destroy() {
        System.exit(0);
    }
}