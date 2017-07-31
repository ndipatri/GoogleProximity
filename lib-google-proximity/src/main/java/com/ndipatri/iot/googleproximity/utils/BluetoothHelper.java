package com.ndipatri.iot.googleproximity.utils;


import android.bluetooth.BluetoothAdapter;

public class BluetoothHelper {

    public boolean isBluetoothSupported() {
        return BluetoothAdapter.getDefaultAdapter() != null;
    }

    public boolean isBluetoothEnabled() {
        return isBluetoothSupported() && BluetoothAdapter.getDefaultAdapter().isEnabled();
    }
}
