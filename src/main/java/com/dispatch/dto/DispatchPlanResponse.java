package com.dispatch.dto;

import java.util.List;

public record DispatchPlanResponse(
        List<VehiclePlanDto> dispatchPlan,
        List<UnassignedOrderDto> unassignedOrders,
        PlanSummaryDto summary) {
}
