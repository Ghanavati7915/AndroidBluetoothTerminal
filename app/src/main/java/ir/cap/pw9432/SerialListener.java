package ir.cap.pw9432;

import java.util.ArrayDeque;

interface SerialListener {
    void onSerialConnect      () throws InterruptedException;
    void onSerialConnectError (Exception e);
    void onSerialRead         (byte[] data);                // socket -> service
    void onSerialRead         (ArrayDeque<byte[]> datas);   // service -> UI thread
    void onSerialIoError      (Exception e);
}
