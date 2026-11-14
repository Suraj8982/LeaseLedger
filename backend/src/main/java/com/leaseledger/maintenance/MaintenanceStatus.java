package com.leaseledger.maintenance;

import java.util.Set;

/**
 * The life of a maintenance request, as a small state machine:
 *
 * <pre>
 *   OPEN ──────► IN_PROGRESS ──────► RESOLVED
 *     │ └──────────────────────────────▲
 *     └────────► CANCELLED
 * </pre>
 *
 * The allowed moves are defined here, in one place, instead of as if-statements
 * scattered through the service.
 */
public enum MaintenanceStatus {
    OPEN,
    IN_PROGRESS,
    RESOLVED,
    CANCELLED;

    private Set<MaintenanceStatus> nextStatuses() {
        return switch (this) {
            case OPEN -> Set.of(IN_PROGRESS, RESOLVED, CANCELLED);
            case IN_PROGRESS -> Set.of(RESOLVED);
            case RESOLVED, CANCELLED -> Set.of(); // final: nothing follows
        };
    }

    public boolean canMoveTo(MaintenanceStatus next) {
        return nextStatuses().contains(next);
    }

    /** Photos can be added only while the request is still being worked on. */
    public boolean isActive() {
        return this == OPEN || this == IN_PROGRESS;
    }
}
