package com.ndipatri.iot.googleproximity.container;

import android.content.Context;

import com.ndipatri.iot.googleproximity.GoogleProximity;

import javax.inject.Singleton;

import dagger.Component;

@Singleton
@Component(modules = {GPModule.class})
public interface ObjectGraph {

    void inject(GoogleProximity thingy);

    final class Initializer {
        public static ObjectGraph init(final Context context, final boolean trustAllConnections) {
            return DaggerObjectGraph.builder().gPModule(new GPModule(context, trustAllConnections)).build();
        }
    }
}
