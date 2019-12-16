package com.yrobot.exo.app.data;

import android.util.Log;

public class LegData {
    private final static String TAG = LegData.class.getSimpleName();

    private static float convert(short val) {
        return ((float) val / 1000.0f);
    }

    class Imu {
        public Imu() {
        }

        public void set(short ang, short g, short acc) {
            angle = convert(ang);
            accel = convert(acc);
            gyro = convert(g) / 10.0f;
        }

        public float angle = 0;
        public float accel = 0;
        public float gyro = 0;
    }

    private String prefix;
    public float hip_angle;
    public float knee_angle;
    public float ankle_angle;
    public Imu[] imu;

    public LegData(String prefix_in) {
        hip_angle = 0.0f;
        knee_angle = 0.0f;
        ankle_angle = 0.0f;
        prefix = prefix_in;
        imu = new Imu[3];
        for (int i = 0; i < imu.length; i++) {
            imu[i] = new Imu();
        }
    }

    public void set(short hip, short knee, short ankle) {
        hip_angle = convert(hip) * 10.0f;
        knee_angle = convert(knee) * 10.0f;
        ankle_angle = convert(ankle) * 10.0f;
    }

    public void print() {
        Log.v(TAG, "Leg [" + prefix + "] data: (" + knee_angle + ", " + ankle_angle + ")");
    }

    public float[] getArray() {
//        float[] vals = new float[3];
//        vals[0] = hip_angle;
//        vals[1] = knee_angle;
//        vals[2] = ankle_angle;
        float[] vals = new float[1];
        vals[0] = knee_angle;
        return vals;
    }

    public float[] getImuArray(int index) {
        if (index > (imu.length - 1)) {
            return null;
        }
//        float[] vals = new float[3];
//        vals[0] = imu[index].angle;
//        vals[1] = imu[index].gyro;
//        vals[2] = imu[index].accel;
        float[] vals = new float[1];
        vals[0] = imu[index].gyro;
        return vals;
    }
}


