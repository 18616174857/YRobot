package com.yrobot.exo.app;

import android.app.Activity;
import android.app.AlertDialog;
import android.bluetooth.BluetoothGatt;
import android.content.Context;
import android.graphics.Paint;
import android.graphics.Typeface;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.v4.app.Fragment;
import android.support.v4.util.CircularArray;
import android.support.v7.app.ActionBar;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;
import android.view.View;
import android.widget.TextView;
import android.widget.Toast;

import com.github.mikephil.charting.charts.LineChart;
import com.github.mikephil.charting.data.Entry;
import com.github.mikephil.charting.data.LineData;
import com.github.mikephil.charting.data.LineDataSet;
import com.github.mikephil.charting.interfaces.datasets.ILineDataSet;
import com.google.common.collect.EvictingQueue;
import com.yrobot.exo.R;
import com.yrobot.exo.app.data.FirmwareManager;
import com.yrobot.exo.app.data.ParamManager;
import com.yrobot.exo.app.utils.CRC8;
import com.yrobot.exo.app.data.ChartManager;
import com.yrobot.exo.app.data.DataPacket;
import com.yrobot.exo.app.data.ExoData;
import com.yrobot.exo.app.utils.MsgStat;
import com.yrobot.exo.app.data.SeekBarManager;
import com.yrobot.exo.ble.BleUtils;
import com.yrobot.exo.ble.central.BlePeripheral;
import com.yrobot.exo.ble.central.BlePeripheralUart;
import com.yrobot.exo.ble.central.BleScanner;
import com.yrobot.exo.ble.central.UartDataManager;
import com.yrobot.exo.utils.DialogUtils;

import java.lang.ref.WeakReference;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Queue;

import static com.yrobot.exo.app.YrConstants.BIT_RECORDED;
import static com.yrobot.exo.app.YrConstants.BIT_RECORDING_DONE;
import static com.yrobot.exo.app.YrConstants.IDX_CRC;
import static com.yrobot.exo.app.YrConstants.IDX_DATA;
import static com.yrobot.exo.app.YrConstants.IDX_KEY;
import static com.yrobot.exo.app.YrConstants.IDX_LEN;
import static com.yrobot.exo.app.YrConstants.KEY_CALIBRATE_CMD;
import static com.yrobot.exo.app.YrConstants.KEY_ENABLE;
import static com.yrobot.exo.app.YrConstants.KEY_FEEDBACK_PACKET_LEG_BOARD;
import static com.yrobot.exo.app.YrConstants.KEY_FEEDBACK_PACKET_MOTOR;
import static com.yrobot.exo.app.YrConstants.KEY_FEEDBACK_PACKET_STATUS;
import static com.yrobot.exo.app.YrConstants.KEY_FIRMWARE_PACKET;
import static com.yrobot.exo.app.YrConstants.KEY_FIRMWARE_PACKET_META;
import static com.yrobot.exo.app.YrConstants.KEY_PACKET_SELECT;
import static com.yrobot.exo.app.YrConstants.KEY_PARAM_REQUEST;
import static com.yrobot.exo.app.YrConstants.KEY_RESTART;
import static com.yrobot.exo.app.YrConstants.USE_RX_RING_BUFFER;
import static com.yrobot.exo.app.YrConstants.USE_TX_RING_BUFFER;
import static com.yrobot.exo.app.YrConstants.addKey;
import static com.yrobot.exo.app.YrConstants.bytesToShort;
import static com.yrobot.exo.app.YrConstants.colors;

// helper class with common behaviour for all peripheral modules
public class ConnectedPeripheralFragment extends Fragment implements UartDataManager.UartDataManagerListener, BlePeripheral.CompletionHandler {

    @SuppressWarnings("unused")
    private final static String TAG = "yr-" + ConnectedPeripheralFragment.class.getSimpleName();

    // Fragment parameters
    protected static final String ARG_SINGLEPERIPHERALIDENTIFIER = "SinglePeripheralIdentifier";

    protected UartDataManager mUartDataManager;
    protected long mOriginTimestamp;
    protected List<BlePeripheralUart> mBlePeripheralsUart = new ArrayList<>();
    protected Activity mActivity;
    protected final Handler mMainHandler = new Handler(Looper.getMainLooper());
    protected BlePeripheral mBlePeripheral;
    HashMap<String, SeekBarManager> seekBarManagers;

    protected ArrayList<MsgStat> msgStats = new ArrayList<>();

    public enum MsgStatType {
        BLE_RX, BLE_TX, DISPLAY
    }

    private int mIntervalFastInit = 30;
    private int mIntervalFast = mIntervalFastInit;
    private int mIntervalTx = 10;
    private int mIntervalSlow = 500;
    private Handler mHandlerFast = new Handler();
    private Handler mHandlerTx = new Handler();
    private Handler mHandlerSlow = new Handler();

    public static final int RX_BUF_SIZE = 1000;

    public Queue<DataPacket> rxDataRingBuffer = EvictingQueue.create(RX_BUF_SIZE);

    protected CircularArray<DataPacket> txDataRingBuffer = new CircularArray<>();

    public long rx_count = 0;

    // Common interfaces
    public interface SuccessHandler {
        void result(boolean success);
    }

    public ConnectedPeripheralFragment() {
        seekBarManagers = new HashMap<>();
    }

    public void setActivity(Activity activity) {
        YrConstants.log(YrConstants.DEBUG, TAG + ".setActivity");
        mActivity = activity;
    }

    // region Fragment Lifecycle
    protected static Bundle createFragmentArgs(@Nullable String singlePeripheralIdentifier) {      // if singlePeripheralIdentifier is null, uses multi-connect
        Bundle args = new Bundle();
        args.putString(ARG_SINGLEPERIPHERALIDENTIFIER, singlePeripheralIdentifier);
        return args;
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (getArguments() != null) {
            String singlePeripheralIdentifier = getArguments().getString(ARG_SINGLEPERIPHERALIDENTIFIER);
            mBlePeripheral = BleScanner.getInstance().getPeripheralWithIdentifier(singlePeripheralIdentifier);
        }

        setHasOptionsMenu(true);

        msgStats = new ArrayList<>();
        for (MsgStatType stat : MsgStatType.values()) {
            msgStats.add(new MsgStat());
        }

        mHandlerFast = new Handler();
        mHandlerTx = new Handler();
        mHandlerSlow = new Handler();

        startRepeatingTask();
    }

    public String getDataRateString() {
        String str = "";
        str += "BLE Rx [" + String.format("%.1f", msgStats.get(MsgStatType.BLE_RX.ordinal()).getRateAverage()) + "] ";
        str += "BLE Tx [" + String.format("%.1f", msgStats.get(MsgStatType.BLE_TX.ordinal()).getRate()) + "] ";
        str += "UI [" + String.format("%.1f", msgStats.get(MsgStatType.DISPLAY.ordinal()).getRate()) + "] ";
        str += "Buf [" + rxDataRingBuffer.size() + "] ";
        return str;
    }

    long time_last = 0;
    long time_last_change = 0;
    long change_timeout = 500;

    private byte mLastPacket = 0;

    public boolean updateUiFunction() {
//        if (msgStats.get(MsgStatType.BLE_RX.ordinal()).getRate() < 1.0) {
//            if (mLastPacket != 0) {
//                sendPacketSelect(mLastPacket);
//            }
//        }

        if (!USE_RX_RING_BUFFER) {
            return false;
        }
        if (rxDataRingBuffer.isEmpty()) {
            return false;
        } else {
            int num = rxDataRingBuffer.size();

            try {
                DataPacket packet = rxDataRingBuffer.remove();

                if (packet == null) {
                    return false;
                }

                setRxData(packet.data);

                long time = System.currentTimeMillis();
                long dt = packet.timestamp - time_last;
                long dt_change = time - time_last_change;
                time_last = packet.timestamp;

//            Log.v(TAG, "Empty Ring [" + num + "] Dt [" + dt + "] Interval [" + mIntervalFast + "]");

                if (dt_change > change_timeout) {
                    if (num > 10) {
                        mIntervalFast--;
                        mIntervalFast = Math.max(5, mIntervalFast);
                        time_last_change = time;
                    } else {
                        if (mIntervalFast < mIntervalFastInit) {
                            mIntervalFast++;
                            time_last_change = time;
                        }
                    }
                }
            } catch (NoSuchElementException e) {
                Log.e(TAG, "remove element [" + e + "]");
                return false;
            }

            return true;
        }
    }

    Runnable mRunnableFast = new Runnable() {
        @Override
        public void run() {
            try {
                updateUiFunction();
            } finally {
                mHandlerFast.postDelayed(mRunnableFast, mIntervalFast);
            }
        }
    };

    volatile boolean isTxThreadRunning = false;

    Runnable mRunnableTx = new Runnable() {
        @Override
        public void run() {
            isTxThreadRunning = true;
            try {
                updateUiFunctionTx();
            } catch (Exception e) {
                e.printStackTrace();
                isTxThreadRunning = false;
            } finally {
                mHandlerTx.postDelayed(mRunnableTx, mIntervalTx);
            }
        }
    };

    Runnable mRunnableSlow = new Runnable() {
        @Override
        public void run() {
            try {
                updateUiFunctionSlow();
            } finally {
                mHandlerSlow.postDelayed(mRunnableSlow, mIntervalSlow);
            }
        }
    };

    void startRepeatingTask() {
        mRunnableFast.run();
        mRunnableSlow.run();
    }

    void startTxThread() {
        mRunnableTx.run();
        isTxThreadRunning = true;
    }

    void stopTxThread() {
        mHandlerTx.removeCallbacks(mRunnableTx);
        isTxThreadRunning = false;
    }

    void stopRepeatingTask() {
        mHandlerFast.removeCallbacks(mRunnableFast);
        mHandlerSlow.removeCallbacks(mRunnableSlow);
    }

    // region UartDataManagerListener
    public void onRxData() {
    }

    public void onRxFirstMessage() {
    }

    public void updateDfuProgress(int progress, int index, boolean done) {
    }

    public void setRxData(byte[] data) {
//        if (rx_count % 3 == 0) {
        ExoData.getInstance().setPacket(data);
        onRxData();
//        }
    }

    public byte[] getMetadataBuffer() {
        int sz = FirmwareManager.getInstance().getFileSize();
        int num_packets = FirmwareManager.getInstance().getNumPackets();
        Log.v(TAG, "Got metadata request [" + sz + "] num packets [" + num_packets + "]");
        byte[] byte_buffer = new byte[6];
        if (sz > 0) {
            byte_buffer[0] = (byte) (sz & 0xff);
            byte_buffer[1] = (byte) ((sz >> 8) & 0xff);
            byte_buffer[2] = (byte) ((sz >> 16) & 0xff);
            byte_buffer[3] = (byte) ((sz >> 24) & 0xff);
            byte_buffer[4] = (byte) (num_packets & 0xff);
            byte_buffer[5] = (byte) ((num_packets >> 8) & 0xff);
        }
        return byte_buffer;
    }

    public void onUploadRecordedData() {
        new Handler(Looper.getMainLooper()).post(() -> {
            Toast.makeText(getContext(), "Done uploading recorded data [" + ExoData.getInstance().rxRecordedDataRingBuffer.size() + "]", Toast.LENGTH_SHORT).show();
        });
        Log.v(TAG, "Uploaded recorded data [" + ExoData.getInstance().rxRecordedDataRingBuffer.size() + "]");
    }

    @Override
    public void onUartRx(@NonNull byte[] data, @NonNull String peripheralIdentifier) {

        if (data.length < 3) {
            return;
        }

        int key = data[IDX_KEY];

        if (key == KEY_FIRMWARE_PACKET) {
            int index = bytesToShort(data[1], data[2]);
            Log.v(TAG, "[" + rx_count + "] Rx Fw Pckt [" + index + "] (" + data[1] + ", " + data[2] + ")");
            if (index >= 0) {
                if (!isTxThreadRunning) {
                    startTxThread();
                }
                byte[] byte_buffer = FirmwareManager.getInstance().getByteBuffer(index);
                if (byte_buffer != null) {
                    byte[] bytes_to_send = addKey(byte_buffer, KEY_FIRMWARE_PACKET);
                    DataPacket packet = new DataPacket();
                    packet.setData(bytes_to_send);
                    packet.index = index;
                    sendByteBufferPacket(packet);
                    // update DFU progress
                    double progress_fraction = (double) index / (double) (FirmwareManager.getInstance().getNumPackets() - 1);
                    int progress = (int) Math.round(progress_fraction * 100.0);
                    updateDfuProgress(progress, index, false);
                } else {
                    updateDfuProgress(100, index, true);
                    stopTxThread();
                }
            }
        } else if (key == KEY_FIRMWARE_PACKET_META) {
            Log.v(TAG, "Rx FW Meta [" + BleUtils.bytesToHex2(data) + "]");
            byte[] byte_buffer = getMetadataBuffer();
            sendByteBuffer(KEY_FIRMWARE_PACKET_META, byte_buffer);
        } else {
            boolean is_packet = (key == KEY_FEEDBACK_PACKET_LEG_BOARD || key == KEY_FEEDBACK_PACKET_MOTOR || key == KEY_FEEDBACK_PACKET_STATUS);
            if (USE_RX_RING_BUFFER && is_packet) {
                DataPacket packet = new DataPacket();
                packet.setData(data);

                int skip_num = 1;
                boolean use_skip = false;

//                use_skip = rxDataRingBuffer.size() > 30;
//                if (rxDataRingBuffer.size() > 30) {
//                    skip_num = 2;
//                } else if (rxDataRingBuffer.size() > 100) {
//                    skip_num = 3;
//                }

//                Log.d(TAG, ".onUartRx: [" + key + "] [" + data.length + "] [" + BleUtils.bytesToHex2(data) + "]");

                boolean isRecordedPacket = (data[IDX_DATA] & BIT_RECORDED) == BIT_RECORDED;
                if (isRecordedPacket) {
                    boolean isRecordingDone = (data[IDX_DATA] & BIT_RECORDING_DONE) == BIT_RECORDING_DONE;
                    try {
                        ExoData.getInstance().rxRecordedDataRingBuffer.add(packet);
                        if (ExoData.getInstance().rxRecordedDataRingBuffer.size() % 10 == 0) {
                            Log.v(TAG, "Received recorded data [" + ExoData.getInstance().rxRecordedDataRingBuffer.size() + "]");
                        }
                        if (isRecordingDone) {
                            Log.v(TAG, "Received recorded data [" + ExoData.getInstance().rxRecordedDataRingBuffer.size() + "] Done.");
                            onUploadRecordedData();
                        }
                        use_skip = true;
                        skip_num = 5;
                    } catch (NoSuchElementException e) {
                        Log.e(TAG, "add to queue error [" + e.toString() + "]");
                    }
                } else {
                    if (!use_skip || (use_skip && (rx_count % skip_num == 0))) {
                        try {
                            rxDataRingBuffer.add(packet);
                        } catch (NoSuchElementException e) {
                            Log.e(TAG, "add to queue error [" + e.toString() + "]");
                        }
                    }
                }

                if (rx_count == 0) {
                    onRxFirstMessage();
                }

                onRxData();
            } else {
                ExoData.getInstance().setPacket(data);
                onRxData();
            }
            msgStats.get(MsgStatType.BLE_RX.ordinal()).add();
            rx_count++;
        }

        mUartDataManager.clearRxCache(peripheralIdentifier);
    }

    //---------------------------------------------------------------------------------//
    //  SEND
    //---------------------------------------------------------------------------------//
    protected void sendEnable(boolean enable) {
        sendBool(KEY_ENABLE, enable);
    }

    protected void sendCalibrateCmd() {
        sendBool(KEY_CALIBRATE_CMD, true);
    }

    protected BlePeripheralUart getBlePeripheral() {
        if (mBlePeripheralsUart == null) {
            return null;
        }
        if (mBlePeripheralsUart.isEmpty()) {
            return null;
        }
        return mBlePeripheralsUart.get(0);
    }

    //---------------------------------------------------------------------------------//
    //  SEND - PRIMITIVES
    //---------------------------------------------------------------------------------//
    protected long writeCount = 0;

    public void updateUiFunctionSlow() {
        for (MsgStat stat : msgStats) {
            stat.update();
        }
        if (!ParamManager.getInstance().isInitialized()) {
            sendPacketSelect(KEY_PARAM_REQUEST);
            new Handler(Looper.getMainLooper()).post(() -> {
                Toast.makeText(getContext(), "Request Params", Toast.LENGTH_SHORT).show();
            });
        }
    }

    public void updateUiFunctionTx() {
        if (USE_TX_RING_BUFFER) {
//            if (completed != BUSY) {
            emptyTxBuffer();
//            }
        }
    }

    DataPacket lastPackedAttempted = null;

    public static final int BUSY = 0;
    public static final int COMPLETE_FAIL = 1;
    public static final int COMPLETE_SUCESS = 2;

    private void emptyTxBuffer() {
        if (lastPackedAttempted != null) {
            Log.v(TAG, "emptyTxBuffer != null");
            return;
        }
        if (!txDataRingBuffer.isEmpty()) {
            if (mBlePeripheralsUart != null) {
                if (!mBlePeripheralsUart.isEmpty()) {
                    BlePeripheralUart peripheral = mBlePeripheralsUart.get(0);
                    DataPacket packet;
                    packet = txDataRingBuffer.getLast();
//                    packet = txDataRingBuffer.popLast();
                    lastPackedAttempted = packet;
                    if (packet != null) {
                        Log.v(TAG, "txRing - empty [" + packet.index + "] [" + txDataRingBuffer.size() + "] writes [" + writeCount + "]");
                        mUartDataManager.send(peripheral, packet.data, this);
                        completed = BUSY;
                    }
                }
            }
        }
    }

    public void completion(int status) {
//        Log.v(TAG, "txRing completion [" + writeCount + "] [" + status + "]");
        if (status == BluetoothGatt.GATT_SUCCESS) {
            completed = COMPLETE_SUCESS;
            writeCount++;
            if (!txDataRingBuffer.isEmpty()) {
                try {
                    DataPacket packet = txDataRingBuffer.getLast();
                    if (lastPackedAttempted.index == packet.index) {
                        txDataRingBuffer.popLast();
                    }
                    Log.v(TAG, "txRing completion [" + (lastPackedAttempted.index) + "] [" + packet.index + "] [" + status + "]");
                } catch (Exception e) {
                }
                lastPackedAttempted = null;
            } else {
                lastPackedAttempted = null;
            }
//            emptyTxBuffer();
//            completed = true;
        } else {
            try {
                Log.v(TAG, "txRing FAILED completion [" + (lastPackedAttempted.index) + "] [" + writeCount + "] [" + status + "]");
            } catch (Exception e) {
            }
            lastPackedAttempted = null;
            completed = COMPLETE_FAIL;
//            emptyTxBuffer();
        }
    }

    int completed = COMPLETE_SUCESS;

    public void sendByteBufferPacket(DataPacket packet) {
        if (mBlePeripheralsUart == null) {
            return;
        }
        if (mBlePeripheralsUart.isEmpty()) {
            return;
        }
        BlePeripheralUart peripheral = mBlePeripheralsUart.get(0);
        if (peripheral != null) {
//            YrConstants.log(YrConstants.DEBUG, TAG + ".sendByteBuffer [" + bytes.length + "]");
//            mUartDataManager.send(peripheral, addKey(bytes, key), null);
            if (USE_TX_RING_BUFFER) {
                Log.v(TAG, "txRing - add to buffer [" + packet.index + "] num: [" + txDataRingBuffer.size() + "] writes [" + writeCount + "]");
                txDataRingBuffer.addFirst(packet);
//                if (completed == COMPLETE_SUCESS && txDataRingBuffer.isEmpty()) {
//                    Log.v(TAG, "txRing - send direct [" + packet.index + "] num: [" + txDataRingBuffer.size() + "]");
//                    completed = BUSY;
//                    mUartDataManager.send(peripheral, packet.data, this);
//                } else {
//                    txDataRingBuffer.addFirst(packet);
////                    emptyTxBuffer();
//                    Log.v(TAG, "txRing - add to buffer [" + packet.index + "] num: [" + txDataRingBuffer.size() + "] writes [" + writeCount + "]");
//                }
            } else {
                mUartDataManager.send(peripheral, packet.data, null);
            }
        }
    }

    //---------------------------------------------------------------------------------//
    //  SEND OBJECTS
    //---------------------------------------------------------------------------------//
    private void sendBytes(byte[] bytes) {
        if (mBlePeripheralsUart == null) {
            return;
        }
        if (mBlePeripheralsUart.isEmpty()) {
            return;
        }
        BlePeripheralUart peripheral = mBlePeripheralsUart.get(0);
        mUartDataManager.send(peripheral, bytes, this);
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

    public void sendByteBuffer(byte key, byte[] data) {
        byte[] bytes = addPacketHeader(key, data);
        sendBytes(bytes);
    }

    public void sendFloat(byte key, float data) {
        byte[] data_bytes = ByteBuffer.allocate(4).putFloat(data).array();
        byte[] bytes = addPacketHeader(key, data_bytes);
        YrConstants.log(YrConstants.DEBUG, TAG + ".sendFloat [" + key + "] [" + data + "] [" + bytes.length + "]");
        sendBytes(bytes);
    }

    public void sendPacketSelect(byte packet) {
        sendInteger(KEY_PACKET_SELECT, packet);
        mLastPacket = packet;
    }

    public void sendBool(byte key, boolean data) {
        sendInteger(key, (byte) (data ? 1 : 0));
    }

    public void sendInteger(byte key, byte data) {
        byte[] data_bytes = ByteBuffer.allocate(1).order(ByteOrder.LITTLE_ENDIAN).put(data).array();
        byte[] bytes = addPacketHeader(key, data_bytes);
        YrConstants.log(YrConstants.DEBUG, TAG + ".sendInteger [" + key + "] [" + data + "] [" + bytes.length + "] [" + BleUtils.bytesToHex2(bytes) + "]");
        sendBytes(bytes);
    }

    //---------------------------------------------------------------------------------//
    //  SETUP UART
    //---------------------------------------------------------------------------------//
    protected void setupUartComplete() {
        Iterator it = seekBarManagers.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry pair = (Map.Entry) it.next();
            ((SeekBarManager) pair.getValue()).setup();
            it.remove();
        }
    }

    protected void setupUart() {
        // Line dashes assigned to peripherals
        if (!BlePeripheralUart.isUartInitialized(mBlePeripheral, mBlePeripheralsUart)) { // If was not previously setup (i.e. orientation change)
            BlePeripheralUart blePeripheralUart = new BlePeripheralUart(mBlePeripheral);
            mBlePeripheralsUart.add(blePeripheralUart);
            blePeripheralUart.uartEnable(mUartDataManager, status -> mMainHandler.post(() -> {
                if (status == BluetoothGatt.GATT_SUCCESS) {
                    if (getBlePeripheral() != null) {
                        Log.v(TAG, "yr-uart is uart enabled [" + getBlePeripheral().isUartEnabled() + "]");
                    }
                    setupUartComplete();
                } else {
                    Context context = getContext();
                    if (context != null) {
                        WeakReference<BlePeripheralUart> weakBlePeripheralUart = new WeakReference<>(blePeripheralUart);
                        AlertDialog.Builder builder = new AlertDialog.Builder(context);
                        AlertDialog dialog = builder.setMessage(R.string.uart_error_peripheralinit)
                                .setPositiveButton(android.R.string.ok, (dialogInterface, which) -> {
                                    BlePeripheralUart strongBlePeripheralUart = weakBlePeripheralUart.get();
                                    if (strongBlePeripheralUart != null) {
                                        strongBlePeripheralUart.disconnect();
                                    }
                                })
                                .show();
                        DialogUtils.keepDialogOnOrientationChanges(dialog);
                    }
                }
            }));
        }
    }

    @Override
    public void onDestroy() {
        stopRepeatingTask();
        if (mUartDataManager != null) {
            Context context = getContext();
            if (context != null) {
                mUartDataManager.setEnabled(context, false);
            }
        }

        if (mBlePeripheralsUart != null) {
            for (BlePeripheralUart blePeripheralUart : mBlePeripheralsUart) {
                blePeripheralUart.uartDisable();
            }
            mBlePeripheralsUart.clear();
            mBlePeripheralsUart = null;
        }

        super.onDestroy();
    }


    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        Context context = getContext();
        if (context != null) {
            mUartDataManager = new UartDataManager(context, this, true);
            mOriginTimestamp = System.currentTimeMillis();
            setupUart();
        }
    }

    // region Action Bar
    protected void setActionBarTitle(int titleStringId) {
        AppCompatActivity activity = (AppCompatActivity) getActivity();
        if (activity != null) {
            ActionBar actionBar = activity.getSupportActionBar();
            if (actionBar != null) {
                actionBar.setTitle(titleStringId);
                actionBar.setDisplayHomeAsUpEnabled(true);
            }
        }
    }
}
