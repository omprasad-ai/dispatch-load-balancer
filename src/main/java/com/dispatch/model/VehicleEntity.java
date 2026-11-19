package com.dispatch.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PostLoad;
import jakarta.persistence.PostPersist;
import jakarta.persistence.Table;
import jakarta.persistence.Transient;
import org.springframework.data.domain.Persistable;

/** Fleet vehicle. vehicleId is the primary key, so re-posting a vehicle upserts it. */
@Entity
@Table(name = "vehicles")
public class VehicleEntity implements Persistable<String> {

    @Id
    @Column(name = "vehicle_id", length = 64)
    private String vehicleId;

    @Column(nullable = false)
    private double capacity;

    @Column(name = "current_latitude", nullable = false)
    private double currentLatitude;

    @Column(name = "current_longitude", nullable = false)
    private double currentLongitude;

    @Column(name = "current_address", nullable = false, length = 500)
    private String currentAddress;

    @Transient
    private boolean newEntity = true;

    protected VehicleEntity() {
    }

    public VehicleEntity(String vehicleId, double capacity, double currentLatitude,
                         double currentLongitude, String currentAddress) {
        this.vehicleId = vehicleId;
        update(capacity, currentLatitude, currentLongitude, currentAddress);
    }

    public void update(double capacity, double currentLatitude,
                       double currentLongitude, String currentAddress) {
        this.capacity = capacity;
        this.currentLatitude = currentLatitude;
        this.currentLongitude = currentLongitude;
        this.currentAddress = currentAddress;
    }

    @Override
    public String getId() {
        return vehicleId;
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

    public String getVehicleId() {
        return vehicleId;
    }

    public double getCapacity() {
        return capacity;
    }

    public double getCurrentLatitude() {
        return currentLatitude;
    }

    public double getCurrentLongitude() {
        return currentLongitude;
    }

    public String getCurrentAddress() {
        return currentAddress;
    }
}
