package nl.loc.data.weather;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Finds slots whose forecast is due and queues one message each.
 *
 * The scan never calls Open-Meteo itself, and never reads slots outside the forecast window,
 * so the catalog can grow without the daily work growing with it.
 */
@Slf4j
@Component
@ConditionalOnProperty(name = "loc.weather.enabled", havingValue = "true")
public class WeatherRefreshScan {

    private final WeatherRepository weatherRepository;
    private final RabbitTemplate rabbitTemplate;
    private final int scanLimit;
    private final Duration claimTtl;

    public WeatherRefreshScan(WeatherRepository weatherRepository,
                              RabbitTemplate rabbitTemplate,
                              @Value("${loc.weather.scan-limit}") int scanLimit,
                              @Value("${loc.weather.claim-ttl}") Duration claimTtl) {
        this.weatherRepository = weatherRepository;
        this.rabbitTemplate = rabbitTemplate;
        this.scanLimit = scanLimit;
        this.claimTtl = claimTtl;
    }

    @Scheduled(fixedDelayString = "${loc.weather.scan-interval}",
            initialDelayString = "${loc.weather.scan-initial-delay}")
    public void queueDueRefreshes() {
        long startedAt = System.nanoTime();
        Instant now = Instant.now();
        Instant windowEnd = now.plus(WeatherSchedule.FORECAST_WINDOW);
        String stage = "clear-stale-forecasts";

        try {
            int stale = weatherRepository.deleteStale();
            if (stale > 0) {
                log.info("Cleared forecasts that no longer match their slot count={}", stale);
            }

            stage = "find-due-slots";
            List<WeatherTarget> due = weatherRepository.findDue(now, windowEnd, scanLimit);
            if (due.isEmpty()) {
                log.debug("No weather refreshes due windowEnd={}", windowEnd);
                return;
            }

            stage = "queue-refreshes";
            Instant claimUntil = now.plus(claimTtl);
            int queued = 0;
            for (WeatherTarget target : due) {
                try {
                    weatherRepository.claim(target, claimUntil);
                    rabbitTemplate.convertAndSend(WeatherMessaging.EXCHANGE, WeatherMessaging.REFRESH_ROUTING_KEY,
                            Long.toString(target.timeSlotId()));
                    queued++;
                } catch (RuntimeException exception) {
                    // The claim expires on its own, so one unqueued slot must not abandon the rest.
                    log.error("Could not queue a weather refresh timeSlotId={} startsAt={}",
                            target.timeSlotId(), target.startsAt(), exception);
                }
            }

            log.info("Queued weather refreshes queued={} due={} limit={} windowEnd={} claimUntil={} durationMs={}",
                    queued, due.size(), scanLimit, windowEnd, claimUntil,
                    (System.nanoTime() - startedAt) / 1_000_000);
            if (due.size() >= scanLimit) {
                log.info("Weather scan filled its limit; the remaining due slots wait for the next scan limit={}",
                        scanLimit);
            }
        } catch (RuntimeException exception) {
            log.error("Weather scan failed stage={} windowEnd={} durationMs={}",
                    stage, windowEnd, (System.nanoTime() - startedAt) / 1_000_000, exception);
        }
    }
}
