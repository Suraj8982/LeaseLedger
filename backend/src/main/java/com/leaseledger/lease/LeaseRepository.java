package com.leaseledger.lease;

import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

/**
 * Every query here uses JOIN FETCH. A lease response shows the unit number, property
 * name and tenant name, which live in three other tables. Because those associations
 * are LAZY, reading them one lease at a time would cost three extra queries per lease
 * (N+1). JOIN FETCH loads them in the same SELECT.
 */
public interface LeaseRepository extends JpaRepository<Lease, Long> {

    String WITH_DETAILS = "select l from Lease l join fetch l.unit u join fetch u.property join fetch l.tenant ";
    String NEWEST_FIRST = " order by l.startDate desc, l.id desc";

    @Query(value = WITH_DETAILS + "where l.managerId = :managerId" + NEWEST_FIRST,
            countQuery = "select count(l) from Lease l where l.managerId = :managerId")
    Page<Lease> findByManager(Long managerId, Pageable pageable);

    @Query(value = WITH_DETAILS + "where l.tenant.id = :tenantId" + NEWEST_FIRST,
            countQuery = "select count(l) from Lease l where l.tenant.id = :tenantId")
    Page<Lease> findByTenant(Long tenantId, Pageable pageable);

    @Query(WITH_DETAILS + "where l.id = :id")
    Optional<Lease> findWithDetailsById(Long id);
}
