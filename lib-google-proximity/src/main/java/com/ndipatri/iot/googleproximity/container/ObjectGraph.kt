package com.ndipatri.iot.googleproximity.container

import android.content.Context

import com.ndipatri.iot.googleproximity.GoogleProximity

import javax.inject.Singleton

import dagger.Component

@Singleton
@Component(modules = [GPModule::class])
interface ObjectGraph {

    fun inject(thingy: GoogleProximity)

    object Initializer {
        fun init(context: Context, trustAllConnections: Boolean): ObjectGraph {
            return DaggerObjectGraph.builder().gPModule(GPModule(context, trustAllConnections)).build()
        }
    }
}
