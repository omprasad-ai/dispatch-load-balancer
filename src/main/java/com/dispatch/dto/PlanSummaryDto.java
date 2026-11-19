package com.dispatch.dto;

public record PlanSummaryDto(
        int totalOrders,
        int assignedOrders,
        int unassignedOrders,
        int totalVehicles,
        double totalLoad,
        double totalDistanceKm) {
}
