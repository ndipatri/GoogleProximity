package com.ndipatri.iot.googleproximity.models.api;

import android.util.Base64;

import com.ndipatri.iot.googleproximity.utils.ByteUtils;

public class Beacon {

    // These constants are in the Proximity Service Status enum:
    public static final String STATUS_NOT_FOUND = "NOT_FOUND";
    public static final String STATUS_UNREGISTERED = "UNREGISTERED";
    public static final String STATUS_ACTIVE = "ACTIVE";
    public static final String STATUS_DECOMMISSIONED = "DECOMMISSIONED";
    public static final String STATUS_INACTIVE = "INACTIVE";

    public AdvertisedId advertisedId;

    public String status;
    public String placeId;

    public Beacon(byte[] advertiseId) {
        this("EDDYSTONE", advertiseId, Beacon.STATUS_ACTIVE);
    }

    public Beacon(String type, byte[] rawId, String status) {
        this.advertisedId = new AdvertisedId(type, rawId);
        this.status = status;
        this.placeId = null;
    }

    /**
     * The beaconName is formatted as "beacons/%d!%s" where %d is an integer representing the
     * beacon ID type. For Eddystone this is 3. The %s is the base16 (hex) ASCII for the ID bytes.
     */
    public String getBeaconName() {

        byte[] rawId = Base64.decode(advertisedId.id, Base64.DEFAULT);
        String hexEncodedId = ByteUtils.convertBytesToHex(rawId);

        return "3!" + hexEncodedId;
    }
}
