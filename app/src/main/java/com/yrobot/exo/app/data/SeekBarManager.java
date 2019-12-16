package com.yrobot.exo.app.data;

import android.graphics.Color;
import android.util.Log;
import android.widget.LinearLayout;
import android.widget.SeekBar;

import com.sdsmdg.harjot.crollerTest.Croller;
import com.yrobot.exo.app.ConnectedPeripheralFragment;
import com.yrobot.exo.app.YrConstants;
import com.yrobot.exo.ble.BleUtils;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;

import static com.yrobot.exo.app.YrConstants.KEY_PARAM_SET;
import static com.yrobot.exo.app.YrConstants.map;

public class SeekBarManager {

    private final static String TAG = "yr-" + SeekBarManager.class.getSimpleName();

    public String mLabel;
    public float mMin;
    public float mMax;
    public Croller mSeekBar;
    public SeekBar mSeekBar2;
    public byte mKey;
    public boolean mInitialized = false;
    ConnectedPeripheralFragment mFragment;
    public int mType;
    Param mParam;

    public static final int TYPE_BOOL = 1;
    public static final int TYPE_FLOAT = 2;

    public SeekBarManager(Croller seekBar, String label, float min, float max, byte key, ConnectedPeripheralFragment fragment) {
        mSeekBar = seekBar;
        mLabel = label;
        mMin = min;
        mMax = max;
        mKey = key;
        mType = TYPE_FLOAT;
        YrConstants.configCroller(seekBar);
        mFragment = fragment;
        mSeekBar.setLabel(label);
        configCroller(mSeekBar);
    }

    public SeekBarManager(SeekBar seekBar, final Param param, ConnectedPeripheralFragment fragment) {
        mSeekBar2 = seekBar;
        mParam = param;
        mLabel = param.name;
//        mMin = param.min;
//        mMax = param.max;
//        mKey = param.key;
        mType = TYPE_FLOAT;
        mFragment = fragment;
//        mSeekBar2.setLabel(label);

        configSeekBar(mSeekBar2);

        mSeekBar2.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                final float factor = map(progress, 0, 100, mMin, mMax);
                switch (mType) {
                    case TYPE_BOOL:
                        break;
                    case TYPE_FLOAT:
//                        ByteBuffer buffer = ByteBuffer.allocate(4).order(ByteOrder.LITTLE_ENDIAN);
//                        buffer.putFloat(factor);
////                        Log.v(TAG, "PARAM buf [" + BleUtils.bytesToHex2(buffer.array()) + "]");
//                        byte[] bytes = buffer.array();
////                        Log.v(TAG, "PARAM buf [" + BleUtils.bytesToHex2(bytes) + "]");
//                        bytes = YrConstants.addKey(bytes, mKey);
//                        Log.v(TAG, "PARAM buf2 [" + BleUtils.bytesToHex2(bytes) + "]");
//                        mFragment.sendByteBuffer(KEY_PARAM_SET, bytes);

                        ByteBuffer buffer = ByteBuffer.allocate(4).order(ByteOrder.LITTLE_ENDIAN);
                        buffer.putFloat(factor);
//                        Log.v(TAG, "PARAM buf [" + BleUtils.bytesToHex2(buffer.array()) + "]");
                        byte[] bytes = buffer.array();
//                        Log.v(TAG, "PARAM buf [" + BleUtils.bytesToHex2(bytes) + "]");
                        bytes = YrConstants.addKey(bytes, mKey);
                        Log.v(TAG, "PARAM buf2 [" + BleUtils.bytesToHex2(bytes) + "]");
                        mFragment.sendByteBuffer(KEY_PARAM_SET, bytes);
                        break;
                }
            }

            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {
            }

            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {
            }
        });
    }

    private void configSeekBar(SeekBar seekBar) {
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        int margin = 40;
        int margin_side = 30;
        lp.setMargins(margin_side, margin, margin_side, margin);

//        seekBar.getThumb().setBounds(0, 0, seekBar.getThumb().getIntrinsicWidth(), 30);
        seekBar.getThumb().setBounds(0, 0, 50, 50);
        //force a redraw
//        int progress = seekBar.getProgress();
//        seekBar.setProgress(0);
//        seekBar.setProgress(progress);

        seekBar.setLayoutParams(lp);
    }

    public void setup() {
        Log.v(TAG, "Setup Seekbar listener [" + mKey + "]");
        if (mSeekBar != null) {
            mSeekBar.setOnProgressChangedListener(new Croller.onProgressChangedListener() {
                @Override
                public void onProgressChanged(int progress) {
                    final float factor = map(progress, 0, 100, mMin, mMax);
                    switch (mType) {
                        case TYPE_BOOL:
                            break;
                        case TYPE_FLOAT:
                            mFragment.sendFloat(mKey, factor);
                            break;
                    }
                }
            });
        }
    }

    public void configCroller(Croller croller) {
        croller.setIndicatorWidth(10);
        croller.setBackCircleColor(Color.parseColor("#EDEDED"));
        croller.setMainCircleColor(Color.WHITE);
        croller.setMax(100);
        croller.setStartOffset(45);
//        croller.setIsContinuous(false);
        croller.setIsContinuous(true);
        croller.setLabelColor(Color.BLACK);
        croller.setProgressPrimaryColor(Color.parseColor("#0B3C49"));
        croller.setIndicatorColor(Color.parseColor("#0B3C49"));
        croller.setProgressSecondaryColor(Color.parseColor("#EEEEEE"));
    }
}
