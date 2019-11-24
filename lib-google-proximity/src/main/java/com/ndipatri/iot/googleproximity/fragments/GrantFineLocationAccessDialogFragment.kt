package com.ndipatri.iot.googleproximity.fragments

import android.Manifest
import android.app.AlertDialog
import android.app.Dialog
import android.os.Bundle
import android.util.TypedValue
import android.view.Gravity
import android.widget.TextView

import com.ndipatri.iot.googleproximity.R

import androidx.fragment.app.DialogFragment

class GrantFineLocationAccessDialogFragment : DialogFragment() {

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {

        val builder = AlertDialog.Builder(activity)

        val dialogTitle = resources.getString(R.string.user_permission_requested)
        val titleView = TextView(activity)
        titleView.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f)
        titleView.text = dialogTitle
        titleView.gravity = Gravity.CENTER

        builder.setTitle(dialogTitle)
                .setNeutralButton(R.string.grant_fine_location_access) { dialog, which -> requestPermissions(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION), PERMISSION_REQUEST_FINE_LOCATION) }

        val dialog = builder.create()

        dialog.window!!.attributes.windowAnimations = R.style.slideup_dialog_animation
        dialog.setCanceledOnTouchOutside(false)

        return dialog
    }

    companion object {

        private val TAG = GrantFineLocationAccessDialogFragment::class.java!!.getSimpleName()

        val PERMISSION_REQUEST_FINE_LOCATION = 444
    }
}

