package com.ndipatri.iot.googleproximity.sample;

import android.os.Bundle;
import android.app.Activity;
import android.util.Log;

import com.ndipatri.iot.googleproximity.GoogleProximity;
import com.ndipatri.iot.googleproximity.activities.RequirementsActivity;

import io.reactivex.MaybeObserver;
import io.reactivex.disposables.Disposable;

public class MainActivity extends RequirementsActivity {

    private static final String TAG = MainActivity.class.getSimpleName();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
    }

    @Override
    protected void onResume() {
        super.onResume();

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
