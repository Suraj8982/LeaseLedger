package com.leaseledger.lease;

import com.leaseledger.common.PageResponse;
import com.leaseledger.lease.LeaseDtos.InvoiceResponse;
import com.leaseledger.lease.LeaseDtos.MarkPaidRequest;
import com.leaseledger.security.AuthenticatedUser;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/invoices")
public class InvoiceController {

    private final InvoiceService invoiceService;

    public InvoiceController(InvoiceService invoiceService) {
        this.invoiceService = invoiceService;
    }

    @GetMapping
    @PreAuthorize("hasAnyRole('MANAGER', 'TENANT')")
    public PageResponse<InvoiceResponse> list(@AuthenticationPrincipal AuthenticatedUser caller,
                                              @RequestParam(defaultValue = "ALL") InvoiceFilter filter,
                                              @RequestParam(defaultValue = "0") int page,
                                              @RequestParam(defaultValue = "20") int size) {
        return invoiceService.list(caller, filter, page, size);
    }

    @PostMapping("/{invoiceId}/mark-paid")
    @PreAuthorize("hasRole('MANAGER')")
    public InvoiceResponse markPaid(@AuthenticationPrincipal AuthenticatedUser caller,
                                    @PathVariable Long invoiceId,
                                    @Valid @RequestBody(required = false) MarkPaidRequest request) {
        return invoiceService.markPaid(caller.id(), invoiceId, request == null ? null : request.reference());
    }
}
