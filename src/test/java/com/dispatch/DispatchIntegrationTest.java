package com.dispatch;

import com.dispatch.repository.OrderRepository;
import com.dispatch.repository.VehicleRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.io.ClassPathResource;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.util.StreamUtils;

import java.nio.charset.StandardCharsets;
import java.util.HashSet;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** End-to-end tests: real controller, service, JPA repositories and in-memory H2. */
@SpringBootTest
@AutoConfigureMockMvc
class DispatchIntegrationTest {

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private ObjectMapper objectMapper;
    @Autowired
    private OrderRepository orderRepository;
    @Autowired
    private VehicleRepository vehicleRepository;

    @BeforeEach
    void cleanDatabase() {
        orderRepository.deleteAll();
        vehicleRepository.deleteAll();
    }

    private static String resource(String name) throws Exception {
        return StreamUtils.copyToString(new ClassPathResource(name).getInputStream(), StandardCharsets.UTF_8);
    }

    private void postSample() throws Exception {
        mockMvc.perform(post("/api/dispatch/orders").contentType(MediaType.APPLICATION_JSON)
                .content(resource("sample-orders.json"))).andExpect(status().isOk());
        mockMvc.perform(post("/api/dispatch/vehicles").contentType(MediaType.APPLICATION_JSON)
                .content(resource("sample-vehicles.json"))).andExpect(status().isOk());
    }

    private JsonNode getPlan() throws Exception {
        String body = mockMvc.perform(get("/api/dispatch/plan"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(body);
    }

    @Test
    void fullSampleProducesAPlanThatRespectsEveryConstraint() throws Exception {
        postSample();
        assertThat(orderRepository.count()).isEqualTo(30);
        assertThat(vehicleRepository.count()).isEqualTo(5);

        JsonNode plan = getPlan();

        assertThat(plan.get("dispatchPlan")).hasSize(5);
        Set<String> seen = new HashSet<>();
        double totalLoad = 0;
        int assigned = 0;
        for (JsonNode vehicle : plan.get("dispatchPlan")) {
            double load = vehicle.get("totalLoad").asDouble();
            assertThat(load).isLessThanOrEqualTo(vehicle.get("capacity").asDouble());
            assertThat(vehicle.get("totalDistance").asText()).endsWith(" km");
            double weightSum = 0;
            for (JsonNode order : vehicle.get("assignedOrders")) {
                assertThat(seen.add(order.get("orderId").asText())).as("order assigned twice").isTrue();
                assertThat(order.get("address").asText()).isNotBlank();
                weightSum += order.get("packageWeight").asDouble();
                assigned++;
            }
            assertThat(weightSum).isEqualTo(load);
            totalLoad += load;
        }
        // total demand is 645 but total fleet capacity is only 500, so some orders must be unassigned
        assertThat(totalLoad).isLessThanOrEqualTo(500);
        JsonNode unassigned = plan.get("unassignedOrders");
        assertThat(unassigned.size()).isPositive();
        assertThat(assigned + unassigned.size()).isEqualTo(30);
        for (JsonNode u : unassigned) {
            assertThat(seen.add(u.get("order").get("orderId").asText())).isTrue();
            assertThat(u.get("reason").asText()).isNotBlank();
        }
        assertThat(plan.get("summary").get("totalOrders").asInt()).isEqualTo(30);
        assertThat(plan.get("summary").get("assignedOrders").asInt()).isEqualTo(assigned);
    }

    @Test
    void highAndMediumPriorityOrdersAreServedBeforeLowPriorityOnTheSample() throws Exception {
        postSample();
        JsonNode plan = getPlan();

        for (JsonNode u : plan.get("unassignedOrders")) {
            assertThat(u.get("order").get("priority").asText())
                    .as("unassigned order %s", u.get("order").get("orderId").asText())
                    .isEqualTo("LOW");
        }
    }

    @Test
    void sampleResultMatchesTheVerifiedBaseline() throws Exception {
        postSample();
        JsonNode summary = getPlan().get("summary");

        assertThat(summary.get("assignedOrders").asInt()).isEqualTo(22);
        assertThat(summary.get("unassignedOrders").asInt()).isEqualTo(8);
        assertThat(summary.get("totalLoad").asDouble()).isEqualTo(490.0);
    }

    @Test
    void repostingAnOrderIdUpdatesTheRowInsteadOfDuplicatingIt() throws Exception {
        String first = """
                {"orders":[{"orderId":"ORD001","latitude":12.9716,"longitude":77.5946,
                "address":"MG Road","packageWeight":10,"priority":"HIGH"}]}""";
        String second = """
                {"orders":[{"orderId":"ORD001","latitude":13.0,"longitude":77.6,
                "address":"Updated Road","packageWeight":25,"priority":"LOW"}]}""";

        mockMvc.perform(post("/api/dispatch/orders").contentType(MediaType.APPLICATION_JSON).content(first))
                .andExpect(status().isOk());
        mockMvc.perform(post("/api/dispatch/orders").contentType(MediaType.APPLICATION_JSON).content(second))
                .andExpect(status().isOk());

        assertThat(orderRepository.count()).isEqualTo(1);
        var stored = orderRepository.findById("ORD001").orElseThrow();
        assertThat(stored.getAddress()).isEqualTo("Updated Road");
        assertThat(stored.getPackageWeight()).isEqualTo(25.0);
        assertThat(stored.getPriority().name()).isEqualTo("LOW");
    }

    @Test
    void repostingAVehicleIdUpdatesCapacityAndLocation() throws Exception {
        String first = """
                {"vehicles":[{"vehicleId":"VEH001","capacity":100,"currentLatitude":12.9,
                "currentLongitude":77.6,"currentAddress":"Depot A"}]}""";
        String second = """
                {"vehicles":[{"vehicleId":"VEH001","capacity":250,"currentLatitude":13.1,
                "currentLongitude":77.7,"currentAddress":"Depot B"}]}""";

        mockMvc.perform(post("/api/dispatch/vehicles").contentType(MediaType.APPLICATION_JSON).content(first))
                .andExpect(status().isOk());
        mockMvc.perform(post("/api/dispatch/vehicles").contentType(MediaType.APPLICATION_JSON).content(second))
                .andExpect(status().isOk());

        assertThat(vehicleRepository.count()).isEqualTo(1);
        var stored = vehicleRepository.findById("VEH001").orElseThrow();
        assertThat(stored.getCapacity()).isEqualTo(250.0);
        assertThat(stored.getCurrentAddress()).isEqualTo("Depot B");
    }

    @Test
    void duplicateIdsInOneRequestAreRejected() throws Exception {
        String body = """
                {"orders":[
                {"orderId":"DUP","latitude":1,"longitude":1,"address":"a","packageWeight":1,"priority":"HIGH"},
                {"orderId":"DUP","latitude":2,"longitude":2,"address":"b","packageWeight":2,"priority":"LOW"}]}""";

        mockMvc.perform(post("/api/dispatch/orders").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Duplicate orderId values in request"))
                .andExpect(jsonPath("$.errors[0]").value(containsString("DUP")));
        assertThat(orderRepository.count()).isZero();
    }

    @Test
    void planWithNothingSubmittedReturns409() throws Exception {
        mockMvc.perform(get("/api/dispatch/plan"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.status").value("error"));
    }

    @Test
    void planWithOnlyOrdersAsksForVehicles() throws Exception {
        mockMvc.perform(post("/api/dispatch/orders").contentType(MediaType.APPLICATION_JSON)
                .content(resource("sample-orders.json"))).andExpect(status().isOk());

        mockMvc.perform(get("/api/dispatch/plan"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value(containsString("vehicles")));
    }

    @Test
    void planWithOnlyVehiclesAsksForOrders() throws Exception {
        mockMvc.perform(post("/api/dispatch/vehicles").contentType(MediaType.APPLICATION_JSON)
                .content(resource("sample-vehicles.json"))).andExpect(status().isOk());

        mockMvc.perform(get("/api/dispatch/plan"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value(containsString("orders")));
    }

    @Test
    void anOrderHeavierThanAnyVehicleIsReportedNotDropped() throws Exception {
        mockMvc.perform(post("/api/dispatch/orders").contentType(MediaType.APPLICATION_JSON).content("""
                {"orders":[{"orderId":"HUGE","latitude":12.9,"longitude":77.6,"address":"x",
                "packageWeight":9999,"priority":"HIGH"}]}""")).andExpect(status().isOk());
        mockMvc.perform(post("/api/dispatch/vehicles").contentType(MediaType.APPLICATION_JSON).content("""
                {"vehicles":[{"vehicleId":"V1","capacity":100,"currentLatitude":12.9,
                "currentLongitude":77.6,"currentAddress":"depot"}]}""")).andExpect(status().isOk());

        mockMvc.perform(get("/api/dispatch/plan"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.unassignedOrders[0].order.orderId").value("HUGE"))
                .andExpect(jsonPath("$.unassignedOrders[0].reason")
                        .value(containsString("exceeds the capacity of every vehicle")))
                .andExpect(jsonPath("$.dispatchPlan[0].assignedOrders").isEmpty());
    }

    @Test
    void swaggerDocumentationIsPublished() throws Exception {
        mockMvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("/api/dispatch/plan")))
                .andExpect(content().string(containsString("/api/dispatch/orders")))
                .andExpect(content().string(containsString("/api/dispatch/vehicles")));
    }

    @Test
    void unknownPathReturns404NotAServerError() throws Exception {
        mockMvc.perform(get("/api/dispatch/nope")).andExpect(status().isNotFound());
    }
}
