package nl.loc.backend.photo.service;

import jakarta.annotation.PreDestroy;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.URI;
import java.time.Duration;
import java.util.Optional;
import java.util.UUID;
import lombok.extern.slf4j.Slf4j;
import nl.loc.backend.photo.model.PhotoVariant;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.core.ResponseInputStream;
import software.amazon.awssdk.core.checksums.RequestChecksumCalculation;
import software.amazon.awssdk.core.exception.SdkException;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectResponse;
import software.amazon.awssdk.services.s3.model.NoSuchKeyException;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;

/**
 * Event photos in Cloudflare R2, through its S3-compatible API. Only the card and full JPEGs are stored.
 * When the event-photo key or bucket is unset, the avatar bucket and credentials are used.
 * Without any of those settings, uploads are turned off.
 */
@Slf4j
@Component
public class EventPhotoStorage {

    private final S3Client client;
    private final String bucket;

    public EventPhotoStorage(@Value("${app.event-photo.storage.endpoint:}") String endpoint,
                             @Value("${app.event-photo.storage.region:auto}") String region,
                             @Value("${app.event-photo.storage.access-key:}") String accessKey,
                             @Value("${app.event-photo.storage.secret-key:}") String secretKey,
                             @Value("${app.event-photo.storage.bucket:}") String bucket,
                             @Value("${app.avatar.storage.access-key:}") String avatarAccessKey,
                             @Value("${app.avatar.storage.secret-key:}") String avatarSecretKey,
                             @Value("${app.avatar.storage.bucket:}") String avatarBucket) {
        boolean dedicated = !accessKey.isBlank() && !secretKey.isBlank() && !bucket.isBlank();
        String resolvedKey = dedicated ? accessKey : avatarAccessKey;
        String resolvedSecret = dedicated ? secretKey : avatarSecretKey;
        this.bucket = dedicated ? bucket : avatarBucket;
        if (endpoint.isBlank() || resolvedKey.isBlank() || resolvedSecret.isBlank() || this.bucket.isBlank()) {
            log.warn("Event photo storage is not configured; photo uploads are turned off");
            this.client = null;
            return;
        }
        this.client = S3Client.builder()
                .endpointOverride(URI.create(endpoint))
                .region(Region.of(region))
                .credentialsProvider(StaticCredentialsProvider.create(
                        AwsBasicCredentials.create(resolvedKey, resolvedSecret)))
                .forcePathStyle(true)
                // R2 rejects the newer default checksums on some calls
                .requestChecksumCalculation(RequestChecksumCalculation.WHEN_REQUIRED)
                .overrideConfiguration(config -> config
                        .apiCallTimeout(Duration.ofSeconds(20))
                        .apiCallAttemptTimeout(Duration.ofSeconds(10)))
                .build();
    }

    public static String key(UUID photoId, PhotoVariant variant) {
        return "event-photos/" + photoId + "/" + variant.fileName();
    }

    public boolean acceptsUploads() {
        return client != null;
    }

    /** Bucket actually written to, after the avatar fallback. Visible for tests. */
    public String configuredBucket() {
        return bucket;
    }

    public void put(String key, byte[] jpeg) {
        if (client == null) {
            throw new EventPhotoStorageUnavailableException();
        }
        client.putObject(PutObjectRequest.builder()
                        .bucket(bucket)
                        .key(key)
                        .contentType("image/jpeg")
                        .cacheControl("public, max-age=31536000, immutable")
                        .build(),
                RequestBody.fromBytes(jpeg));
    }

    public Optional<byte[]> get(String key) {
        if (client == null) {
            throw new EventPhotoStorageUnavailableException();
        }
        try (ResponseInputStream<GetObjectResponse> body = client.getObject(GetObjectRequest.builder()
                .bucket(bucket)
                .key(key)
                .build())) {
            return Optional.of(body.readAllBytes());
        } catch (NoSuchKeyException ex) {
            return Optional.empty();
        } catch (IOException ex) {
            throw new UncheckedIOException(ex);
        }
    }

    // best effort: a leftover object only costs storage, so a failure must not fail the request
    public void delete(String key) {
        if (key == null || client == null) {
            return;
        }
        try {
            client.deleteObject(DeleteObjectRequest.builder().bucket(bucket).key(key).build());
        } catch (SdkException ex) {
            log.warn("Could not delete event photo object {}: {}", key, ex.toString());
        }
    }

    @PreDestroy
    void close() {
        if (client != null) {
            client.close();
        }
    }
}
