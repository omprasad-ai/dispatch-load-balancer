package com.dispatch.allocation;

import com.dispatch.distance.DistanceCalculator;
import com.dispatch.model.OrderEntity;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Improves the visiting order of a single vehicle's stops with 2-opt (segment reversal).
 * The vehicle's start position is fixed and the path is open (no return trip).
 * Skipped for very long routes to keep the cost bounded (2-opt is O(n^3) per pass here).
 */
@Component
public class RouteOptimizer {

    static final int MAX_STOPS = 150;
    private static final double MIN_GAIN_KM = 1e-9;

    private final DistanceCalculator calculator;

    public RouteOptimizer(DistanceCalculator calculator) {
        this.calculator = calculator;
    }

    public void optimize(VehicleRoute route) {
        List<OrderEntity> stops = new ArrayList<>(route.getStops());
        int n = stops.size();
        if (n < 2 || n > MAX_STOPS) {
            return;
        }
        double best = pathLength(route.getStartLatitude(), route.getStartLongitude(), stops);
        boolean improved = true;
        while (improved) {
            improved = false;
            for (int i = 0; i < n - 1; i++) {
                for (int j = i + 1; j < n; j++) {
                    Collections.reverse(stops.subList(i, j + 1));
                    double candidate = pathLength(route.getStartLatitude(), route.getStartLongitude(), stops);
                    if (candidate < best - MIN_GAIN_KM) {
                        best = candidate;
                        improved = true;
                    } else {
                        Collections.reverse(stops.subList(i, j + 1));
                    }
                }
            }
        }
        route.replaceStops(stops, best);
    }

    double pathLength(double startLat, double startLon, List<OrderEntity> stops) {
        double total = 0;
        double lat = startLat;
        double lon = startLon;
        for (OrderEntity stop : stops) {
            total += calculator.distanceKm(lat, lon, stop.getLatitude(), stop.getLongitude());
            lat = stop.getLatitude();
            lon = stop.getLongitude();
        }
        return total;
    }
}
