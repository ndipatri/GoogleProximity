package com.ndipatri.iot.googleproximity.models.api

import android.util.Base64
import kotlin.experimental.and

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
            val hexEncodedId = convertBytesToHex(rawId)

            return "3!$hexEncodedId"
        }

    constructor(advertiseId: ByteArray) : this("EDDYSTONE", advertiseId, Beacon.STATUS_ACTIVE) {}

    init {
        this.advertisedId = AdvertisedId(type, rawId)
        this.placeId = null
    }

    private val HEX_ARRAY = "0123456789ABCDEF".toCharArray()
    @ExperimentalUnsignedTypes
    fun convertBytesToHex(bytes: ByteArray): String {
        val hex = CharArray(bytes.size * 2)
        for (i in bytes.indices) {
            val v = bytes[i].toUInt() and 0xFF.toUInt()
            hex[i * 2] = HEX_ARRAY[v.toInt() ushr 4]
            hex[i * 2 + 1] = HEX_ARRAY[(v and 0x0F.toUInt()).toInt()]
        }

        return String(hex).toLowerCase()
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
