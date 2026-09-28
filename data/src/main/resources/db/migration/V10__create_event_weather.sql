-- Forecast enrichment for one time slot. Every column here is owned by the weather job;
-- source imports never write this table, so refreshing a forecast is not a new source version.
CREATE TABLE catalog.event_weather
(
    time_slot_id                      BIGINT PRIMARY KEY
        REFERENCES catalog.event_time_slot (id) ON DELETE CASCADE,
    requested_latitude                DOUBLE PRECISION NOT NULL,
    requested_longitude               DOUBLE PRECISION NOT NULL,
    requested_for_hour                TIMESTAMPTZ      NOT NULL,
    forecast_fetched_at               TIMESTAMPTZ,
    temperature_celsius               NUMERIC(5, 1),
    precipitation_probability_percent INTEGER CHECK (precipitation_probability_percent BETWEEN 0 AND 100),
    wind_speed_kmh                    NUMERIC(5, 1) CHECK (wind_speed_kmh >= 0),
    weather_code                      INTEGER CHECK (weather_code >= 0),
    next_check_at                     TIMESTAMPTZ,
    attempts                          INTEGER          NOT NULL DEFAULT 0 CHECK (attempts >= 0),
    last_attempt_at                   TIMESTAMPTZ,
    last_error                        TEXT,
    CONSTRAINT event_weather_values_need_a_fetch
        CHECK (forecast_fetched_at IS NOT NULL
            OR (temperature_celsius IS NULL
                AND precipitation_probability_percent IS NULL
                AND wind_speed_kmh IS NULL
                AND weather_code IS NULL))
);

COMMENT ON TABLE catalog.event_weather IS
    'Open-Meteo forecast for the start hour of one time slot, written only by the data weather job.';

COMMENT ON COLUMN catalog.event_weather.requested_for_hour IS
    'UTC hour the stored values were requested for. A mismatch with the slot start means the row is stale.';

COMMENT ON COLUMN catalog.event_weather.weather_code IS
    'Raw WMO code. The reading side decides the wording so the catalog stays language-neutral.';

COMMENT ON COLUMN catalog.event_weather.next_check_at IS
    'When this slot may be looked at again. NULL means no further check is due before the event starts.';

COMMENT ON COLUMN catalog.event_weather.attempts IS
    'Consecutive failed attempts, used for backoff. Reset once a lookup answers.';

-- The scan reads only due rows, so slots outside the forecast window are never loaded.
CREATE INDEX event_weather_next_check_at_idx
    ON catalog.event_weather (next_check_at);
