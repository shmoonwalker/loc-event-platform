package nl.loc.data.weather;

import java.time.Instant;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import nl.loc.data.publication.PublicationService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

/**
 * Refreshes the forecast for one time slot.
 *
 * Deliberately not transactional: the Open-Meteo call happens between two short database
 * transactions rather than inside one, so a slow request never holds a connection open and a
 * recorded failure is not rolled back when the exception is rethrown for the queue to retry.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class WeatherEnrichmentService {

    private final WeatherRepository weatherRepository;
    private final OpenMeteoClient openMeteoClient;
    private final PublicationService publicationService;

    @Value("${loc.weather.daily-limit}")
    private int dailyLimit;

    /** The message carries only an id, so eligibility is decided here and not by the sender. */
    public void refresh(long timeSlotId) {
        Instant now = Instant.now();
        WeatherTarget target = weatherRepository.findEligible(timeSlotId, now);
        if (target == null) {
            log.debug("Skipping weather refresh timeSlotId={} reason=slot is no longer eligible", timeSlotId);
            return;
        }
        PublicationService.Readiness readiness = publicationService.slotReadiness(timeSlotId);
        if (!readiness.eligible()) {
            weatherRepository.saveAttempt(target, readiness.retryAt(), target.attempts(), now,
                    String.join(",", readiness.reasons()));
            log.info("Skipping weather timeSlotId={} retryAt={} reasons={}",
                    timeSlotId, readiness.retryAt(), readiness.reasons());
            return;
        }
        if (!WeatherSchedule.withinWindow(now, target.startsAt())) {
            Instant nextCheckAt = WeatherSchedule.afterOutsideRange(now, target.startsAt());
            log.debug("Deferring weather refresh timeSlotId={} startsAt={} nextCheckAt={} "
                    + "reason=outside the forecast window", timeSlotId, target.startsAt(), nextCheckAt);
            weatherRepository.saveAttempt(target, nextCheckAt, target.attempts(), now, null);
            return;
        }

        if (!weatherRepository.reserveWeatherAttempt(now, dailyLimit, timeSlotId)) {
            Instant tomorrow = now.atZone(java.time.ZoneOffset.UTC).toLocalDate().plusDays(1)
                    .atStartOfDay(java.time.ZoneOffset.UTC).toInstant();
            weatherRepository.saveAttempt(target, tomorrow, target.attempts(), now, "DAILY_REQUEST_LIMIT");
            log.info("Deferring weather timeSlotId={} dailyLimit={} nextCheckAt={}",
                    timeSlotId, dailyLimit, tomorrow);
            return;
        }

        try {
            apply(target, openMeteoClient.lookup(target.latitude(), target.longitude(), target.requestedHour()), now);
        } catch (WeatherRequestRejectedException exception) {
            // Repeating a rejected request cannot help, so it is recorded and backed off instead.
            Instant nextCheckAt = recordFailure(target, now, exception.getMessage());
            log.warn("Open-Meteo rejected a weather request timeSlotId={} hour={} attempts={} nextCheckAt={}",
                    target.timeSlotId(), target.requestedHour(), target.attempts() + 1, nextCheckAt, exception);
        } catch (WeatherServiceUnavailableException exception) {
            Instant nextCheckAt = recordFailure(target, now, exception.getMessage());
            log.warn("Weather lookup failed timeSlotId={} hour={} attempts={} nextCheckAt={} reason={}",
                    target.timeSlotId(), target.requestedHour(), target.attempts() + 1,
                    nextCheckAt, exception.getMessage());
            // Rethrown so the queue retries shortly; the stored next check only matters if those run out.
            throw exception;
        }
    }

    private void apply(WeatherTarget target, WeatherLookup lookup, Instant now) {
        switch (lookup) {
            case WeatherLookup.Forecast forecast -> {
                Instant nextCheckAt = WeatherSchedule.afterForecast(now, target.startsAt());
                if (weatherRepository.saveForecast(target, forecast, now, nextCheckAt)) {
                    log.debug("Stored forecast timeSlotId={} hour={} temperature={} rainChance={} wind={} "
                                    + "code={} nextCheckAt={}",
                            target.timeSlotId(), target.requestedHour(), forecast.temperatureCelsius(),
                            forecast.precipitationProbabilityPercent(), forecast.windSpeedKmh(),
                            forecast.weatherCode(), nextCheckAt);
                } else {
                    log.info("Discarded forecast timeSlotId={} hour={} reason=the slot changed while it was fetched",
                            target.timeSlotId(), target.requestedHour());
                }
            }
            case WeatherLookup.HourUnavailable ignored -> {
                Instant nextCheckAt = WeatherSchedule.afterHourUnavailable(now, target.startsAt());
                log.debug("No forecast values yet timeSlotId={} hour={} nextCheckAt={}",
                        target.timeSlotId(), target.requestedHour(), nextCheckAt);
                weatherRepository.saveAttempt(target, nextCheckAt, 0, now, null);
            }
            case WeatherLookup.OutsideForecastRange outside -> {
                Instant nextCheckAt = WeatherSchedule.afterOutsideRange(now, target.startsAt());
                log.debug("Hour outside the forecast range timeSlotId={} hour={} nextCheckAt={} reason={}",
                        target.timeSlotId(), target.requestedHour(), nextCheckAt, outside.reason());
                weatherRepository.saveAttempt(target, nextCheckAt, 0, now, null);
            }
        }
    }

    private Instant recordFailure(WeatherTarget target, Instant now, String error) {
        int attempts = target.attempts() + 1;
        Instant nextCheckAt = WeatherSchedule.afterFailure(now, target.startsAt(), attempts);
        weatherRepository.saveAttempt(target, nextCheckAt, attempts, now, error);
        return nextCheckAt;
    }
}
