package com.dispatch.allocation;

import com.dispatch.model.OrderEntity;
import com.dispatch.model.VehicleEntity;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Mutable working state of one vehicle while a plan is being built: the ordered stops,
 * the load carried and the distance travelled from the vehicle's current position
 * through each stop in turn (no return trip).
 */
public class VehicleRoute {

    private static final double EPSILON = 1e-9;

    private final VehicleEntity vehicle;
    private final List<OrderEntity> stops = new ArrayList<>();
    private double load;
    private double distanceKm;
    private double lastLatitude;
    private double lastLongitude;

    public VehicleRoute(VehicleEntity vehicle) {
        this.vehicle = vehicle;
        this.lastLatitude = vehicle.getCurrentLatitude();
        this.lastLongitude = vehicle.getCurrentLongitude();
    }

    public boolean canFit(double weight) {
        return load + weight <= vehicle.getCapacity() + EPSILON;
    }

    public double remainingCapacity() {
        return vehicle.getCapacity() - load;
    }

    /** Appends a stop; legKm is the distance from the previous position to this stop. */
    public void addStop(OrderEntity order, double legKm) {
        stops.add(order);
        load += order.getPackageWeight();
        distanceKm += legKm;
        lastLatitude = order.getLatitude();
        lastLongitude = order.getLongitude();
    }

    /** Replaces the stop sequence (same orders, different order) after route optimization. */
    public void replaceStops(List<OrderEntity> reordered, double newDistanceKm) {
        stops.clear();
        stops.addAll(reordered);
        distanceKm = newDistanceKm;
        if (!reordered.isEmpty()) {
            OrderEntity last = reordered.get(reordered.size() - 1);
            lastLatitude = last.getLatitude();
            lastLongitude = last.getLongitude();
        }
    }

    public VehicleEntity getVehicle() {
        return vehicle;
    }

    public List<OrderEntity> getStops() {
        return Collections.unmodifiableList(stops);
    }

    public double getLoad() {
        return load;
    }

    public double getDistanceKm() {
        return distanceKm;
    }

    public double getLastLatitude() {
        return lastLatitude;
    }

    public double getLastLongitude() {
        return lastLongitude;
    }

    public double getStartLatitude() {
        return vehicle.getCurrentLatitude();
    }

    public double getStartLongitude() {
        return vehicle.getCurrentLongitude();
    }
}
