package com.leaseledger.maintenance;

import com.leaseledger.common.ConflictException;
import com.leaseledger.common.NotFoundException;
import com.leaseledger.common.PageResponse;
import com.leaseledger.common.Paging;
import com.leaseledger.lease.Lease;
import com.leaseledger.lease.LeaseRepository;
import com.leaseledger.lease.LeaseStatus;
import com.leaseledger.maintenance.MaintenanceDtos.ChangeStatusRequest;
import com.leaseledger.maintenance.MaintenanceDtos.CreateMaintenanceRequest;
import com.leaseledger.maintenance.MaintenanceDtos.MaintenanceDetailResponse;
import com.leaseledger.maintenance.MaintenanceDtos.MaintenanceResponse;
import com.leaseledger.maintenance.MaintenanceDtos.PhotoResponse;
import com.leaseledger.security.AuthenticatedUser;
import com.leaseledger.storage.FileStorage;
import com.leaseledger.user.Role;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Set;
import org.springframework.data.domain.Page;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
public class MaintenanceService {

    static final Duration VIEW_URL_LIFETIME = Duration.ofMinutes(10);

    /** Which statuses each role is allowed to set. The workflow order is in MaintenanceStatus. */
    private static final Set<MaintenanceStatus> MANAGER_MAY_SET =
            Set.of(MaintenanceStatus.IN_PROGRESS, MaintenanceStatus.RESOLVED);
    private static final Set<MaintenanceStatus> TENANT_MAY_SET = Set.of(MaintenanceStatus.CANCELLED);

    private final MaintenanceRequestRepository requests;
    private final MaintenancePhotoRepository photos;
    private final LeaseRepository leases;
    private final FileStorage storage;
    private final Clock clock;

    public MaintenanceService(MaintenanceRequestRepository requests, MaintenancePhotoRepository photos,
                              LeaseRepository leases, FileStorage storage, Clock clock) {
        this.requests = requests;
        this.photos = photos;
        this.leases = leases;
        this.storage = storage;
        this.clock = clock;
    }

    /** A tenant reports a problem in a unit they currently rent. */
    public MaintenanceDetailResponse create(Long tenantId, CreateMaintenanceRequest request) {
        Lease lease = leases.findWithDetailsById(request.leaseId())
                .filter(l -> l.getTenant().getId().equals(tenantId))
                .orElseThrow(() -> new NotFoundException("Lease not found"));
        if (lease.getStatus() != LeaseStatus.ACTIVE) {
            throw new ConflictException("Maintenance can only be requested on an active lease");
        }
        // The unit and manager come from the lease, not from the request body, so a
        // tenant cannot file a request against a unit they do not rent.
        MaintenanceRequest saved = requests.save(new MaintenanceRequest(
                lease.getUnit(), lease.getTenant(), lease.getManagerId(),
                request.title().trim(), request.description().trim(),
                request.priority() == null ? MaintenancePriority.MEDIUM : request.priority(),
                Instant.now(clock)));
        return new MaintenanceDetailResponse(MaintenanceResponse.from(saved), List.of());
    }

    @Transactional(readOnly = true)
    public PageResponse<MaintenanceResponse> list(AuthenticatedUser caller, MaintenanceStatus status,
                                                  int page, int size) {
        var pageable = Paging.of(page, size);
        Page<MaintenanceRequest> result = caller.role() == Role.MANAGER
                ? requests.findByManager(caller.id(), status, pageable)
                : requests.findByTenant(caller.id(), status, pageable);
        return PageResponse.from(result.map(MaintenanceResponse::from));
    }

    @Transactional(readOnly = true)
    public MaintenanceDetailResponse get(AuthenticatedUser caller, Long requestId) {
        MaintenanceRequest request = requireParticipant(caller, requestId);
        List<PhotoResponse> photoResponses = photos.findByRequestIdOrderById(requestId).stream()
                .map(p -> new PhotoResponse(p.getId(), p.getContentType(), p.getSizeBytes(),
                        // The bucket is private. Each response carries fresh short-lived links.
                        storage.createDownloadUrl(p.getObjectKey(), VIEW_URL_LIFETIME)))
                .toList();
        return new MaintenanceDetailResponse(MaintenanceResponse.from(request), photoResponses);
    }

    public MaintenanceResponse changeStatus(AuthenticatedUser caller, Long requestId, ChangeStatusRequest change) {
        MaintenanceRequest request = requireParticipant(caller, requestId);

        // Two separate questions. First: may this role set this status at all? (403)
        Set<MaintenanceStatus> allowedForRole = caller.role() == Role.MANAGER ? MANAGER_MAY_SET : TENANT_MAY_SET;
        if (!allowedForRole.contains(change.status())) {
            throw new AccessDeniedException("Your role cannot set status " + change.status());
        }
        // Second: does the workflow allow this move from where the request is now? (409)
        String note = change.note() == null || change.note().isBlank() ? null : change.note().trim();
        if (!request.moveTo(change.status(), note, Instant.now(clock))) {
            throw new ConflictException("A request that is " + request.getStatus()
                    + " cannot be moved to " + change.status());
        }
        return MaintenanceResponse.from(request);
    }

    /** Loads a request only for its tenant or its manager. Anyone else gets 404. Used by the photo service too. */
    MaintenanceRequest requireParticipant(AuthenticatedUser caller, Long requestId) {
        return requests.findWithDetailsById(requestId)
                .filter(r -> r.getManagerId().equals(caller.id()) || r.getTenant().getId().equals(caller.id()))
                .orElseThrow(() -> new NotFoundException("Maintenance request not found"));
    }
}
