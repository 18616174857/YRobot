package com.yrobot.exo.app.data;

import android.support.annotation.NonNull;
import android.util.Log;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.Arrays;

import static com.yrobot.exo.app.YrConstants.IDX_DATA;
import static com.yrobot.exo.app.YrConstants.convertToFloat;
import static com.yrobot.exo.app.YrConstants.convertToShort;
import static com.yrobot.exo.app.YrConstants.map;

public class SystemData {
    private final static String TAG = SystemData.class.getSimpleName();

    public static final int DATA_LEN = 5;

    public short status = 0;
    public float voltage = 0;
    public float current = 0;
    public float temp = 0;
    public float timeLeftHours = 10;

    public int battery_soc = 100;
    public float battery_voltage = 24.0f;

    public byte[] getByteArray() {
        short[] vals = new short[DATA_LEN];
        vals[0] = status;
        vals[1] = convertToShort(temp, 100);
        vals[2] = convertToShort(voltage, 100);
        vals[3] = convertToShort(current, 100);
        vals[4] = convertToShort(timeLeftHours, 1000.0f);
        ByteBuffer buffer = ByteBuffer.allocate(DATA_LEN * 2).order(ByteOrder.LITTLE_ENDIAN);
        for (int i = 0; i < vals.length; i++) {
            buffer.putShort(vals[i]);
        }
        return buffer.array();
    }

    public SystemData() {
    }

    public void setFromByteArray(@NonNull byte[] data) {
        short[] vals = new short[DATA_LEN];
        for (int i = 0; i < vals.length; i++) {
            int offset = IDX_DATA + i * 2;
            vals[i] = ByteBuffer.wrap(Arrays.copyOfRange(data, offset, offset + 2)).order(ByteOrder.LITTLE_ENDIAN).getShort();
        }
        this.status = vals[0];
        this.temp = convertToFloat(vals[1], 100);
        this.voltage = convertToFloat(vals[2], 100);
        this.current = convertToFloat(vals[3], 100);
        this.timeLeftHours = convertToFloat(vals[4], 1000);
    }

    public void print() {
        String s = "";
        s += "[";
        s += "Status: [" + status + "],  ";
        s += "Temp: [" + temp + "],  ";
        s += "Voltage: [" + voltage + "],  ";
        s += "Current: [" + current + "],  ";
        s += "Hours: [" + timeLeftHours + "]  ";
        s += "]";
        Log.d(TAG, "uart rx read (utf8): " + s);
    }

//    public void setBatteryByte(byte val) {
////        battery_voltage = batteryByteToVoltage(val);
//    }
//
//    float batteryByteToVoltage(byte data, float voltage_lo, float voltage_hi) {
//        return map(data, 0, 255, voltage_lo, voltage_hi);
//    }
}
