package com.dispatch.controller;

import com.dispatch.dto.DispatchPlanResponse;
import com.dispatch.dto.PlanSummaryDto;
import com.dispatch.exception.NoDataException;
import com.dispatch.service.DispatchService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.hamcrest.Matchers.allOf;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasItem;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(DispatchController.class)
class DispatchControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private DispatchService dispatchService;

    private static String orderJson(String fields) {
        return "{\"orders\":[{" + fields + "}]}";
    }

    private static final String VALID_ORDER_FIELDS = """
            "orderId":"ORD001","latitude":12.9716,"longitude":77.5946,
            "address":"MG Road, Bangalore, Karnataka, India","packageWeight":10,"priority":"HIGH"
            """;

    private static final String VALID_VEHICLE = """
            {"vehicles":[{"vehicleId":"VEH001","capacity":100,"currentLatitude":12.9716,
            "currentLongitude":77.6413,"currentAddress":"Indiranagar, Bangalore, Karnataka, India"}]}
            """;

    @Test
    void validOrdersAreAccepted() throws Exception {
        mockMvc.perform(post("/api/dispatch/orders").contentType(MediaType.APPLICATION_JSON)
                        .content(orderJson(VALID_ORDER_FIELDS)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("Delivery orders accepted."))
                .andExpect(jsonPath("$.status").value("success"));
        verify(dispatchService).saveOrders(any());
    }

    @Test
    void validVehiclesAreAccepted() throws Exception {
        mockMvc.perform(post("/api/dispatch/vehicles").contentType(MediaType.APPLICATION_JSON)
                        .content(VALID_VEHICLE))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("Vehicle details accepted."))
                .andExpect(jsonPath("$.status").value("success"));
        verify(dispatchService).saveVehicles(any());
    }

    @Test
    void latitudeOutOfRangeIsRejected() throws Exception {
        String body = orderJson(VALID_ORDER_FIELDS.replace("12.9716", "95.0"));
        mockMvc.perform(post("/api/dispatch/orders").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value("error"))
                .andExpect(jsonPath("$.errors", hasItem(containsString("latitude must be between -90 and 90"))));
        verifyNoInteractions(dispatchService);
    }

    @Test
    void longitudeOutOfRangeIsRejected() throws Exception {
        String body = orderJson(VALID_ORDER_FIELDS.replace("77.5946", "181"));
        mockMvc.perform(post("/api/dispatch/orders").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors", hasItem(containsString("longitude must be between -180 and 180"))));
    }

    @Test
    void nonPositiveWeightIsRejected() throws Exception {
        String body = orderJson(VALID_ORDER_FIELDS.replace("\"packageWeight\":10", "\"packageWeight\":-5"));
        mockMvc.perform(post("/api/dispatch/orders").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors", hasItem(containsString("packageWeight must be greater than 0"))));
    }

    @Test
    void blankOrderIdAndAddressAreRejected() throws Exception {
        String body = orderJson(VALID_ORDER_FIELDS
                .replace("\"orderId\":\"ORD001\"", "\"orderId\":\" \"")
                .replace("MG Road, Bangalore, Karnataka, India", ""));
        mockMvc.perform(post("/api/dispatch/orders").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors", hasItem(containsString("orderId is required"))))
                .andExpect(jsonPath("$.errors", hasItem(containsString("address is required"))));
    }

    @Test
    void missingPriorityIsRejected() throws Exception {
        String body = orderJson(VALID_ORDER_FIELDS.replace(",\"priority\":\"HIGH\"", ""));
        mockMvc.perform(post("/api/dispatch/orders").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors", hasItem(containsString("priority is required"))));
    }

    @Test
    void unknownPriorityValueIsRejectedWithAllowedValues() throws Exception {
        String body = orderJson(VALID_ORDER_FIELDS.replace("\"HIGH\"", "\"URGENT\""));
        mockMvc.perform(post("/api/dispatch/orders").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors", hasItem(allOf(
                        containsString("URGENT"), containsString("HIGH"), containsString("LOW")))));
    }

    @Test
    void wrongTypeForNumericFieldIsRejected() throws Exception {
        String body = orderJson(VALID_ORDER_FIELDS.replace("\"packageWeight\":10", "\"packageWeight\":\"heavy\""));
        mockMvc.perform(post("/api/dispatch/orders").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value("error"));
    }

    @Test
    void emptyOrdersListIsRejected() throws Exception {
        mockMvc.perform(post("/api/dispatch/orders").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"orders\":[]}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors", hasItem(containsString("orders must contain at least one order"))));
    }

    @Test
    void missingOrdersPropertyIsRejected() throws Exception {
        mockMvc.perform(post("/api/dispatch/orders").contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void emptyVehiclesListIsRejected() throws Exception {
        mockMvc.perform(post("/api/dispatch/vehicles").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"vehicles\":[]}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors", hasItem(containsString("vehicles must contain at least one vehicle"))));
    }

    @Test
    void zeroCapacityVehicleIsRejected() throws Exception {
        mockMvc.perform(post("/api/dispatch/vehicles").contentType(MediaType.APPLICATION_JSON)
                        .content(VALID_VEHICLE.replace("\"capacity\":100", "\"capacity\":0")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors", hasItem(containsString("capacity must be greater than 0"))));
    }

    @Test
    void malformedJsonIsRejected() throws Exception {
        mockMvc.perform(post("/api/dispatch/orders").contentType(MediaType.APPLICATION_JSON).content("{ not json"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Malformed or missing JSON request body"));
    }

    @Test
    void missingBodyIsRejected() throws Exception {
        mockMvc.perform(post("/api/dispatch/orders").contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isBadRequest());
    }

    @Test
    void nonJsonContentTypeIsRejected() throws Exception {
        mockMvc.perform(post("/api/dispatch/orders").contentType(MediaType.TEXT_PLAIN).content("hello"))
                .andExpect(status().isUnsupportedMediaType());
    }

    @Test
    void unsupportedHttpMethodReturns405NotAServerError() throws Exception {
        mockMvc.perform(get("/api/dispatch/orders")).andExpect(status().isMethodNotAllowed());
    }

    @Test
    void planIsReturnedWhenDataExists() throws Exception {
        when(dispatchService.buildPlan()).thenReturn(new DispatchPlanResponse(
                List.of(), List.of(), new PlanSummaryDto(0, 0, 0, 0, 0, 0)));

        mockMvc.perform(get("/api/dispatch/plan"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.dispatchPlan").isArray())
                .andExpect(jsonPath("$.unassignedOrders").isArray())
                .andExpect(jsonPath("$.summary.totalOrders").value(0));
    }

    @Test
    void planWithoutDataReturns409() throws Exception {
        when(dispatchService.buildPlan()).thenThrow(new NoDataException("No delivery orders found."));

        mockMvc.perform(get("/api/dispatch/plan"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.status").value("error"))
                .andExpect(jsonPath("$.message").value("No delivery orders found."));
    }

    @Test
    void unexpectedFailuresReturn500WithoutLeakingDetails() throws Exception {
        doThrow(new IllegalStateException("secret internal detail")).when(dispatchService).saveOrders(any());

        mockMvc.perform(post("/api/dispatch/orders").contentType(MediaType.APPLICATION_JSON)
                        .content(orderJson(VALID_ORDER_FIELDS)))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.message").value("Unexpected server error"));
    }
}
