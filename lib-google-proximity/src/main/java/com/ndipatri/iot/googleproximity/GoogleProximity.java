package com.ndipatri.iot.googleproximity;


import android.content.Context;
import android.preference.PreferenceManager;

import com.f2prateek.rx.preferences2.Preference;
import com.f2prateek.rx.preferences2.RxSharedPreferences;
import com.google.common.base.Strings;
import com.ndipatri.iot.googleproximity.container.ObjectGraph;
import com.ndipatri.iot.googleproximity.utils.BeaconScanHelper;

import org.altbeacon.beacon.Beacon;

import javax.inject.Inject;

import io.reactivex.Completable;
import io.reactivex.Maybe;
import io.reactivex.Observable;
import io.reactivex.Single;

public class GoogleProximity {

    private static final String TAG = GoogleProximity.class.getSimpleName();

    private ObjectGraph graph;

    private RxSharedPreferences sharedPreferences;

    @Inject
    BeaconProximityHelper beaconProximityHelper;

    @Inject
    BeaconScanHelper beaconScanHelper;

    private static GoogleProximity instance = null;

    public static void initialize(final Context context, final boolean trustAllConnections) {
        instance = new GoogleProximity(context, trustAllConnections);
    }

    public GoogleProximity(final Context context, final boolean trustAllConnections) {
        this.sharedPreferences = RxSharedPreferences.create(PreferenceManager.getDefaultSharedPreferences(context));

        graph = ObjectGraph.Initializer.init(context, trustAllConnections);
        graph.inject(this);
    }

    public ObjectGraph getGraph() {
        return graph;
    }

    public static GoogleProximity getInstance() {
        return instance;
    }

    public void setGoogleAccountForOAuth(final String googleAccount) {
        getGoogleAccountForOAuth().set(googleAccount);
    }

    public void clearGoogleAccountForOAuth() {
        setGoogleAccountForOAuth(null);
    }

    public Preference<String> getGoogleAccountForOAuth() {
        return sharedPreferences.getString("GOOGLE_ACCOUNT_FOR_OAUTH", null);
    }

    public boolean hasGoogleAccountForOAuth() {
        return !Strings.isNullOrEmpty(getGoogleAccountForOAuth().get());
    }

    public Maybe<String[]> retrieveAttachment(final Beacon beacon) {
        return retrieveAttachment(beaconScanHelper.getAdvertiseId(beacon));
    }

    public Maybe<String[]> retrieveAttachment(final byte[] advertiseId) {
        return beaconProximityHelper.retrieveAttachment(advertiseId);
    }

    public Completable updateBeacon(final Beacon beacon,
                                    final String[] attachment) {
        return updateBeacon(beaconScanHelper.getAdvertiseId(beacon), attachment);
    }

    public Completable updateBeacon(final byte[] advertiseId,
                                    final String[] attachment) {
        return beaconProximityHelper
                .createAttachment(advertiseId, attachment);
    }

    public void redirectToAuthenticationActivityIfNecessary(Context activity) {
        beaconProximityHelper.redirectToAuthenticationActivityIfNecessary(activity);
    }

    public Single<String> getOAuthToken(final String selectedGoogleAccount) {
        return beaconProximityHelper.getOAuthToken(selectedGoogleAccount);
    }

    public Observable<Beacon> scanForNearbyBeacon(String beaconNamespaceId) {
        return beaconScanHelper.scanForNearbyBeacon(beaconNamespaceId);
    }

    public Observable<Beacon> scanForNearbyBeacon(String beaconNamespaceId, int timeoutSeconds) {
        return beaconScanHelper.scanForNearbyBeacon(beaconNamespaceId, timeoutSeconds);
    }

    public void stopBeaconScanning() {
        beaconScanHelper.stopBeaconScanning();
    }
}
