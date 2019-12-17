package com.yrobot.exo.app.data;

import android.support.annotation.NonNull;
import android.util.Log;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.Arrays;

import static com.yrobot.exo.app.YrConstants.IDX_DATA;
import static com.yrobot.exo.app.YrConstants.convertToFloat;
import static com.yrobot.exo.app.YrConstants.convertToShort;

public class LegData {

    private final static String TAG = LegData.class.getSimpleName();

    private String prefix;

    public short status = 0;
    public byte gaitCyclePercent = 0;
    public byte gaitCyclePhase = 0;
    public float hip_angle;
    public float knee_angle;
    public float ankle_angle;
    public Imu[] imu;

    public static final int DATA_LEN = 2;
    private final float MULTIPLIER = 1000f;

    class Imu {
        public Imu() {
        }

        public void set(short ang, short g, short acc) {
            angle = convertToFloat(ang);
            accel = convertToFloat(acc);
            gyro = convertToFloat(g) / 10.0f;
        }

        public float angle = 0;
        public float accel = 0;
        public float gyro = 0;
    }

    public byte[] getByteArray() {
        short[] vals = new short[DATA_LEN];
        vals[0] = convertToShort(this.ankle_angle, MULTIPLIER);
        vals[1] = convertToShort(this.knee_angle, MULTIPLIER);
        ByteBuffer buffer = ByteBuffer.allocate(4 + DATA_LEN * 2).order(ByteOrder.LITTLE_ENDIAN);
        buffer.putShort(status);
        buffer.put(gaitCyclePhase);
        buffer.put(gaitCyclePercent);
        for (int i = 0; i < vals.length; i++) {
            buffer.putShort(vals[i]);
        }
        return buffer.array();
    }

    public void setFromByteArray(@NonNull byte[] data) {
        this.status = ByteBuffer.wrap(Arrays.copyOfRange(data, IDX_DATA, IDX_DATA + 2)).order(ByteOrder.LITTLE_ENDIAN).getShort();
        this.gaitCyclePhase = data[IDX_DATA + 2];
        this.gaitCyclePercent = data[IDX_DATA + 3];
        short[] vals = new short[DATA_LEN];
        for (int i = 0; i < vals.length; i++) {
            int offset = IDX_DATA + 4 + (i * 2);
            vals[i] = ByteBuffer.wrap(Arrays.copyOfRange(data, offset, offset + 2)).order(ByteOrder.LITTLE_ENDIAN).getShort();
        }
        this.ankle_angle = convertToFloat(vals[0], MULTIPLIER);
        this.knee_angle = convertToFloat(vals[1], MULTIPLIER);
    }

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

    public void print() {
        Log.v(TAG, "Leg [" + prefix + "] data: (" + knee_angle + ", " + ankle_angle + ")");
    }

    public float[] getArray() {
        return new float[]{knee_angle};
    }

    public float[] getImuArray(int index) {
        if (index > (imu.length - 1)) {
            return null;
        }
        return new float[]{imu[index].gyro};
    }
}


