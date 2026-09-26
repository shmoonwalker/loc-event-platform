ALTER TABLE catalog.event
    ADD COLUMN lifecycle_status TEXT NOT NULL DEFAULT 'UNKNOWN'
        CHECK (lifecycle_status IN ('SCHEDULED', 'CANCELLED', 'POSTPONED', 'RESCHEDULED', 'UNKNOWN'));

ALTER TABLE catalog.event_location
    ADD COLUMN external_venue_id TEXT,
    ADD COLUMN country_code TEXT;

ALTER TABLE catalog.event_time_slot
    ADD COLUMN local_start_date DATE,
    ADD COLUMN local_start_time TIME,
    ADD COLUMN local_end_date DATE,
    ADD COLUMN local_end_time TIME,
    ADD COLUMN timezone TEXT,
    ADD COLUMN start_date_status TEXT NOT NULL DEFAULT 'UNKNOWN'
        CHECK (start_date_status IN ('KNOWN', 'TBA', 'TBD', 'UNKNOWN')),
    ADD COLUMN start_time_status TEXT NOT NULL DEFAULT 'UNKNOWN'
        CHECK (start_time_status IN ('KNOWN', 'TBA', 'UNSPECIFIED', 'UNKNOWN')),
    ADD COLUMN end_date_status TEXT NOT NULL DEFAULT 'UNKNOWN'
        CHECK (end_date_status IN ('KNOWN', 'TBA', 'TBD', 'UNKNOWN')),
    ADD COLUMN end_time_status TEXT NOT NULL DEFAULT 'UNKNOWN'
        CHECK (end_time_status IN ('KNOWN', 'TBA', 'UNSPECIFIED', 'UNKNOWN')),
    ADD COLUMN end_approximate BOOLEAN NOT NULL DEFAULT FALSE;

-- Existing imports contain exact instants but do not record a source-local timezone.
UPDATE catalog.event_time_slot
SET start_date_status = CASE WHEN starts_at IS NULL THEN 'UNKNOWN' ELSE 'KNOWN' END,
    start_time_status = CASE WHEN starts_at IS NULL THEN 'UNKNOWN' ELSE 'KNOWN' END,
    end_date_status = CASE WHEN ends_at IS NULL THEN 'UNKNOWN' ELSE 'KNOWN' END,
    end_time_status = CASE WHEN ends_at IS NULL THEN 'UNKNOWN' ELSE 'KNOWN' END;

CREATE TABLE catalog.event_price_range
(
    id          BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    event_id    BIGINT NOT NULL REFERENCES catalog.event (id) ON DELETE CASCADE,
    range_index INTEGER NOT NULL CHECK (range_index >= 0),
    min_price   NUMERIC CHECK (min_price >= 0),
    max_price   NUMERIC CHECK (max_price >= 0),
    currency    TEXT,
    price_type  TEXT,
    CONSTRAINT event_price_range_event_index_key UNIQUE (event_id, range_index),
    CHECK (min_price IS NOT NULL OR max_price IS NOT NULL),
    CHECK (min_price IS NULL OR max_price IS NULL OR min_price <= max_price)
);

CREATE TABLE catalog.event_image
(
    id            BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    event_id      BIGINT NOT NULL REFERENCES catalog.event (id) ON DELETE CASCADE,
    display_order INTEGER NOT NULL CHECK (display_order >= 0),
    url           TEXT NOT NULL,
    width         INTEGER CHECK (width > 0),
    height        INTEGER CHECK (height > 0),
    ratio         TEXT,
    attribution   TEXT,
    fallback      BOOLEAN,
    CONSTRAINT event_image_event_order_key UNIQUE (event_id, display_order)
);
