package com.dispatch.allocation;

import com.dispatch.model.OrderEntity;

public record UnassignedOrder(OrderEntity order, String reason) {
}
