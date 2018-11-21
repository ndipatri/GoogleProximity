package com.ndipatri.iot.googleproximity.sample;

import android.app.Application;
import android.util.Log;

import com.ndipatri.iot.googleproximity.GoogleProximity;

public class GoogleProximityApp extends Application {

    private static final String TAG = GoogleProximityApp.class.getSimpleName();

    private static GoogleProximityApp instance;

    public GoogleProximityApp() {
        GoogleProximityApp.instance = this;
    }

    public static GoogleProximityApp getInstance() {
        return instance;
    }

    @Override
    public void onCreate() {
        super.onCreate();

        Log.d(TAG, "Starting app ...");

        GoogleProximity.initialize(this, true);
    }
}
