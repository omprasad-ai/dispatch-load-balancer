package com.dispatch.allocation;

import com.dispatch.model.OrderEntity;
import com.dispatch.model.VehicleEntity;

import java.util.List;

/** Strategy pattern: different allocation algorithms can be plugged in behind this interface. */
public interface AllocationStrategy {

    DispatchResult allocate(List<OrderEntity> orders, List<VehicleEntity> vehicles);
}
