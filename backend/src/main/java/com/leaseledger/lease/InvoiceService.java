package com.leaseledger.lease;

import com.leaseledger.common.ConflictException;
import com.leaseledger.common.NotFoundException;
import com.leaseledger.common.PageResponse;
import com.leaseledger.common.Paging;
import com.leaseledger.lease.LeaseDtos.InvoiceResponse;
import com.leaseledger.security.AuthenticatedUser;
import com.leaseledger.user.Role;
import jakarta.persistence.criteria.Predicate;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
public class InvoiceService {

    private final InvoiceRepository invoices;
    private final Clock clock;

    public InvoiceService(InvoiceRepository invoices, Clock clock) {
        this.invoices = invoices;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public PageResponse<InvoiceResponse> list(AuthenticatedUser caller, InvoiceFilter filter, int page, int size) {
        LocalDate today = LocalDate.now(clock);
        var pageable = Paging.of(page, size, Sort.by("dueDate", "id"));
        return PageResponse.from(
                invoices.findAll(visibleTo(caller, filter, today), pageable)
                        .map(i -> InvoiceResponse.from(i, today)));
    }

    /**
     * Builds the WHERE clause from parts. The conditions depend on who is asking and
     * which filter they chose; a Specification assembles only the ones that apply,
     * instead of one hand-written query per combination.
     */
    private static Specification<Invoice> visibleTo(AuthenticatedUser caller, InvoiceFilter filter, LocalDate today) {
        return (invoice, query, cb) -> {
            List<Predicate> where = new ArrayList<>();

            // Always present: restrict to the caller's own data.
            var lease = invoice.get("lease");
            where.add(caller.role() == Role.MANAGER
                    ? cb.equal(lease.get("managerId"), caller.id())
                    : cb.equal(lease.get("tenant").get("id"), caller.id()));

            switch (filter) {
                case ALL -> { }
                case PENDING -> where.add(cb.equal(invoice.get("status"), InvoiceStatus.PENDING));
                case PAID -> where.add(cb.equal(invoice.get("status"), InvoiceStatus.PAID));
                case CANCELLED -> where.add(cb.equal(invoice.get("status"), InvoiceStatus.CANCELLED));
                case OVERDUE -> {
                    where.add(cb.equal(invoice.get("status"), InvoiceStatus.PENDING));
                    where.add(cb.lessThan(invoice.get("dueDate"), today));
                }
            }
            return cb.and(where.toArray(Predicate[]::new));
        };
    }

    /**
     * Records a payment received outside the system (cheque, cash, bank transfer).
     * Phase 2 adds card payments, where the payment gateway triggers this instead of a manager.
     */
    public InvoiceResponse markPaid(Long managerId, Long invoiceId, String reference) {
        Invoice invoice = invoices.findByIdAndLeaseManagerId(invoiceId, managerId)
                .orElseThrow(() -> new NotFoundException("Invoice not found"));
        if (invoice.getStatus() != InvoiceStatus.PENDING) {
            throw new ConflictException("Only a pending invoice can be marked paid; this one is "
                    + invoice.getStatus());
        }
        invoice.markPaid(Instant.now(clock), reference == null || reference.isBlank() ? null : reference.trim());
        // Flush so the version check runs inside this method (see Invoice.version).
        invoices.saveAndFlush(invoice);
        return InvoiceResponse.from(invoice, LocalDate.now(clock));
    }
}
