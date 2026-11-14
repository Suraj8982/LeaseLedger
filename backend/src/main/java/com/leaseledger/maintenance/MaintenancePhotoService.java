package com.leaseledger.maintenance;

import com.leaseledger.common.ConflictException;
import com.leaseledger.maintenance.MaintenanceDtos.PhotoResponse;
import com.leaseledger.maintenance.MaintenanceDtos.UploadUrlResponse;
import com.leaseledger.security.AuthenticatedUser;
import com.leaseledger.storage.FileStorage;
import com.leaseledger.storage.FileStorage.StoredFile;
import com.leaseledger.storage.FileStorage.UploadUrl;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

/**
 * Photo upload in two steps, so image bytes never pass through this server:
 *
 * <ol>
 *   <li>The client asks for an upload URL. We check permissions and return a short-lived
 *       presigned URL for one specific key in the bucket.</li>
 *   <li>The browser PUTs the file straight to S3 using that URL.</li>
 *   <li>The client tells us the upload is done. We check with S3 that the file really
 *       exists and is acceptable, and only then record it in the database.</li>
 * </ol>
 */
@Service
@Transactional
public class MaintenancePhotoService {

    static final int MAX_PHOTOS_PER_REQUEST = 5;
    static final long MAX_PHOTO_BYTES = 5L * 1024 * 1024;
    static final Duration UPLOAD_URL_LIFETIME = Duration.ofMinutes(10);

    /** Allowed image types and the file extension each gets. Everything else is refused. */
    private static final Map<String, String> EXTENSION_BY_CONTENT_TYPE = Map.of(
            "image/jpeg", "jpg",
            "image/png", "png",
            "image/webp", "webp");

    private final MaintenanceService maintenanceService;
    private final MaintenancePhotoRepository photos;
    private final FileStorage storage;
    private final Clock clock;

    public MaintenancePhotoService(MaintenanceService maintenanceService, MaintenancePhotoRepository photos,
                                   FileStorage storage, Clock clock) {
        this.maintenanceService = maintenanceService;
        this.photos = photos;
        this.storage = storage;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public UploadUrlResponse createUploadUrl(AuthenticatedUser caller, Long requestId, String contentType) {
        requireRoomForAnotherPhoto(caller, requestId);
        String extension = EXTENSION_BY_CONTENT_TYPE.get(contentType);
        if (extension == null) {
            throw badRequest("Photos must be JPEG, PNG or WebP");
        }
        // The server invents the key. A client-supplied file name is never used, so it
        // cannot contain "../", overwrite someone else's file, or collide with another upload.
        String key = keyPrefix(requestId) + UUID.randomUUID() + "." + extension;
        UploadUrl upload = storage.createUploadUrl(key, contentType, UPLOAD_URL_LIFETIME);
        return new UploadUrlResponse(key, upload.url(), contentType, upload.expiresInSeconds());
    }

    public PhotoResponse attach(AuthenticatedUser caller, Long requestId, String objectKey) {
        MaintenanceRequest request = requireRoomForAnotherPhoto(caller, requestId);

        // Never trust what the client says it uploaded. Three checks follow.
        // 1. The key must be one we issued for this request, not any object in the bucket.
        if (!objectKey.startsWith(keyPrefix(requestId))) {
            throw badRequest("This file does not belong to this maintenance request");
        }
        if (photos.existsByObjectKey(objectKey)) {
            throw new ConflictException("This photo is already attached");
        }
        // 2. The file must actually be in storage: ask S3, do not take the client's word.
        StoredFile file = storage.find(objectKey)
                .orElseThrow(() -> badRequest("No uploaded file found. Upload it before attaching."));
        // 3. A presigned PUT URL cannot limit size by itself, so the limit is enforced here.
        if (file.sizeBytes() > MAX_PHOTO_BYTES) {
            storage.delete(objectKey);
            throw badRequest("Photos must be 5 MB or smaller");
        }

        MaintenancePhoto saved = photos.save(new MaintenancePhoto(
                request, objectKey, file.contentType(), file.sizeBytes(), Instant.now(clock)));
        return new PhotoResponse(saved.getId(), saved.getContentType(), saved.getSizeBytes(),
                storage.createDownloadUrl(objectKey, MaintenanceService.VIEW_URL_LIFETIME));
    }

    private MaintenanceRequest requireRoomForAnotherPhoto(AuthenticatedUser caller, Long requestId) {
        MaintenanceRequest request = maintenanceService.requireParticipant(caller, requestId);
        if (!request.getStatus().isActive()) {
            throw new ConflictException("Photos cannot be added to a request that is " + request.getStatus());
        }
        if (photos.countByRequestId(requestId) >= MAX_PHOTOS_PER_REQUEST) {
            throw new ConflictException("A request can have at most " + MAX_PHOTOS_PER_REQUEST + " photos");
        }
        return request;
    }

    private static String keyPrefix(Long requestId) {
        return "maintenance/" + requestId + "/";
    }

    private static ResponseStatusException badRequest(String message) {
        return new ResponseStatusException(HttpStatus.BAD_REQUEST, message);
    }
}
