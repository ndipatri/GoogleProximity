package com.ndipatri.iot.googleproximity.utils;

public class ByteUtils {
    final static char[] HEX_ARRAY = "0123456789ABCDEF".toCharArray();
    public static String convertBytesToHex(byte[] bytes) {
        char[] hex = new char[bytes.length * 2];
        for (int i = 0; i < bytes.length; i++) {
            int v = bytes[i] & 0xFF;
            hex[i * 2] = HEX_ARRAY[v >>> 4];
            hex[i * 2 + 1] = HEX_ARRAY[v & 0x0F];
        }

        return new String(hex).toLowerCase();
    }
}
