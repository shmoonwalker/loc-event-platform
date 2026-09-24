CREATE SCHEMA IF NOT EXISTS catalog;

CREATE TABLE catalog.event
(
    id                 BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    source             TEXT        NOT NULL,
    external_id        TEXT        NOT NULL,
    raw_object_key     TEXT        NOT NULL,
    title              TEXT,
    description        TEXT,
    source_url         TEXT,
    registration_url   TEXT,
    organizer_names    TEXT[]      NOT NULL DEFAULT '{}',
    source_created_at  TIMESTAMPTZ,
    source_updated_at  TIMESTAMPTZ,
    CONSTRAINT event_source_external_id_key UNIQUE (source, external_id)
);

CREATE TABLE catalog.event_time_slot
(
    id          BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    event_id    BIGINT      NOT NULL REFERENCES catalog.event (id) ON DELETE CASCADE,
    slot_index  INTEGER     NOT NULL,
    starts_at   TIMESTAMPTZ,
    ends_at     TIMESTAMPTZ,
    CONSTRAINT event_time_slot_event_index_key UNIQUE (event_id, slot_index)
);

CREATE TABLE catalog.event_location
(
    event_id      BIGINT PRIMARY KEY REFERENCES catalog.event (id) ON DELETE CASCADE,
    location_type TEXT           NOT NULL CHECK (location_type IN ('PHYSICAL', 'ONLINE', 'UNKNOWN')),
    venue_name    TEXT,
    address       TEXT,
    city          TEXT,
    postal_code   TEXT,
    country       TEXT,
    latitude      DOUBLE PRECISION,
    longitude     DOUBLE PRECISION
);
