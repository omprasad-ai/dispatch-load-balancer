package com.dispatch.distance;

import org.springframework.stereotype.Component;

/** Great-circle distance using the Haversine formula. */
@Component
public class HaversineDistanceCalculator implements DistanceCalculator {

    private static final double EARTH_RADIUS_KM = 6371.0088;

    @Override
    public double distanceKm(double lat1, double lon1, double lat2, double lon2) {
        double dLat = Math.toRadians(lat2 - lat1);
        double dLon = Math.toRadians(lon2 - lon1);
        double sinLat = Math.sin(dLat / 2);
        double sinLon = Math.sin(dLon / 2);
        double a = sinLat * sinLat
                + Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2)) * sinLon * sinLon;
        // clamp: floating point error can push 'a' marginally above 1 for antipodal points
        return 2 * EARTH_RADIUS_KM * Math.asin(Math.sqrt(Math.min(1.0, a)));
    }
}
