package com.ndipatri.iot.googleproximity.fragments

import android.app.AlertDialog
import android.app.Dialog
import android.bluetooth.BluetoothAdapter
import android.content.DialogInterface
import android.content.Intent
import android.os.Bundle
import android.util.TypedValue
import android.view.Gravity
import android.widget.TextView

import com.ndipatri.iot.googleproximity.R

import androidx.fragment.app.DialogFragment

class EnableBluetoothDialogFragment : DialogFragment() {

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {

        val builder = AlertDialog.Builder(activity)

        val dialogTitle = resources.getString(R.string.bluetooth_disabled)
        val titleView = TextView(activity)
        titleView.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f)
        titleView.text = dialogTitle
        titleView.gravity = Gravity.CENTER

        builder.setTitle(dialogTitle)
                .setNeutralButton("Enable Blueooth") { dialog, which ->
                    val enableBtIntent = Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE)
                    startActivityForResult(enableBtIntent, REQUEST_ENABLE_BT)
                }

        val dialog = builder.create()

        dialog.window!!.attributes.windowAnimations = R.style.slideup_dialog_animation
        dialog.setCanceledOnTouchOutside(false)

        return dialog
    }

    companion object {

        private val TAG = EnableBluetoothDialogFragment::class.java!!.getSimpleName()

        val REQUEST_ENABLE_BT = -1
    }
}

