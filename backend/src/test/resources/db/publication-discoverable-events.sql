DROP SCHEMA IF EXISTS publication CASCADE;
CREATE SCHEMA publication;

CREATE TABLE publication.event_snapshot (
    loc_occurrence_id UUID PRIMARY KEY,
    loc_event_id UUID NOT NULL,
    loc_group_id UUID NOT NULL,
    revision BIGINT NOT NULL CHECK (revision > 0),
    discoverable BOOLEAN NOT NULL,
    state TEXT NOT NULL,
    payload JSONB NOT NULL,
    first_published_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL
);

CREATE VIEW publication.discoverable_events AS
    SELECT *
    FROM publication.event_snapshot
    WHERE discoverable
      AND (payload->>'validUntil')::timestamptz > CURRENT_TIMESTAMP;

CREATE TABLE publication.organizer_identity (
    loc_organizer_id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    source TEXT NOT NULL,
    external_id TEXT NOT NULL,
    UNIQUE (source, external_id)
);
