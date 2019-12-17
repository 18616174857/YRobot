package com.yrobot.exo.app.data;

import android.support.annotation.NonNull;
import android.util.Log;

import com.google.common.collect.EvictingQueue;
import com.yrobot.exo.app.utils.CRC8;
import com.yrobot.exo.app.utils.ParamManager;
import com.yrobot.exo.ble.BleUtils;

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

    private final static String TAG = ExoData.class.getSimpleName();

    public LegData legDataR = new LegData("R");
    public LegData legDataL = new LegData("L");
    public MotorData motorDataR = new MotorData("R");
    public MotorData motorDataL = new MotorData("L");
    public SystemData systemData = new SystemData();
    public GaitTuningData gait_tuning_data = new GaitTuningData();

    private boolean mNewStatus = false;
    private boolean mPrintRx = false;

    public boolean mStreaming = true;
    public static final int RX_RECORDED_DATA_BUF_SIZE = 10000;
    public Queue<DataPacket> rxRecordedDataRingBuffer = EvictingQueue.create(RX_RECORDED_DATA_BUF_SIZE);

    byte[] data_last = null;
    long time_last_rx = 0;

    private ExoData() {
    }

    private static volatile ExoData sSoleInstance = new ExoData();

    public static ExoData getInstance() {
        return sSoleInstance;
    }

    public boolean hasStatusUpdate() {
        boolean tmp = mNewStatus;
        mNewStatus = false;
        return tmp;
    }

    public void setPacket(@NonNull byte[] data) {

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

        if (mPrintRx) {
            long time = System.currentTimeMillis();
            long dt = time - time_last_rx;
            time_last_rx = time;
            Log.v(TAG, "setPacket [" + key + "] [" + data_len + " ] CRC [" + crc + " | " + crcCalculated + "] [" + BleUtils.bytesToHex2(data) + "]");
        }

        switch (key) {
            case KEY_PARAM_REQUEST:
                ParamManager.getInstance().updateParams(data);
                break;
            case KEY_FEEDBACK_PACKET_MOTOR:
                motorDataL.setFromByteArray(data);
                break;
            case KEY_FEEDBACK_PACKET_LEG_BOARD:
                legDataL.setFromByteArray(data);
                break;
            case KEY_FEEDBACK_PACKET_STATUS:
                systemData.setFromByteArray(data);
                mNewStatus = true;
                break;
            default:
                break;
        }

        data_last = data;
    }


    public void printDebug() {
        motorDataL.print();
        legDataR.print();
        legDataL.print();
    }
}
