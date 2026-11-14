package com.leaseledger.storage;

import java.time.Duration;
import java.util.Optional;

/**
 * What the application needs from a file store, and nothing more.
 *
 * <p>Business code depends on this interface, not on the AWS SDK. Production uses
 * {@link S3FileStorage}; most tests use an in-memory fake, so they need no network.
 */
public interface FileStorage {

    /** A URL that lets its holder upload exactly one object, for a limited time. */
    record UploadUrl(String url, long expiresInSeconds) {
    }

    record StoredFile(long sizeBytes, String contentType) {
    }

    /**
     * Creates a temporary URL to which a browser can HTTP PUT a file directly. The upload
     * must send the same Content-Type that was given here, or the store rejects it.
     */
    UploadUrl createUploadUrl(String key, String contentType, Duration validFor);

    /** Creates a temporary URL from which the file can be downloaded. */
    String createDownloadUrl(String key, Duration validFor);

    /** Looks up a file's metadata without downloading it. Empty if nothing is stored at the key. */
    Optional<StoredFile> find(String key);

    void delete(String key);
}
