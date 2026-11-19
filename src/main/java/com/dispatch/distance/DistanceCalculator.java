package com.dispatch.distance;

/** Abstraction over the distance metric so it can be swapped (e.g. road distance) without touching allocation. */
public interface DistanceCalculator {

    /** Distance in kilometres between two coordinates given in decimal degrees. */
    double distanceKm(double lat1, double lon1, double lat2, double lon2);
}
