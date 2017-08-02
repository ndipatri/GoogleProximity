package com.ndipatri.iot.googleproximity.container;

import android.content.Context;

import com.ndipatri.iot.googleproximity.BeaconProximityAPI;
import com.ndipatri.iot.googleproximity.BeaconProximityHelper;
import com.ndipatri.iot.googleproximity.utils.BeaconScanHelper;

import javax.inject.Singleton;

import dagger.Module;
import dagger.Provides;

@Module
public class GPModule {

    private Context context = null;
    private boolean trustAllConnections;

    public GPModule(Context context, boolean trustAllConnections) {
        this.context = context;
        this.trustAllConnections = trustAllConnections;
    }

    @Provides
    @Singleton
    BeaconProximityAPI provideProximityBeaconProvider() {
        return new BeaconProximityAPI(trustAllConnections);
    }

    @Provides
    @Singleton
    BeaconProximityHelper provideProximityBeaconHelper(BeaconProximityAPI beaconProximityAPI) {
        return new BeaconProximityHelper(beaconProximityAPI, context);
    }

    @Provides
    @Singleton
    BeaconScanHelper provideBeaconScanHelper() {
        return new BeaconScanHelper(context);
    }
}
