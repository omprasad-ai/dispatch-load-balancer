package com.dispatch.allocation;

import com.dispatch.distance.HaversineDistanceCalculator;
import com.dispatch.model.OrderEntity;
import com.dispatch.model.Priority;
import com.dispatch.model.VehicleEntity;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

class RouteOptimizerTest {

    private final HaversineDistanceCalculator calculator = new HaversineDistanceCalculator();
    private final RouteOptimizer optimizer = new RouteOptimizer(calculator);

    private static OrderEntity stop(String id, double lon) {
        return new OrderEntity(id, 0, lon, "addr", 1, Priority.HIGH);
    }

    @Test
    void reordersStopsOnALineIntoNearestFirstOrder() {
        VehicleRoute route = new VehicleRoute(new VehicleEntity("V1", 100, 0, 0, "start"));
        route.addStop(stop("A", 3), 0);
        route.addStop(stop("B", 1), 0);
        route.addStop(stop("C", 2), 0);

        optimizer.optimize(route);

        assertThat(route.getStops()).extracting(OrderEntity::getOrderId).containsExactly("B", "C", "A");
        assertThat(route.getDistanceKm()).isCloseTo(3 * 111.195, within(0.1));
    }

    @Test
    void optimizationNeverMakesARouteLonger() {
        VehicleRoute route = new VehicleRoute(new VehicleEntity("V1", 100, 0, 0, "start"));
        route.addStop(stop("A", 1), 0);
        route.addStop(stop("B", 2), 0);
        route.addStop(stop("C", 3), 0);
        double before = optimizer.pathLength(0, 0, route.getStops());

        optimizer.optimize(route);

        assertThat(route.getDistanceKm()).isLessThanOrEqualTo(before + 1e-9);
        assertThat(route.getStops()).extracting(OrderEntity::getOrderId).containsExactly("A", "B", "C");
    }

    @Test
    void emptyAndSingleStopRoutesAreLeftAlone() {
        VehicleRoute empty = new VehicleRoute(new VehicleEntity("V1", 100, 0, 0, "start"));
        optimizer.optimize(empty);
        assertThat(empty.getStops()).isEmpty();
        assertThat(empty.getDistanceKm()).isZero();

        VehicleRoute single = new VehicleRoute(new VehicleEntity("V2", 100, 0, 0, "start"));
        single.addStop(stop("A", 1), 111.195);
        optimizer.optimize(single);
        assertThat(single.getStops()).hasSize(1);
        assertThat(single.getDistanceKm()).isEqualTo(111.195);
    }

    @Test
    void pathLengthSumsEachLegFromTheStart() {
        double length = optimizer.pathLength(0, 0, List.of(stop("A", 1), stop("B", 2)));
        assertThat(length).isCloseTo(2 * 111.195, within(0.1));
    }
}
