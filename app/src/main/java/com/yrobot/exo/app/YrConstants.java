package com.yrobot.exo.app;

import android.content.Context;
import android.graphics.Color;
import android.graphics.PorterDuff;
import android.graphics.drawable.ShapeDrawable;
import android.graphics.drawable.shapes.OvalShape;
import android.os.Handler;
import android.os.Looper;
import android.support.v4.content.ContextCompat;
import android.util.DisplayMetrics;
import android.util.Log;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.Toast;

import com.github.mikephil.charting.utils.ColorTemplate;
import com.sdsmdg.harjot.crollerTest.Croller;
import com.xw.repo.BubbleSeekBar;
import com.yrobot.exo.R;

import static android.support.constraint.Constraints.TAG;

public class YrConstants {

    public static String PREFIX = "yr-";
    public static String DEBUG = PREFIX + "DEBUG";
    public static String SCANNER = PREFIX + "SCANNER";

    public static String BLE_NAME = "YRobot";

    //--------------------------------------------------------------------------//
    //  BLE COMM IDS
    //--------------------------------------------------------------------------//
    public static final byte KEY_UNKNOWN = 0;
    public static final byte KEY_ENABLE = 1;
    public static final byte KEY_GAIN_CONTROL = 2;
    public static final byte KEY_FEEDBACK_APP = 3;
    public static final byte KEY_CALIBRATE_CMD = 4;
    public static final byte KEY_GAIN_CONTROL2 = 5;
    public static final byte KEY_CALIBRATE_STATUS = 10;
    public static final byte KEY_ENABLE_CURRENT = 11;
    public static final byte KEY_ACCEL = 15;
    public static final byte KEY_VEL = 16;
    public static final byte KEY_PACKET_SELECT = 17;
    public static final byte KEY_RESTART = 18;
    public static final byte KEY_PROFILE_CONTROL_MODE = 19;
    public static final byte KEY_PROFILE_TUNING = 20;
    public static final byte KEY_PARAM_REQUEST = 25;
    public static final byte KEY_PARAM_SET = 26;
    public static final byte KEY_DATA_RECORDER = 28;
    public static final byte KEY_FIRMWARE_PACKET = 30;
    public static final byte KEY_FIRMWARE_PACKET_META = 31;
    public static final byte KEY_DFU_START = 32;

    public final static byte KEY_DFU_START_ENABLE = 1;
    public final static byte KEY_DFU_START_CANCEL = 2;

    public final static byte KEY_DATA_RECORDER_START = 1;
    public final static byte KEY_DATA_RECORDER_STOP = 2;

    public static final String TEXT_RECORD = "Record";
    public static final String TEXT_STOP = "Stop";

    // PACKET SELECT IDENTIFIERS
    public static final byte KEY_FEEDBACK_PACKET_MOTOR = 7;
    public static final byte KEY_FEEDBACK_PACKET_LEG_BOARD = 8;
    public static final byte KEY_FEEDBACK_PACKET_STATUS = 9;

    //--------------------------------------------------------------------------//
    //  BLE PACKET PROTOCOL
    //--------------------------------------------------------------------------//
    public static final byte IDX_KEY = 0;
    public static final byte IDX_LEN = 1;
    public static final byte IDX_CRC = 2;
    public static final byte IDX_DATA = 3;

    //--------------------------------------------------------------------------//
    //  OPTIONS
    //--------------------------------------------------------------------------//
    public final static boolean USE_RX_RING_BUFFER = true;
    public static final boolean USE_TX_RING_BUFFER = true;

    public static final byte BIT_RECORDED = (byte) 0x80;
    public static final byte BIT_RECORDING_DONE = (byte) 0x40;

    public static final boolean CHECK_CRC = true;
//    public static final boolean CHECK_CRC = false;

    public static final int gs = 200;
    public static final int gs2 = gs - 50;
    public static final int fillColor = Color.argb(150, gs, gs, gs);
    public static final int fillColorDark = Color.argb(200, gs2, gs2, gs2);
    public static final int fillColorGrid = Color.argb(150, gs2, gs2, gs2);

    public static final int[] colors = new int[]{
            ColorTemplate.PASTEL_COLORS[0],
            ColorTemplate.PASTEL_COLORS[1],
            ColorTemplate.PASTEL_COLORS[2],
    };

    public String g_peripheral_identifier = null;

    //--------------------------------------------------------------------------//
    //  UTIL & HELPER FUNCTIONS
    //--------------------------------------------------------------------------//
    public static void log(String msg, String msg2) {
        if (msg2 == null) {
            msg2 = "";
        }
        Log.v(DEBUG, msg + ": " + msg2);
    }

    public static void setTextView(final TextView tv, final String text) {
        new Handler(Looper.getMainLooper()).post(() -> {
            tv.setText(text);
        });
    }

    public static void log(String msg) {
        Log.v(DEBUG, msg);
    }

    public static String getCurrentMethodName() {
        return "Unknown";
//        return MethodHandles.lookup().lookupClass().getEnclosingMethod().getName();
    }

    public static int dpToPx(Context context, float dp) {
        return Math.round(dp * context.getResources().getDisplayMetrics().density);
//        return Math.round(dp * DisplayMetrics.getDisplayMetrics(context).density);
    }

    public static void toastText(Context context, String text) {
        Toast.makeText(context, text, Toast.LENGTH_SHORT).show();
    }

    public static long millis() {
        return System.currentTimeMillis();
    }

    public static byte[] addKey(byte[] bytes, byte key) {
        byte[] b = new byte[bytes.length + 1];
        b[0] = key;
        for (int i = 0; i < bytes.length; i++) {
            b[i + 1] = bytes[i];
        }
        return b;
    }

    public static void configBubbleSeekBar(Context context, BubbleSeekBar seekBar, float min, float max, float progress) {
        seekBar.getConfigBuilder()
                .min(min)
                .max(max)
                .progress(progress)
                .trackColor(fillColor)
                .secondTrackColor(fillColorDark)
                .thumbColor(fillColorDark)
                .showThumbText()
                .thumbRadius(12)
                .thumbRadiusOnDragging(15)
                .thumbTextColor(ContextCompat.getColor(context, R.color.black))
                .thumbTextSize(18)
                .bubbleColor(ContextCompat.getColor(context, R.color.blue_700))
                .bubbleTextSize(20)
                .sectionCount(1)
                .sectionTextColor(ContextCompat.getColor(context, R.color.colorPrimary))
                .showThumbText()
                .thumbRadius(8)
                .thumbRadiusOnDragging(12)
                .thumbTextSize(14)
                .bubbleTextSize(14)
                .showSectionMark()
                .sectionTextPosition(BubbleSeekBar.TextPosition.BELOW_SECTION_MARK)
                .build();

//                        .thumbColor(ContextCompat.getColor(getContext(), R.color.black))
//                        .trackColor(ContextCompat.getColor(getContext(), R.color.color_gray))
//                        .secondTrackColor(ContextCompat.getColor(getContext(), R.color.color_blue))
//                        .thumbColor(ContextCompat.getColor(getContext(), R.color.color_blue))
//                        .showSectionText()
//                        .sectionTextSize(18)
//                        .thumbTextColor(ContextCompat.getColor(getContext(), R.color.color_red))
//                        .bubbleColor(ContextCompat.getColor(getContext(), R.color.color_green))
//                        .seekBySection()
//                        .autoAdjustSectionMark()


    }

    //-----------------------------------------------------------------------------------------//
    //  HELPER FUNCTIONS
    //-----------------------------------------------------------------------------------------//
    public static float map(float x, float in_min, float in_max, float out_min, float out_max) {
        return (x - in_min) * (out_max - out_min) / (in_max - in_min) + out_min;
    }

    public static float constrain(float val, float min, float max) {
        return Math.max(min, Math.min(val, max));
    }

    public static int constrain(int val, int min, int max) {
        return Math.max(min, Math.min(val, max));
    }

    public static short bytesToShort(byte b0, byte b1) {
        return (short) ((b0 & 0xff) + ((b1 & 0xff) << 8));
    }

    //-----------------------------------------------------------------------------------------//
    //  STYLE
    //-----------------------------------------------------------------------------------------//
    public static void styleSeekBar(SeekBar seekBar) {
        ShapeDrawable th = new ShapeDrawable(new OvalShape());
        th.setIntrinsicHeight(100);
        th.setIntrinsicWidth(100);
        th.setColorFilter(Color.GRAY, PorterDuff.Mode.SRC_OVER);
        seekBar.setThumb(th);
    }

    public static void configCroller(Croller croller) {
        croller.setIndicatorWidth(10);
        croller.setBackCircleColor(Color.parseColor("#EDEDED"));
        croller.setMainCircleColor(Color.WHITE);
        croller.setMax(50);
        croller.setStartOffset(45);
        croller.setIsContinuous(false);
        croller.setLabelColor(Color.BLACK);
        croller.setProgressPrimaryColor(Color.parseColor("#0B3C49"));
        croller.setIndicatorColor(Color.parseColor("#0B3C49"));
        croller.setProgressSecondaryColor(Color.parseColor("#EEEEEE"));
    }
}
