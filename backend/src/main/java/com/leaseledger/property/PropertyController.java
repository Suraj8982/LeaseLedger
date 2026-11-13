package com.leaseledger.property;

import com.leaseledger.common.PageResponse;
import com.leaseledger.property.PropertyDtos.PropertyRequest;
import com.leaseledger.property.PropertyDtos.PropertyResponse;
import com.leaseledger.property.PropertyDtos.PropertySummary;
import com.leaseledger.security.AuthenticatedUser;
import jakarta.validation.Valid;
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
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/properties")
@PreAuthorize("hasRole('MANAGER')") // role check: tenants get 403 on every method here
public class PropertyController {

    private final PropertyService propertyService;

    public PropertyController(PropertyService propertyService) {
        this.propertyService = propertyService;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public PropertyResponse create(@AuthenticationPrincipal AuthenticatedUser caller,
                                   @Valid @RequestBody PropertyRequest request) {
        return propertyService.create(caller.id(), request);
    }

    @GetMapping
    public PageResponse<PropertySummary> list(@AuthenticationPrincipal AuthenticatedUser caller,
                                              @RequestParam(defaultValue = "0") int page,
                                              @RequestParam(defaultValue = "20") int size) {
        return propertyService.list(caller.id(), page, size);
    }

    @GetMapping("/{propertyId}")
    public PropertyResponse get(@AuthenticationPrincipal AuthenticatedUser caller,
                                @PathVariable Long propertyId) {
        return propertyService.get(caller.id(), propertyId);
    }

    @PutMapping("/{propertyId}")
    public PropertyResponse update(@AuthenticationPrincipal AuthenticatedUser caller,
                                   @PathVariable Long propertyId,
                                   @Valid @RequestBody PropertyRequest request) {
        return propertyService.update(caller.id(), propertyId, request);
    }

    @DeleteMapping("/{propertyId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@AuthenticationPrincipal AuthenticatedUser caller, @PathVariable Long propertyId) {
        propertyService.delete(caller.id(), propertyId);
    }
}
