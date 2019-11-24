package com.ndipatri.iot.googleproximity.utils


import android.bluetooth.BluetoothAdapter

class BluetoothHelper {

    val isBluetoothSupported: Boolean
        get() = BluetoothAdapter.getDefaultAdapter() != null

    val isBluetoothEnabled: Boolean
        get() = isBluetoothSupported && BluetoothAdapter.getDefaultAdapter().isEnabled
}
