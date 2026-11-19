package com.dispatch.allocation;

import com.dispatch.distance.HaversineDistanceCalculator;
import com.dispatch.model.OrderEntity;
import com.dispatch.model.Priority;
import com.dispatch.model.VehicleEntity;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class GreedyNearestVehicleStrategyTest {

    private final HaversineDistanceCalculator calculator = new HaversineDistanceCalculator();
    private final GreedyNearestVehicleStrategy strategy =
            new GreedyNearestVehicleStrategy(calculator, new RouteOptimizer(calculator));

    private static OrderEntity order(String id, double lat, double lon, double weight, Priority priority) {
        return new OrderEntity(id, lat, lon, "addr-" + id, weight, priority);
    }

    private static VehicleEntity vehicle(String id, double capacity, double lat, double lon) {
        return new VehicleEntity(id, capacity, lat, lon, "depot-" + id);
    }

    private static VehicleRoute routeOf(DispatchResult result, String vehicleId) {
        return result.routes().stream()
                .filter(r -> r.getVehicle().getVehicleId().equals(vehicleId))
                .findFirst().orElseThrow();
    }

    @Test
    void highPriorityGetsScarceCapacityBeforeLowPriority() {
        // only one vehicle with room for one of the two orders; the LOW order is nearer
        List<OrderEntity> orders = List.of(
                order("LOW1", 10.0, 10.0, 30, Priority.LOW),
                order("HIGH1", 10.5, 10.5, 40, Priority.HIGH));
        List<VehicleEntity> vehicles = List.of(vehicle("V1", 50, 10.0, 10.0));

        DispatchResult result = strategy.allocate(orders, vehicles);

        assertThat(routeOf(result, "V1").getStops()).extracting(OrderEntity::getOrderId).containsExactly("HIGH1");
        assertThat(result.unassigned()).extracting(u -> u.order().getOrderId()).containsExactly("LOW1");
    }

    @Test
    void mediumPriorityBeatsLowPriority() {
        List<OrderEntity> orders = List.of(
                order("L", 1, 1, 60, Priority.LOW),
                order("M", 1, 1, 60, Priority.MEDIUM));
        DispatchResult result = strategy.allocate(orders, List.of(vehicle("V1", 100, 1, 1)));

        assertThat(routeOf(result, "V1").getStops()).extracting(OrderEntity::getOrderId).containsExactly("M");
        assertThat(result.unassigned()).extracting(u -> u.order().getOrderId()).containsExactly("L");
    }

    @Test
    void nearestVehicleIsChosen() {
        VehicleEntity bangalore = vehicle("BLR", 100, 12.9716, 77.5946);
        VehicleEntity chennai = vehicle("MAA", 100, 13.0827, 80.2707);
        OrderEntity nearChennai = order("O1", 13.0674, 80.2376, 10, Priority.HIGH);

        DispatchResult result = strategy.allocate(List.of(nearChennai), List.of(bangalore, chennai));

        assertThat(routeOf(result, "MAA").getStops()).hasSize(1);
        assertThat(routeOf(result, "BLR").getStops()).isEmpty();
        assertThat(routeOf(result, "MAA").getDistanceKm()).isLessThan(10);
    }

    @Test
    void orderFallsBackToNextNearestVehicleWhenNearestIsFull() {
        VehicleEntity near = vehicle("NEAR", 10, 0, 0);
        VehicleEntity far = vehicle("FAR", 100, 0, 5);
        List<OrderEntity> orders = List.of(
                order("A", 0, 0.1, 10, Priority.HIGH),   // fills NEAR
                order("B", 0, 0.2, 5, Priority.MEDIUM)); // NEAR is full -> FAR

        DispatchResult result = strategy.allocate(orders, List.of(near, far));

        assertThat(routeOf(result, "NEAR").getStops()).extracting(OrderEntity::getOrderId).containsExactly("A");
        assertThat(routeOf(result, "FAR").getStops()).extracting(OrderEntity::getOrderId).containsExactly("B");
        assertThat(result.unassigned()).isEmpty();
    }

    @Test
    void orderWeightEqualToCapacityFits() {
        DispatchResult result = strategy.allocate(
                List.of(order("A", 1, 1, 100, Priority.HIGH)), List.of(vehicle("V1", 100, 1, 1)));

        assertThat(result.unassigned()).isEmpty();
        assertThat(routeOf(result, "V1").getLoad()).isEqualTo(100);
    }

    @Test
    void orderHeavierThanEveryVehicleIsUnassignedWithReason() {
        DispatchResult result = strategy.allocate(
                List.of(order("BIG", 1, 1, 500, Priority.HIGH)),
                List.of(vehicle("V1", 100, 1, 1), vehicle("V2", 150, 1, 1)));

        assertThat(result.unassigned()).hasSize(1);
        assertThat(result.unassigned().get(0).reason())
                .contains("exceeds the capacity of every vehicle")
                .contains("150.00");
        assertThat(result.routes()).allSatisfy(r -> assertThat(r.getStops()).isEmpty());
    }

    @Test
    void orderThatNoLongerFitsAnywhereReportsRemainingCapacityReason() {
        DispatchResult result = strategy.allocate(
                List.of(order("A", 1, 1, 60, Priority.HIGH), order("B", 1, 1, 60, Priority.LOW)),
                List.of(vehicle("V1", 100, 1, 1)));

        assertThat(result.unassigned()).hasSize(1);
        assertThat(result.unassigned().get(0).reason()).isEqualTo("No vehicle has enough remaining capacity");
    }

    @Test
    void noVehiclesMeansEverythingIsUnassigned() {
        DispatchResult result = strategy.allocate(
                List.of(order("A", 1, 1, 1, Priority.HIGH), order("B", 2, 2, 1, Priority.LOW)), List.of());

        assertThat(result.routes()).isEmpty();
        assertThat(result.unassigned()).hasSize(2);
        assertThat(result.unassigned()).allSatisfy(u -> assertThat(u.reason()).isEqualTo("No vehicles available"));
    }

    @Test
    void noOrdersGivesEmptyRoutesForEveryVehicle() {
        DispatchResult result = strategy.allocate(List.of(), List.of(vehicle("V1", 10, 1, 1), vehicle("V2", 10, 2, 2)));

        assertThat(result.routes()).hasSize(2);
        assertThat(result.routes()).allSatisfy(r -> {
            assertThat(r.getStops()).isEmpty();
            assertThat(r.getLoad()).isZero();
            assertThat(r.getDistanceKm()).isZero();
        });
        assertThat(result.unassigned()).isEmpty();
    }

    @Test
    void ordersAtTheSameLocationAsTheVehicleCostNoDistance() {
        List<OrderEntity> orders = List.of(
                order("A", 28.6139, 77.2090, 10, Priority.HIGH),
                order("B", 28.6139, 77.2090, 10, Priority.MEDIUM),
                order("C", 28.6139, 77.2090, 10, Priority.LOW));

        DispatchResult result = strategy.allocate(orders, List.of(vehicle("V1", 100, 28.6139, 77.2090)));

        assertThat(routeOf(result, "V1").getStops()).hasSize(3);
        assertThat(routeOf(result, "V1").getDistanceKm()).isZero();
    }

    @Test
    void equalDistanceTiePrefersTheVehicleWithLessRemainingCapacity() {
        VehicleEntity small = vehicle("SMALL", 50, 5, 5);
        VehicleEntity large = vehicle("LARGE", 100, 5, 5);

        DispatchResult result = strategy.allocate(
                List.of(order("A", 5, 5, 40, Priority.HIGH)), List.of(large, small));

        assertThat(routeOf(result, "SMALL").getStops()).hasSize(1);
        assertThat(routeOf(result, "LARGE").getStops()).isEmpty();
    }

    @Test
    void distanceIncludesTheLegFromTheVehicleToTheFirstStop() {
        VehicleEntity v = vehicle("V1", 100, 0, 0);
        DispatchResult result = strategy.allocate(List.of(order("A", 0, 1, 10, Priority.HIGH)), List.of(v));

        assertThat(routeOf(result, "V1").getDistanceKm()).isBetween(111.0, 111.4);
    }

    @Test
    void resultIsDeterministicRegardlessOfInputOrder() {
        List<OrderEntity> orders = randomOrders(new Random(7), 60);
        List<VehicleEntity> vehicles = randomVehicles(new Random(8), 6, 100);
        List<OrderEntity> shuffled = new ArrayList<>(orders);
        java.util.Collections.shuffle(shuffled, new Random(99));

        DispatchResult first = strategy.allocate(orders, vehicles);
        DispatchResult second = strategy.allocate(shuffled, vehicles);

        for (VehicleRoute r : first.routes()) {
            assertThat(routeOf(second, r.getVehicle().getVehicleId()).getStops())
                    .extracting(OrderEntity::getOrderId)
                    .containsExactlyElementsOf(r.getStops().stream().map(OrderEntity::getOrderId).toList());
        }
    }

    @Test
    void invariantsHoldOnRandomisedData() {
        for (long seed = 1; seed <= 25; seed++) {
            Random random = new Random(seed);
            List<OrderEntity> orders = randomOrders(random, 80);
            List<VehicleEntity> vehicles = randomVehicles(random, 5, 150);

            DispatchResult result = strategy.allocate(orders, vehicles);

            Set<String> assigned = new HashSet<>();
            for (VehicleRoute route : result.routes()) {
                assertThat(route.getLoad()).isLessThanOrEqualTo(route.getVehicle().getCapacity() + 1e-9);
                double summed = route.getStops().stream().mapToDouble(OrderEntity::getPackageWeight).sum();
                assertThat(route.getLoad()).isCloseTo(summed, org.assertj.core.api.Assertions.within(1e-6));
                route.getStops().forEach(o -> assertThat(assigned.add(o.getOrderId())).isTrue());
            }
            result.unassigned().forEach(u -> assertThat(assigned.add(u.order().getOrderId())).isTrue());
            assertThat(assigned).hasSize(orders.size());
        }
    }

    @Test
    void unassignedOrdersCannotFitAnywhereAfterAllocation() {
        // Load only ever grows, so an order rejected earlier must still not fit in any vehicle at the end.
        Random random = new Random(3);
        List<OrderEntity> orders = randomOrders(random, 120);
        List<VehicleEntity> vehicles = randomVehicles(random, 4, 120);

        DispatchResult result = strategy.allocate(orders, vehicles);

        for (UnassignedOrder u : result.unassigned()) {
            for (VehicleRoute route : result.routes()) {
                assertThat(route.canFit(u.order().getPackageWeight()))
                        .as("%s should not fit anywhere after allocation", u.order().getOrderId())
                        .isFalse();
            }
        }
    }

    @Test
    void handlesLargeDatasetsQuickly() {
        Random random = new Random(42);
        List<OrderEntity> orders = randomOrders(random, 5000);
        List<VehicleEntity> vehicles = randomVehicles(random, 200, 500);

        long start = System.nanoTime();
        DispatchResult result = strategy.allocate(orders, vehicles);
        long millis = (System.nanoTime() - start) / 1_000_000;

        assertThat(result.routes()).hasSize(200);
        assertThat(millis).as("5000 orders x 200 vehicles took %d ms", millis).isLessThan(10_000);
    }

    private static List<OrderEntity> randomOrders(Random random, int count) {
        Priority[] priorities = Priority.values();
        List<OrderEntity> orders = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            orders.add(order("ORD" + i, 12 + random.nextDouble() * 16, 72 + random.nextDouble() * 10,
                    1 + random.nextInt(40), priorities[random.nextInt(priorities.length)]));
        }
        return orders;
    }

    private static List<VehicleEntity> randomVehicles(Random random, int count, int maxCapacity) {
        List<VehicleEntity> vehicles = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            vehicles.add(vehicle("V" + i, 20 + random.nextInt(maxCapacity),
                    12 + random.nextDouble() * 16, 72 + random.nextDouble() * 10));
        }
        return vehicles;
    }
}
