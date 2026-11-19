package com.dispatch.dto;

public record UnassignedOrderDto(OrderDto order, String reason) {
}
