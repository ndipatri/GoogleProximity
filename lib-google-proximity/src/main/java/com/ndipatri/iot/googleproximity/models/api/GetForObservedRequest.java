package com.ndipatri.iot.googleproximity.models.api;

import java.util.List;

public class GetForObservedRequest {

    public List<Observation> observations;
    public List<String> namespacedTypes;

    public static class Observation {

        public Observation(AdvertisedId advertisedId)  {
            this.advertisedId = advertisedId;
        }

        public AdvertisedId advertisedId;
    }

}
