package com.sangluo.onestep;

import static com.sangluo.onestep.utils.ExtDisplayUtil.TYPE_VIRTUAL;

import android.app.Activity;
import android.app.ActivityOptions;
import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.util.Log;
import android.view.Display;

import com.sangluo.onestep.constant.CommonConstant;
import com.sangluo.onestep.hook.DisPlayStringConst;
import com.sangluo.onestep.utils.ExtDisplayUtil;
import com.sangluo.onestep.utils.ReflectUtil;

/** Opens the OneStep host directly from its launcher icon. */
public final class HomeRedirectActivity extends Activity {
    private static final String TAG = "OneStep40";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        Intent appIntent = new Intent(this, MainActivity.class)
                .setAction(Intent.ACTION_MAIN)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        if (getIntent().getBooleanExtra(MainActivity.EXTRA_SHOW_DESKTOP_HOME, false)) {
            appIntent.putExtra(MainActivity.EXTRA_SHOW_DESKTOP_HOME, true);
        }
        if (getIntent().getBooleanExtra(
                MainActivity.EXTRA_DEFAULT_DISPLAY_RELAY_ATTEMPTED, false)) {
            appIntent.putExtra(MainActivity.EXTRA_DEFAULT_DISPLAY_RELAY_ATTEMPTED, true);
        }
        try {
            ActivityOptions options = ActivityOptions.makeBasic();
            Integer hdmiDisplayId = ExtDisplayUtil.getHdmiDisplayId(getApplicationContext());
            Log.i(TAG, "启动onestep4在：DisplayId" + hdmiDisplayId);
            SharedPreferences prefs = this.getSharedPreferences(CommonConstant.MODULE_PREFS_NAME, MODE_PRIVATE);
            SharedPreferences.Editor editor = prefs.edit();
            editor.putBoolean(DisPlayStringConst.IS_VIRTUAL_DISPLAY_RUN, false);
            if (hdmiDisplayId != null){
                Display displayObj = ExtDisplayUtil.getDisplayObj(getApplicationContext(), hdmiDisplayId);
                int mType = (int) ReflectUtil.invokeMethodVal(Display.class, "getType", displayObj);
                if (mType == TYPE_VIRTUAL) {
                    editor.putBoolean(DisPlayStringConst.IS_VIRTUAL_DISPLAY_RUN, true);
                }
            }
            editor.apply();
            options.setLaunchDisplayId(hdmiDisplayId == null ? 0 : hdmiDisplayId);
            startActivity(appIntent, options.toBundle());
        } catch (ActivityNotFoundException e) {
            Log.e(TAG, "Unable to open OneStep main activity", e);
        } finally {
            finish();
            overridePendingTransition(0, 0);
        }
    }
}
