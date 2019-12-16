package com.yrobot.exo.app.data;

import android.support.annotation.NonNull;
import android.util.Log;

import com.google.common.collect.EvictingQueue;
import com.yrobot.exo.app.utils.CRC8;

import java.util.Queue;

import static com.yrobot.exo.app.YrConstants.CHECK_CRC;
import static com.yrobot.exo.app.YrConstants.IDX_CRC;
import static com.yrobot.exo.app.YrConstants.IDX_DATA;
import static com.yrobot.exo.app.YrConstants.IDX_KEY;
import static com.yrobot.exo.app.YrConstants.IDX_LEN;
import static com.yrobot.exo.app.YrConstants.KEY_FEEDBACK_PACKET_LEG_BOARD;
import static com.yrobot.exo.app.YrConstants.KEY_FEEDBACK_PACKET_MOTOR;
import static com.yrobot.exo.app.YrConstants.KEY_FEEDBACK_PACKET_STATUS;
import static com.yrobot.exo.app.YrConstants.KEY_PARAM_REQUEST;
import static com.yrobot.exo.app.YrConstants.bytesToShort;
import static com.yrobot.exo.app.YrConstants.map;

public class ExoData {

    private final static String TAG = "yr-" + ExoData.class.getSimpleName();

    public LegData legDataR = new LegData("R");
    public LegData legDataL = new LegData("L");
    public MotorData motorDataR = new MotorData("R");
    public MotorData motorDataL = new MotorData("L");

    public int battery_soc = 100;
    public float battery_voltage = 24.0f;

    public float mGaitCyclePercent = 0.0f;
    public int mGaitPhase = 1;

    public float voltage = 0;
    public float current = 0;
    public float temp = 0;
    public float timeLeftHours = 10;

    private boolean newStatus = false;

    public static final int RX_RECORDED_DATA_BUF_SIZE = 10000;
    public Queue<DataPacket> rxRecordedDataRingBuffer = EvictingQueue.create(RX_RECORDED_DATA_BUF_SIZE);

    public boolean mStreaming = true;

    byte[] data_last = null;
    long time_last_rx = 0;

    private static final int offset = IDX_DATA + 1;

    private ExoData() {
    }

    private static volatile ExoData sSoleInstance = new ExoData();

    public static ExoData getInstance() {
        return sSoleInstance;
    }

//    public void setBatteryByte(byte val) {
//        battery_voltage = batteryByteToVoltage(val);
//    }

    float batteryByteToVoltage(byte data, float voltage_lo, float voltage_hi) {
        return map(data, 0, 255, voltage_lo, voltage_hi);
    }

    float batteryByteToVoltage(byte data) {
        return batteryByteToVoltage(data, 18.0f, 28.0f);
    }

    public GaitTuningData gait_tuning_data = new GaitTuningData();

    private void parsePacketMotor(@NonNull byte[] data) {
        byte status = data[offset + 0];
//        byte stamp = data[offset + 1];
        short int_vals[] = new short[6];
        for (int i = 0; i < int_vals.length; i++) {
            int offset2 = (i * 2) + (offset + 1);
            try {
                int_vals[i] = (short) ((data[offset2] & 0xff) + ((data[offset2 + 1] & 0xff) << 8));
            } catch (ArrayIndexOutOfBoundsException e) {
                break;
            }
        }
//        mGaitPhase = stamp;
        motorDataL.set(int_vals[0], int_vals[1], int_vals[2]);
        motorDataL.setRequest(int_vals[3], int_vals[4], int_vals[5]);

//        motorDataL.print();

        motorDataL.setStatus(status);
        motorDataR.setStatus((byte) ((status >> 4) & (byte) 0x0f));
    }

    private void parsePacketLegBoard(@NonNull byte[] data) {
        byte status = data[offset + 0];
        byte gaitCyclePhase = data[offset + 1];
        byte gaitCyclePercent = data[offset + 2];
        short int_vals[] = new short[2];
        for (int i = 0; i < int_vals.length; i++) {
            int offset2 = (i * 2) + (offset + 3);
            try {
                int_vals[i] = (short) ((data[offset2] & 0xff) + ((data[offset2 + 1] & 0xff) << 8));
            } catch (ArrayIndexOutOfBoundsException e) {
                break;
            }
        }
        mGaitCyclePercent = gaitCyclePercent;
        mGaitPhase = gaitCyclePhase;

        motorDataL.setStatus(status);
        motorDataR.setStatus((byte) ((status >> 4) & (byte) 0x0f));

//        legDataL.imu[0].set(int_vals[0], int_vals[2], int_vals[4]);
//        legDataL.imu[1].set(int_vals[1], int_vals[3], int_vals[5]);
//        legDataL.set((short) 0, int_vals[6], int_vals[7]);
        legDataL.set((short) 0, int_vals[0], int_vals[1]);
    }

    public boolean hasStatusUpdate() {
        boolean tmp = newStatus;
        newStatus = false;
        return tmp;
    }

    private void parsePacketStatus(@NonNull byte[] data) {
        short temp_byte = bytesToShort(data[offset + 1], data[offset + 2]);
        short voltage_byte = bytesToShort(data[offset + 3], data[offset + 4]);
        short current_byte = bytesToShort(data[offset + 5], data[offset + 6]);
        timeLeftHours = ((float) bytesToShort(data[offset + 7], data[offset + 8])) / 1000.0f;
        temp = ((float) temp_byte) / 100.0f;
        voltage = ((float) voltage_byte) / 100.0f;
        current = ((float) current_byte) / 100.0f;
        newStatus = true;
    }

    public void setPacket(@NonNull byte[] data) {
        long time = System.currentTimeMillis();
        long dt = time - time_last_rx;
        time_last_rx = time;

        byte key = data[IDX_KEY];
        byte data_len = data[IDX_LEN];
        byte crc = data[IDX_CRC];
        byte crcCalculated = CRC8.arrayUpdate(data, Math.min(IDX_DATA + data_len, data.length));

        if (CHECK_CRC) {
            if (crc != crcCalculated) {
                Log.v(TAG, "setPacket CRC Mismatch Key: [" + key + "] BufferLen: [" + data.length + "] DataLen: [" + data_len + " ] CRC [" + crc + " | " + crcCalculated + "]");
                return;
            }
        }

        String s = "[" + dt + "] [" + data.length + "]";
//        Log.v(TAG, "setPacket [" + key + "] [" + data_len + " ] CRC [" + crc + " | " + crcCalculated + "] [" + BleUtils.bytesToHex2(data) + "]");

        switch (key) {
            case KEY_PARAM_REQUEST:
                ParamManager.getInstance().updateParams(data);
                break;
            case KEY_FEEDBACK_PACKET_MOTOR:
                parsePacketMotor(data);
                break;
            case KEY_FEEDBACK_PACKET_LEG_BOARD:
                parsePacketLegBoard(data);
                break;
            case KEY_FEEDBACK_PACKET_STATUS:
                parsePacketStatus(data);
                break;
            default:
                break;
        }

        data_last = data;
    }

    private void printStatus() {
        String s = "";
        s += "[";
        s += "Temp: [" + temp + "],  ";
        s += "Voltage: [" + voltage + "],  ";
        s += "Current: [" + current + "],  ";
        s += "Hours: [" + timeLeftHours + "]  ";
        s += "]";
        Log.d(TAG, "uart rx read (utf8): " + s);
    }

    public void printDebug() {
        motorDataL.print();
        legDataR.print();
        legDataL.print();
    }
}
