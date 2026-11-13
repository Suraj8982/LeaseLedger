package com.leaseledger.user;

public enum Role {
    /** Platform operator. Cannot be self-registered. */
    ADMIN,
    /** Property manager or landlord: owns properties, leases and invoices. */
    MANAGER,
    /** Tenant: sees their own lease and invoices, raises maintenance requests. */
    TENANT
}
