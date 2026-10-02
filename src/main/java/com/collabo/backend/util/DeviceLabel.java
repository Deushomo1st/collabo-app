package com.collabo.backend.util;

/** "Chrome on Windows" from a User-Agent, for the list of signed-in devices. A guess for people to recognise, not a fingerprint. */
public final class DeviceLabel {

    private DeviceLabel() {}

    public static String of(String ua) {
        if (ua == null || ua.isBlank()) return "Unknown device";
        String browser = ua.contains("Edg/") ? "Edge" : ua.contains("OPR/") ? "Opera" : ua.contains("Firefox/") ? "Firefox"
                : ua.contains("Chrome/") || ua.contains("CriOS/") ? "Chrome" : ua.contains("Safari/") ? "Safari" : "Browser";
        String os = ua.contains("Android") ? "Android" : ua.contains("iPhone") || ua.contains("iPad") ? "iOS" : ua.contains("Windows") ? "Windows"
                : ua.contains("Mac OS") ? "macOS" : ua.contains("Linux") ? "Linux" : null;
        return os == null ? browser : browser + " on " + os;
    }
}
