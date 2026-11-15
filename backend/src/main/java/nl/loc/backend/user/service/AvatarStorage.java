package nl.loc.backend.user.service;

import jakarta.annotation.PreDestroy;
import java.net.URI;
import java.time.Duration;
import lombok.extern.slf4j.Slf4j;
import nl.loc.backend.user.model.AvatarPreset;
import nl.loc.backend.user.model.UserAccount;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.core.checksums.RequestChecksumCalculation;
import software.amazon.awssdk.core.exception.SdkException;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;

/**
 * Uploaded avatars in a public Cloudflare R2 bucket, through its S3-compatible API.
 * Browsers load them from the bucket's public domain, never through the API.
 * Without settings (local development) uploads are turned off; presets keep working.
 */
@Slf4j
@Component
public class AvatarStorage {

    private final S3Client client;
    private final String bucket;
    private final String publicUrl;

    public AvatarStorage(@Value("${app.avatar.storage.endpoint:}") String endpoint,
                         @Value("${app.avatar.storage.region:auto}") String region,
                         @Value("${app.avatar.storage.access-key:}") String accessKey,
                         @Value("${app.avatar.storage.secret-key:}") String secretKey,
                         @Value("${app.avatar.storage.bucket:}") String bucket,
                         @Value("${app.avatar.storage.public-url:}") String publicUrl) {
        this.bucket = bucket;
        this.publicUrl = publicUrl.endsWith("/") ? publicUrl.substring(0, publicUrl.length() - 1) : publicUrl;
        if (endpoint.isBlank() || accessKey.isBlank() || secretKey.isBlank() || bucket.isBlank() || publicUrl.isBlank()) {
            log.warn("Avatar storage is not configured; avatar uploads are turned off");
            this.client = null;
            return;
        }
        this.client = S3Client.builder()
                .endpointOverride(URI.create(endpoint))
                .region(Region.of(region))
                .credentialsProvider(StaticCredentialsProvider.create(AwsBasicCredentials.create(accessKey, secretKey)))
                .forcePathStyle(true)
                // R2 rejects the newer default checksums on some calls
                .requestChecksumCalculation(RequestChecksumCalculation.WHEN_REQUIRED)
                .overrideConfiguration(config -> config
                        .apiCallTimeout(Duration.ofSeconds(20))
                        .apiCallAttemptTimeout(Duration.ofSeconds(10)))
                .build();
    }

    public String avatarUrl(UserAccount account) {
        return avatarUrl(account.getAvatarKey(), account.getAvatarPreset());
    }

    public String avatarUrl(String avatarKey, AvatarPreset preset) {
        return avatarKey == null ? preset.url() : publicUrl + "/" + avatarKey;
    }

    public void put(String key, byte[] jpeg) {
        if (client == null) {
            throw new AvatarStorageUnavailableException();
        }
        client.putObject(PutObjectRequest.builder()
                        .bucket(bucket)
                        .key(key)
                        .contentType("image/jpeg")
                        // every upload gets a new key, so the object never changes
                        .cacheControl("public, max-age=31536000, immutable")
                        .build(),
                RequestBody.fromBytes(jpeg));
    }

    // best effort: a leftover object only costs storage, so a failure must not fail the request
    public void delete(String key) {
        if (key == null || client == null) {
            return;
        }
        try {
            client.deleteObject(DeleteObjectRequest.builder().bucket(bucket).key(key).build());
        } catch (SdkException ex) {
            log.warn("Could not delete avatar object {}: {}", key, ex.toString());
        }
    }

    @PreDestroy
    void close() {
        if (client != null) {
            client.close();
        }
    }
}
