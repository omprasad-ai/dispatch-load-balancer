package com.dispatch.dto;

import com.dispatch.model.Priority;

public record OrderDto(
        String orderId,
        double latitude,
        double longitude,
        String address,
        double packageWeight,
        Priority priority) {
}
