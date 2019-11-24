package com.ndipatri.iot.googleproximity.container

import android.content.Context

import com.ndipatri.iot.googleproximity.BeaconProximityAPI
import com.ndipatri.iot.googleproximity.BeaconProximityHelper
import com.ndipatri.iot.googleproximity.GoogleProximity
import com.ndipatri.iot.googleproximity.utils.BeaconScanHelper

import javax.inject.Singleton

import dagger.Module
import dagger.Provides

@Module
class GPModule(val context: Context, private val trustAllConnections: Boolean) {

    @Provides
    @Singleton
    internal fun provideProximityBeaconHelper(): BeaconProximityHelper {
        // NOTE: This helper currently does not need IdlingResource
        // as all of its background processing is done using RxJava
        // and we assume these schedulers can be synchronized with Espresso
        // without IdlingResource
        return BeaconProximityHelper(context, trustAllConnections)
    }

    @Provides
    @Singleton
    internal fun provideBeaconScanHelper(): BeaconScanHelper {
        return BeaconScanHelper(context)
    }
}
