package com.ndipatri.iot.googleproximity.models.api

class GetForObservedResponse {

    var beacons: List<BeaconInfo>? = null

    class BeaconInfo {
        var attachments: List<AttachmentInfo>? = null
    }
}
