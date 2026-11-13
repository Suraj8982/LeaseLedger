package com.leaseledger.property;

import com.leaseledger.property.PropertyDtos.UnitRequest;
import com.leaseledger.property.PropertyDtos.UnitResponse;
import com.leaseledger.security.AuthenticatedUser;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** Units are always addressed through their property: /api/properties/{propertyId}/units. */
@RestController
@RequestMapping("/api/properties/{propertyId}/units")
@PreAuthorize("hasRole('MANAGER')")
public class UnitController {

    private final UnitService unitService;

    public UnitController(UnitService unitService) {
        this.unitService = unitService;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public UnitResponse add(@AuthenticationPrincipal AuthenticatedUser caller,
                            @PathVariable Long propertyId,
                            @Valid @RequestBody UnitRequest request) {
        return unitService.add(caller.id(), propertyId, request);
    }

    @GetMapping
    public List<UnitResponse> list(@AuthenticationPrincipal AuthenticatedUser caller,
                                   @PathVariable Long propertyId) {
        return unitService.list(caller.id(), propertyId);
    }

    @PutMapping("/{unitId}")
    public UnitResponse update(@AuthenticationPrincipal AuthenticatedUser caller,
                               @PathVariable Long propertyId,
                               @PathVariable Long unitId,
                               @Valid @RequestBody UnitRequest request) {
        return unitService.update(caller.id(), propertyId, unitId, request);
    }

    @DeleteMapping("/{unitId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@AuthenticationPrincipal AuthenticatedUser caller,
                       @PathVariable Long propertyId,
                       @PathVariable Long unitId) {
        unitService.delete(caller.id(), propertyId, unitId);
    }
}
