package com.ndipatri.iot.googleproximity.sample;

import android.app.Application;
import android.util.Log;

import com.ndipatri.iot.googleproximity.GoogleProximity;

import io.reactivex.MaybeObserver;
import io.reactivex.disposables.Disposable;

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

        GoogleProximity.initialize(this, false);

        GoogleProximity.getInstance().retrieveAttachment(new byte[] {}).subscribe(new MaybeObserver<String[]>() {
            @Override
            public void onSubscribe(Disposable d) {

            }

            @Override
            public void onSuccess(String[] strings) {
                Log.d(TAG, "Success.");
            }

            @Override
            public void onError(Throwable e) {
                Log.e(TAG, "Exception.", e);
            }

            @Override
            public void onComplete() {

            }
        });
    }
}
