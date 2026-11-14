package com.leaseledger.maintenance;

import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface MaintenanceRequestRepository extends JpaRepository<MaintenanceRequest, Long> {

    String WITH_DETAILS =
            "select r from MaintenanceRequest r join fetch r.unit u join fetch u.property join fetch r.tenant ";
    // A null status means "any status".
    String STATUS_FILTER = " and (:status is null or r.status = :status)";
    String NEWEST_FIRST = " order by r.createdAt desc, r.id desc";

    @Query(value = WITH_DETAILS + "where r.managerId = :managerId" + STATUS_FILTER + NEWEST_FIRST,
            countQuery = "select count(r) from MaintenanceRequest r where r.managerId = :managerId" + STATUS_FILTER)
    Page<MaintenanceRequest> findByManager(Long managerId, MaintenanceStatus status, Pageable pageable);

    @Query(value = WITH_DETAILS + "where r.tenant.id = :tenantId" + STATUS_FILTER + NEWEST_FIRST,
            countQuery = "select count(r) from MaintenanceRequest r where r.tenant.id = :tenantId" + STATUS_FILTER)
    Page<MaintenanceRequest> findByTenant(Long tenantId, MaintenanceStatus status, Pageable pageable);

    @Query(WITH_DETAILS + "where r.id = :id")
    Optional<MaintenanceRequest> findWithDetailsById(Long id);
}
