package com.leaseledger.lease;

import com.leaseledger.common.PageResponse;
import com.leaseledger.lease.LeaseDtos.CreateLeaseRequest;
import com.leaseledger.lease.LeaseDtos.LeaseDetailResponse;
import com.leaseledger.lease.LeaseDtos.LeaseResponse;
import com.leaseledger.security.AuthenticatedUser;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/leases")
public class LeaseController {

    private final LeaseService leaseService;

    public LeaseController(LeaseService leaseService) {
        this.leaseService = leaseService;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasRole('MANAGER')")
    public LeaseDetailResponse create(@AuthenticationPrincipal AuthenticatedUser caller,
                                      @Valid @RequestBody CreateLeaseRequest request) {
        return leaseService.create(caller.id(), request);
    }

    @GetMapping
    @PreAuthorize("hasAnyRole('MANAGER', 'TENANT')")
    public PageResponse<LeaseResponse> list(@AuthenticationPrincipal AuthenticatedUser caller,
                                            @RequestParam(defaultValue = "0") int page,
                                            @RequestParam(defaultValue = "20") int size) {
        return leaseService.list(caller, page, size);
    }

    @GetMapping("/{leaseId}")
    @PreAuthorize("hasAnyRole('MANAGER', 'TENANT')")
    public LeaseDetailResponse get(@AuthenticationPrincipal AuthenticatedUser caller, @PathVariable Long leaseId) {
        return leaseService.get(caller, leaseId);
    }

    // POST, not DELETE: the lease is not removed, it changes state and keeps its history.
    @PostMapping("/{leaseId}/terminate")
    @PreAuthorize("hasRole('MANAGER')")
    public LeaseDetailResponse terminate(@AuthenticationPrincipal AuthenticatedUser caller,
                                         @PathVariable Long leaseId) {
        return leaseService.terminate(caller.id(), leaseId);
    }
}
