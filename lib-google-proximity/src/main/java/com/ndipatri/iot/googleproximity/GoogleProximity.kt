package com.ndipatri.iot.googleproximity


import android.content.Context
import android.preference.PreferenceManager

import com.f2prateek.rx.preferences2.Preference
import com.f2prateek.rx.preferences2.RxSharedPreferences
import com.google.common.base.Strings
import com.ndipatri.iot.googleproximity.container.ObjectGraph
import com.ndipatri.iot.googleproximity.utils.BeaconScanHelper

import org.altbeacon.beacon.Beacon

import javax.inject.Inject

import io.reactivex.Completable
import io.reactivex.Maybe
import io.reactivex.Observable
import io.reactivex.Single

class GoogleProximity(context: Context, trustAllConnections: Boolean) {

    val graph: ObjectGraph

    private val sharedPreferences: RxSharedPreferences

    @Inject
    lateinit var beaconProximityHelper: BeaconProximityHelper

    @Inject
    lateinit var beaconScanHelper: BeaconScanHelper

    val googleAccountForOAuth: Preference<String>
        get() = sharedPreferences.getString("GOOGLE_ACCOUNT_FOR_OAUTH", null)

    init {
        this.sharedPreferences = RxSharedPreferences.create(PreferenceManager.getDefaultSharedPreferences(context))

        graph = ObjectGraph.Initializer.init(context, trustAllConnections)
        graph.inject(this)
    }

    fun setGoogleAccountForOAuth(googleAccount: String?) {
        googleAccountForOAuth.set(googleAccount)
    }

    fun clearGoogleAccountForOAuth() {
        setGoogleAccountForOAuth(null)
    }

    fun hasGoogleAccountForOAuth(): Boolean {
        return !Strings.isNullOrEmpty(googleAccountForOAuth.get())
    }

    fun retrieveAttachment(beacon: Beacon): Maybe<Array<String>> {
        return retrieveAttachment(beaconScanHelper!!.getAdvertiseId(beacon))
    }

    fun retrieveAttachment(advertiseId: ByteArray): Maybe<Array<String>> {
        return beaconProximityHelper!!.retrieveAttachment(advertiseId)
    }

    fun updateBeacon(beacon: Beacon,
                     attachment: Array<String>): Completable {
        return updateBeacon(beaconScanHelper!!.getAdvertiseId(beacon), attachment)
    }

    fun updateBeacon(advertiseId: ByteArray,
                     attachment: Array<String>): Completable {
        return beaconProximityHelper!!
                .createAttachment(advertiseId, attachment)
    }

    fun redirectToAuthenticationActivityIfNecessary(activity: Context) {
        beaconProximityHelper!!.redirectToAuthenticationActivityIfNecessary(activity)
    }

    fun getOAuthToken(selectedGoogleAccount: String): Single<String> {
        return beaconProximityHelper!!.getOAuthToken(selectedGoogleAccount)
    }

    fun scanForNearbyBeacon(beaconNamespaceId: String): Observable<BeaconScanHelper.BeaconUpdate> {
        return beaconScanHelper!!.scanForNearbyBeacon(beaconNamespaceId)
    }

    fun scanForNearbyBeacon(beaconNamespaceId: String, timeoutSeconds: Int): Observable<BeaconScanHelper.BeaconUpdate> {
        return beaconScanHelper!!.scanForNearbyBeacon(beaconNamespaceId, timeoutSeconds)
    }

    fun stopBeaconScanning() {
        beaconScanHelper!!.stopBeaconScanning()
    }

    companion object {

        private val TAG = GoogleProximity::class.java!!.getSimpleName()

        var instance: GoogleProximity? = null
            private set

        fun initialize(context: Context, trustAllConnections: Boolean) {
            instance = GoogleProximity(context, trustAllConnections)
        }
    }
}
