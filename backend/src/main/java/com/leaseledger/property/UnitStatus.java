package com.leaseledger.property;

public enum UnitStatus {
    /** Ready to be leased. */
    VACANT,
    /** Has an active lease. Set by the lease module (milestone 3), never directly by a manager. */
    OCCUPIED,
    /** Taken off the market by the manager, for example during renovation. */
    UNAVAILABLE
}
