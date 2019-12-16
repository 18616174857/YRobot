package com.yrobot.exo.app.data;

import android.content.Context;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Typeface;
import android.view.View;

import com.github.mikephil.charting.charts.LineChart;
import com.github.mikephil.charting.components.Legend;
import com.github.mikephil.charting.components.XAxis;
import com.github.mikephil.charting.components.YAxis;
import com.github.mikephil.charting.data.Entry;
import com.github.mikephil.charting.data.LineData;
import com.github.mikephil.charting.data.LineDataSet;
import com.github.mikephil.charting.interfaces.datasets.ILineDataSet;
import com.github.mikephil.charting.utils.EntryXComparator;
import com.yrobot.exo.R;

import java.util.ArrayList;
import java.util.Collections;

import static com.yrobot.exo.app.YrConstants.colors;
import static com.yrobot.exo.app.YrConstants.map;

public class ChartManager {

    private final static String TAG = ChartManager.class.getSimpleName();

    private LineChart mChart;
    private boolean mIsAutoScrollEnabled = true;
    private float mVisibleRange = 100;
    private LineDataSet mLastDataSetModified;
    private boolean mLimitsSet = false;
    private long mEntryCount = 0;

    private int mNumData = 1;
    private float mMin = 1.0f;
    private float mMax = 1.0f;

    private float mOffsetX = 0;
    float mZoomX = 1f;

    private Context mContext;
    private ArrayList<String> mDataLabels;

    public ChartManager(Context context, LineChart chart, String description, ArrayList<String> dataLabels, float min, float max, boolean cubic) {
        mContext = context;
        mChart = chart;
        mDataLabels = dataLabels;
        mNumData = dataLabels.size();

        Typeface typeface = context.getResources().getFont(R.font.opensans);
        chart.getDescription().setTypeface(typeface);
        chart.getDescription().setText(description);

        configChart();

        initializeChartData(chart, dataLabels, cubic);

        setYAxisLimits(min, max);
    }

    private void configChart() {
//        mChart.setExtraOffsets(10, 10, 10, 10);

        mChart.getDescription().setPosition(50, 200);
        mChart.getDescription().setTextAlign(Paint.Align.LEFT);
        mChart.getDescription().setTextSize(15.0f);
        mChart.getDescription().setTextColor(R.color.black);

        mChart.setNoDataTextColor(Color.BLACK);
        mChart.setNoDataText("");

        mChart.setBackgroundColor(Color.WHITE);

        final int gs = 0x88;
        mChart.setBorderColor(Color.argb(255, gs, gs, gs));
        mChart.setBorderWidth(1);
        mChart.setDrawBorders(true);

        mChart.setHardwareAccelerationEnabled(true);

        mChart.setDrawGridBackground(false);
        mChart.getDescription().setEnabled(true);

        mChart.getAxisLeft().setEnabled(true);
        mChart.getXAxis().setEnabled(true);
        mChart.getAxisRight().setEnabled(false);

        mChart.getXAxis().setDrawLabels(true);
        mChart.getXAxis().setDrawAxisLine(false);
        mChart.getXAxis().setDrawGridLines(true);
        mChart.getXAxis().setPosition(XAxis.XAxisPosition.BOTTOM);
        mChart.getXAxis().setDrawGridLines(true);
        mChart.getXAxis().setDrawGridLinesBehindData(true);
        mChart.getXAxis().setGranularityEnabled(true);
        mChart.getXAxis().setGranularity(50);

//        mChart.getAxisLeft().setXOffset(10);
        mChart.getAxisLeft().setDrawGridLinesBehindData(true);
        mChart.getAxisLeft().setDrawAxisLine(false);
        mChart.getAxisLeft().setDrawLabels(true);
        mChart.getAxisLeft().setDrawGridLines(true);
        mChart.getAxisLeft().setGranularityEnabled(true);
        mChart.getAxisLeft().setGranularity(2);

        mChart.setDragEnabled(false);
        mChart.setScaleEnabled(false);
        mChart.setTouchEnabled(false);
//        mChart.setDragEnabled(!mIsAutoScrollEnabled);

        mChart.setTouchEnabled(true);
//        mChart.setDragXEnabled(true);
        mChart.setScaleXEnabled(true);
//        mChart.setPinchZoom(true);

        mChart.getLegend().setEnabled(false);

        Legend l = mChart.getLegend();
        l.setVerticalAlignment(Legend.LegendVerticalAlignment.TOP);
        l.setHorizontalAlignment(Legend.LegendHorizontalAlignment.RIGHT);
        l.setXOffset(15);
        l.setYOffset(10);
        l.setOrientation(Legend.LegendOrientation.VERTICAL);
        l.setDrawInside(true);
    }

    public LineChart getChart() {
        return mChart;
    }

    public void setYAxisLimits(float min, float max) {
        mMin = min;
        mMax = max;
        setAxisLimits();
    }

    public void setAxisLimits() {
//        if (mLimitsSet) {           return;         }
        mChart.getAxisLeft().setAxisMaximum(mMax);
        mChart.getAxisLeft().setAxisMinimum(mMin);
        mLimitsSet = true;
    }

    public void setZoom(float v) {
        mZoomX = v;
        float v2 = map(mZoomX, 0.5f, 2f, 50, mChart.getData().getEntryCount() - 1);
        setVisibleRange(v2);
//        updateZoom();

        moveToXOffset(mOffsetX);
//        notifyDatasetChanged();
    }

    public void setVisibleRange(float v) {
        mVisibleRange = v;

        mChart.setVisibleXRangeMaximum(mVisibleRange);
        mChart.setVisibleXRangeMinimum(mVisibleRange);

//        moveToXOffset(mOffsetX);
    }

    public void notifyDatasetChanged() {
        if (mChart.getData() != null) {
            mChart.getData().notifyDataChanged();
        }
        mChart.notifyDataSetChanged();
        mChart.invalidate();
        mChart.setVisibleXRangeMaximum(mVisibleRange);
        mChart.setVisibleXRangeMinimum(mVisibleRange);
        setAxisLimits();

//        if (mLastDataSetModified != null && mIsAutoScrollEnabled) {
//            final List<Entry> values = mLastDataSetModified.getValues();
//            final float xOffset = (values != null && values.size() > 0 ? values.get(values.size() - 1).getX() : 0) - (mVisibleRange - 1);
//            mChart.moveViewToX(xOffset);
//        }
    }

    public void resetChartData() {
        initializeChartData(mChart, mDataLabels, false);
    }

    //---------------------------------------------------------------------------------//
    //  CHART
    //---------------------------------------------------------------------------------//
    protected void initializeChartData(LineChart chart, ArrayList<String> labels, boolean cubic) {
        ArrayList<ILineDataSet> sets = new ArrayList<>();

        for (int k = 0; k < labels.size(); k++) {
            ArrayList<Entry> values = new ArrayList<>();
            values.add(new Entry(0, (float) 0));

            String label = labels.get(k);
            LineDataSet set = new LineDataSet(values, label);
            set.setLineWidth(1.5f);
            set.setCircleRadius(1.5f);
            set.setDrawCircles(false);
            set.setDrawCircleHole(false);
//            d.setDrawCircles(true);
//            d.setDrawCircleHole(true);

            int color = colors[k % colors.length];
            set.setColor(color);
            set.setCircleColor(color);

            cubic = false;
            if (cubic) {
                set.setMode(LineDataSet.Mode.CUBIC_BEZIER);
                //            set.setMode(LineDataSet.Mode.HORIZONTAL_BEZIER);
                set.setCubicIntensity(0.5f);
            }

            sets.add(set);
        }

        LineData data = new LineData(sets);
        data.setDrawValues(false);

        chart.setData(data);

        chart.notifyDataSetChanged();
        chart.invalidate();
    }

    public void addEntry(int delay, float[] y) {
        addEntry(delay, y, true);
    }

    public void addEntry(int delay, float[] y, boolean update) {
        if (mChart.getVisibility() == View.INVISIBLE) {
            return;
        }

        LineData data = mChart.getData();
        if (data == null) {
            initializeChartData(mChart, mDataLabels, false);
        }
//        for (int i = 0; i < y.length; i++) {
//            ILineDataSet set = data.getDataSetByIndex(i);
//            if (set == null) {
//                set = createSet();
//                data.addDataSet(set);
//            }
//        }

        ArrayList<Entry> values = new ArrayList<>();
        for (int i = 0; i < y.length; i++) {
            values.add(new Entry(data.getDataSetByIndex(i).getEntryCount(), y[i]));
        }
        if (y.length == 0) {
        } else {
            mEntryCount = data.getDataSetByIndex(y.length - 1).getEntryCount();
        }

        Collections.sort(values, new EntryXComparator());
        for (int i = 0; i < y.length; i++) {
            ILineDataSet set = data.getDataSetByIndex(i);
            set.addEntry(values.get(i));
        }

        if (update) {
            data.notifyDataChanged();

            // let the chart know it's data has changed
            mChart.notifyDataSetChanged();
            mChart.invalidate();

            // limit the number of visible entries
//            mChart.setVisibleXRangeMaximum(data.getEntryCount() - 1);
            mChart.setVisibleXRangeMaximum(mVisibleRange);
            mChart.setVisibleXRangeMinimum(mVisibleRange);

            // move to the latest entry
            mChart.moveViewToX(data.getEntryCount());
        }

        setAxisLimits();
    }

    public void moveToXOffset(float x) {

        // let the chart know it's data has changed
//        mChart.notifyDataSetChanged();
//        mChart.invalidate();

        // limit the number of visible entries
//        mChart.setVisibleXRangeMinimum(mVisibleRange);
        float range = mChart.getVisibleXRange() / 2;
//        mChart.setVisibleXRange(x - range, x + range);
        // chart.setVisibleYRange(30, AxisDependency.LEFT);

        // move to the latest entry
//        mChart.moveViewToX(x);
//        mChart.centerViewToAnimated(x, 0, YAxis.AxisDependency.LEFT, 100);
//        mChart.setVisibleXRange(mVisibleRange, mVisibleRange);

        mOffsetX = x;

        mChart.centerViewTo(x, 0, YAxis.AxisDependency.LEFT);

        notifyDatasetChanged();

//        updateZoom();
    }

    public void updateZoom() {
        mChart.fitScreen();
//        mChart.zoom(mZoomX, 1, mOffsetX, 0, YAxis.AxisDependency.LEFT);
        mChart.zoom(mZoomX, 1, 0, 0, YAxis.AxisDependency.LEFT);
//        notifyDatasetChanged();
    }

    public long getEntryCount() {
        return mEntryCount;
    }

    public void updateChartData() {
        notifyDatasetChanged();

        mChart.setTouchEnabled(true);
//        mChart.setDragEnabled(true);
        mChart.setDragEnabled(false);
        mChart.setScaleXEnabled(true);
        mChart.setPinchZoom(true);

//        mChart.setOnTouchListener(new View.OnTouchListener() {
//            @Override
//            public boolean onTouch(View v, MotionEvent event) {
//                Log.v(TAG, "onTouch [" + event.getX() + ", " + event.getY() + "]");
//                return false;
//            }
//        });

//        mChart.setOnChartGestureListener(new OnChartGestureListener() {
//            @Override
//            public void onChartGestureStart(MotionEvent me, ChartTouchListener.ChartGesture lastPerformedGesture) {
//
//            }
//
//            @Override
//            public void onChartGestureEnd(MotionEvent me, ChartTouchListener.ChartGesture lastPerformedGesture) {
//
//            }
//
//            @Override
//            public void onChartLongPressed(MotionEvent me) {
//
//            }
//
//            @Override
//            public void onChartDoubleTapped(MotionEvent me) {
//
//            }
//
//            @Override
//            public void onChartSingleTapped(MotionEvent me) {
//
//            }
//
//            @Override
//            public void onChartFling(MotionEvent me1, MotionEvent me2, float velocityX, float velocityY) {
//
//            }
//
//            @Override
//            public void onChartScale(MotionEvent me, float scaleX, float scaleY) {
//                float xPos = (me.getX(0) + me.getX(1)) / 2f, yPos = (me.getY(0) + me.getY(1)) / 2f;
////                chart2.fitScreen();
////                chart2.zoom(scaleX, scaleY, xPos, yPos);
//            }
//
//            @Override
//            public void onChartTranslate(MotionEvent me, float dX, float dY) {
//
//            }
//        });

        mChart.getAxisLeft().setDrawGridLines(true);
        mChart.getAxisLeft().setGranularityEnabled(true);
        mChart.getAxisLeft().setGranularity(1);

        mChart.getXAxis().setDrawGridLines(true);
        mChart.getXAxis().setGranularityEnabled(true);
        mChart.getXAxis().setGranularity(100);
    }

//    public void clearDataSets(int count) {
//        LineData data = mChart.getData();
//        if (data != null) {
//            data = new LineData();
//            for (int i = 0; i < count; i++) {
//                ILineDataSet set = data.getDataSetByIndex(i);
//                set = createSet();
//                data.addDataSet(set);
//            }
//        }
//    }

//    private LineDataSet createSet() {
//        LineDataSet set = new LineDataSet(null, "Dynamic Data");
//        set.setAxisDependency(YAxis.AxisDependency.RIGHT);
//        set.setColor(ColorTemplate.getHoloBlue());
//        set.setCircleColor(Color.WHITE);
//        set.setLineWidth(1f);
//        set.setDrawCircles(false);
//        set.setDrawCircleHole(false);
//        set.setCircleRadius(3f);
//
////        set.setDrawCircles(true);
////        set.setCircleRadius(1f);
////        set.setFillAlpha(65);
////        set.setFillColor(ColorTemplate.getHoloBlue());
////        set.setHighLightColor(Color.rgb(244, 117, 117));
////        set.setValueTextColor(Color.WHITE);
////        set.setValueTextSize(10f);
//
//        set.setDrawValues(false);
//        return set;
//    }
}
