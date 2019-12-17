package com.yrobot.exo.app.data;

import android.support.annotation.NonNull;
import android.util.Log;

import com.yrobot.exo.app.utils.State;
import com.yrobot.exo.ble.BleUtils;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;

import static com.yrobot.exo.app.YrConstants.IDX_DATA;
import static com.yrobot.exo.app.YrConstants.convertToFloat;
import static com.yrobot.exo.app.YrConstants.convertToShort;

public class MotorData {

    private final static String TAG = MotorData.class.getSimpleName();

    private String prefix;

    public short status;
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

    public static final boolean USE_REQUEST = true;
    public static final int DATA_LEN = USE_REQUEST ? 7 : 4;

    private final float MULTIPLIER = 1000f;

    public MotorData(String prefix_in) {
        position = 0.0f;
        velocity = 0.0f;
        current = 0.0f;
        position_req = 0.0f;
        velocity_req = 0.0f;
        current_req = 0.0f;
        prefix = prefix_in;

        enabled.setOnStateChange(new Runnable() {
            @Override
            public void run() {
                Log.v(TAG, "Enable changed [" + enabled.state + "]");
            }
        });

        initGaitCycleArray();
    }

    public byte[] getByteArray() {
        short[] vals = new short[DATA_LEN];
        vals[0] = this.status;
        vals[1] = convertToShort(this.position, MULTIPLIER);
        vals[2] = convertToShort(this.velocity, MULTIPLIER);
        vals[3] = convertToShort(this.current, MULTIPLIER);
        if (USE_REQUEST) {
            vals[4] = convertToShort(this.position_req, MULTIPLIER);
            vals[5] = convertToShort(this.velocity_req, MULTIPLIER);
            vals[6] = convertToShort(this.current_req, MULTIPLIER);
        }
        ByteBuffer buffer = ByteBuffer.allocate(DATA_LEN * 2).order(ByteOrder.LITTLE_ENDIAN);
        for (int i = 0; i < vals.length; i++) {
            buffer.putShort(vals[i]);
        }
        return buffer.array();
    }

    public void setFromByteArray(@NonNull byte[] data) {
        short[] vals = new short[DATA_LEN];
        for (int i = 0; i < vals.length; i++) {
            int offset = IDX_DATA + (i * 2);
            vals[i] = ByteBuffer.wrap(Arrays.copyOfRange(data, offset, offset + 2)).order(ByteOrder.LITTLE_ENDIAN).getShort();
        }
        this.status = vals[0];
        this.position = convertToFloat(vals[1], MULTIPLIER);
        this.velocity = convertToFloat(vals[2], MULTIPLIER);
        this.current = convertToFloat(vals[3], MULTIPLIER);
        if (USE_REQUEST) {
            this.position_req = convertToFloat(vals[4], MULTIPLIER);
            this.velocity_req = convertToFloat(vals[5], MULTIPLIER);
            this.current_req = convertToFloat(vals[6], MULTIPLIER);
        }
    }

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
}
