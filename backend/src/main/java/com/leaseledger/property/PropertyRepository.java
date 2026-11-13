package com.leaseledger.property;

import com.leaseledger.property.PropertyDtos.PropertySummary;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface PropertyRepository extends JpaRepository<Property, Long> {

    /**
     * The ownership check and the lookup in one query: a property that belongs to
     * someone else is indistinguishable from one that does not exist.
     */
    Optional<Property> findByIdAndManagerId(Long id, Long managerId);

    /**
     * One page of a manager's properties with unit counts, in a single SQL statement.
     *
     * <p>The naive version loads the properties and then counts units for each one:
     * 1 query + N more (the "N+1 problem"). Here the database does the counting with
     * a LEFT JOIN and GROUP BY. LEFT, so a property with no units still appears.
     */
    @Query(value = """
            select new com.leaseledger.property.PropertyDtos$PropertySummary(
                p.id, p.name, p.city,
                count(u),
                coalesce(sum(case when u.status = com.leaseledger.property.UnitStatus.VACANT then 1L else 0L end), 0L))
            from Property p
            left join Unit u on u.property = p
            where p.managerId = :managerId
            group by p.id, p.name, p.city
            order by p.name, p.id
            """,
            countQuery = "select count(p) from Property p where p.managerId = :managerId")
    Page<PropertySummary> findSummariesByManagerId(Long managerId, Pageable pageable);
}
