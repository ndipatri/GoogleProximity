package com.ndipatri.iot.googleproximity.models.api;


import android.util.Base64;

public class AdvertisedId {

    // This constructor is for creating this object locally instead of being
    // rendered from incoming JSON.
    public AdvertisedId(final String type, final byte[] rawId) {
        this.type = type;
        this.id = Base64.encodeToString(rawId, Base64.NO_WRAP);
    }

    // Should always be EDDYSTONE
    public String type;

    // The actual beacon identifier, as broadcast by the beacon hardware.
    // Must be base64 encoded in HTTP requests, and will be so encoded (with padding)
    // in responses. The base64 encoding should be of the binary byte-stream and not
    // any textual (such as hex) representation thereof.
    public String id;


}
