package com.leaseledger.property;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.Instant;

/** One rentable apartment, villa or office inside a property. */
@Entity
@Table(name = "units")
public class Unit {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // LAZY: loading a unit does not also load its property unless the code asks for it.
    // (@ManyToOne is EAGER by default, which silently adds a join to every query.)
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "property_id", nullable = false, updatable = false)
    private Property property;

    @Column(name = "unit_number", nullable = false)
    private String unitNumber;

    @Column(nullable = false)
    private int bedrooms;

    // BigDecimal, not double: 0.1 + 0.2 must be exactly 0.3 when it is someone's rent.
    @Column(name = "monthly_rent", nullable = false)
    private BigDecimal monthlyRent;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private UnitStatus status;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected Unit() {
    }

    public Unit(Property property, String unitNumber, int bedrooms, BigDecimal monthlyRent, UnitStatus status) {
        this.property = property;
        this.unitNumber = unitNumber;
        this.bedrooms = bedrooms;
        this.monthlyRent = monthlyRent;
        this.status = status;
        this.createdAt = Instant.now();
    }

    public void update(String unitNumber, int bedrooms, BigDecimal monthlyRent, UnitStatus status) {
        this.unitNumber = unitNumber;
        this.bedrooms = bedrooms;
        this.monthlyRent = monthlyRent;
        this.status = status;
    }

    public Long getId() {
        return id;
    }

    public Property getProperty() {
        return property;
    }

    public String getUnitNumber() {
        return unitNumber;
    }

    public int getBedrooms() {
        return bedrooms;
    }

    public BigDecimal getMonthlyRent() {
        return monthlyRent;
    }

    public UnitStatus getStatus() {
        return status;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
