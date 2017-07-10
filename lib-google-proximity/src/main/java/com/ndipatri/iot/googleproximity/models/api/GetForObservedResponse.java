package com.ndipatri.iot.googleproximity.models.api;

import java.util.List;

public class GetForObservedResponse {

    public List<BeaconInfo> beacons;

    public static class BeaconInfo {
        public List<AttachmentInfo> attachments;
    }
}
