package com.dispatch.service;

import com.dispatch.allocation.AllocationStrategy;
import com.dispatch.allocation.DispatchResult;
import com.dispatch.dto.DispatchPlanResponse;
import com.dispatch.dto.OrderRequest;
import com.dispatch.dto.VehicleRequest;
import com.dispatch.exception.InvalidRequestException;
import com.dispatch.exception.NoDataException;
import com.dispatch.model.OrderEntity;
import com.dispatch.model.VehicleEntity;
import com.dispatch.repository.OrderRepository;
import com.dispatch.repository.VehicleRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;

@Service
public class DispatchService {

    /** Keeps IN (...) lists well below database bind-parameter limits for large uploads. */
    private static final int LOOKUP_CHUNK = 1000;

    private final OrderRepository orderRepository;
    private final VehicleRepository vehicleRepository;
    private final AllocationStrategy allocationStrategy;
    private final PlanMapper planMapper;

    public DispatchService(OrderRepository orderRepository, VehicleRepository vehicleRepository,
                           AllocationStrategy allocationStrategy, PlanMapper planMapper) {
        this.orderRepository = orderRepository;
        this.vehicleRepository = vehicleRepository;
        this.allocationStrategy = allocationStrategy;
        this.planMapper = planMapper;
    }

    /**
     * Upsert: existing orderIds are updated, new ones inserted. Existing rows are fetched in
     * chunked bulk queries (not one SELECT per order) and everything is written in one saveAll.
     */
    @Transactional
    public void saveOrders(List<OrderRequest> requests) {
        rejectDuplicates(requests.stream().map(OrderRequest::orderId).toList(), "orderId");

        Map<String, OrderEntity> existing = new HashMap<>();
        for (List<String> chunk : partition(requests.stream().map(OrderRequest::orderId).toList())) {
            orderRepository.findAllById(chunk).forEach(o -> existing.put(o.getOrderId(), o));
        }

        List<OrderEntity> toSave = new ArrayList<>(requests.size());
        for (OrderRequest r : requests) {
            OrderEntity entity = existing.get(r.orderId());
            if (entity == null) {
                entity = new OrderEntity(r.orderId(), r.latitude(), r.longitude(),
                        r.address(), r.packageWeight(), r.priority());
            } else {
                entity.update(r.latitude(), r.longitude(), r.address(), r.packageWeight(), r.priority());
            }
            toSave.add(entity);
        }
        orderRepository.saveAll(toSave);
    }

    @Transactional
    public void saveVehicles(List<VehicleRequest> requests) {
        rejectDuplicates(requests.stream().map(VehicleRequest::vehicleId).toList(), "vehicleId");

        Map<String, VehicleEntity> existing = new java.util.HashMap<>();
        for (List<String> chunk : partition(requests.stream().map(VehicleRequest::vehicleId).toList())) {
            vehicleRepository.findAllById(chunk).forEach(v -> existing.put(v.getVehicleId(), v));
        }

        List<VehicleEntity> toSave = new ArrayList<>(requests.size());
        for (VehicleRequest r : requests) {
            VehicleEntity entity = existing.get(r.vehicleId());
            if (entity == null) {
                entity = new VehicleEntity(r.vehicleId(), r.capacity(), r.currentLatitude(),
                        r.currentLongitude(), r.currentAddress());
            } else {
                entity.update(r.capacity(), r.currentLatitude(), r.currentLongitude(), r.currentAddress());
            }
            toSave.add(entity);
        }
        vehicleRepository.saveAll(toSave);
    }

    @Transactional(readOnly = true)
    public DispatchPlanResponse buildPlan() {
        List<OrderEntity> orders = orderRepository.findAll();
        List<VehicleEntity> vehicles = vehicleRepository.findAll();

        if (orders.isEmpty() && vehicles.isEmpty()) {
            throw new NoDataException("No orders or vehicles found. POST /api/dispatch/orders and "
                    + "POST /api/dispatch/vehicles first.");
        }
        if (orders.isEmpty()) {
            throw new NoDataException("No delivery orders found. POST /api/dispatch/orders first.");
        }
        if (vehicles.isEmpty()) {
            throw new NoDataException("No vehicles found. POST /api/dispatch/vehicles first.");
        }

        DispatchResult result = allocationStrategy.allocate(orders, vehicles);
        return planMapper.toResponse(result, orders.size());
    }

    private static void rejectDuplicates(List<String> ids, String field) {
        Set<String> seen = new HashSet<>();
        List<String> duplicates = ids.stream().filter(id -> !seen.add(id)).distinct().toList();
        if (!duplicates.isEmpty()) {
            throw new InvalidRequestException("Duplicate " + field + " values in request",
                    duplicates.stream().map(d -> field + " '" + d + "' appears more than once").toList());
        }
    }

    private static <T> List<List<T>> partition(List<T> list) {
        List<List<T>> chunks = new ArrayList<>();
        for (int i = 0; i < list.size(); i += LOOKUP_CHUNK) {
            chunks.add(list.subList(i, Math.min(list.size(), i + LOOKUP_CHUNK)));
        }
        return chunks;
    }
}
