package com.yrobot.exo.app.views;

import android.annotation.SuppressLint;
import android.graphics.Color;
import android.graphics.Point;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.v4.content.ContextCompat;
import android.support.v4.view.MotionEventCompat;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.RadioButton;
import android.widget.RadioGroup;

import com.github.mikephil.charting.charts.LineChart;
import com.github.mikephil.charting.components.Legend;
import com.github.mikephil.charting.components.XAxis;
import com.github.mikephil.charting.components.YAxis;
import com.github.mikephil.charting.data.Entry;
import com.github.mikephil.charting.data.LineData;
import com.github.mikephil.charting.data.LineDataSet;
import com.github.mikephil.charting.formatter.IFillFormatter;
import com.github.mikephil.charting.interfaces.dataprovider.LineDataProvider;
import com.github.mikephil.charting.interfaces.datasets.ILineDataSet;
import com.xw.repo.BubbleSeekBar;
import com.yrobot.exo.R;
import com.yrobot.exo.app.ConnectedPeripheralFragment;
import com.yrobot.exo.app.utils.ChartManager;
import com.yrobot.exo.app.data.ExoData;
import com.yrobot.exo.app.data.MotorData;

import java.util.ArrayList;

import static android.graphics.Color.WHITE;
import static com.yrobot.exo.app.data.GaitTuningData.CABLE_LEN_COMMAND_SINE;
import static com.yrobot.exo.app.data.GaitTuningData.CABLE_LEN_COMMAND_STEP;
import static com.yrobot.exo.app.data.GaitTuningData.CABLE_LEN_COMMAND_TRIANGLE;
import static com.yrobot.exo.app.data.MotorData.MAX_CYCLES;
import static com.yrobot.exo.app.YrConstants.KEY_FEEDBACK_PACKET_MOTOR;
import static com.yrobot.exo.app.YrConstants.KEY_PROFILE_CONTROL_MODE;
import static com.yrobot.exo.app.YrConstants.KEY_PROFILE_TUNING;
import static com.yrobot.exo.app.YrConstants.constrain;
import static com.yrobot.exo.app.YrConstants.fillColor;
import static com.yrobot.exo.app.YrConstants.fillColorDark;
import static com.yrobot.exo.app.YrConstants.fillColorGrid;
import static com.yrobot.exo.app.YrConstants.map;
import static java.lang.Math.PI;
import static java.lang.Math.sin;

public class UserStatusFragment extends ConnectedPeripheralFragment {

    private static final String TAG = UserStatusFragment.class.getSimpleName();
//    private OnFragmentInteractionListener mListener;
//    private UartDataManager mUartDataManager;

    private LineChart chart;
    ChartManager chartManager;
    ArrayList<String> dataLabels;

    private final int gs = 200;
    private final int gs2 = gs - 50;
    private final int[] fillColorActual;
    private final int[] fillColorActualDark;

    private RadioGroup radioGroup;

    BubbleSeekBar mSeekBar1;
    BubbleSeekBar mSeekBar2;

    MotorData md = ExoData.getInstance().motorDataL;

    public static UserStatusFragment newInstance(@Nullable String singlePeripheralIdentifier) {
        UserStatusFragment fragment = new UserStatusFragment();
        fragment.setArguments(createFragmentArgs(singlePeripheralIdentifier));
        return fragment;
    }

    public UserStatusFragment() {
        dataLabels = new ArrayList<>();
        dataLabels.add("Hip Angle");
        fillColorActual = new int[MAX_CYCLES];
        fillColorActualDark = new int[MAX_CYCLES];
        for (int i = 0; i < fillColorActual.length; i++) {
            int offset = 100;
            int step = 20;
            int mag = offset + i * step;
            fillColorActual[i] = Color.argb(200, mag, gs, gs);
            fillColorActualDark[i] = Color.argb(200, mag, gs2, gs2);
        }
    }

    public static UserStatusFragment newInstance(String param1, String param2) {
        UserStatusFragment fragment = new UserStatusFragment();
        Bundle args = new Bundle();
        fragment.setArguments(args);
        return fragment;
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (getArguments() != null) {
        }
    }

    float getCableLengthFromGaitPercent(float percent) {
        // @TODO(ks): hardcoded values
        float len_1 = ExoData.getInstance().gait_tuning_data.len_1;  // mm (from home pos)
        float len_2 = ExoData.getInstance().gait_tuning_data.len_2;  // mm
        float len_range = len_2 - len_1;
        float percent_active_min = ExoData.getInstance().gait_tuning_data.percent_1;
        float percent_active_max = ExoData.getInstance().gait_tuning_data.percent_2;
        float percent_active_range = percent_active_max - percent_active_min;
        float percent_active_mid = (percent_active_min + percent_active_max) / 2.0f;
        float percent_active = percent - percent_active_min;
        float len = len_1;
        if (percent >= percent_active_min && percent < percent_active_max) {
            switch (ExoData.getInstance().gait_tuning_data.mode) {
                case CABLE_LEN_COMMAND_TRIANGLE:
                    if (percent < percent_active_mid) {
                        len = map(percent, percent_active_min, percent_active_mid, len_1, len_2);
                    } else {
                        len = map(percent, percent_active_mid, percent_active_max, len_2, len_1);
                    }
                    break;
                case CABLE_LEN_COMMAND_SINE:
                    len = (float) (len_1 + len_range * sin(((percent_active * PI) / percent_active_range)));
                    break;
                case CABLE_LEN_COMMAND_STEP:
                    len = (percent < percent_active_mid) ? len_2 : len_1;
                    break;
            }
        }
        return len;
    }

    @Override
    public void onRxFirstMessage() {
        Log.v(TAG, "onRxFirstMessage [" + rx_count + "]");
        sendPacketSelect(KEY_FEEDBACK_PACKET_MOTOR);
    }

    @Override
    public void onRxData() {
//        updateProfileChartActual();
        if (md.hasEnoughMotorGaitData()) {
            updateProfileChart();
        }
        if (rx_count < 10) {
            switch ((int) rx_count % 10) {
                case 4:
                    sendInteger(KEY_PROFILE_TUNING, (byte) ExoData.getInstance().gait_tuning_data.percent_1);
                    break;
                case 6:
                    sendInteger(KEY_PROFILE_TUNING, (byte) ExoData.getInstance().gait_tuning_data.percent_2);
                    break;
            }
        }
    }

    private LineDataSet createSet(int index) {
        ArrayList<Entry> values2 = new ArrayList<>();

        MotorData.MotorAndGait[] array = md.getCycleArray(index);
        for (int i = 0; i < array.length; i++) {
            if (array[i] != null) {
                values2.add(new Entry(array[i].percent, array[i].position));
            }
        }

        LineDataSet set2 = new LineDataSet(values2, "Data [" + index + "]");
        set2.setAxisDependency(YAxis.AxisDependency.LEFT);
        set2.setColor(fillColorActualDark[index]);
        set2.setDrawCircles(false);
        set2.setLineWidth(1f);
        set2.setCircleRadius(3f);
        set2.setFillAlpha(155);
        set2.setDrawFilled(true);
        set2.setFillColor(fillColorActual[index]);
        set2.setHighLightColor(fillColorActual[index]);
        set2.setDrawCircleHole(false);
        set2.setFillFormatter(new IFillFormatter() {
            @Override
            public float getFillLinePosition(ILineDataSet dataSet, LineDataProvider dataProvider) {
                return 0;
            }
        });
        return set2;
    }

    private LineDataSet createStaticSet() {
        ArrayList<Entry> values1 = new ArrayList<>();

        for (int i = 0; i < 100; i++) {
            float val = getCableLengthFromGaitPercent(i);
            values1.add(new Entry(i, val));
        }

        LineDataSet set1 = new LineDataSet(values1, "Data1");
        set1.setAxisDependency(YAxis.AxisDependency.LEFT);
        set1.setColor(fillColorDark);
        set1.setDrawCircles(false);
        set1.setLineWidth(1f);
        set1.setCircleRadius(3f);
        set1.setFillAlpha(155);
        set1.setDrawFilled(true);
        set1.setFillColor(fillColor);
        set1.setHighLightColor(fillColor);
        set1.setDrawCircleHole(false);
        set1.setFillFormatter(new IFillFormatter() {
            @Override
            public float getFillLinePosition(ILineDataSet dataSet, LineDataProvider dataProvider) {
                // change the return value here to better understand the effect
                return 0;
//                return chart.getAxisLeft().getAxisMinimum();
            }
        });
        return set1;
    }

    private void updateProfileChart() {
        ArrayList<ILineDataSet> dataSets = new ArrayList<>();

        if (md.hasEnoughMotorGaitData()) {
            for (int i = 0; i < MAX_CYCLES; i++) {
//                Log.v(TAG, "addMotorGait to chart [" + i + "] [" + md.getCycleArray(i).length + "]");
                dataSets.add(createSet(i));
            }
        }

        dataSets.add(createStaticSet());

        LineData data = new LineData(dataSets);
        data.setDrawValues(false);
        chart.setData(data);
        chart.invalidate();
    }

    protected Point getRelativePosition(View v, MotionEvent event) {
        int[] location = new int[2];
        v.getLocationOnScreen(location);
        float screenX = event.getRawX();
        float screenY = event.getRawY();
        float viewX = screenX - location[0];
        float viewY = screenY - location[1];
        float viewXF = viewX * 100 / v.getWidth();
        float viewYF = viewY * 100 / v.getHeight();
        return new Point((int) viewXF, (int) viewYF);
    }

    @SuppressLint("ClickableViewAccessibility")
    private void setupProfileChart() {
        chart.setBackgroundColor(WHITE);
        chart.setGridBackgroundColor(WHITE);
        chart.setDrawGridBackground(true);

        chart.setBorderColor(fillColor);
        chart.setBorderWidth(2.0f);
        chart.setDrawBorders(true);
//        chart.setDrawBorders(false);

        // no description text
        chart.getDescription().setEnabled(false);

        // if disabled, scaling can be done on x- and y-axis separately
        chart.setPinchZoom(false);
        chart.setDragEnabled(false);
        chart.setScaleEnabled(false);
        chart.setTouchEnabled(true);

//        chart.getAxisRight().setDrawAxisLine(true);
//        chart.getAxisRight().setEnabled(true);
//        chart.getAxisRight().setDrawLabels(false);
//        chart.getAxisRight().setDrawGridLines(false);

        chart.setOnTouchListener(new View.OnTouchListener() {
            @Override
            public boolean onTouch(View v, MotionEvent event) {
                int action = MotionEventCompat.getActionMasked(event);
                switch (action) {
                    case (MotionEvent.ACTION_DOWN):
                    case (MotionEvent.ACTION_MOVE):
                        Point p = getRelativePosition(v, event);
                        Log.d(TAG, "Action was MOVE [" + p.x + ", " + p.y + "]");
                        setForceMagnitude((100 - p.y));
                        return true;
                    case (MotionEvent.ACTION_UP):
                        Log.d(TAG, "Action was UP");
                        return true;
                    case (MotionEvent.ACTION_CANCEL):
                        Log.d(TAG, "Action was CANCEL");
                        return true;
                    case (MotionEvent.ACTION_OUTSIDE):
                        Log.d(TAG, "Movement occurred outside bounds " +
                                "of current screen element");
                        return true;
                }
                return false;
            }
        });

        Legend l = chart.getLegend();
        l.setEnabled(false);

        XAxis xAxis = chart.getXAxis();
        xAxis.setEnabled(true);
        xAxis.setPosition(XAxis.XAxisPosition.BOTTOM);

        YAxis leftAxis = chart.getAxisLeft();
//        leftAxis.setAxisMaximum(900f);
//        leftAxis.setAxisMinimum(-250f);
        leftAxis.setDrawAxisLine(false);
        leftAxis.setDrawZeroLine(false);
        leftAxis.setDrawGridLines(true);
        leftAxis.setDrawLabels(false);
        leftAxis.setGridColor(fillColorGrid);

        xAxis.setDrawGridLines(true);
        xAxis.setGridColor(fillColorGrid);

        chart.getAxisRight().setEnabled(false);

        int min = 0;
        int max = 100;
        float sp1 = chart.getAxisLeft().getSpaceBottom();
        float sp2 = chart.getAxisLeft().getSpaceTop();
//        Log.v(TAG, "Setup chart [" + sp1 + ", " + sp2 + "]");
        chart.getAxisLeft().setSpaceBottom(0);
        chart.getAxisLeft().setSpaceTop(0);
        chart.getXAxis().setSpaceMax(0);
        chart.getXAxis().setSpaceMin(0);
        chart.getAxisLeft().setAxisMinimum(min);
        chart.getAxisLeft().setAxisMaximum(max);
        chart.getAxisRight().setAxisMinimum(min);
        chart.getAxisRight().setAxisMaximum(max);
    }

    private void setForceMagnitude(int in) {
        int val = constrain(in, 10, 99);
        Log.v(TAG, "setForceMagnitude [" + in + "] [" + val + "]");
        ExoData.getInstance().gait_tuning_data.len_2 = val;
        sendInteger(KEY_PROFILE_TUNING, (byte) (100 + val));
        updateProfileChart();
    }

    private void configSeekBar(BubbleSeekBar seekBar, byte key) {
        int sectionCount = 50;
        seekBar.getConfigBuilder()
                .min(0.0f)
                .max(100)
                .trackColor(fillColor)
                .secondTrackColor(fillColorDark)
                .thumbColor(fillColorDark)
//                .thumbColor(ContextCompat.getColor(getContext(), R.color.black))
                .showThumbText()
                .thumbRadius(12)
                .thumbRadiusOnDragging(15)
                .thumbTextColor(ContextCompat.getColor(getContext(), R.color.black))
                .thumbTextSize(18)
                .bubbleColor(ContextCompat.getColor(getContext(), R.color.blue_700))
                .bubbleTextSize(20)
//                .sectionCount(sectionCount)
//                .showSectionText()
//                .sectionTextColor(ContextCompat.getColor(getContext(), R.color.colorPrimary))
//                .sectionTextSize(18)
//                .showSectionMark()
//                .seekBySection()
//                .autoAdjustSectionMark()
//                .sectionTextPosition(BubbleSeekBar.TextPosition.BELOW_SECTION_MARK)
                .build();

        seekBar.setOnProgressChangedListener(new BubbleSeekBar.OnProgressChangedListener() {

            @Override
            public void onProgressChanged(BubbleSeekBar bubbleSeekBar, int progress, float progressFloat, boolean s) {
                Log.v(TAG, "onProgress [" + progress + "] [" + progressFloat + "]");
                if (progressFloat > percentCenter) {
                    ExoData.getInstance().gait_tuning_data.percent_2 = progressFloat;
                    sendInteger(KEY_PROFILE_TUNING, (byte) ExoData.getInstance().gait_tuning_data.percent_2);
                } else if (progress < percentCenter) {
                    ExoData.getInstance().gait_tuning_data.percent_1 = progressFloat;
                    sendInteger(KEY_PROFILE_TUNING, (byte) ExoData.getInstance().gait_tuning_data.percent_1);
                }
                updateProfileChart();
            }

            @Override
            public void getProgressOnActionUp(BubbleSeekBar bubbleSeekBar, int progress, float progressFloat) {
            }

            @Override
            public void getProgressOnFinally(BubbleSeekBar bubbleSeekBar, int progress, float progressFloat, boolean s) {
            }
        });
    }

    //    private int percentCenter = 75;
    private int percentCenter = 50;
    private int percentPad = 2;

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        // Inflate the layout for this fragment
        View view = inflater.inflate(R.layout.fragment_user_status, container, false);

        chart = view.findViewById(R.id.chart1);

        radioGroup = view.findViewById(R.id.radioModeSelect);

        radioGroup.setOnCheckedChangeListener(new RadioGroup.OnCheckedChangeListener() {
            public void onCheckedChanged(RadioGroup group, int checkedId) {
                RadioButton checkedRadioButton = group.findViewById(checkedId);
                switch (checkedId) {
                    case R.id.radioTriangle:
                        ExoData.getInstance().gait_tuning_data.mode = CABLE_LEN_COMMAND_TRIANGLE;
                        break;
                    case R.id.radioSine:
                        ExoData.getInstance().gait_tuning_data.mode = CABLE_LEN_COMMAND_SINE;
                        break;
                }
                sendInteger(KEY_PROFILE_CONTROL_MODE, ExoData.getInstance().gait_tuning_data.mode);
                boolean isChecked = checkedRadioButton.isChecked();
                if (isChecked) {
                    updateProfileChart();
                }
            }
        });

        mSeekBar1 = view.findViewById(R.id.seekBarProfile1);
        mSeekBar2 = view.findViewById(R.id.seekBarProfile2);

        configSeekBar(mSeekBar1, KEY_PROFILE_TUNING);
        configSeekBar(mSeekBar2, KEY_PROFILE_TUNING);

//        ExoData.getInstance().gait_tuning_data.percent_1 = percentCenter - 10;
//        ExoData.getInstance().gait_tuning_data.percent_2 = percentCenter + 10;

        mSeekBar1.getConfigBuilder()
                .min(percentPad)
                .max(percentCenter - percentPad)
                .progress(ExoData.getInstance().gait_tuning_data.percent_1)
                .build();

        mSeekBar2.getConfigBuilder()
                .min(percentCenter + percentPad)
                .max(100 - percentPad)
                .progress(ExoData.getInstance().gait_tuning_data.percent_2)
                .build();

        setupProfileChart();
        updateProfileChart();

        return view;
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
//        setActionBarTitle("User Status");
        chart.invalidate();
    }

//    @Override
//    public void onAttach(Context context) {
//        super.onAttach(context);
//    }
//
//    @Override
//    public void onDetach() {
//        super.onDetach();
//        mListener = null;
//    }
//
//    public interface OnFragmentInteractionListener {
//        void onFragmentInteraction(Uri uri);
//    }
//
//    @Override
//    public void onUartRx(@NonNull byte[] data, @NonNull String peripheralIdentifier) {
//    }
}
