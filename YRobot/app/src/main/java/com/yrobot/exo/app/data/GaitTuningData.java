package com.yrobot.exo.app.data;

public class GaitTuningData {
    public float len_1 = 10;
    public float len_2 = 80;
    public float percent_1 = 40;
    public float percent_2 = 60;
    public byte mode = CABLE_LEN_COMMAND_SINE;

    public final static int CABLE_LEN_COMMAND_TRIANGLE = 0;
    public static final int CABLE_LEN_COMMAND_SINE = 1;
    public static final int CABLE_LEN_COMMAND_STEP = 2;

    public GaitTuningData() {
        len_1 = -0.5f;
        len_2 = 80;
        percent_1 = 40;
        percent_2 = 60;
        mode = CABLE_LEN_COMMAND_SINE;
    }
}
