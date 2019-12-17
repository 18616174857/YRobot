package com.yrobot.exo.app.utils;

import android.os.Environment;
import android.util.Log;

import java.io.BufferedInputStream;
import java.io.DataInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.ArrayList;

public class FirmwareManager {

    private final static String TAG = "yr-" + FirmwareManager.class.getSimpleName();

    public static final int PACKET_SIZE = 128;
    public static final long MAX_BYTE_SIZE = 300000;
//    public static final String DEFAULT_FILENAME = "main_board_1.0.1.bin";
    public static final String DEFAULT_FILENAME = "main_board_1.0.3.bin";

    byte binary_file_bytes[] = null;
    ArrayList<byte[]> byte_buffer_list = null;

    int mIndexSent = 0;
    boolean mFileLoaded = false;

    private FirmwareManager() {
//        if (checkSelfPermission(android.Manifest.permission.WRITE_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED) {
//        }
        mFileLoaded = loadFile(DEFAULT_FILENAME);
        resetBinaryFile();
    }

    private static volatile FirmwareManager sSoleInstance = new FirmwareManager();

    public static FirmwareManager getInstance() {
        return sSoleInstance;
    }

    public int getFileSize() {
        if (!mFileLoaded) {
            return -1;
        } else {
            return binary_file_bytes.length;
        }
    }

    public int getNumPackets() {
        if (!mFileLoaded) {
            return -1;
        } else {
            return byte_buffer_list.size();
        }
    }

    public boolean loadFile(String filename) {
        String path = Environment.getExternalStorageDirectory().getAbsolutePath() + "/Download/" + filename;
        binary_file_bytes = getBinaryFile(path);
        if (binary_file_bytes == null) {
            return false;
        }
        if (binary_file_bytes.length < 100) {
            return false;
        }

        int num_bytes = binary_file_bytes.length;
        int num_packets = (int) Math.floor(((double) num_bytes / (double) PACKET_SIZE));
        int remainder = num_bytes % PACKET_SIZE;

        byte_buffer_list = new ArrayList<>();
        for (int i = 0; i < num_packets; i++) {
            byte[] byte_buffer = new byte[PACKET_SIZE];
            for (int j = 0; j < PACKET_SIZE; j++) {
                int idx = i * PACKET_SIZE + j;
                byte_buffer[j] = binary_file_bytes[idx];
            }
            byte_buffer_list.add(byte_buffer);
        }

        if (remainder > 0) {
            byte[] byte_buffer = new byte[remainder];
            for (int j = 0; j < remainder; j++) {
                int idx = num_packets * PACKET_SIZE + j;
                byte_buffer[j] = binary_file_bytes[idx];
            }
            byte_buffer_list.add(byte_buffer);
        }

        Log.v(TAG, "File [" + binary_file_bytes.length + "] " +
                "packets [" + num_packets + "] [" + byte_buffer_list.size() + "] remainder: [" + remainder + "]");

        return true;
    }

    public void resetBinaryFile() {
        mIndexSent = 0;
    }

    public byte[] getByteBuffer(int index) {
        if (!mFileLoaded) {
            return null;
        }
        if (index > byte_buffer_list.size() - 1) {
            Log.v(TAG, "getByteBuffer [" + index + "] greater than byte buffer list " + byte_buffer_list.size() + ")");
            return null;
        }
        byte[] buf = new byte[PACKET_SIZE + 3];
        byte[] data_buf = byte_buffer_list.get(index);
        buf[0] = (byte) index;
        buf[1] = (byte) (index >> 8);
        buf[2] = (byte) data_buf.length;
        for (int i = 0; i < data_buf.length; i++) {
            buf[i + 3] = data_buf[i];
        }
        mIndexSent = index;
        return buf;
    }

    private byte[] getBinaryFile(String path) {
        Log.v(TAG, "Read file [" + path + "]");
        File file = new File(path);
        Log.v(TAG, "Read file length [" + file.length() + "]");
        byte bytes[] = new byte[(int) file.length()];
        try {
            BufferedInputStream bis = new BufferedInputStream(new FileInputStream(file));
            DataInputStream dis = new DataInputStream(bis);
            dis.readFully(bytes);
            Log.v(TAG, "Read file [" + bytes.length + "]");
            return bytes;
        } catch (FileNotFoundException e) {
            e.printStackTrace();
        } catch (IOException e) {
            e.printStackTrace();
        }
        return null;

    }

}
