package com.leaseledger.lease;

/**
 * There is deliberately no OVERDUE status. Overdue means "PENDING and the due date has
 * passed", which is a fact about today's date, not something to store: a stored flag
 * would need a nightly job to keep it true and would be wrong whenever that job failed.
 */
public enum InvoiceStatus {
    PENDING,
    PAID,
    /** Voided because the lease was terminated before this invoice fell due. */
    CANCELLED
}
