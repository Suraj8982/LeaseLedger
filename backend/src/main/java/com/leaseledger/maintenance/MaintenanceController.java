package com.leaseledger.maintenance;

import com.leaseledger.common.PageResponse;
import com.leaseledger.maintenance.MaintenanceDtos.AttachPhotoRequest;
import com.leaseledger.maintenance.MaintenanceDtos.ChangeStatusRequest;
import com.leaseledger.maintenance.MaintenanceDtos.CreateMaintenanceRequest;
import com.leaseledger.maintenance.MaintenanceDtos.MaintenanceDetailResponse;
import com.leaseledger.maintenance.MaintenanceDtos.MaintenanceResponse;
import com.leaseledger.maintenance.MaintenanceDtos.PhotoResponse;
import com.leaseledger.maintenance.MaintenanceDtos.UploadUrlRequest;
import com.leaseledger.maintenance.MaintenanceDtos.UploadUrlResponse;
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
@RequestMapping("/api/maintenance-requests")
@PreAuthorize("hasAnyRole('MANAGER', 'TENANT')")
public class MaintenanceController {

    private final MaintenanceService maintenanceService;
    private final MaintenancePhotoService photoService;

    public MaintenanceController(MaintenanceService maintenanceService, MaintenancePhotoService photoService) {
        this.maintenanceService = maintenanceService;
        this.photoService = photoService;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasRole('TENANT')") // overrides the class-level rule for this method
    public MaintenanceDetailResponse create(@AuthenticationPrincipal AuthenticatedUser caller,
                                            @Valid @RequestBody CreateMaintenanceRequest request) {
        return maintenanceService.create(caller.id(), request);
    }

    @GetMapping
    public PageResponse<MaintenanceResponse> list(@AuthenticationPrincipal AuthenticatedUser caller,
                                                  @RequestParam(required = false) MaintenanceStatus status,
                                                  @RequestParam(defaultValue = "0") int page,
                                                  @RequestParam(defaultValue = "20") int size) {
        return maintenanceService.list(caller, status, page, size);
    }

    @GetMapping("/{requestId}")
    public MaintenanceDetailResponse get(@AuthenticationPrincipal AuthenticatedUser caller,
                                         @PathVariable Long requestId) {
        return maintenanceService.get(caller, requestId);
    }

    @PostMapping("/{requestId}/status")
    public MaintenanceResponse changeStatus(@AuthenticationPrincipal AuthenticatedUser caller,
                                            @PathVariable Long requestId,
                                            @Valid @RequestBody ChangeStatusRequest request) {
        return maintenanceService.changeStatus(caller, requestId, request);
    }

    /** Step 1 of a photo upload: get a URL to upload to. */
    @PostMapping("/{requestId}/photos/upload-url")
    public UploadUrlResponse createUploadUrl(@AuthenticationPrincipal AuthenticatedUser caller,
                                             @PathVariable Long requestId,
                                             @Valid @RequestBody UploadUrlRequest request) {
        return photoService.createUploadUrl(caller, requestId, request.contentType());
    }

    /** Step 3 of a photo upload: record a file that has been uploaded. */
    @PostMapping("/{requestId}/photos")
    @ResponseStatus(HttpStatus.CREATED)
    public PhotoResponse attachPhoto(@AuthenticationPrincipal AuthenticatedUser caller,
                                     @PathVariable Long requestId,
                                     @Valid @RequestBody AttachPhotoRequest request) {
        return photoService.attach(caller, requestId, request.objectKey());
    }
}
