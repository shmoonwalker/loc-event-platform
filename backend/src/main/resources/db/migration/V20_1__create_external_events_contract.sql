-- The data pipeline owns the rows in this table. The backend owns the
-- contract so a fresh database can start before the replacement pipeline is
-- deployed.
CREATE SCHEMA IF NOT EXISTS analytics;

CREATE TABLE IF NOT EXISTS analytics.external_events
(
    logical_event_id  UUID        NOT NULL,
    source            TEXT        NOT NULL,
    is_published      BOOLEAN     NOT NULL DEFAULT TRUE,
    external_event_id TEXT        NOT NULL,
    source_url        TEXT,
    external_venue_id TEXT,
    start_date        DATE        NOT NULL,
    title             TEXT        NOT NULL,
    description       TEXT,
    category          TEXT,
    categories        TEXT[],
    start_at          TIMESTAMPTZ NOT NULL,
    end_at            TIMESTAMPTZ,
    price_min         NUMERIC,
    street_name       TEXT,
    house_number      TEXT,
    postal_code       TEXT,
    city_name         TEXT,
    province          TEXT,
    latitude          NUMERIC(9, 6),
    longitude         NUMERIC(9, 6),
    image_url         TEXT,
    is_cancelled      BOOLEAN     NOT NULL DEFAULT FALSE
);
