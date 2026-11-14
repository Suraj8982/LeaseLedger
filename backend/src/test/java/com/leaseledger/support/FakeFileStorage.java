package com.leaseledger.support;

import com.leaseledger.storage.FileStorage;
import java.time.Duration;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.context.annotation.Primary;

/**
 * In-memory stand-in for S3, used by the API flow tests so they need no Docker or
 * network. {@code @Primary} makes Spring inject it instead of S3FileStorage.
 * The real S3 code is tested separately in S3FileStorageTest.
 */
@Primary
public class FakeFileStorage implements FileStorage {

    private final Map<String, StoredFile> files = new ConcurrentHashMap<>();

    /** Simulates the browser finishing an upload to a presigned URL. */
    public void simulateUpload(String key, String contentType, long sizeBytes) {
        files.put(key, new StoredFile(sizeBytes, contentType));
    }

    public boolean contains(String key) {
        return files.containsKey(key);
    }

    @Override
    public UploadUrl createUploadUrl(String key, String contentType, Duration validFor) {
        return new UploadUrl("https://fake-storage.test/upload/" + key, validFor.toSeconds());
    }

    @Override
    public String createDownloadUrl(String key, Duration validFor) {
        return "https://fake-storage.test/download/" + key;
    }

    @Override
    public Optional<StoredFile> find(String key) {
        return Optional.ofNullable(files.get(key));
    }

    @Override
    public void delete(String key) {
        files.remove(key);
    }
}
