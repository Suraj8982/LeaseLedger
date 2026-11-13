package com.leaseledger.lease;

import com.leaseledger.property.Unit;
import com.leaseledger.user.User;
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
import java.time.LocalDate;

/** An agreement that one tenant rents one unit for a period, for a total rent paid in installments. */
@Entity
@Table(name = "leases")
public class Lease {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "unit_id", nullable = false, updatable = false)
    private Unit unit;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "tenant_id", nullable = false, updatable = false)
    private User tenant;

    @Column(name = "manager_id", nullable = false, updatable = false)
    private Long managerId;

    @Column(name = "start_date", nullable = false, updatable = false)
    private LocalDate startDate;

    @Column(name = "end_date", nullable = false, updatable = false)
    private LocalDate endDate;

    // The agreed rent is stored on the lease. Changing the unit's asking rent later
    // must not change what an existing tenant owes.
    @Column(name = "total_rent", nullable = false, updatable = false)
    private BigDecimal totalRent;

    @Column(nullable = false, updatable = false)
    private int installments;

    @Column(name = "security_deposit", nullable = false, updatable = false)
    private BigDecimal securityDeposit;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private LeaseStatus status;

    @Column(name = "terminated_on")
    private LocalDate terminatedOn;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected Lease() {
    }

    public Lease(Unit unit, User tenant, Long managerId, LocalDate startDate, LocalDate endDate,
                 BigDecimal totalRent, int installments, BigDecimal securityDeposit) {
        this.unit = unit;
        this.tenant = tenant;
        this.managerId = managerId;
        this.startDate = startDate;
        this.endDate = endDate;
        this.totalRent = totalRent;
        this.installments = installments;
        this.securityDeposit = securityDeposit;
        this.status = LeaseStatus.ACTIVE;
        this.createdAt = Instant.now();
    }

    public void terminate(LocalDate on) {
        this.status = LeaseStatus.TERMINATED;
        this.terminatedOn = on;
    }

    public Long getId() {
        return id;
    }

    public Unit getUnit() {
        return unit;
    }

    public User getTenant() {
        return tenant;
    }

    public Long getManagerId() {
        return managerId;
    }

    public LocalDate getStartDate() {
        return startDate;
    }

    public LocalDate getEndDate() {
        return endDate;
    }

    public BigDecimal getTotalRent() {
        return totalRent;
    }

    public int getInstallments() {
        return installments;
    }

    public BigDecimal getSecurityDeposit() {
        return securityDeposit;
    }

    public LeaseStatus getStatus() {
        return status;
    }

    public LocalDate getTerminatedOn() {
        return terminatedOn;
    }
}
