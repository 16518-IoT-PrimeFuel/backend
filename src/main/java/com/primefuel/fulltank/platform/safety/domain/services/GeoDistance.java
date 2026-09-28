package com.primefuel.fulltank.platform.safety.domain.services;

/**
 * Great-circle distance between two coordinates (S17). The geofence is a circle (U09), so the only geometric
 * primitive needed is the distance between the evaluated position and the policy centre.
 */
public final class GeoDistance {

    private static final double EARTH_RADIUS_METERS = 6_371_000.0;

    private GeoDistance() {
    }

    /** Haversine distance in metres between {@code (lat1, lon1)} and {@code (lat2, lon2)}. */
    public static double metersBetween(double lat1, double lon1, double lat2, double lon2) {
        double dLat = Math.toRadians(lat2 - lat1);
        double dLon = Math.toRadians(lon2 - lon1);
        double a = Math.sin(dLat / 2) * Math.sin(dLat / 2)
                + Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2))
                * Math.sin(dLon / 2) * Math.sin(dLon / 2);
        double c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
        return EARTH_RADIUS_METERS * c;
    }
}
