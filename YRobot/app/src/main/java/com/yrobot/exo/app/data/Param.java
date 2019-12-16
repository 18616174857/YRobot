package com.yrobot.exo.app.data;

import android.support.annotation.NonNull;
import android.util.Log;

import com.yrobot.exo.ble.BleUtils;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;

import static com.yrobot.exo.app.YrConstants.IDX_DATA;

public class Param<T> {

    private final static String TAG = "yr-" + Param.class.getSimpleName();

    public static final byte PARAM_TYPE_INT8 = 0;
    public static final byte PARAM_TYPE_INT16 = 1;
    public static final byte PARAM_TYPE_INT32 = 2;
    public static final byte PARAM_TYPE_FLOAT = 3;
    public static final byte PARAM_TYPE_BOOL = 4;
    public static final byte PARAM_TYPE_SIGNAL = 5;

    private int getLen() {
        switch (this.type) {
            case PARAM_TYPE_INT8:
            case PARAM_TYPE_BOOL:
            case PARAM_TYPE_SIGNAL:
                return 1;
            case PARAM_TYPE_INT16:
                return 2;
            case PARAM_TYPE_INT32:
            case PARAM_TYPE_FLOAT:
                return 4;
        }
        return 1;
    }

    public static final byte KEY_PARAM_DONE_LOADING = (byte) 0xff;

    public static final byte IDX_DATA_KEY = IDX_DATA;
    public static final byte IDX_DATA_TYPE = IDX_DATA + 1;

    public Param(T v, @NonNull byte[] data) {
        this.val = v;
        this.min = v;
        this.max = v;
        update(data);
        Log.v(TAG, "Param::Create [" + key + "] [" + type + "]");
    }

    public void update(@NonNull byte[] data) {
        int offset = IDX_DATA;
        this.key = data[offset];
        offset += 1;
        this.type = data[offset];
        offset += 1;
        this.len = getLen();

        this.min = getValFromBuffer(data, offset, this.len);
        offset += this.len;
        this.max = getValFromBuffer(data, offset, this.len);
        offset += this.len;
        this.val = getValFromBuffer(data, offset, this.len);
        offset += this.len;

        //        Log.v(TAG, "Param::update [" + BleUtils.bytesToHex2(data) + "]");

        this.name_len = data[offset];
        offset += 1;
        byte[] str_slice = Arrays.copyOfRange(data, offset, offset + name_len);
        Log.v(TAG, "Param::update [" + BleUtils.bytesToHex2(str_slice) + "]");
        byte[] str_bytes = ByteBuffer.allocate(name_len).wrap(str_slice).order(ByteOrder.BIG_ENDIAN).array();
        this.name = new String(str_bytes, StandardCharsets.UTF_8);
        Log.v(TAG, "Param::update key: [" + key + "] type: [" + type + "] len [" + len + "]" +
                " name_len: [" + name_len + "] name: [" + this.name + "] min/max: (" + str(min) + ", " + str(max) + ") val: [" + str(val) + "]");
    }

    public void setVal(float v) {
        if (val instanceof Short) {
            val = (T) (Short) (short) v;
        } else if (val instanceof Integer) {
            val = (T) (Integer) (int) v;
        } else if (val instanceof Byte) {
            val = (T) (Byte) (byte) v;
        } else {
            val = (T) (Float) v;
        }
        this.val = val;
    }

    public T get() {
        return this.val;
    }

    public float getVal() {
        if (val instanceof Short) {
            return (short) (Short) val;
        } else if (val instanceof Integer) {
            return (int) (Integer) val;
        } else if (val instanceof Byte) {
            return (byte) (Byte) val;
        } else {
            return (Float) val;
        }
    }

    public byte[] getBytes(float v) {
        ByteBuffer buffer = ByteBuffer.allocate(this.len).order(ByteOrder.LITTLE_ENDIAN);
        if (val instanceof Float) {
            buffer.putFloat(v);
        } else {
            long vr = Math.round(v);
            if (val instanceof Short) {
                buffer.putShort((short) vr);
            } else if (val instanceof Integer) {
                buffer.putInt((int) vr);
            } else if (val instanceof Byte) {
                buffer.put((byte) vr);
            }
        }
        return buffer.array();
    }

    public float getMin() {
        if (min instanceof Short) {
            return (short) (Short) min;
        } else if (min instanceof Integer) {
            return (int) (Integer) min;
        } else if (min instanceof Byte) {
            return (byte) (Byte) min;
        } else {
            return (Float) min;
        }
    }

    public float getMax() {
        if (max instanceof Short) {
            return (short) (Short) max;
        } else if (max instanceof Integer) {
            return (int) (Integer) max;
        } else if (max instanceof Byte) {
            return (byte) (Byte) max;
        } else {
            return (Float) max;
        }
    }

    private String str(T v) {
        if (v instanceof Float) {
            return String.format("%.2f", v);
        } else {
            return String.format("%d", v);
        }
    }

    private T getValFromBuffer(@NonNull byte[] data, int idx, int length) {
        byte[] slice = Arrays.copyOfRange(data, idx, idx + length);
        ByteBuffer buffer = ByteBuffer.wrap(slice).order(ByteOrder.LITTLE_ENDIAN);
//        Log.v(TAG, "Param::getValFromBuffer [" + slice.length + "] [" + idx + " - " + length + "] Class [" + val.getClass().getTypeName() + "]");
        if (val instanceof Float) {
            float v = ByteBuffer.wrap(slice).getFloat();
            return (T) (Float) buffer.getFloat();
        } else if (val instanceof Byte) {
            return (T) (Byte) buffer.get();
        } else if (val instanceof Short) {
            return (T) (Short) buffer.getShort();
        } else if (val instanceof Integer) {
            return (T) (Integer) buffer.getInt();
        }
        return null;
    }

    private byte[] getBytesFromVal(T v) {
        if (val instanceof Float) {
            return ByteBuffer.allocate(this.len).putFloat(((Float) val).floatValue()).array();
        } else if (val instanceof Integer) {
            return ByteBuffer.allocate(this.len).putInt(((Integer) val).intValue()).array();
        }
        return null;
    }

    public int key;
    public int type;
    public int len;
    public T val;
    public T min;
    public T max;
    public int name_len = 1;
    public String name = "";
}
