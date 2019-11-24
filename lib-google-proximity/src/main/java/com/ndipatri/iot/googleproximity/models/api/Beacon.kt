package com.ndipatri.iot.googleproximity.models.api

import android.util.Base64

class Beacon(type: String, rawId: ByteArray, var status: String) {

    var advertisedId: AdvertisedId
    var placeId: String? = null

    /**
     * The beaconName is formatted as "beacons/%d!%s" where %d is an integer representing the
     * beacon ID type. For Eddystone this is 3. The %s is the base16 (hex) ASCII for the ID bytes.
     */
    val beaconName: String
        get() {

            val rawId = Base64.decode(advertisedId.id, Base64.DEFAULT)
            val hexEncodedId = String.format("%02X", rawId)

            return "3!$hexEncodedId"
        }

    constructor(advertiseId: ByteArray) : this("EDDYSTONE", advertiseId, Beacon.STATUS_ACTIVE) {}

    init {
        this.advertisedId = AdvertisedId(type, rawId)
        this.placeId = null
    }

    companion object {

        // These constants are in the Proximity Service Status enum:
        val STATUS_NOT_FOUND = "NOT_FOUND"
        val STATUS_UNREGISTERED = "UNREGISTERED"
        val STATUS_ACTIVE = "ACTIVE"
        val STATUS_DECOMMISSIONED = "DECOMMISSIONED"
        val STATUS_INACTIVE = "INACTIVE"
    }
}
