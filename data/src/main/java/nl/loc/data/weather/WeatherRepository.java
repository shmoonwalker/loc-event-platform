package nl.loc.data.weather;

import java.sql.ResultSet;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

/**
 * Access to catalog.event_weather.
 *
 * Written in SQL because every write also has to confirm the slot still describes what was
 * requested, and because the Open-Meteo call must not run inside a database transaction.
 */
@Repository
@RequiredArgsConstructor
public class WeatherRepository {

    private static final String LATITUDE = "(p.payload->'location'->>'latitude')::double precision";
    private static final String LONGITUDE = "(p.payload->'location'->>'longitude')::double precision";

    /**
     * Only occurrences in the published product get a forecast, using the coordinates that were
     * published. Online events and anything the publication rules hold are never fetched.
     */
    private static final String ELIGIBLE_SLOT = """
            FROM catalog.event_time_slot s
                     JOIN publication.event_snapshot p ON p.loc_occurrence_id = s.occurrence_key
                     LEFT JOIN catalog.event_weather w ON w.time_slot_id = s.id
            WHERE p.discoverable
              AND p.payload->'location'->>'type' = 'PHYSICAL'
              AND p.payload->'location'->>'latitude' IS NOT NULL
              AND p.payload->'location'->>'longitude' IS NOT NULL
              AND s.retired = FALSE
              AND s.starts_at IS NOT NULL
            """;

    private static final String SELECT_TARGET =
            "SELECT s.id, " + LATITUDE + ", " + LONGITUDE + ", s.starts_at, COALESCE(w.attempts, 0) ";

    private static final RowMapper<WeatherTarget> TARGET = (ResultSet row, int rowNumber) -> new WeatherTarget(
            row.getLong(1),
            row.getDouble(2),
            row.getDouble(3),
            row.getObject(4, OffsetDateTime.class).toInstant(),
            row.getInt(5));

    private final JdbcTemplate jdbcTemplate;

    @Transactional(readOnly = true)
    public List<WeatherTarget> findDue(Instant now, Instant windowEnd, int limit) {
        return jdbcTemplate.query(SELECT_TARGET + ELIGIBLE_SLOT + """
                  AND s.starts_at > ?
                  AND s.starts_at <= ?
                  AND (w.time_slot_id IS NULL OR (w.next_check_at IS NOT NULL AND w.next_check_at <= ?))
                ORDER BY s.starts_at
                LIMIT ?
                """, TARGET, utc(now), utc(windowEnd), utc(now), limit);
    }

    /** Re-read for a queued slot, which may have moved or been cancelled since it was queued. */
    @Transactional(readOnly = true)
    public WeatherTarget findEligible(long timeSlotId, Instant now) {
        return jdbcTemplate.query(SELECT_TARGET + ELIGIBLE_SLOT + """
                  AND s.id = ?
                  AND s.starts_at > ?
                """, TARGET, timeSlotId, utc(now)).stream().findFirst().orElse(null);
    }

    /**
     * Records what is about to be asked and moves the next check out, so a later scan does not
     * queue this slot again while the first request is still in flight. If the worker never runs,
     * the claim expires and the slot becomes due again.
     */
    @Transactional
    public void claim(WeatherTarget target, Instant claimUntil) {
        jdbcTemplate.update("""
                        INSERT INTO catalog.event_weather (time_slot_id, requested_latitude, requested_longitude,
                                                           requested_for_hour, next_check_at)
                        VALUES (?, ?, ?, ?, ?)
                        ON CONFLICT (time_slot_id) DO UPDATE SET next_check_at = EXCLUDED.next_check_at
                        """,
                target.timeSlotId(), target.latitude(), target.longitude(),
                utc(target.requestedHour()), utc(claimUntil));
    }

    /** Reserve one real weather request. The reservation is committed before the network call. */
    @Transactional
    public boolean reserveWeatherAttempt(Instant now, int dailyLimit, long timeSlotId) {
        jdbcTemplate.query("SELECT pg_advisory_xact_lock(hashtext('loc-budget-weather'))", (ResultSet row) -> { });
        Integer used = jdbcTemplate.queryForObject("""
                SELECT COUNT(*) FROM catalog.enrichment_request
                WHERE kind = 'WEATHER' AND attempted_at >= ?
                """, Integer.class, utc(startOfUtcDay(now)));
        if (used != null && used >= dailyLimit) return false;
        jdbcTemplate.update("""
                INSERT INTO catalog.enrichment_request(kind, time_slot_id, attempted_at)
                VALUES ('WEATHER', ?, ?)
                """, timeSlotId, utc(now));
        return true;
    }

    /** Returns false when the slot moved while the forecast was being fetched. */
    @Transactional
    public boolean saveForecast(WeatherTarget target, WeatherLookup.Forecast forecast,
                                Instant fetchedAt, Instant nextCheckAt) {
        int updated = jdbcTemplate.update("""
                        UPDATE catalog.event_weather
                        SET forecast_fetched_at               = ?,
                            temperature_celsius               = CAST(? AS NUMERIC),
                            precipitation_probability_percent = CAST(? AS INTEGER),
                            wind_speed_kmh                    = CAST(? AS NUMERIC),
                            weather_code                      = ?,
                            next_check_at                     = CAST(? AS TIMESTAMPTZ),
                            attempts                          = 0,
                            last_attempt_at                   = ?,
                            last_error                        = NULL
                        WHERE time_slot_id = ?
                          AND requested_latitude = ?
                          AND requested_longitude = ?
                          AND requested_for_hour = ?
                        """,
                utc(fetchedAt), forecast.temperatureCelsius(), forecast.precipitationProbabilityPercent(),
                forecast.windSpeedKmh(), forecast.weatherCode(), utc(nextCheckAt), utc(fetchedAt),
                target.timeSlotId(), target.latitude(), target.longitude(), utc(target.requestedHour()));
        return updated == 1;
    }

    /** Records an attempt that produced no values, keeping any forecast already stored for the hour. */
    @Transactional
    public void saveAttempt(WeatherTarget target, Instant nextCheckAt, int attempts,
                            Instant attemptedAt, String error) {
        jdbcTemplate.update("""
                        UPDATE catalog.event_weather
                        SET next_check_at   = CAST(? AS TIMESTAMPTZ),
                            attempts        = ?,
                            last_attempt_at = ?,
                            last_error      = CAST(? AS TEXT)
                        WHERE time_slot_id = ?
                          AND requested_latitude = ?
                          AND requested_longitude = ?
                          AND requested_for_hour = ?
                        """,
                utc(nextCheckAt), attempts, utc(attemptedAt), error,
                target.timeSlotId(), target.latitude(), target.longitude(), utc(target.requestedHour()));
    }

    /**
     * Drops rows that no longer describe their slot's hour or published place. Import clears these
     * directly, so this is the safety net for anything it missed.
     */
    @Transactional
    public int deleteStale() {
        return jdbcTemplate.update("""
                DELETE FROM catalog.event_weather w
                WHERE NOT EXISTS (SELECT 1
                                  FROM catalog.event_time_slot s
                                           JOIN publication.event_snapshot p ON p.loc_occurrence_id = s.occurrence_key
                                  WHERE s.id = w.time_slot_id
                                    AND s.retired = FALSE
                                    AND s.starts_at IS NOT NULL
                                    AND p.payload->'location'->>'type' = 'PHYSICAL'
                                    AND w.requested_for_hour = date_trunc('hour', s.starts_at)
                                    AND w.requested_latitude = %s
                                    AND w.requested_longitude = %s)
                """.formatted(LATITUDE, LONGITUDE));
    }

    @Transactional
    public int deleteForTimeSlots(Collection<Long> timeSlotIds) {
        if (timeSlotIds.isEmpty()) {
            return 0;
        }
        String placeholders = String.join(", ", Collections.nCopies(timeSlotIds.size(), "?"));
        return jdbcTemplate.update(
                "DELETE FROM catalog.event_weather WHERE time_slot_id IN (" + placeholders + ")",
                timeSlotIds.toArray());
    }

    private static OffsetDateTime utc(Instant instant) {
        return instant == null ? null : instant.atOffset(ZoneOffset.UTC);
    }

    private static Instant startOfUtcDay(Instant now) {
        return now.atZone(ZoneOffset.UTC).toLocalDate().atStartOfDay(ZoneOffset.UTC).toInstant();
    }
}
