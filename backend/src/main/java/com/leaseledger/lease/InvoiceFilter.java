package com.leaseledger.lease;

/** Values accepted by GET /api/invoices?filter=... */
public enum InvoiceFilter {
    ALL,
    PENDING,
    /** PENDING and past its due date. */
    OVERDUE,
    PAID,
    CANCELLED
}
