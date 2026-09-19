package nl.loc.data.storage;

import java.net.URI;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.core.checksums.RequestChecksumCalculation;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;

@Configuration(proxyBeanMethods = false)
@ConditionalOnProperty(name = "loc.object-storage.enabled", havingValue = "true")
public class ObjectStorageConfiguration {

    @Bean(destroyMethod = "close")
    public S3Client r2Client(
            @Value("${loc.object-storage.endpoint:}") String endpoint,
            @Value("${loc.object-storage.region:auto}") String region,
            @Value("${loc.object-storage.access-key-id:}") String accessKey,
            @Value("${loc.object-storage.secret-access-key:}") String secretKey,
            @Value("${loc.object-storage.bucket:}") String bucket) {
        List<String> missing = new ArrayList<>();
        if (endpoint.isBlank()) missing.add("R2_ENDPOINT");
        if (region.isBlank()) missing.add("R2_REGION");
        if (accessKey.isBlank()) missing.add("R2_ACCESS_KEY");
        if (secretKey.isBlank()) missing.add("R2_SECRET_KEY");
        if (bucket.isBlank()) missing.add("R2_BUCKET");
        if (!missing.isEmpty()) {
            throw new IllegalStateException("R2 is enabled but settings are missing: "
                    + String.join(", ", missing));
        }

        URI endpointUri;
        try {
            endpointUri = URI.create(endpoint);
        } catch (IllegalArgumentException exception) {
            throw new IllegalArgumentException("R2_ENDPOINT must be a valid HTTPS URL");
        }
        if (!"https".equalsIgnoreCase(endpointUri.getScheme())
                || endpointUri.getHost() == null
                || endpointUri.getUserInfo() != null
                || endpointUri.getQuery() != null
                || endpointUri.getFragment() != null
                || !(endpointUri.getPath().isEmpty() || endpointUri.getPath().equals("/"))) {
            throw new IllegalArgumentException("R2_ENDPOINT must be an HTTPS endpoint without a bucket path");
        }

        return S3Client.builder()
                .endpointOverride(endpointUri)
                .region(Region.of(region))
                .credentialsProvider(StaticCredentialsProvider.create(
                        AwsBasicCredentials.create(accessKey, secretKey)))
                .forcePathStyle(true)
                .requestChecksumCalculation(RequestChecksumCalculation.WHEN_REQUIRED)
                .overrideConfiguration(config -> config
                        .apiCallTimeout(Duration.ofSeconds(60))
                        .apiCallAttemptTimeout(Duration.ofSeconds(30)))
                .build();
    }

    @Bean
    public RawObjectStore rawObjectStore(
            S3Client r2Client,
            @Value("${loc.object-storage.bucket}") String bucket) {
        return new S3RawObjectStore(r2Client, bucket);
    }
}
