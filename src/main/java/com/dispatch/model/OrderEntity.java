package com.dispatch.model;

import jakarta.persistence.Column;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PostLoad;
import jakarta.persistence.PostPersist;
import jakarta.persistence.Table;
import jakarta.persistence.Transient;
import org.springframework.data.domain.Persistable;

/**
 * Delivery order. The business key (orderId) is the primary key, so re-posting an order
 * updates the existing row (upsert). Implementing {@link Persistable} lets Spring Data
 * INSERT new rows directly instead of issuing a SELECT per entity before saving.
 */
@Entity
@Table(name = "delivery_orders")
public class OrderEntity implements Persistable<String> {

    @Id
    @Column(name = "order_id", length = 64)
    private String orderId;

    @Column(nullable = false)
    private double latitude;

    @Column(nullable = false)
    private double longitude;

    @Column(nullable = false, length = 500)
    private String address;

    @Column(name = "package_weight", nullable = false)
    private double packageWeight;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private Priority priority;

    @Transient
    private boolean newEntity = true;

    protected OrderEntity() {
    }

    public OrderEntity(String orderId, double latitude, double longitude,
                       String address, double packageWeight, Priority priority) {
        this.orderId = orderId;
        update(latitude, longitude, address, packageWeight, priority);
    }

    public void update(double latitude, double longitude, String address,
                       double packageWeight, Priority priority) {
        this.latitude = latitude;
        this.longitude = longitude;
        this.address = address;
        this.packageWeight = packageWeight;
        this.priority = priority;
    }

    @Override
    public String getId() {
        return orderId;
    }

    @Override
    public boolean isNew() {
        return newEntity;
    }

    @PostLoad
    @PostPersist
    void markNotNew() {
        this.newEntity = false;
    }

    public String getOrderId() {
        return orderId;
    }

    public double getLatitude() {
        return latitude;
    }

    public double getLongitude() {
        return longitude;
    }

    public String getAddress() {
        return address;
    }

    public double getPackageWeight() {
        return packageWeight;
    }

    public Priority getPriority() {
        return priority;
    }
}
