package com.leaseledger.property;

import jakarta.persistence.LockModeType;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;

public interface UnitRepository extends JpaRepository<Unit, Long> {

    List<Unit> findByPropertyIdOrderByUnitNumber(Long propertyId);

    /** Scoped by property so a unit id from another building cannot be reached through this URL. */
    Optional<Unit> findByIdAndPropertyId(Long id, Long propertyId);

    boolean existsByPropertyId(Long propertyId);

    /**
     * Loads a unit with SELECT ... FOR UPDATE. The database locks the row until the
     * current transaction ends; any other transaction asking for the same lock waits.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select u from Unit u where u.id = :id")
    Optional<Unit> findByIdForUpdate(Long id);
}
