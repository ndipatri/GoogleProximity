package com.ndipatri.iot.googleproximity.sample

import android.os.Bundle
import android.util.Log

import com.ndipatri.iot.googleproximity.GoogleProximity
import com.ndipatri.iot.googleproximity.activities.RequirementsActivity
import com.ndipatri.iot.googleproximity.utils.BeaconScanHelper
import io.reactivex.MaybeObserver

import org.altbeacon.beacon.Beacon

import io.reactivex.Observable
import io.reactivex.Observer
import io.reactivex.disposables.Disposable

class MainActivity : RequirementsActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
    }

    override fun onResume() {
        super.onResume()

        // NJD TODO - need to use this class to test the library and write tests.. duh!

        scanForNearbyPanelEasy().subscribe(object : Observer<BeaconScanHelper.BeaconUpdate> {
            override fun onSubscribe(d: Disposable) {
                Log.d(TAG, "onSubscribe()")
            }

            override fun onNext(beaconUpdate: BeaconScanHelper.BeaconUpdate) {
                Log.d(TAG, "onNext(): '$beaconUpdate'")

                beaconUpdate?.beacon?.apply {
                    if (this.isPresent) {
                        extractBeaconMetaDataFromCloud(this.get())
                    }
                }
            }

            override fun onError(e: Throwable) {
                Log.e(TAG, "onError()", e)
            }

            override fun onComplete() {
                Log.d(TAG, "onComplete()")
            }
        })
    }

    fun scanForNearbyPanelEasy(): Observable<BeaconScanHelper.BeaconUpdate> {
        val beaconNamespaceId = resources.getString(R.string.beaconNamespaceId)

        return GoogleProximity.instance!!.scanForNearbyBeacon(beaconNamespaceId, 10)
    }

    fun extractBeaconMetaDataFromCloud(beacon: Beacon) {
        Log.d(TAG, "Found beacon! ($beacon')")

        // Some beacon information is stored in the Google Cloud so it's available to
        // all consumers of these beacons and is mutable.
        //
        // We could use Google's NearbyAPI, but it has requirements on the foregrounded-ness of
        // our app, so we use the ProximityAPI, particularly since we're already using it...
        //

        // We use the unauthorized version so the user isn't challenged in any way: the user's google account
        // doesn't have to be declared.

        GoogleProximity.instance!!.retrieveAttachment(beacon).subscribe(object : MaybeObserver<Array<String?>> {
            override fun onSubscribe(d: Disposable) {}

            override fun onSuccess(beaconAttachment: Array<String?>) {
                Log.d(TAG, "Information retrieved for found beacon: '$beaconAttachment'.")
            }

            override fun onError(e: Throwable) {
                Log.e(TAG, "Error while retrieving attachment.", e)
            }

            override fun onComplete() {
                Log.d(TAG, "No attachment exists for found beacon!")
            }
        })
    }

    companion object {
        private val TAG = MainActivity::class.java!!.getSimpleName()
    }
}
