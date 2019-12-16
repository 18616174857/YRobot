package com.yrobot.exo.app.views;

import android.app.Dialog;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.design.widget.Snackbar;
import android.support.v4.app.FragmentActivity;
import android.support.v7.widget.SwitchCompat;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.ProgressBar;
import android.widget.TextView;

import com.daimajia.numberprogressbar.NumberProgressBar;
import com.github.mikephil.charting.components.YAxis;
import com.sdsmdg.harjot.crollerTest.Croller;
import com.xw.repo.BubbleSeekBar;
import com.yrobot.exo.R;
import com.yrobot.exo.app.ConnectedPeripheralFragment;
import com.yrobot.exo.app.data.ChartManager;
import com.yrobot.exo.app.data.DataPacket;
import com.yrobot.exo.app.data.ExoData;
import com.yrobot.exo.app.data.FirmwareManager;
import com.yrobot.exo.app.data.MotorData;
import com.github.mikephil.charting.charts.LineChart;
import com.yrobot.exo.app.YrConstants;
import com.yrobot.exo.app.data.Param;
import com.yrobot.exo.app.data.ParamManager;

import java.lang.ref.WeakReference;
import java.text.DecimalFormat;
import java.util.ArrayList;

import static com.yrobot.exo.app.YrConstants.KEY_PARAM_SET;
import static com.yrobot.exo.app.YrConstants.configBubbleSeekBar;
import static com.yrobot.exo.app.YrConstants.dpToPx;
import static com.yrobot.exo.app.YrConstants.map;
import static com.yrobot.exo.app.YrConstants.setTextView;
import static com.yrobot.exo.app.data.MotorData.USE_REQUEST;
import static com.yrobot.exo.app.YrConstants.KEY_DATA_RECORDER;
import static com.yrobot.exo.app.YrConstants.KEY_DATA_RECORDER_START;
import static com.yrobot.exo.app.YrConstants.KEY_DATA_RECORDER_STOP;
import static com.yrobot.exo.app.YrConstants.KEY_DFU_START;
import static com.yrobot.exo.app.YrConstants.KEY_DFU_START_CANCEL;
import static com.yrobot.exo.app.YrConstants.KEY_DFU_START_ENABLE;
import static com.yrobot.exo.app.YrConstants.KEY_FEEDBACK_PACKET_MOTOR;
import static com.yrobot.exo.app.YrConstants.TEXT_RECORD;
import static com.yrobot.exo.app.YrConstants.TEXT_STOP;

public class ControlFragment extends ConnectedPeripheralFragment {
    // Log
    private final static String TAG = "yr-" + ControlFragment.class.getSimpleName();

    private Button btnCalibration;
    private Button btnRecord;
    private Button btnReset;
    private ProgressBar progressBar;
    private TextView tvEnabled;
    private SwitchCompat enableSwitch;
    private TextView tvStreaming;
    private SwitchCompat streamingSwitch;
    private TextView tvCurrent;
    private TextView tvVoltage;
    private TextView tvTemp;
    private TextView tvTimeLeftHours;
    private Croller crollerLeft;
    private Croller crollerRight;
    private Croller crollerAccel;
    private Croller crollerVel;

    BubbleSeekBar mSeekBarXZoom;
    BubbleSeekBar mSeekBarXOffset;

    private Handler timerHandler;
    private Runnable timerRunnable;

    Dialog mDfuDialog = null;

    ChartManager chartManagerMotorPos;
    ChartManager chartManagerMotorVel;
    ChartManager chartManagerMotorCur;
    ArrayList<String> dataLabelsMotorPos = new ArrayList<>();
    ArrayList<String> dataLabelsMotorVel = new ArrayList<>();
    ArrayList<String> dataLabelsMotorCur = new ArrayList<>();

    private boolean mEnabled = false;
    private boolean mEnabledLast = false;
    volatile boolean mShowRecordedData = false;

    public static ControlFragment newInstance(@Nullable String singlePeripheralIdentifier) {
        ControlFragment fragment = new ControlFragment();
        fragment.setArguments(createFragmentArgs(singlePeripheralIdentifier));
        return fragment;
    }

    public ControlFragment() {
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setRetainInstance(true);
    }

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        // Inflate the layout for this fragment
        View view = inflater.inflate(R.layout.fragment_control, container, false);

        tvEnabled = view.findViewById(R.id.tvEnable);
        tvStreaming = view.findViewById(R.id.tvStreaming);

        tvCurrent = view.findViewById(R.id.tvCurrent);
        tvTemp = view.findViewById(R.id.tvTemp);
        tvVoltage = view.findViewById(R.id.tvVoltage);
        tvTimeLeftHours = view.findViewById(R.id.tvTimeLeftHours);

        progressBar = view.findViewById(R.id.progressBarCalibration);
        progressBar.setVisibility(View.GONE);

        btnRecord = view.findViewById(R.id.btn_record);
        btnRecord.setOnClickListener(v -> {
            Button btn = (Button) v;
            String text = btn.getText().toString();
            if (text.equals(TEXT_RECORD)) {
                sendInteger(KEY_DATA_RECORDER, KEY_DATA_RECORDER_START);
                ExoData.getInstance().rxRecordedDataRingBuffer.clear();
                btnRecord.setText(TEXT_STOP);
            } else if (text.equals(TEXT_STOP)) {
                sendInteger(KEY_DATA_RECORDER, KEY_DATA_RECORDER_STOP);
                btnRecord.setText(TEXT_RECORD);
            }
        });

        btnCalibration = view.findViewById(R.id.btnCalibration);
        btnCalibration.setOnClickListener(v -> {
            sendCalibrateCmd();
            Snackbar snackbar = Snackbar.make(view, "Running calibration", Snackbar.LENGTH_SHORT);
            snackbar.show();
            progressBar.setVisibility(View.VISIBLE);
            setTimer(10000);
        });

        btnReset = view.findViewById(R.id.btnResetFirmware);
        btnReset.setOnClickListener(btn -> {

            mDfuDialog = new Dialog(getContext());
            mDfuDialog.setContentView(R.layout.dialog_dfu);
            mDfuDialog.setTitle("Dialog Title");

            TextView tvTitle = mDfuDialog.findViewById(R.id.dfuTvTitle);
            tvTitle.setText("Firmware Upgrade");

            TextView tvStatus = mDfuDialog.findViewById(R.id.dfuTvStatus);
            tvStatus.setText("Status");
            tvStatus.setVisibility(View.GONE);

            updateDfuProgress(0, 0, false);

            Button dfuBtnStart = mDfuDialog.findViewById(R.id.dfuBtnStart);
            dfuBtnStart.setOnClickListener(v -> {
                Log.v(TAG, "Btn DFU Start");
                byte[] byte_buffer = getMetadataBuffer();
                byte[] bytes = new byte[7];
                bytes[0] = KEY_DFU_START_ENABLE;
                for (int i = 0; i < byte_buffer.length; i++) {
                    bytes[i + 1] = byte_buffer[i];
                }
                sendByteBuffer(KEY_DFU_START, bytes);
                tvStatus.setVisibility(View.VISIBLE);
                v.setVisibility(View.GONE);
            });

            Button dfuBtnCancel = mDfuDialog.findViewById(R.id.dfuBtnCancel);
            dfuBtnCancel.setOnClickListener(v -> {
                Log.v(TAG, "Btn DFU Cancel");
                sendInteger(KEY_DFU_START, KEY_DFU_START_CANCEL);
                mDfuDialog.dismiss();
            });

            mDfuDialog.show();
        });

        dataLabelsMotorPos.add("Pos Actual");
        dataLabelsMotorVel.add("Vel Actual");
        dataLabelsMotorCur.add("Cur Actual");
        if (USE_REQUEST) {
            dataLabelsMotorPos.add("Pos Request");
            dataLabelsMotorVel.add("Vel Request");
            dataLabelsMotorCur.add("Cur Average");
        }

        LineChart chartMotorPos = view.findViewById(R.id.chart_motor_pos);
        LineChart chartMotorVel = view.findViewById(R.id.chart_motor_vel);
        LineChart chartMotorCur = view.findViewById(R.id.chart_motor_cur);

        chartManagerMotorPos = new ChartManager(getContext(), chartMotorPos, "Position", dataLabelsMotorPos, -5.0f, 5.0f, true);
        chartManagerMotorVel = new ChartManager(getContext(), chartMotorVel, "Velocity", dataLabelsMotorVel, -17.0f, 17.0f, true);
        chartManagerMotorCur = new ChartManager(getContext(), chartMotorCur, "Current", dataLabelsMotorCur, -3.0f, 3.0f, false);

        chartManagerMotorPos.setVisibleRange(100);
        chartManagerMotorVel.setVisibleRange(100);
        chartManagerMotorCur.setVisibleRange(100);

        configChart(chartManagerMotorPos.getChart());
        configChart(chartManagerMotorVel.getChart());
        configChart(chartManagerMotorCur.getChart());

        enableSwitch = view.findViewById(R.id.enableSwitch);
        enableSwitch.setOnCheckedChangeListener((buttonView, isChecked) -> {
            if (buttonView.isPressed()) {
                sendEnable(!mEnabled);
            }
        });

        streamingSwitch = view.findViewById(R.id.streamingSwitch);
        streamingSwitch.setOnCheckedChangeListener((buttonView, isChecked) -> {
            if (buttonView.isPressed()) {
//                ExoData.getInstance().mStreaming = !ExoData.getInstance().mStreaming;
                ExoData.getInstance().mStreaming = isChecked;
                mShowRecordedData = !ExoData.getInstance().mStreaming;
                new Handler(Looper.getMainLooper()).post(() -> {
                    tvStreaming.setText(ExoData.getInstance().mStreaming ? "Streaming" : "Recording");
                    if (!ExoData.getInstance().mStreaming) {
                        displayRecordedData();
                    }
                });
            }
        });

        Button btnParamCalibrate = view.findViewById(R.id.btn_param_calibrate);
        btnParamCalibrate.setOnClickListener(btn -> {
            if (ParamManager.getInstance().isInitialized()) {
                final String PARAM_NAME_CALIBRATE = "Calibrate";
                if (ParamManager.getInstance().params_by_name.containsKey(PARAM_NAME_CALIBRATE)) {
                    Param param = ParamManager.getInstance().params_by_name.get(PARAM_NAME_CALIBRATE);
                    byte[] bytes = YrConstants.addKey(new byte[]{1}, (byte) param.key);
                    sendByteBuffer(KEY_PARAM_SET, bytes);
                }
            }
        });

        streamingSwitch.setChecked(ExoData.getInstance().mStreaming);

        crollerLeft = view.findViewById(R.id.circularSeekBarLeft);
        crollerRight = view.findViewById(R.id.circularSeekBarRight);
        crollerAccel = view.findViewById(R.id.circularSeekBarAccel);
        crollerVel = view.findViewById(R.id.circularSeekBarVel);

        mSeekBarXZoom = view.findViewById(R.id.motor_seekbar_1);
        mSeekBarXOffset = view.findViewById(R.id.motor_seekbar_2);

        configBubbleSeekBar(getContext(), mSeekBarXZoom, 50, 100, 50);
        configBubbleSeekBar(getContext(), mSeekBarXOffset, 0, 100, 50);

        mSeekBarXZoom.setOnProgressChangedListener(new BubbleSeekBar.OnProgressChangedListener() {
            @Override
            public void onProgressChanged(BubbleSeekBar bubbleSeekBar, int progress, float progressFloat, boolean fromUser) {
                float max = bubbleSeekBar.getMax();
                float min = bubbleSeekBar.getMin();
                float new_x = map(progressFloat, min, max, 0.5f, 2);
                chartManagerMotorCur.setZoom(new_x);
                chartManagerMotorPos.setZoom(new_x);
                chartManagerMotorVel.setZoom(new_x);
            }

            @Override
            public void getProgressOnActionUp(BubbleSeekBar bubbleSeekBar, int progress, float progressFloat) {
            }

            @Override
            public void getProgressOnFinally(BubbleSeekBar bubbleSeekBar, int progress, float progressFloat, boolean fromUser) {
            }
        });


        mSeekBarXOffset.setOnProgressChangedListener(new BubbleSeekBar.OnProgressChangedListener() {
            @Override
            public void onProgressChanged(BubbleSeekBar bubbleSeekBar, int progress, float progressFloat, boolean fromUser) {
                float max = mSeekBarXOffset.getMax();
                float min = mSeekBarXOffset.getMin();
                float range = chartMotorCur.getVisibleXRange();
                float x_max = chartManagerMotorCur.getEntryCount();
//                float x_min = (x_max - range);
                float x_min = 0;
                float new_x = map(progressFloat, min, max, x_min, x_max);
                Log.v(TAG, "seekBarXOffset [" + progress + "] new_x: [" + String.format("%.1f", new_x) + "] (" + String.format("%.1f", min) + ", " + String.format("%.1f", max) + ") (" + String.format("%.1f", x_min) + ", " + String.format("%.1f", x_max) + ")");

                chartManagerMotorCur.moveToXOffset(new_x);
                chartManagerMotorPos.moveToXOffset(new_x);
                chartManagerMotorVel.moveToXOffset(new_x);
            }

            @Override
            public void getProgressOnActionUp(BubbleSeekBar bubbleSeekBar, int progress, float progressFloat) {
            }

            @Override
            public void getProgressOnFinally(BubbleSeekBar bubbleSeekBar, int progress, float progressFloat, boolean fromUser) {
            }
        });

//        seekBarManagers.put("Force", new SeekBarManager(crollerLeft, "Force", 0.0f, 1.0f, KEY_GAIN_CONTROL2, this));
//        seekBarManagers.put("Fan", new SeekBarManager(crollerRight, "Fan", 0.0f, 1.0f, KEY_GAIN_CONTROL, this));
//        seekBarManagers.put("Accel", new SeekBarManager(crollerAccel, "Accel", -1.0f, 1.0f, KEY_ACCEL, this));
//        seekBarManagers.put("Vel", new SeekBarManager(crollerVel, "Vel", -1.0f, 1.0f, KEY_VEL, this));

        return view;
    }

    private void configChart(LineChart chart) {
        chart.getLegend().setEnabled(true);
        // @TODO(ks) - use dptopx
        float x = dpToPx(getContext(), 30);
        float y = dpToPx(getContext(), 40);
        chart.getDescription().setPosition(x, y);
    }

    @Override
    public boolean updateUiFunction() {
        if (mShowRecordedData) {
            return false;
        }
        if (chartManagerMotorPos == null) {
            return false;
        }
        boolean new_data = super.updateUiFunction();
        if (!new_data) {
            return false;
        }

        msgStats.get(MsgStatType.DISPLAY.ordinal()).add();
        int delay = 30;
        MotorData motorData = ExoData.getInstance().motorDataL;
        boolean update = true;
        chartManagerMotorPos.addEntry(delay, motorData.getValsPos(), update);
        chartManagerMotorVel.addEntry(delay, motorData.getValsVel(), update);
        chartManagerMotorCur.addEntry(delay, motorData.getValsCur(), update);
        return true;
    }

    @Override
    public void updateDfuProgress(int progress, int current_packet, boolean done) {
        if (mDfuDialog != null) {
            NumberProgressBar dfuProgressBar = mDfuDialog.findViewById(R.id.dfuProgressBar);
            Button dfuBtnCancel = mDfuDialog.findViewById(R.id.dfuBtnCancel);
            TextView tvStatus = mDfuDialog.findViewById(R.id.dfuTvStatus);

            dfuProgressBar.setProgress(progress);

            final int total_packets = FirmwareManager.getInstance().getNumPackets();
            new Handler(Looper.getMainLooper()).post(() -> {
                tvStatus.setText("Downloading packet " + (current_packet + 1) + " / " + (total_packets) + "");
            });
            int delay = 1500;
            if (done) {
                new Handler(Looper.getMainLooper()).post(() -> {
                    tvStatus.setText("Done");
                    dfuProgressBar.setVisibility(View.GONE);
                    dfuBtnCancel.setVisibility(View.GONE);
                    YrConstants.toastText(getContext(), "Upgrade Complete");
                });
                timerHandler = new Handler(Looper.getMainLooper());
                timerRunnable = () -> {
                    mDfuDialog.dismiss();
                };
                timerHandler.postDelayed(timerRunnable, delay);
                Log.v(TAG, "DFU Done");
            }
        }
    }

    private void cancelTimeout() {
        timerHandler.removeCallbacks(timerRunnable);
    }

    void setTimer(final int delay) {
        timerHandler = new Handler(Looper.getMainLooper());
        timerRunnable = () -> {
            YrConstants.toastText(getContext(), "Calibration timed out");
            progressBar.setVisibility(View.GONE);
        };
        timerHandler.postDelayed(timerRunnable, delay);
    }

    private void displayRecordedData() {
        if (ExoData.getInstance().rxRecordedDataRingBuffer.isEmpty()) {
            return;
        }

        mShowRecordedData = true;

        int num = ExoData.getInstance().rxRecordedDataRingBuffer.size();

        chartManagerMotorPos.resetChartData();
        chartManagerMotorVel.resetChartData();
        chartManagerMotorCur.resetChartData();

        chartManagerMotorPos.setVisibleRange(num);
        chartManagerMotorVel.setVisibleRange(num);
        chartManagerMotorCur.setVisibleRange(num);

        int delay = 1;
        Object[] array = ExoData.getInstance().rxRecordedDataRingBuffer.toArray();
        for (int i = 0; i < num; i++) {
            DataPacket packet = (DataPacket) array[i];
            if (packet == null) {
                continue;
            }
            ExoData.getInstance().setPacket(packet.data);
            MotorData motorData = ExoData.getInstance().motorDataL;
            boolean update = false;
            chartManagerMotorPos.addEntry(delay, motorData.getValsPos(), update);
            chartManagerMotorVel.addEntry(delay, motorData.getValsVel(), update);
            chartManagerMotorCur.addEntry(delay, motorData.getValsCur(), update);
        }

        chartManagerMotorPos.updateChartData();
        chartManagerMotorVel.updateChartData();
        chartManagerMotorCur.updateChartData();
    }

    @Override
    public void onUploadRecordedData() {
        super.onUploadRecordedData();
        new Handler(Looper.getMainLooper()).post(() -> {
            streamingSwitch.setChecked(false);
            tvStreaming.setText("Recording");
        });
        displayRecordedData();
    }

    @Override
    public void onRxFirstMessage() {
        sendPacketSelect(KEY_FEEDBACK_PACKET_MOTOR);
    }

    @Override
    public void onRxData() {
        if (ExoData.getInstance().hasStatusUpdate() && (tvTemp != null)) {
            updateStatusViews();
        }
        setEnabled(ExoData.getInstance().motorDataL.enabled.state);
    }

    private void updateStatusViews() {
        setTextView(tvTemp, new DecimalFormat("##.#").format(ExoData.getInstance().temp) + " °C");
        setTextView(tvVoltage, new DecimalFormat("##.#").format(ExoData.getInstance().voltage) + " Volts");
        setTextView(tvCurrent, new DecimalFormat("#.#").format(ExoData.getInstance().current) + " Amps");
        setTextView(tvTimeLeftHours, new DecimalFormat("##.#").format(ExoData.getInstance().timeLeftHours) + " Hrs");
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        setActionBarTitle(R.string.plotter_tab_title);

//        ExoData.getInstance().motorDataL.calibrated.setOnStateChange(() -> {
//            Log.v(TAG, "Calibration changed [" + ExoData.getInstance().motorDataL.calibrated.state + "]");
//            if (ExoData.getInstance().motorDataL.calibrated.state) {
//                cancelTimeout();
//            }
//        });

        updateStatusViews();

        onRxData();

        if (!ExoData.getInstance().mStreaming) {
            displayRecordedData();
        }
    }

    private void setEnabled(boolean enabled) {
        mEnabledLast = mEnabled;
        mEnabled = enabled;
        if (mEnabled != mEnabledLast) {
            Log.v(TAG, "yr-setEnabled [" + enabled + "]");
            new Handler(Looper.getMainLooper()).post(() -> {
                tvEnabled.setText(mEnabled ? "Enabled" : "Disabled");
                enableSwitch.setChecked(mEnabled);
            });
        }
    }

    @Override
    public void onCreateOptionsMenu(Menu menu, MenuInflater inflater) {
        super.onCreateOptionsMenu(menu, inflater);
        inflater.inflate(R.menu.menu_help, menu);
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        FragmentActivity activity = getActivity();
        switch (item.getItemId()) {
            default:
                return super.onOptionsItemSelected(item);
        }
    }
}
