package com.leaseledger.maintenance;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.util.List;

public final class MaintenanceDtos {

    private MaintenanceDtos() {
    }

    public record CreateMaintenanceRequest(
            /** The tenant's lease; identifies which unit the problem is in. */
            @NotNull Long leaseId,
            @NotBlank @Size(max = 120) String title,
            @NotBlank @Size(max = 2000) String description,
            /** Optional. Defaults to MEDIUM. */
            MaintenancePriority priority) {
    }

    public record ChangeStatusRequest(
            @NotNull MaintenanceStatus status,
            @Size(max = 1000) String note) {
    }

    public record MaintenanceResponse(
            Long id,
            String title,
            String description,
            MaintenancePriority priority,
            MaintenanceStatus status,
            String resolutionNote,
            Instant createdAt,
            Instant updatedAt,
            Long unitId,
            String unitNumber,
            String propertyName,
            Long tenantId,
            String tenantName) {

        static MaintenanceResponse from(MaintenanceRequest r) {
            return new MaintenanceResponse(
                    r.getId(), r.getTitle(), r.getDescription(), r.getPriority(), r.getStatus(),
                    r.getResolutionNote(), r.getCreatedAt(), r.getUpdatedAt(),
                    r.getUnit().getId(), r.getUnit().getUnitNumber(), r.getUnit().getProperty().getName(),
                    r.getTenant().getId(), r.getTenant().getFullName());
        }
    }

    public record MaintenanceDetailResponse(MaintenanceResponse request, List<PhotoResponse> photos) {
    }

    public record PhotoResponse(
            Long id,
            String contentType,
            long sizeBytes,
            /** Temporary link to view the image. Expires; fetch the request again for a fresh one. */
            String viewUrl) {
    }

    public record UploadUrlRequest(@NotBlank String contentType) {
    }

    public record UploadUrlResponse(
            /** Send this back in the attach call once the upload has finished. */
            String objectKey,
            /** HTTP PUT the file here, with a Content-Type header equal to contentType. */
            String uploadUrl,
            String contentType,
            long expiresInSeconds) {
    }

    public record AttachPhotoRequest(@NotBlank @Size(max = 255) String objectKey) {
    }
}
