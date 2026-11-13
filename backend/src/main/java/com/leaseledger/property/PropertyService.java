package com.leaseledger.property;

import com.leaseledger.common.ConflictException;
import com.leaseledger.common.NotFoundException;
import com.leaseledger.common.PageResponse;
import com.leaseledger.property.PropertyDtos.PropertyRequest;
import com.leaseledger.property.PropertyDtos.PropertyResponse;
import com.leaseledger.property.PropertyDtos.PropertySummary;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Every method takes the caller's id and only ever touches that manager's properties.
 * The id comes from the verified token, never from the request body or URL.
 */
@Service
@Transactional
public class PropertyService {

    private static final int MAX_PAGE_SIZE = 100;

    private final PropertyRepository properties;
    private final UnitRepository units;

    public PropertyService(PropertyRepository properties, UnitRepository units) {
        this.properties = properties;
        this.units = units;
    }

    public PropertyResponse create(Long managerId, PropertyRequest request) {
        Property property = new Property(
                managerId, request.name().trim(), request.address().trim(), request.city().trim());
        return PropertyResponse.from(properties.save(property));
    }

    @Transactional(readOnly = true)
    public PageResponse<PropertySummary> list(Long managerId, int page, int size) {
        // Clamp instead of trusting the client: ?size=1000000 must not load a million rows.
        var pageable = PageRequest.of(Math.max(page, 0), Math.min(Math.max(size, 1), MAX_PAGE_SIZE));
        return PageResponse.from(properties.findSummariesByManagerId(managerId, pageable));
    }

    @Transactional(readOnly = true)
    public PropertyResponse get(Long managerId, Long propertyId) {
        return PropertyResponse.from(requireOwned(managerId, propertyId));
    }

    public PropertyResponse update(Long managerId, Long propertyId, PropertyRequest request) {
        Property property = requireOwned(managerId, propertyId);
        // No save() call: the entity is "managed" inside this transaction, so Hibernate
        // notices the changed fields and writes an UPDATE at commit (dirty checking).
        property.update(request.name().trim(), request.address().trim(), request.city().trim());
        return PropertyResponse.from(property);
    }

    public void delete(Long managerId, Long propertyId) {
        Property property = requireOwned(managerId, propertyId);
        if (units.existsByPropertyId(propertyId)) {
            throw new ConflictException("Remove the property's units before deleting it");
        }
        properties.delete(property);
    }

    /**
     * Loads a property only if it belongs to this manager. Used by UnitService too.
     * Someone else's property gives 404, not 403, so ids cannot be probed to learn what exists.
     */
    Property requireOwned(Long managerId, Long propertyId) {
        return properties.findByIdAndManagerId(propertyId, managerId)
                .orElseThrow(() -> new NotFoundException("Property not found"));
    }
}
