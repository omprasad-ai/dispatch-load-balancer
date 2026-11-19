package com.dispatch.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.List;

public record VehiclesRequest(
        @NotEmpty(message = "vehicles must contain at least one vehicle")
        @Size(max = 10000, message = "at most 10000 vehicles can be submitted per request")
        List<@NotNull(message = "vehicle entries must not be null") @Valid VehicleRequest> vehicles) {
}
