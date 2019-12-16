package com.yrobot.exo.app.utils;

import java.util.DoubleSummaryStatistics;

import static com.yrobot.exo.app.YrConstants.millis;


public class MsgStat {
    DoubleSummaryStatistics stats = new DoubleSummaryStatistics();

    public MsgStat() {
        count_ = 0;
        count_last_ = 0;
        rate_ = 0;
        rate_average_ = 0;
        time_last_add_ = 0;
        time_last_update_rate_ = 0;
    }

    public void add(long val) {
        count_ += val;
        time_last_add_ = millis();
    }

    public void add() {
        add(1);
    }

    public void updateTime() {
        time_last_add_ = millis();
    }

    public void setCount(long val) {
        count_ = val;
    }

    public void reset() {
        count_ = 0;
        count_last_ = 0;
        time_last_update_rate_ = millis();
        time_last_add_ = millis();
    }

    public long getDt() {
        return millis() - time_last_add_;
    }

    public void update() {
        long now_us = millis();
        long count_diff = count_ - count_last_;
        long time_diff = now_us - time_last_update_rate_;
        rate_ = (double) count_diff * 1000.0 / ((double) time_diff + 1e-6);
        count_last_ = count_;
        time_last_update_rate_ = now_us;
//        stats.accept(rate_);
        updateRollingAverage(rate_);
    }

    final int NUM_SAMPLES = 10;

    void updateRollingAverage(double new_sample) {
        rate_average_ -= rate_average_ / NUM_SAMPLES;
        rate_average_ += new_sample / NUM_SAMPLES;
    }

    public double getRate() {
        return rate_;
    }

    public double getRateAverage() {
//        return stats.getAverage();
        return rate_average_;
    }

    public long getTimeLast() {
        return time_last_add_;
    }

    public long getCount() {
        return count_;
    }

    long count_;
    long count_last_;
    double rate_;
    long time_last_update_rate_;
    long time_last_add_;
    double rate_average_;
}
