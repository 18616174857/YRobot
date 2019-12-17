package com.yrobot.exo.app.data;

import android.os.Handler;


import android.support.annotation.NonNull;
import android.util.Log;

import com.yrobot.exo.app.ConnectedPeripheralFragment;
import com.yrobot.exo.app.utils.CRC8;

//import exo_api.PersonOuterClass;

import static com.yrobot.exo.app.YrConstants.CHECK_CRC;
import static com.yrobot.exo.app.YrConstants.IDX_CRC;
import static com.yrobot.exo.app.YrConstants.IDX_DATA;
import static com.yrobot.exo.app.YrConstants.IDX_KEY;
import static com.yrobot.exo.app.YrConstants.IDX_LEN;
import static com.yrobot.exo.app.YrConstants.KEY_FEEDBACK_PACKET_LEG_BOARD;
import static com.yrobot.exo.app.YrConstants.KEY_FEEDBACK_PACKET_MOTOR;
import static com.yrobot.exo.app.YrConstants.KEY_FEEDBACK_PACKET_STATUS;
import static com.yrobot.exo.app.YrConstants.KEY_PACKET_SELECT;
import static com.yrobot.exo.app.YrConstants.MODE_IDLE;
import static com.yrobot.exo.app.YrConstants.MODE_STREAMING;
import static com.yrobot.exo.app.YrConstants.SIDE_LEFT;
import static com.yrobot.exo.app.YrConstants.millis;

public class MockHardware {

    private final static String TAG = MockHardware.class.getSimpleName();

    private boolean mRunning;
    private int mIntervalFast = 30;
    private long mLoopCount = 0;
    private Handler mHandlerFast = new Handler();

    private int mPacketSelected = KEY_FEEDBACK_PACKET_MOTOR;

    private int mMode = MODE_STREAMING;
    private int mModeLast = MODE_STREAMING;

    private int mSelectedSide = SIDE_LEFT;

    public MotorData[] motors;
    public LegData[] legs;
    public SystemData systemData;

    private MockHardware() {
        mRunning = false;
        systemData = new SystemData();
        motors = new MotorData[2];
        legs = new LegData[2];
        for (int i = 0; i < motors.length; i++) {
            motors[i] = new MotorData(i == SIDE_LEFT ? "L" : "R");
            legs[i] = new LegData(i == SIDE_LEFT ? "L" : "R");
        }
    }

    public interface MockDeviceHandler {
        void onMockUartRx(@NonNull byte[] data);
    }

    private MockDeviceHandler mockDeviceHandler = null;

    public void setCallback(ConnectedPeripheralFragment fragment) {
        mockDeviceHandler = fragment;
    }

    private static volatile MockHardware sSoleInstance = new MockHardware();

    public static MockHardware getInstance() {
        return sSoleInstance;
    }

    public void start() {
        Log.v(TAG, "start()");
        mRunning = true;
        mRunnableFast.run();
    }

    public void stop() {
        Log.v(TAG, "stop()");
        mHandlerFast.removeCallbacks(mRunnableFast);
    }

    Runnable mRunnableFast = new Runnable() {
        @Override
        public void run() {
            try {
                updateFunction();
                mLoopCount++;
            } finally {
                mHandlerFast.postDelayed(mRunnableFast, mIntervalFast);
            }
        }
    };

    private void updateFunction() {
        switch (mMode) {
            case MODE_STREAMING: {
                switch (mPacketSelected) {
                    case KEY_FEEDBACK_PACKET_LEG_BOARD: {
                        float mag = 5.0f;
                        float val = mag + mag * (float) Math.sin(millis() / 1000.0);
                        legs[mSelectedSide].gaitCyclePercent = (byte) ((millis() / 10) % 100);
                        legs[mSelectedSide].knee_angle = val;
                        legs[mSelectedSide].ankle_angle = val;
                        byte[] bytes = legs[mSelectedSide].getByteArray();
                        bytes = addPacketHeader(KEY_FEEDBACK_PACKET_LEG_BOARD, bytes);
                        sendBytesToApp(bytes);
                    }
                    break;
                    case KEY_FEEDBACK_PACKET_MOTOR: {
                        float mag = 3.0f;
                        motors[mSelectedSide].position = mag * (float) Math.sin(millis() / 1000.0);
                        motors[mSelectedSide].velocity = mag * (float) Math.sin(millis() / 1000.0);
                        motors[mSelectedSide].current = mag * (float) Math.sin(millis() / 1000.0);
                        byte[] bytes = motors[mSelectedSide].getByteArray();
                        bytes = addPacketHeader(KEY_FEEDBACK_PACKET_MOTOR, bytes);
                        sendBytesToApp(bytes);
                    }
                    break;
                    default:
                        break;
                }

                int loopsPerSecond = Math.round(1000.0f / mIntervalFast);
                if (mLoopCount % loopsPerSecond == 0) {
                    float sineVal = (float) Math.sin(millis() / 1000.0);
                    systemData.status = 290;
                    systemData.temp = 23.5f + 0.3f * sineVal;
                    systemData.voltage = 24.5f + 0.4f * sineVal;
                    systemData.current = 0.0f + 3.0f * sineVal;
                    systemData.timeLeftHours = 8.0f + 0.5f * sineVal;
                    byte[] bytes = addPacketHeader(KEY_FEEDBACK_PACKET_STATUS, systemData.getByteArray());
                    sendBytesToApp(bytes);
                }
            }
            break;
        }
    }

    private void sendBytesToApp(@NonNull byte[] data) {
        if (mockDeviceHandler != null) {
            mockDeviceHandler.onMockUartRx(data);
        }
    }

    //---------------------------------------------------------------------------------//
    //  SEND OBJECTS
    //---------------------------------------------------------------------------------//
    public void sendBytesToMockDevice(byte[] data) {
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

        mMode = MODE_IDLE;

        switch (key) {
            case KEY_PACKET_SELECT:
                mPacketSelected = data[IDX_DATA];
                Log.v(TAG, "Mock packet selected [" + mPacketSelected + "]");
                mMode = MODE_STREAMING;
                break;
        }

        if (mMode != mModeLast) {
            Log.v(TAG, "Mode changed [" + mModeLast + "] -> [" + mMode + "]");
        }
        mModeLast = mMode;
    }

    protected byte[] addPacketHeader(byte key, byte[] data) {
        byte[] b = new byte[data.length + IDX_DATA];
        b[IDX_KEY] = key;
        b[IDX_CRC] = 0;
        b[IDX_LEN] = (byte) data.length;
        for (int i = 0; i < data.length; i++) {
            b[IDX_DATA + i] = data[i];
        }
        b[IDX_CRC] = CRC8.arrayUpdate(b);
        return b;
    }
}
