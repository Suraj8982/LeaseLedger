package com.leaseledger.storage;

import static org.assertj.core.api.Assertions.assertThat;

import com.leaseledger.storage.FileStorage.StoredFile;
import com.leaseledger.storage.FileStorage.UploadUrl;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpRequest.BodyPublishers;
import java.net.http.HttpResponse;
import java.net.http.HttpResponse.BodyHandlers;
import java.time.Duration;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.localstack.LocalStackContainer;
import org.testcontainers.containers.localstack.LocalStackContainer.Service;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;

/**
 * Exercises the real S3 code against a real S3 API. Testcontainers starts LocalStack
 * (an AWS emulator) in Docker before the test and removes it afterwards.
 *
 * <p>No Spring here: the objects are built by hand with the same StorageConfig the
 * application uses. The test is skipped, not failed, on a machine without Docker.
 */
@Testcontainers(disabledWithoutDocker = true)
class S3FileStorageTest {

    private static final String BUCKET = "test-bucket";
    private static final Duration TEN_MINUTES = Duration.ofMinutes(10);

    @Container
    static final LocalStackContainer LOCALSTACK =
            new LocalStackContainer(DockerImageName.parse("localstack/localstack:3.8.1")).withServices(Service.S3);

    private static S3Client s3;
    private static S3Presigner presigner;
    private static FileStorage storage;
    private static final HttpClient HTTP = HttpClient.newHttpClient();

    @BeforeAll
    static void connect() {
        var properties = new StorageProperties(
                BUCKET, LOCALSTACK.getRegion(), LOCALSTACK.getEndpointOverride(Service.S3).toString());
        var config = new StorageConfig();
        var credentials = config.awsCredentials(properties);
        s3 = config.s3Client(properties, credentials);
        presigner = config.s3Presigner(properties, credentials);
        s3.createBucket(b -> b.bucket(BUCKET));
        storage = new S3FileStorage(s3, presigner, properties);
    }

    @AfterAll
    static void close() {
        s3.close();
        presigner.close();
    }

    /** What the browser does: a plain HTTP PUT with no AWS credentials, only the URL. */
    private static HttpResponse<String> browserUpload(UploadUrl upload, String contentType, byte[] content)
            throws Exception {
        return HTTP.send(HttpRequest.newBuilder(URI.create(upload.url()))
                .header("Content-Type", contentType)
                .PUT(BodyPublishers.ofByteArray(content)).build(), BodyHandlers.ofString());
    }

    @Test
    void fileUploadedThroughPresignedUrlCanBeFoundDownloadedAndDeleted() throws Exception {
        String key = "maintenance/1/photo.png";
        byte[] image = "pretend these bytes are a PNG".getBytes();

        assertThat(storage.find(key)).isEmpty();

        UploadUrl upload = storage.createUploadUrl(key, "image/png", TEN_MINUTES);
        assertThat(upload.expiresInSeconds()).isEqualTo(600);
        assertThat(upload.url()).contains(BUCKET).contains("photo.png").contains("X-Amz-Signature");

        assertThat(browserUpload(upload, "image/png", image).statusCode()).isEqualTo(200);

        assertThat(storage.find(key)).contains(new StoredFile(image.length, "image/png"));

        HttpResponse<byte[]> download = HTTP.send(
                HttpRequest.newBuilder(URI.create(storage.createDownloadUrl(key, TEN_MINUTES))).GET().build(),
                BodyHandlers.ofByteArray());
        assertThat(download.statusCode()).isEqualTo(200);
        assertThat(download.body()).isEqualTo(image);

        storage.delete(key);
        assertThat(storage.find(key)).isEmpty();
    }

    // Not tested here: that an object cannot be read WITHOUT a signed URL. The emulator
    // does not enforce access control (it serves unsigned requests), so that property
    // can only be checked against a real, private S3 bucket.
}
