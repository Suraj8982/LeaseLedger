package com.leaseledger.storage;

import java.net.URI;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.AwsCredentialsProvider;
import software.amazon.awssdk.auth.credentials.DefaultCredentialsProvider;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.S3Configuration;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;

@Configuration
@EnableConfigurationProperties(StorageProperties.class)
public class StorageConfig {

    /**
     * Where AWS credentials come from.
     *
     * <p>On real AWS: the default chain. On an EC2 instance that resolves to the
     * instance's IAM role, so there are no access keys in this project's code or config
     * at all. Against the local emulator: fixed dummy values, which it accepts.
     */
    @Bean
    AwsCredentialsProvider awsCredentials(StorageProperties properties) {
        return properties.usesEmulator()
                ? StaticCredentialsProvider.create(AwsBasicCredentials.create("test", "test"))
                : DefaultCredentialsProvider.create();
    }

    /** Makes API calls to S3 (used here to check and delete objects). */
    @Bean
    S3Client s3Client(StorageProperties properties, AwsCredentialsProvider credentials) {
        var builder = S3Client.builder()
                .region(Region.of(properties.region()))
                .credentialsProvider(credentials);
        if (properties.usesEmulator()) {
            // Path-style (http://host/bucket/key): the emulator has no per-bucket hostnames.
            builder.endpointOverride(URI.create(properties.endpoint())).forcePathStyle(true);
        }
        return builder.build();
    }

    /** Signs URLs. Makes no network calls: a presigned URL is computed locally from the credentials. */
    @Bean
    S3Presigner s3Presigner(StorageProperties properties, AwsCredentialsProvider credentials) {
        var builder = S3Presigner.builder()
                .region(Region.of(properties.region()))
                .credentialsProvider(credentials);
        if (properties.usesEmulator()) {
            builder.endpointOverride(URI.create(properties.endpoint()))
                    .serviceConfiguration(S3Configuration.builder().pathStyleAccessEnabled(true).build());
        }
        return builder.build();
    }
}
