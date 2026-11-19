package com.dispatch.service;

import com.dispatch.allocation.DispatchResult;
import com.dispatch.allocation.UnassignedOrder;
import com.dispatch.allocation.VehicleRoute;
import com.dispatch.dto.DispatchPlanResponse;
import com.dispatch.dto.OrderDto;
import com.dispatch.dto.PlanSummaryDto;
import com.dispatch.dto.UnassignedOrderDto;
import com.dispatch.dto.VehiclePlanDto;
import com.dispatch.model.OrderEntity;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Locale;

/** Translates the internal allocation result into the API response. */
@Component
public class PlanMapper {

    public DispatchPlanResponse toResponse(DispatchResult result, int totalOrders) {
        List<VehiclePlanDto> plans = result.routes().stream().map(this::toVehiclePlan).toList();
        List<UnassignedOrderDto> unassigned = result.unassigned().stream().map(this::toUnassigned).toList();

        double totalLoad = result.routes().stream().mapToDouble(VehicleRoute::getLoad).sum();
        double totalDistance = result.routes().stream().mapToDouble(VehicleRoute::getDistanceKm).sum();
        int assigned = totalOrders - unassigned.size();

        PlanSummaryDto summary = new PlanSummaryDto(totalOrders, assigned, unassigned.size(),
                result.routes().size(), round(totalLoad), round(totalDistance));
        return new DispatchPlanResponse(plans, unassigned, summary);
    }

    private VehiclePlanDto toVehiclePlan(VehicleRoute route) {
        double km = round(route.getDistanceKm());
        return new VehiclePlanDto(
                route.getVehicle().getVehicleId(),
                route.getVehicle().getCurrentAddress(),
                route.getVehicle().getCapacity(),
                round(route.getLoad()),
                String.format(Locale.ROOT, "%.2f km", km),
                km,
                route.getStops().stream().map(this::toOrderDto).toList());
    }

    private UnassignedOrderDto toUnassigned(UnassignedOrder unassigned) {
        return new UnassignedOrderDto(toOrderDto(unassigned.order()), unassigned.reason());
    }

    private OrderDto toOrderDto(OrderEntity o) {
        return new OrderDto(o.getOrderId(), o.getLatitude(), o.getLongitude(),
                o.getAddress(), o.getPackageWeight(), o.getPriority());
    }

    private static double round(double value) {
        return Math.round(value * 100.0) / 100.0;
    }
}
