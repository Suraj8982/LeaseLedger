package com.leaseledger.lease;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

/** JpaSpecificationExecutor adds findAll(Specification, Pageable) for the filtered invoice list. */
public interface InvoiceRepository extends JpaRepository<Invoice, Long>, JpaSpecificationExecutor<Invoice> {

    List<Invoice> findByLeaseIdOrderByDueDateAscIdAsc(Long leaseId);

    /** Lookup and ownership check in one query: only invoices on this manager's leases. */
    Optional<Invoice> findByIdAndLeaseManagerId(Long id, Long managerId);
}
