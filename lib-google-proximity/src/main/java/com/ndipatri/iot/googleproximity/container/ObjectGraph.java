package com.ndipatri.iot.googleproximity.container;

import android.content.Context;

import com.ndipatri.iot.googleproximity.GoogleProximity;
import com.ndipatri.iot.googleproximity.activities.AuthenticationActivity;

import javax.inject.Singleton;

import dagger.Component;

@Singleton
@Component(modules = {GPModule.class})
public interface ObjectGraph {

    void inject(AuthenticationActivity thingy);
    void inject(GoogleProximity thingy);

    final class Initializer {
        public static ObjectGraph init(Context context, boolean trustAllConnections) {
            return DaggerObjectGraph.builder().gPModule(new GPModule(context, trustAllConnections)).build();
        }
    }
}
