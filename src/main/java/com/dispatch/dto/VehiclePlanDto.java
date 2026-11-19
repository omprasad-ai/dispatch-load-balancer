package com.dispatch.dto;

import java.util.List;

/**
 * totalDistance is the human readable value shown in the assignment spec (e.g. "5.25 km");
 * totalDistanceKm carries the same value as a number for programmatic use.
 */
public record VehiclePlanDto(
        String vehicleId,
        String currentAddress,
        double capacity,
        double totalLoad,
        String totalDistance,
        double totalDistanceKm,
        List<OrderDto> assignedOrders) {
}
