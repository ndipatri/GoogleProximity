package com.ndipatri.iot.googleproximity.models.api

class GetForObservedRequest {

    var observations: List<Observation>? = null
    var namespacedTypes: List<String>? = null

    class Observation(var advertisedId: AdvertisedId)

}
