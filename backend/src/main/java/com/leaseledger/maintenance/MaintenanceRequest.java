package com.leaseledger.maintenance;

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
import java.time.Instant;

/** A problem a tenant has reported in the unit they rent. */
@Entity
@Table(name = "maintenance_requests")
public class MaintenanceRequest {

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

    @Column(nullable = false, updatable = false)
    private String title;

    @Column(nullable = false, updatable = false)
    private String description;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, updatable = false)
    private MaintenancePriority priority;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private MaintenanceStatus status;

    @Column(name = "resolution_note")
    private String resolutionNote;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected MaintenanceRequest() {
    }

    public MaintenanceRequest(Unit unit, User tenant, Long managerId, String title, String description,
                              MaintenancePriority priority, Instant now) {
        this.unit = unit;
        this.tenant = tenant;
        this.managerId = managerId;
        this.title = title;
        this.description = description;
        this.priority = priority;
        this.status = MaintenanceStatus.OPEN;
        this.createdAt = now;
        this.updatedAt = now;
    }

    /**
     * The entity guards its own rule: no code anywhere can put a request into a status
     * the workflow does not allow, because this is the only way to change it.
     *
     * @return false if the move is not allowed from the current status
     */
    public boolean moveTo(MaintenanceStatus next, String note, Instant now) {
        if (!status.canMoveTo(next)) {
            return false;
        }
        this.status = next;
        if (note != null) {
            this.resolutionNote = note;
        }
        this.updatedAt = now;
        return true;
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

    public String getTitle() {
        return title;
    }

    public String getDescription() {
        return description;
    }

    public MaintenancePriority getPriority() {
        return priority;
    }

    public MaintenanceStatus getStatus() {
        return status;
    }

    public String getResolutionNote() {
        return resolutionNote;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }
}
