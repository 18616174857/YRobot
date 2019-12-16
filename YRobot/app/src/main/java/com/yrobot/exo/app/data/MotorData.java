package com.yrobot.exo.app.data;

import android.util.Log;

import com.yrobot.exo.app.utils.State;

import java.util.ArrayList;

public class MotorData {

    private final static String TAG = MotorData.class.getSimpleName();

    public MotorData(String prefix_in) {
        position = 0.0f;
        velocity = 0.0f;
        current = 0.0f;
        prefix = prefix_in;

        enabled.setOnStateChange(new Runnable() {
            @Override
            public void run() {
                Log.v(TAG, "Enable changed [" + enabled.state + "]");
            }
        });

//        calibrated.setOnStateChange(new Runnable() {
//            @Override
//            public void run() {
//                Log.v(TAG, "Calibration changed [" + calibrated.state + "]");
//            }
//        });
        initGaitCycleArray();
    }

    private float convert(int val, float multiplier) {
        return ((float) val / multiplier);
    }

    private float convert(int val) {
        return convert(val, 10.0f);
    }

    float mult = 1000.0f;

    public void set(int p, int v, int c) {
        position = convert(p, mult);
        velocity = convert(v, mult);
        current = convert(c, mult);
//        addNewEntry(current, position);
    }

    public void setRequest(int p, int v, int c) {
        float mult = 1000.0f;
        position_req = convert(p, mult);
        velocity_req = convert(v, mult);
        current_req = convert(c, mult);
    }

    public static final boolean USE_REQUEST = true;

    public float[] getValsPos() {
        if (USE_REQUEST) {
            return new float[]{position, position_req};
        } else {
            return new float[]{position};
        }
    }

    public float[] getValsVel() {
        if (USE_REQUEST) {
            return new float[]{velocity, velocity_req};
        } else {
            return new float[]{velocity};
        }
    }

    public float[] getValsCur() {
        if (USE_REQUEST) {
            return new float[]{current, current_req};
        } else {
            return new float[]{current};
        }
    }

    public void print() {
        Log.v(TAG, "Motor [" + prefix + "] pos: [" + position + "] vel: [" + velocity + "] cur: [" + current + "]  En: " +
                "(" + (enabled.state ? 1 : 0) + ") St: (" + (estopped ? 1 : 0) + ") Cal: (" + (calibrated.state ? 1 : 0) + ")");
    }

    public float[] getArray() {
        float[] vals = new float[3];
        vals[0] = position;
        vals[1] = velocity;
        vals[2] = current;
        return vals;
    }

    public class MotorAndGait {
        public MotorAndGait(float perc, float pos) {
            position = pos;
            percent = perc;
        }

        public float percent;
        public float position;
    }

    public class MotorAndGaitCycle {
        public ArrayList<MotorAndGait> list = new ArrayList<>();

        public MotorAndGaitCycle() {
        }

        public boolean add(float perc, float pos) {
            MotorAndGait mg = new MotorAndGait(perc, pos);
//            Log.v(TAG, "addNewEntry cycleCount [" + perc + " / " + pos + "]");
            if (list.isEmpty()) {
                list.add(mg);
                return true;
            } else {
                MotorAndGait last = list.get(list.size() - 1);
                float dt = perc - last.percent;
                if (dt < 0) {
                    return false;
                } else {
                    if (Math.abs(dt) > 0.1) {
                        list.add(mg);
                    }
                    return true;
                }
            }
        }

        public void reset() {
            list.clear();
        }
    }

    ArrayList<MotorAndGaitCycle> mCycleList = new ArrayList<>();
    public final static int MAX_CYCLES = 2;
    public long cycle_count = 0;
    public int cycle_index = 0;
    MotorAndGaitCycle currentCycle = null;

    private void initGaitCycleArray() {
        for (int i = 0; i < MAX_CYCLES; i++) {
            mCycleList.add(new MotorAndGaitCycle());
        }
        cycle_count = 0;
        cycle_index = 0;
    }

    public boolean hasEnoughMotorGaitData() {
        return cycle_count > MAX_CYCLES;
    }

    public boolean hasNewMotorGaitData() {
        boolean tmp = hasUpdate;
        hasUpdate = false;
        return cycle_count > MAX_CYCLES && tmp;
    }

    private void test() {
        MotorData md = ExoData.getInstance().motorDataL;
        if (md.hasNewMotorGaitData()) {
            MotorAndGait[] array = md.getCycleArray(0);
        }
    }

    public MotorAndGait[] getCycleArray(int index) {
        if (index > MAX_CYCLES) {
            return null;
        }
        MotorAndGait[] array = new MotorAndGait[mCycleList.size()];
        return (MotorAndGait[]) mCycleList.get(index).list.toArray(array);
    }

    private boolean hasUpdate = false;

    public void addNewEntry(float perc, float pos) {
        cycle_index = (int) (cycle_count % MAX_CYCLES);
        currentCycle = mCycleList.get(cycle_index);
        if (!currentCycle.add(perc, pos)) {
            cycle_count++;
            if (cycle_count > MAX_CYCLES) {
                cycle_index = (int) (cycle_count % MAX_CYCLES);
                currentCycle = mCycleList.get(cycle_index);
                currentCycle.reset();
                Log.v(TAG, "addNewEntry Reset [" + cycle_index + " / " + cycle_count + "]");
                hasUpdate = true;
            }
//            Log.v(TAG, "addNewEntry cycleCount [" + cycle_index + " / " + cycle_count + "]");
            if (cycle_count == 1) {
//                Log.v(TAG, "addNewEntry 1 [" + currentCycle.list.size() + "]");
//                Log.v(TAG, "addNewEntry cycleCount [" + getCycleArray(0) + "]");
            }
        }
    }

    public void setStatus(byte val) {
        enabled.set(((val >> 0) & 0x01) == 1);
        estopped = ((val >> 1) & 0x01) == 1;
        calibrated.set(((val >> 2) & 0x01) == 1);
        running = ((val >> 3) & 0x01) == 1;
    }

    private String prefix;

    public float position;
    public float velocity;
    public float current;
    public float position_req;
    public float velocity_req;
    public float current_req;

    public State enabled = new State();
    public boolean estopped;
    public State calibrated = new State();
    public boolean running;
}
