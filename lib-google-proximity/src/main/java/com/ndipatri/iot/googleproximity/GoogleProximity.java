package com.ndipatri.iot.googleproximity;


import android.content.Context;
import android.preference.PreferenceManager;

import com.f2prateek.rx.preferences2.Preference;
import com.f2prateek.rx.preferences2.RxSharedPreferences;
import com.google.common.base.Strings;
import com.ndipatri.iot.googleproximity.container.ObjectGraph;

import javax.inject.Inject;

import io.reactivex.Completable;
import io.reactivex.Maybe;

public class GoogleProximity {

    private ObjectGraph graph;

    private RxSharedPreferences sharedPreferences;

    @Inject
    BeaconProximityHelper beaconProximityHelper;

    private static GoogleProximity instance = null;

    public static void initialize(Context context, boolean trustAllConnections) {
        instance = new GoogleProximity(context, trustAllConnections);
    }

    public GoogleProximity(Context context, boolean trustAllConnections) {
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

    public Maybe<String[]> retrieveAttachment(final byte[] advertiseId) {
        return beaconProximityHelper.retrieveAttachment(advertiseId);
    }

    public Completable updateBeacon(final byte[] advertiseId,
                                    final String[] attachment) {
        return beaconProximityHelper
                .createAttachment(advertiseId, attachment);
    }

    public void redirectToAuthenticationActivityIfNecessary(Context activity) {
        beaconProximityHelper.redirectToAuthenticationActivityIfNecessary(activity);
    }
}
