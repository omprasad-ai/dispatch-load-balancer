package com.dispatch.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.List;

public record OrdersRequest(
        @NotEmpty(message = "orders must contain at least one order")
        @Size(max = 10000, message = "at most 10000 orders can be submitted per request")
        List<@NotNull(message = "order entries must not be null") @Valid OrderRequest> orders) {
}
