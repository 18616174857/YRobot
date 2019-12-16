package com.yrobot.exo.app.data;

public class DataPacket {
    public DataPacket() {

    }

    public void setData(byte[] data_in) {
        data = data_in;
        if (data != null) {
            length = data.length;
        }
        timestamp = System.currentTimeMillis();
    }

    public byte[] data;
    public int length;
    public long timestamp;
    public int index;
}
