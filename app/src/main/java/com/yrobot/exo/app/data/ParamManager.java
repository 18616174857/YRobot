package com.yrobot.exo.app.data;

import android.os.Handler;
import android.os.Looper;
import android.support.annotation.NonNull;
import android.util.Log;
import android.widget.Toast;

import com.yrobot.exo.ble.BleUtils;

import java.util.ArrayList;
import java.util.HashMap;

import static com.yrobot.exo.app.data.Param.IDX_DATA_KEY;
import static com.yrobot.exo.app.data.Param.IDX_DATA_TYPE;
import static com.yrobot.exo.app.data.Param.KEY_PARAM_DONE_LOADING;
import static com.yrobot.exo.app.data.Param.PARAM_TYPE_BOOL;
import static com.yrobot.exo.app.data.Param.PARAM_TYPE_FLOAT;
import static com.yrobot.exo.app.data.Param.PARAM_TYPE_INT16;
import static com.yrobot.exo.app.data.Param.PARAM_TYPE_INT32;
import static com.yrobot.exo.app.data.Param.PARAM_TYPE_INT8;
import static com.yrobot.exo.app.data.Param.PARAM_TYPE_SIGNAL;

public class ParamManager {

    private final static String TAG = "yr-" + ParamManager.class.getSimpleName();

    public HashMap<Byte, Param> params;
    public HashMap<String, Param> params_by_name;
    private boolean mInitialized;

    private ParamManager() {
        mInitialized = false;
        params = new HashMap<>();
        params_by_name = new HashMap<>();
    }

    private static volatile ParamManager sSoleInstance = new ParamManager();

    public static ParamManager getInstance() {
        return sSoleInstance;
    }

    public boolean isInitialized() {
        return mInitialized;
    }

    public ArrayList<Param> getParams() {
        return new ArrayList<>(params.values());
    }

    public void updateParams(@NonNull byte[] data) {
        byte key = data[IDX_DATA_KEY];

        Log.v(TAG, BleUtils.bytesToHex2(data));

        if (key == KEY_PARAM_DONE_LOADING) {
            Log.v(TAG, "got param [" + key + "] done loading");
//            new Handler(Looper.getMainLooper()).post(() -> {
//                Toast.makeText(getContext(), "Request Params", Toast.LENGTH_SHORT).show();
//            });
            for (Param param : params.values()) {
                params_by_name.put(param.name, param);
            }
            mInitialized = true;
            return;
        }

        byte val_type = data[IDX_DATA_TYPE];

        if (params.containsKey(key)) {
            params.get(key).update(data);
        } else {
            switch (val_type) {
                case PARAM_TYPE_INT8:
                case PARAM_TYPE_BOOL:
                case PARAM_TYPE_SIGNAL:
                    params.put(key, new Param<Byte>((byte) 1, data));
                    break;
                case PARAM_TYPE_INT16:
                    params.put(key, new Param<Short>((short) 1, data));
                    break;
                case PARAM_TYPE_INT32:
                    params.put(key, new Param<Integer>(1, data));
                    break;
                case PARAM_TYPE_FLOAT:
                    params.put(key, new Param<Float>(1.0f, data));
                    break;
            }
        }
    }
}
