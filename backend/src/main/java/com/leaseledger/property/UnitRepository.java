package com.leaseledger.property;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface UnitRepository extends JpaRepository<Unit, Long> {

    List<Unit> findByPropertyIdOrderByUnitNumber(Long propertyId);

    /** Scoped by property so a unit id from another building cannot be reached through this URL. */
    Optional<Unit> findByIdAndPropertyId(Long id, Long propertyId);

    boolean existsByPropertyId(Long propertyId);
}
