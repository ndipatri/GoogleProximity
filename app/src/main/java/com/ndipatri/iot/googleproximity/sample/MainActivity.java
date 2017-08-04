package com.ndipatri.iot.googleproximity.sample;

import android.os.Bundle;
import android.util.Log;

import com.ndipatri.iot.googleproximity.GoogleProximity;
import com.ndipatri.iot.googleproximity.activities.RequirementsActivity;

import org.altbeacon.beacon.Beacon;

import io.reactivex.Observable;
import io.reactivex.Observer;
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

        // NJD TODO - need to use this class to test the library and write tests.. duh!

        scanForNearbyPanelEasy().subscribe(new Observer<Beacon>() {
            @Override
            public void onSubscribe(Disposable d) {
                Log.d(TAG, "onSubscribe()");
            }

            @Override
            public void onNext(Beacon beacon) {
                Log.d(TAG, "onNext(): '" + beacon + "'");
            }

            @Override
            public void onError(Throwable e) {
                Log.e(TAG, "onError()", e);
            }

            @Override
            public void onComplete() {
                Log.d(TAG, "onComplete()");
            }
        });

        /**
        GoogleProximity.getInstance().retrieveAttachment(new byte[]{}).subscribe(new MaybeObserver<String[]>() {
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
         **/
    }

    public Observable<Beacon> scanForNearbyPanelEasy() {
        String beaconNamespaceId = getResources().getString(R.string.beaconNamespaceId);

        return GoogleProximity.getInstance().scanForNearbyBeacon(beaconNamespaceId, 10);
    }
}
