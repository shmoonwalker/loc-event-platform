package nl.loc.backend.config.ratelimit;

import io.github.bucket4j.BucketConfiguration;
import io.github.bucket4j.ConsumptionProbe;
import io.github.bucket4j.distributed.ExpirationAfterWriteStrategy;
import io.github.bucket4j.distributed.proxy.ProxyManager;
import io.github.bucket4j.redis.lettuce.Bucket4jLettuce;
import io.lettuce.core.ClientOptions;
import io.lettuce.core.RedisClient;
import io.lettuce.core.SocketOptions;
import io.lettuce.core.codec.ByteArrayCodec;
import io.lettuce.core.codec.RedisCodec;
import io.lettuce.core.codec.StringCodec;
import jakarta.annotation.PreDestroy;
import java.time.Duration;
import java.util.EnumMap;
import java.util.Locale;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Token-bucket limits shared by every backend instance through Redis.
 * Fails open: when Redis is unreachable the request is allowed and a warning is logged.
 */
@Slf4j
@Component
public class RateLimiter {

    // keep a Redis outage from slowing requests down noticeably
    private static final Duration REDIS_TIMEOUT = Duration.ofMillis(500);

    private final RedisClient redisClient;
    private final Map<RateLimitPolicy, BucketConfiguration> configurations = new EnumMap<>(RateLimitPolicy.class);
    private volatile ProxyManager<String> buckets;

    public RateLimiter(RateLimitProperties properties, @Value("${spring.data.redis.url}") String redisUrl) {
        for (RateLimitPolicy policy : RateLimitPolicy.values()) {
            RateLimitProperties.Limit limit = properties.policies() == null ? null : properties.policies().get(policy);
            if (limit == null) {
                throw new IllegalStateException("Missing app.rate-limit.policies." + policy.name().toLowerCase(Locale.ROOT));
            }
            configurations.put(policy, BucketConfiguration.builder()
                    .addLimit(bandwidth -> bandwidth.capacity(limit.capacity()).refillGreedy(limit.capacity(), limit.period()))
                    .build());
        }
        redisClient = RedisClient.create(redisUrl);
        redisClient.setOptions(ClientOptions.builder()
                .socketOptions(SocketOptions.builder().connectTimeout(REDIS_TIMEOUT).build())
                .build());
    }

    /**
     * Takes one token from the bucket of this policy and key.
     *
     * @throws RateLimitExceededException when the bucket is empty
     */
    public void consume(RateLimitPolicy policy, String key) {
        ConsumptionProbe probe;
        try {
            probe = buckets()
                    .getProxy("rate-limit:" + policy.name().toLowerCase(Locale.ROOT) + ":" + key,
                            () -> configurations.get(policy))
                    .tryConsumeAndReturnRemaining(1);
        } catch (RuntimeException ex) {
            log.warn("Rate limiter unavailable, allowing {} request: {}", policy, ex.toString());
            return;
        }
        if (!probe.isConsumed()) {
            // round up so clients never retry a moment too early
            long seconds = Math.max(1, Duration.ofNanos(probe.getNanosToWaitForRefill()).plusNanos(999_999_999).toSeconds());
            throw new RateLimitExceededException(seconds);
        }
    }

    // connect on first use so the application also starts while Redis is down
    private ProxyManager<String> buckets() {
        ProxyManager<String> current = buckets;
        if (current != null) {
            return current;
        }
        synchronized (this) {
            if (buckets == null) {
                buckets = Bucket4jLettuce.casBasedBuilder(
                                redisClient.connect(RedisCodec.of(StringCodec.UTF8, ByteArrayCodec.INSTANCE)))
                        // a key disappears once its bucket would be full again
                        .expirationAfterWrite(ExpirationAfterWriteStrategy.basedOnTimeForRefillingBucketUpToMax(Duration.ZERO))
                        .requestTimeout(REDIS_TIMEOUT)
                        .build();
            }
            return buckets;
        }
    }

    @PreDestroy
    void shutdown() {
        redisClient.shutdown();
    }
}
