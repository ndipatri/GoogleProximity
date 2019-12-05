package com.ndipatri.iot.googleproximity.sample

import android.app.Application
import android.util.Log

import com.ndipatri.iot.googleproximity.GoogleProximity

class GoogleProximityApp : Application() {
    init {
        GoogleProximityApp.instance = this
    }

    override fun onCreate() {
        super.onCreate()

        Log.d(TAG, "Starting app ...")

        GoogleProximity.initialize(this, true)
    }

    companion object {

        private val TAG = GoogleProximityApp::class.java!!.getSimpleName()

        var instance: GoogleProximityApp? = null
            private set
    }
}
