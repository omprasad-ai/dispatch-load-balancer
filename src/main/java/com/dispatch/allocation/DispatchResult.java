package com.dispatch.allocation;

import java.util.List;

/** Outcome of an allocation run: one route per vehicle plus orders that could not be placed. */
public record DispatchResult(List<VehicleRoute> routes, List<UnassignedOrder> unassigned) {
}
