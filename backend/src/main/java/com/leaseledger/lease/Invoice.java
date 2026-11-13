package com.leaseledger.lease;

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
import jakarta.persistence.Version;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

/** One amount a tenant owes on one date: a rent installment or the security deposit. */
@Entity
@Table(name = "invoices")
public class Invoice {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "lease_id", nullable = false, updatable = false)
    private Lease lease;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, updatable = false)
    private InvoiceType type;

    @Column(name = "sequence_no", nullable = false, updatable = false)
    private int sequenceNo;

    @Column(name = "due_date", nullable = false, updatable = false)
    private LocalDate dueDate;

    @Column(nullable = false, updatable = false)
    private BigDecimal amount;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private InvoiceStatus status;

    @Column(name = "paid_at")
    private Instant paidAt;

    @Column(name = "payment_reference")
    private String paymentReference;

    // Optimistic locking. Hibernate adds "AND version = ?" to every UPDATE and bumps the
    // number. If two requests load the same PENDING invoice and both try to pay it, the
    // second UPDATE matches zero rows and fails, instead of recording a second payment.
    @Version
    @Column(nullable = false)
    private Long version;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected Invoice() {
    }

    public Invoice(Lease lease, InvoiceType type, int sequenceNo, LocalDate dueDate, BigDecimal amount) {
        this.lease = lease;
        this.type = type;
        this.sequenceNo = sequenceNo;
        this.dueDate = dueDate;
        this.amount = amount;
        this.status = InvoiceStatus.PENDING;
        this.createdAt = Instant.now();
    }

    public void markPaid(Instant at, String reference) {
        this.status = InvoiceStatus.PAID;
        this.paidAt = at;
        this.paymentReference = reference;
    }

    public void cancel() {
        this.status = InvoiceStatus.CANCELLED;
    }

    public boolean isOverdue(LocalDate today) {
        return status == InvoiceStatus.PENDING && dueDate.isBefore(today);
    }

    public Long getId() {
        return id;
    }

    public Lease getLease() {
        return lease;
    }

    public InvoiceType getType() {
        return type;
    }

    public int getSequenceNo() {
        return sequenceNo;
    }

    public LocalDate getDueDate() {
        return dueDate;
    }

    public BigDecimal getAmount() {
        return amount;
    }

    public InvoiceStatus getStatus() {
        return status;
    }

    public Instant getPaidAt() {
        return paidAt;
    }

    public String getPaymentReference() {
        return paymentReference;
    }
}
