package com.leaseledger.storage;

import java.time.Duration;
import java.util.Optional;
import org.springframework.stereotype.Component;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.NoSuchKeyException;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;

@Component
public class S3FileStorage implements FileStorage {

    private final S3Client s3;
    private final S3Presigner presigner;
    private final String bucket;

    public S3FileStorage(S3Client s3, S3Presigner presigner, StorageProperties properties) {
        this.s3 = s3;
        this.presigner = presigner;
        this.bucket = properties.bucket();
    }

    @Override
    public UploadUrl createUploadUrl(String key, String contentType, Duration validFor) {
        // The signature covers the bucket, key, Content-Type and expiry. Change any of
        // them and S3 rejects the request, so this URL can only create this one object.
        var presigned = presigner.presignPutObject(p -> p
                .signatureDuration(validFor)
                .putObjectRequest(put -> put.bucket(bucket).key(key).contentType(contentType)));
        return new UploadUrl(presigned.url().toString(), validFor.toSeconds());
    }

    @Override
    public String createDownloadUrl(String key, Duration validFor) {
        return presigner.presignGetObject(p -> p
                        .signatureDuration(validFor)
                        .getObjectRequest(get -> get.bucket(bucket).key(key)))
                .url().toString();
    }

    @Override
    public Optional<StoredFile> find(String key) {
        try {
            // HEAD returns the object's metadata without transferring its content.
            var head = s3.headObject(h -> h.bucket(bucket).key(key));
            return Optional.of(new StoredFile(head.contentLength(), head.contentType()));
        } catch (NoSuchKeyException e) {
            return Optional.empty();
        }
    }

    @Override
    public void delete(String key) {
        s3.deleteObject(d -> d.bucket(bucket).key(key));
    }
}
