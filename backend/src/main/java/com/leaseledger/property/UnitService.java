package com.leaseledger.property;

import com.leaseledger.common.ConflictException;
import com.leaseledger.common.NotFoundException;
import com.leaseledger.property.PropertyDtos.UnitRequest;
import com.leaseledger.property.PropertyDtos.UnitResponse;
import java.util.List;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
@Transactional
public class UnitService {

    private final PropertyService propertyService;
    private final UnitRepository units;

    public UnitService(PropertyService propertyService, UnitRepository units) {
        this.propertyService = propertyService;
        this.units = units;
    }

    public UnitResponse add(Long managerId, Long propertyId, UnitRequest request) {
        Property property = propertyService.requireOwned(managerId, propertyId);
        Unit unit = new Unit(property, request.unitNumber().trim(), request.bedrooms(),
                request.monthlyRent(), requestedStatus(request));
        return UnitResponse.from(saveCheckingUnitNumber(unit));
    }

    @Transactional(readOnly = true)
    public List<UnitResponse> list(Long managerId, Long propertyId) {
        propertyService.requireOwned(managerId, propertyId);
        return units.findByPropertyIdOrderByUnitNumber(propertyId).stream().map(UnitResponse::from).toList();
    }

    public UnitResponse update(Long managerId, Long propertyId, Long unitId, UnitRequest request) {
        Unit unit = requireUnit(managerId, propertyId, unitId);
        UnitStatus status = request.status() == null ? unit.getStatus() : requestedStatus(request);
        if (unit.getStatus() == UnitStatus.OCCUPIED && status != UnitStatus.OCCUPIED) {
            throw new ConflictException("An occupied unit's status changes only when its lease ends");
        }
        unit.update(request.unitNumber().trim(), request.bedrooms(), request.monthlyRent(), status);
        return UnitResponse.from(saveCheckingUnitNumber(unit));
    }

    public void delete(Long managerId, Long propertyId, Long unitId) {
        Unit unit = requireUnit(managerId, propertyId, unitId);
        if (unit.getStatus() == UnitStatus.OCCUPIED) {
            throw new ConflictException("An occupied unit cannot be deleted");
        }
        units.delete(unit);
    }

    private Unit requireUnit(Long managerId, Long propertyId, Long unitId) {
        // Ownership first: is this the caller's property? Then: is the unit inside it?
        propertyService.requireOwned(managerId, propertyId);
        return units.findByIdAndPropertyId(unitId, propertyId)
                .orElseThrow(() -> new NotFoundException("Unit not found"));
    }

    /** Managers may mark a unit VACANT or UNAVAILABLE. OCCUPIED is reserved for the lease module. */
    private static UnitStatus requestedStatus(UnitRequest request) {
        if (request.status() == UnitStatus.OCCUPIED) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "status must be VACANT or UNAVAILABLE");
        }
        return request.status() == null ? UnitStatus.VACANT : request.status();
    }

    private Unit saveCheckingUnitNumber(Unit unit) {
        try {
            // Flush now so a duplicate unit number fails here, where we can explain it,
            // rather than at commit time after this method has returned.
            return units.saveAndFlush(unit);
        } catch (DataIntegrityViolationException e) {
            throw new ConflictException("This property already has a unit with that number");
        }
    }
}
