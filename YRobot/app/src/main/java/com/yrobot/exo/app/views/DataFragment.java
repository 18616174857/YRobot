package com.yrobot.exo.app.views;

import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.v4.app.FragmentActivity;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import com.github.mikephil.charting.charts.LineChart;
import com.yrobot.exo.R;
import com.yrobot.exo.app.ConnectedPeripheralFragment;
import com.yrobot.exo.app.utils.ChartManager;
import com.yrobot.exo.app.data.ExoData;

import java.util.ArrayList;

import static com.yrobot.exo.app.YrConstants.KEY_FEEDBACK_PACKET_LEG_BOARD;
import static com.yrobot.exo.app.YrConstants.USE_MOCK_DEVICE;

public class DataFragment extends ConnectedPeripheralFragment {

    private final static String TAG = "yr-" + DataFragment.class.getSimpleName();

    private LineChart chartLeg;
    private LineChart chartLegImu;
    private LineChart chartGaitPercent;
    private ArrayList<String> dataLabelsLeg;
    private ArrayList<String> dataLabelsLegImu;
    private ArrayList<String> dataLabelsGaitPercent;
    private ChartManager chartManagerLeg;
    private ChartManager chartManagerLegImu;
    private ChartManager chartManagerGaitPercent;
    private TextView tvMsgRates;

    // region Fragment Lifecycle
    public static DataFragment newInstance(@Nullable String singlePeripheralIdentifier) {
        DataFragment fragment = new DataFragment();
        fragment.setArguments(createFragmentArgs(singlePeripheralIdentifier));
        return fragment;
    }

    public DataFragment() {
        dataLabelsLeg = new ArrayList<>();
//        dataLabelsLeg.add("Hip");
//        dataLabelsLeg.add("Knee");
        dataLabelsLeg.add("Ankle");
        dataLabelsLegImu = new ArrayList<>();
//        dataLabelsLegImu.add("Angle");
//        dataLabelsLegImu.add("Accel");
        dataLabelsLegImu.add("Gyro");
//        dataLabelsGaitPhase = new ArrayList<>();
//        dataLabelsGaitPhase.add("Gait Phase");
        dataLabelsGaitPercent = new ArrayList<>();
        dataLabelsGaitPercent.add("Gait %");
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setRetainInstance(true);
    }

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_data, container, false);
        tvMsgRates = view.findViewById(R.id.data_tv_msg_rates);
        tvMsgRates.setText("");
        return view;
    }

    @Override
    public void onRxFirstMessage() {
        Log.v(TAG, "onRxFirstMessage [" + rx_count + "]");
        sendPacketSelect(KEY_FEEDBACK_PACKET_LEG_BOARD);
    }

    long display_count = 0;

    @Override
    public void updateUiFunctionSlow() {
        super.updateUiFunctionSlow();
        new Handler(Looper.getMainLooper()).post(() -> {
            String str = getDataRateString();
            tvMsgRates.setText(str);
        });
    }

    @Override
    public boolean updateUiFunction() {
        boolean new_data = super.updateUiFunction();
        if (chartManagerLeg == null) {
            return false;
        }
        if (!new_data) {
            return false;
        }

        display_count++;
        if (display_count % 3 == 0) {
//            return;
        }
        msgStats.get(MsgStatType.DISPLAY.ordinal()).add();
        int delay = 30;
        chartManagerLeg.addEntry(delay, ExoData.getInstance().legDataL.getArray());
        chartManagerLegImu.addEntry(delay, ExoData.getInstance().legDataL.getImuArray(0));
        chartManagerGaitPercent.addEntry(delay, new float[]{ExoData.getInstance().legDataL.gaitCyclePercent});
        return true;
    }

    @Override
    public void onRxData() {
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        setActionBarTitle(R.string.data_tab_title);

        chartLeg = view.findViewById(R.id.chart_leg);
        chartLegImu = view.findViewById(R.id.chart_leg_imu);
        chartGaitPercent = view.findViewById(R.id.chart_gait_percent);

        chartManagerLeg = new ChartManager(getContext(), chartLeg, "Force (N)", dataLabelsLeg, -1.0f, 150.0f, true);
        chartManagerLegImu = new ChartManager(getContext(), chartLegImu, "IMU Gyro", dataLabelsLegImu, -1.0f, 1.0f, true);
        chartManagerGaitPercent = new ChartManager(getContext(), chartGaitPercent, "Gait Percent", dataLabelsGaitPercent, -10.0f, 100.0f, false);

        chartGaitPercent.getDescription().setPosition(50, 70);

        if (USE_MOCK_DEVICE) {
            sendPacketSelect(KEY_FEEDBACK_PACKET_LEG_BOARD);
        }

//        chartLeg.getData().setDrawCubic(true);
//        chartLeg.getLineData()
//        lineDataSet.notifyDataSetChanged();

//        chartGaitPhase.getLegend().setEnabled(false);
//        chartGaitPercent.getLegend().setEnabled(false);
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
