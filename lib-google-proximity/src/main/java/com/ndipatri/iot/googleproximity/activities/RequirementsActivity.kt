package com.ndipatri.iot.googleproximity.activities


import android.Manifest
import android.bluetooth.BluetoothAdapter
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.os.Build
import android.util.Log
import android.widget.Toast

import com.ndipatri.iot.googleproximity.fragments.EnableBluetoothDialogFragment
import com.ndipatri.iot.googleproximity.fragments.GrantFineLocationAccessDialogFragment
import com.ndipatri.iot.googleproximity.utils.BluetoothHelper

import androidx.appcompat.app.AppCompatActivity

open class RequirementsActivity : AppCompatActivity() {

    protected var enableBluetoothDialogFragment: EnableBluetoothDialogFragment? = null
    protected var grantFineLocationAccessDialogFragment: GrantFineLocationAccessDialogFragment? = null

    protected val bluetoothHelper: BluetoothHelper
        get() = BluetoothHelper()

    private val bluetoothStateChangeReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            if (null != enableBluetoothDialogFragment) {
                beginUserPermissionCheck()
            }
        }
    }

    override fun onResume() {
        super.onResume()

        beginUserPermissionCheck()
    }

    protected fun shouldCheckBluetoothPermissions(): Boolean {
        return true
    }

    override fun onPause() {
        super.onPause()

        try {
            unregisterReceiver(bluetoothStateChangeReceiver)

            // ignore error if not already registered
        } catch (iae: IllegalArgumentException) {
        }

    }

    private fun beginUserPermissionCheck() {
        // NJD TODO - need to get this sorted for lower than M devices...
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            if (this.checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
                grantFineLocationAccessDialogFragment = GrantFineLocationAccessDialogFragment()
                grantFineLocationAccessDialogFragment!!.show(supportFragmentManager.beginTransaction(), "grant location access dialog")

            } else {
                continueWithUserPermissionCheck()
            }
        }
    }

    override fun onRequestPermissionsResult(requestCode: Int,
                                            permissions: Array<String>,
                                            grantResults: IntArray) {
        when (requestCode) {
            GrantFineLocationAccessDialogFragment.PERMISSION_REQUEST_FINE_LOCATION -> {
                if (grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                    Log.d(TAG, "fine location permission granted")
                    continueWithUserPermissionCheck()
                } else {
                    Toast.makeText(this, "This application cannot run without Fine Location Access!", Toast.LENGTH_SHORT).show()
                    failedToFulfillRequirements()
                }
                return
            }
        }
    }

    private fun continueWithUserPermissionCheck() {
        if (shouldCheckBluetoothPermissions()) {
            if (!bluetoothHelper.isBluetoothSupported) {
                Toast.makeText(this, "This application cannot run without Bluetooth support!", Toast.LENGTH_SHORT).show()
                failedToFulfillRequirements()
            } else {
                if (!bluetoothHelper.isBluetoothEnabled) {
                    enableBluetoothDialogFragment = EnableBluetoothDialogFragment()
                    enableBluetoothDialogFragment!!.show(supportFragmentManager.beginTransaction(), "enable bluetooth dialog")
                } else {
                    successfullyFulfilledRequirements()
                }
            }
        } else {
            successfullyFulfilledRequirements()
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)

        when (requestCode) {
            EnableBluetoothDialogFragment.REQUEST_ENABLE_BT -> {
                enableBluetoothDialogFragment = null

                if (resultCode == RESULT_OK) {
                    successfullyFulfilledRequirements()
                } else {
                    Toast.makeText(this, "This application cannot run without Bluetooth enabled!", Toast.LENGTH_SHORT).show()
                    failedToFulfillRequirements()
                }
            }
        }
    }

    private fun registerForBluetoothStateChangeBroadcast() {
        val filter = IntentFilter()
        filter.addAction(BluetoothAdapter.ACTION_CONNECTION_STATE_CHANGED)

        this.registerReceiver(bluetoothStateChangeReceiver, filter)
    }

    /**
     * Override to stop any background services that are not necessary now.
     */
    protected fun failedToFulfillRequirements() {
        finish()
    }

    /**
     * Override to start any background services...
     */
    protected fun successfullyFulfilledRequirements() {
        registerForBluetoothStateChangeBroadcast()
    }

    companion object {

        private val TAG = RequirementsActivity::class.java!!.getSimpleName()
    }
}
