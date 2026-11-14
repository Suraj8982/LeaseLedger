package com.leaseledger.storage;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Settings under "leaseledger.storage" in application.yml, bound to a typed object.
 *
 * @param bucket   S3 bucket that holds uploaded files
 * @param region   AWS region of the bucket
 * @param endpoint blank for real AWS. Set (for example http://localhost:4566) to talk to
 *                 a local S3 emulator instead.
 */
@ConfigurationProperties("leaseledger.storage")
public record StorageProperties(String bucket, String region, String endpoint) {

    public boolean usesEmulator() {
        return endpoint != null && !endpoint.isBlank();
    }
}
