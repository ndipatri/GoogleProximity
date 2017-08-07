package com.ndipatri.iot.googleproximity.container;

import android.content.Context;

import com.ndipatri.iot.googleproximity.BeaconProximityAPI;
import com.ndipatri.iot.googleproximity.BeaconProximityHelper;
import com.ndipatri.iot.googleproximity.GoogleProximity;
import com.ndipatri.iot.googleproximity.utils.BeaconScanHelper;

import javax.inject.Singleton;

import dagger.Module;
import dagger.Provides;

@Module
public class GPModule {

    private Context context = null;
    private boolean trustAllConnections;

    public GPModule(final Context context, final boolean trustAllConnections) {
        this.context = context;
        this.trustAllConnections = trustAllConnections;
    }

    @Provides
    @Singleton
    BeaconProximityHelper provideProximityBeaconHelper() {
        // NOTE: This helper currently does not need IdlingResource
        // as all of its background processing is done using RxJava
        // and we assume these schedulers can be synchronized with Espresso
        // without IdlingResource
        return new BeaconProximityHelper(context, trustAllConnections);
    }

    @Provides
    @Singleton
    BeaconScanHelper provideBeaconScanHelper() {
        return new BeaconScanHelper(context);
    }
}
