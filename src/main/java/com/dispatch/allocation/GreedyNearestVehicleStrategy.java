package com.dispatch.allocation;

import com.dispatch.distance.DistanceCalculator;
import com.dispatch.model.OrderEntity;
import com.dispatch.model.VehicleEntity;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

/**
 * Greedy heuristic:
 * <ol>
 *   <li>Orders are processed HIGH -> MEDIUM -> LOW; within a priority, heavier first
 *       (better packing), then by orderId for deterministic output.</li>
 *   <li>Each order goes to the vehicle that (a) has enough remaining capacity and (b) whose
 *       last stop (initially its current position) is nearest. Ties prefer the vehicle with
 *       less remaining capacity (best-fit), keeping bigger gaps free for later orders.</li>
 *   <li>Orders that fit no vehicle are returned as unassigned with a reason.</li>
 *   <li>Finally each route is refined with 2-opt to shorten the travelled distance.</li>
 * </ol>
 * This is a heuristic, not a proven optimum (the underlying problem is NP-hard).
 * Complexity of the allocation phase is O(n log n + n * m) for n orders and m vehicles.
 */
@Component
public class GreedyNearestVehicleStrategy implements AllocationStrategy {

    private static final double EPSILON = 1e-9;

    private static final Comparator<OrderEntity> ORDER_SEQUENCE =
            Comparator.comparing(OrderEntity::getPriority)
                    .thenComparing(OrderEntity::getPackageWeight, Comparator.reverseOrder())
                    .thenComparing(OrderEntity::getOrderId);

    private final DistanceCalculator calculator;
    private final RouteOptimizer routeOptimizer;

    public GreedyNearestVehicleStrategy(DistanceCalculator calculator, RouteOptimizer routeOptimizer) {
        this.calculator = calculator;
        this.routeOptimizer = routeOptimizer;
    }

    @Override
    public DispatchResult allocate(List<OrderEntity> orders, List<VehicleEntity> vehicles) {
        List<OrderEntity> sortedOrders = new ArrayList<>(orders);
        sortedOrders.sort(ORDER_SEQUENCE);

        List<VehicleRoute> routes = vehicles.stream()
                .sorted(Comparator.comparing(VehicleEntity::getVehicleId))
                .map(VehicleRoute::new)
                .toList();
        double maxCapacity = vehicles.stream().mapToDouble(VehicleEntity::getCapacity).max().orElse(0);

        List<UnassignedOrder> unassigned = new ArrayList<>();
        for (OrderEntity order : sortedOrders) {
            VehicleRoute best = null;
            double bestDistance = Double.MAX_VALUE;
            for (VehicleRoute route : routes) {
                if (!route.canFit(order.getPackageWeight())) {
                    continue;
                }
                double d = calculator.distanceKm(route.getLastLatitude(), route.getLastLongitude(),
                        order.getLatitude(), order.getLongitude());
                if (best == null
                        || d < bestDistance - EPSILON
                        || (Math.abs(d - bestDistance) <= EPSILON
                            && route.remainingCapacity() < best.remainingCapacity())) {
                    best = route;
                    bestDistance = d;
                }
            }
            if (best == null) {
                unassigned.add(new UnassignedOrder(order, reasonFor(order, maxCapacity, routes.isEmpty())));
            } else {
                best.addStop(order, bestDistance);
            }
        }

        routes.forEach(routeOptimizer::optimize);
        return new DispatchResult(routes, unassigned);
    }

    private String reasonFor(OrderEntity order, double maxCapacity, boolean noVehicles) {
        if (noVehicles) {
            return "No vehicles available";
        }
        if (order.getPackageWeight() > maxCapacity + EPSILON) {
            return String.format(Locale.ROOT,
                    "Package weight %.2f exceeds the capacity of every vehicle (largest capacity: %.2f)",
                    order.getPackageWeight(), maxCapacity);
        }
        return "No vehicle has enough remaining capacity";
    }
}
