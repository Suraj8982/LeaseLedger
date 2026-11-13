package com.leaseledger.property;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;

/** A building or compound owned by one manager. */
@Entity
@Table(name = "properties")
public class Property {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // Just the id, not a @ManyToOne to User: this module never needs the manager's
    // details, and it keeps the property module independent of the user module.
    // The foreign key in the database still guarantees the manager exists.
    @Column(name = "manager_id", nullable = false, updatable = false)
    private Long managerId;

    @Column(nullable = false)
    private String name;

    @Column(nullable = false)
    private String address;

    @Column(nullable = false)
    private String city;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected Property() {
    }

    public Property(Long managerId, String name, String address, String city) {
        this.managerId = managerId;
        this.name = name;
        this.address = address;
        this.city = city;
        this.createdAt = Instant.now();
    }

    public void update(String name, String address, String city) {
        this.name = name;
        this.address = address;
        this.city = city;
    }

    public Long getId() {
        return id;
    }

    public Long getManagerId() {
        return managerId;
    }

    public String getName() {
        return name;
    }

    public String getAddress() {
        return address;
    }

    public String getCity() {
        return city;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
