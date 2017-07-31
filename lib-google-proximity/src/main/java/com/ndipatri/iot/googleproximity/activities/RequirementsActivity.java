package com.ndipatri.iot.googleproximity.activities;


import android.Manifest;
import android.bluetooth.BluetoothAdapter;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.os.Build;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;
import android.widget.Toast;

import com.cantrowitz.rxbroadcast.RxBroadcast;
import com.ndipatri.iot.googleproximity.fragments.EnableBluetoothDialogFragment;
import com.ndipatri.iot.googleproximity.fragments.GrantFineLocationAccessDialogFragment;
import com.ndipatri.iot.googleproximity.utils.BluetoothHelper;

import io.reactivex.disposables.Disposable;

/**
 * NJD TODO - MOVE TO LIBRARY
 */
public class RequirementsActivity extends AppCompatActivity {

    private static final String TAG = RequirementsActivity.class.getSimpleName();

    protected EnableBluetoothDialogFragment enableBluetoothDialogFragment;
    protected GrantFineLocationAccessDialogFragment grantFineLocationAccessDialogFragment;

    private Disposable bluetoothStateChangeDisposable;

    @Override
    protected void onResume() {
        super.onResume();

        beginUserPermissionCheck();
    }

    @Override
    protected void onPause() {
        super.onPause();

        if (null != bluetoothStateChangeDisposable) {
            bluetoothStateChangeDisposable.dispose();
        }
    }

    protected BluetoothHelper getBluetoothHelper() {
        return new BluetoothHelper();
    }

    private void beginUserPermissionCheck() {
        // NJD TODO - need to get this sorted for lower than M devices...
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            if (this.checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
                grantFineLocationAccessDialogFragment = new GrantFineLocationAccessDialogFragment();
                grantFineLocationAccessDialogFragment.show(getSupportFragmentManager().beginTransaction(), "grant location access dialog");
            } else {
                continueWithUserPermissionCheck();
            }
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode,
                                           String permissions[],
                                           int[] grantResults) {
        switch ((short) requestCode) {
            case GrantFineLocationAccessDialogFragment.PERMISSION_REQUEST_FINE_LOCATION: {
                if (grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                    Log.d(TAG, "fine location permission granted");
                    continueWithUserPermissionCheck();
                } else {
                    Toast.makeText(this, "This application cannot run without Fine Location Access!", Toast.LENGTH_SHORT).show();
                    failedToFulfillRequirements();
                }
                return;
            }
        }
    }

    private void continueWithUserPermissionCheck() {
        if (!getBluetoothHelper().isBluetoothSupported()) {
            Toast.makeText(this, "This application cannot run without Bluetooth support!", Toast.LENGTH_SHORT).show();
            failedToFulfillRequirements();
        } else {
            if (!getBluetoothHelper().isBluetoothEnabled()) {
                enableBluetoothDialogFragment = new EnableBluetoothDialogFragment();
                enableBluetoothDialogFragment.show(getSupportFragmentManager().beginTransaction(), "enable bluetooth dialog");
            } else {
                successfullyFulfilledRequirements();
            }
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);

        switch (requestCode) {
            case EnableBluetoothDialogFragment.REQUEST_ENABLE_BT:
                enableBluetoothDialogFragment = null;

                if (resultCode == RESULT_OK) {
                    successfullyFulfilledRequirements();
                } else {
                    Toast.makeText(this, "This application cannot run without Bluetooth enabled!", Toast.LENGTH_SHORT).show();
                    failedToFulfillRequirements();
                }
                break;
        }
    }

    private Disposable registerForBluetoothStateChangeBroadcast() {
        IntentFilter filter = new IntentFilter();
        filter.addAction(BluetoothAdapter.ACTION_CONNECTION_STATE_CHANGED);

        return RxBroadcast.fromBroadcast(this, filter).subscribe(intent -> {
            if (null != enableBluetoothDialogFragment) {
                beginUserPermissionCheck();
            }
        });
    }

    /**
     * Override to stop any background services that are not necessary now.
     */
    protected void failedToFulfillRequirements() {
        finish();
    }

    /**
     * Override to start any background services...
     */
    protected void successfullyFulfilledRequirements() {
        bluetoothStateChangeDisposable = registerForBluetoothStateChangeBroadcast();
    }
}
