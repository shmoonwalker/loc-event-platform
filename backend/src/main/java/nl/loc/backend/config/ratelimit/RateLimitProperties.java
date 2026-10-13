package nl.loc.backend.config.ratelimit;

import java.time.Duration;
import java.util.Map;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("app.rate-limit")
public record RateLimitProperties(Map<RateLimitPolicy, Limit> policies) {

    /** capacity requests per period, refilled steadily. */
    public record Limit(int capacity, Duration period) {
    }
}
