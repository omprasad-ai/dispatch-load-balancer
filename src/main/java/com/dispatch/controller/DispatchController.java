package com.dispatch.controller;

import com.dispatch.dto.ApiResponse;
import com.dispatch.dto.DispatchPlanResponse;
import com.dispatch.dto.ErrorResponse;
import com.dispatch.dto.OrdersRequest;
import com.dispatch.dto.VehiclesRequest;
import com.dispatch.service.DispatchService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping(value = "/api/dispatch", produces = MediaType.APPLICATION_JSON_VALUE)
@Tag(name = "Dispatch", description = "Submit orders and fleet, then fetch the optimized dispatch plan")
public class DispatchController {

    private final DispatchService dispatchService;

    public DispatchController(DispatchService dispatchService) {
        this.dispatchService = dispatchService;
    }

    @Operation(summary = "Submit delivery orders",
            description = "Upsert: an existing orderId is updated, a new orderId is inserted.")
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Orders accepted"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "Validation failed",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    @PostMapping(value = "/orders", consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<ApiResponse> submitOrders(@Valid @RequestBody OrdersRequest request) {
        dispatchService.saveOrders(request.orders());
        return ResponseEntity.ok(ApiResponse.success("Delivery orders accepted."));
    }

    @Operation(summary = "Submit fleet details",
            description = "Upsert: an existing vehicleId is updated, a new vehicleId is inserted.")
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Vehicles accepted"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "Validation failed",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    @PostMapping(value = "/vehicles", consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<ApiResponse> submitVehicles(@Valid @RequestBody VehiclesRequest request) {
        dispatchService.saveVehicles(request.vehicles());
        return ResponseEntity.ok(ApiResponse.success("Vehicle details accepted."));
    }

    @Operation(summary = "Get the dispatch plan",
            description = "Allocates all stored orders to all stored vehicles. Orders are handled HIGH, then MEDIUM, "
                    + "then LOW; vehicle capacity is never exceeded; distances are Haversine kilometres from the "
                    + "vehicle's current position through each stop (no return trip). Orders that cannot be "
                    + "placed are listed in unassignedOrders with a reason.")
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Dispatch plan"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "409",
                    description = "Orders or vehicles have not been submitted yet",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    @GetMapping("/plan")
    public ResponseEntity<DispatchPlanResponse> getPlan() {
        return ResponseEntity.ok(dispatchService.buildPlan());
    }
}
