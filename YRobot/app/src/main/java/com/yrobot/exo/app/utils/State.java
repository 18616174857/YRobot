package com.yrobot.exo.app.utils;

public class State {

    public State() {

    }

    public void set(boolean v) {
        state_last = v;
        state = v;
        if (state != state_last) {
            if (mCallback != null) {
                mCallback.run();
            }
        }
    }

    public void setOnStateChange(Runnable r) {
        mCallback = r;
    }

    public Runnable mCallback;
    public boolean state = false;
    public boolean state_last = false;
}
