package nl.loc.data.storage;

import org.springframework.util.Assert;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;

/** Cloudflare R2 storage through its S3-compatible API. */
public class S3RawObjectStore implements RawObjectStore {

    private static final Logger log = LoggerFactory.getLogger(S3RawObjectStore.class);

    private final S3Client client;
    private final String bucket;

    public S3RawObjectStore(S3Client client, String bucket) {
        this.client = client;
        this.bucket = bucket;
    }

    @Override
    public void put(String key, byte[] payload, String contentType) {
        Assert.hasText(key, "Object key must not be blank");
        Assert.notNull(payload, "Payload must not be null");
        Assert.hasText(contentType, "Content type must not be blank");

        client.putObject(PutObjectRequest.builder()
                .bucket(bucket)
                .key(key)
                .contentType(contentType)
                .ifNoneMatch("*")
                .build(), RequestBody.fromBytes(payload));
        log.info("Stored raw object key={} bytes={} contentType={}", key, payload.length, contentType);
    }

    @Override
    public byte[] get(String key) {
        Assert.hasText(key, "Object key must not be blank");
        byte[] payload = client.getObjectAsBytes(GetObjectRequest.builder()
                .bucket(bucket)
                .key(key)
                .build()).asByteArray();
        log.info("Read raw object key={} bytes={}", key, payload.length);
        return payload;
    }
}
