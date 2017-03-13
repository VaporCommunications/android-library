package com.blescent.library;

import android.bluetooth.BluetoothGattCharacteristic;

/**
 * Created by darshit on 6/3/17.
 */
public class QueueData {
    private byte[] payload;
    private boolean waitForResponse;
    private BluetoothGattCharacteristic characteristic;

    QueueData(byte[] payload,boolean waitForResponse,BluetoothGattCharacteristic characteristic){
        this.payload = payload;
        this.waitForResponse = waitForResponse;
        this.characteristic = characteristic;
    }
    public byte[] getPayload() {
        return payload;
    }

    public void setPayload(byte[] payload) {
        this.payload = payload;
    }

    public boolean isWaitForResponse() {
        return waitForResponse;
    }

    public void setWaitForResponse(boolean waitForResponse) {
        this.waitForResponse = waitForResponse;
    }

    public BluetoothGattCharacteristic getCharacteristic() {
        return characteristic;
    }

    public void setCharacteristic(BluetoothGattCharacteristic characteristic) {
        this.characteristic = characteristic;
    }
}
