package com.sangluo.onestep.view;

import android.content.Context;
import android.util.AttributeSet;
import android.view.InputDevice;
import android.view.MotionEvent;
import android.view.SurfaceView;

import com.sangluo.onestep.RootVirtualDisplayHost;

public class MouseSurfaceView extends SurfaceView {

    private final RootVirtualDisplayHost host;

    public MouseSurfaceView(Context context, RootVirtualDisplayHost host) {
        super(context);
        this.host = host;
    }

    public MouseSurfaceView(Context context, AttributeSet attrs) {
        super(context, attrs);
        this.host = null;
    }

    public MouseSurfaceView(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        this.host = null;
    }

    @Override
    public boolean dispatchGenericMotionEvent(MotionEvent event) {
        if ((event.getSource() & InputDevice.SOURCE_MOUSE)
                == InputDevice.SOURCE_MOUSE) {

            if (host != null) {
                return host.onMouseGenericMotionEvent(event);
            }

            return true;
        }

        return super.dispatchGenericMotionEvent(event);
    }
}