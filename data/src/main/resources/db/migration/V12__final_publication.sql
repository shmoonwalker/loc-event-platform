-- Catalogue records remain permissive. Only publication owns the final LOC contract.
CREATE SCHEMA IF NOT EXISTS publication;

ALTER TABLE catalog.event ADD COLUMN qualification_issues TEXT[] NOT NULL DEFAULT '{}';
ALTER TABLE catalog.event_location ADD COLUMN coordinate_evidence TEXT NOT NULL DEFAULT 'UNKNOWN';
-- Existing rows need replay to establish explicit mapping evidence.
ALTER TABLE catalog.event_time_slot
    ADD COLUMN retired BOOLEAN NOT NULL DEFAULT FALSE,
    ADD COLUMN occurrence_key UUID NOT NULL DEFAULT gen_random_uuid();
ALTER TABLE catalog.event_time_slot DROP CONSTRAINT event_time_slot_event_index_key;
CREATE UNIQUE INDEX event_time_slot_occurrence_key ON catalog.event_time_slot(occurrence_key);
CREATE INDEX event_time_slot_active_event ON catalog.event_time_slot(event_id) WHERE NOT retired;

-- Durable identities: intentionally no cascading foreign keys to the catalogue.
CREATE TABLE publication.event_identity (
    loc_event_id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    source TEXT NOT NULL,
    external_id TEXT NOT NULL,
    UNIQUE(source, external_id)
);
CREATE TABLE publication.occurrence_identity (
    loc_occurrence_id UUID PRIMARY KEY,
    loc_event_id UUID NOT NULL REFERENCES publication.event_identity(loc_event_id),
    schedule JSONB NOT NULL DEFAULT '{}',
    retired BOOLEAN NOT NULL DEFAULT FALSE
);
-- Explicit editorial decisions only; no fuzzy title-based duplicate merging.
CREATE TABLE publication.event_review (
    source TEXT NOT NULL,
    external_id TEXT NOT NULL,
    blocked BOOLEAN NOT NULL DEFAULT FALSE,
    duplicate_of UUID REFERENCES publication.event_identity(loc_event_id),
    reason TEXT NOT NULL CHECK (btrim(reason) <> ''),
    reviewed_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    PRIMARY KEY(source, external_id)
);
-- Verified enrichment survives future imports. Removing the override restores source data.
CREATE TABLE publication.location_override (
    source TEXT NOT NULL,
    external_id TEXT NOT NULL,
    location_type TEXT NOT NULL CHECK (location_type IN ('PHYSICAL', 'ONLINE')),
    venue_name TEXT,
    address TEXT,
    city TEXT,
    postal_code TEXT,
    country TEXT,
    country_code TEXT,
    latitude DOUBLE PRECISION,
    longitude DOUBLE PRECISION,
    coordinate_evidence TEXT NOT NULL CHECK (coordinate_evidence IN ('VERIFIED_VENUE','VERIFIED_ADDRESS','NOT_APPLICABLE')),
    evidence_url TEXT NOT NULL,
    source_location_fingerprint TEXT NOT NULL,
    verified_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    PRIMARY KEY(source, external_id),
    CHECK (location_type = 'ONLINE' OR
        (latitude BETWEEN -90 AND 90 AND longitude BETWEEN -180 AND 180
         AND latitude IS NOT NULL AND longitude IS NOT NULL
         AND city IS NOT NULL AND country_code IS NOT NULL
         AND (venue_name IS NOT NULL OR address IS NOT NULL)))
);
CREATE TABLE publication.decision (
    subject_id UUID PRIMARY KEY,
    loc_event_id UUID NOT NULL REFERENCES publication.event_identity(loc_event_id),
    subject_type TEXT NOT NULL CHECK (subject_type IN ('EVENT', 'OCCURRENCE')),
    decision TEXT NOT NULL CHECK (decision IN ('ELIGIBLE', 'HELD')),
    blocking_reasons JSONB NOT NULL,
    warnings JSONB NOT NULL,
    evaluated_at TIMESTAMPTZ NOT NULL,
    rule_version TEXT NOT NULL,
    input_revision TEXT NOT NULL,
    next_evaluation_at TIMESTAMPTZ NOT NULL
);
CREATE INDEX publication_decision_due ON publication.decision(next_evaluation_at);
CREATE TABLE publication.decision_history (
    id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    subject_id UUID NOT NULL,
    decision JSONB NOT NULL,
    recorded_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE TABLE publication.event_snapshot (
    loc_occurrence_id UUID PRIMARY KEY REFERENCES publication.occurrence_identity(loc_occurrence_id),
    loc_event_id UUID NOT NULL REFERENCES publication.event_identity(loc_event_id),
    revision BIGINT NOT NULL CHECK (revision > 0),
    discoverable BOOLEAN NOT NULL,
    state TEXT NOT NULL,
    payload JSONB NOT NULL,
    first_published_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL
);
CREATE VIEW publication.discoverable_events AS
    SELECT * FROM publication.event_snapshot WHERE discoverable
        AND (payload->>'validUntil')::timestamptz > CURRENT_TIMESTAMP;

-- An immutable change log/outbox. Shared-database consumers can read snapshots directly.
-- Remote consumers keep their own cursor and apply only increasing per-occurrence revisions.
CREATE TABLE publication.change_log (
    sequence BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    loc_occurrence_id UUID NOT NULL,
    revision BIGINT NOT NULL,
    operation TEXT NOT NULL CHECK (operation IN ('UPSERT', 'WITHDRAW')),
    payload JSONB NOT NULL,
    created_at TIMESTAMPTZ NOT NULL,
    UNIQUE(loc_occurrence_id, revision)
);

-- All input writers participate in a statement-level input lock, including enrichment.
-- The evaluator can therefore qualify and export one coherent input revision atomically.
CREATE FUNCTION catalog.lock_publication_source() RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN
    PERFORM pg_advisory_xact_lock(hashtext('loc-catalog-input'));
    RETURN NULL;
END;
$$;

CREATE TRIGGER publication_source_lock BEFORE INSERT OR UPDATE OR DELETE ON catalog.event
    FOR EACH STATEMENT EXECUTE FUNCTION catalog.lock_publication_source();
CREATE TRIGGER publication_source_lock BEFORE INSERT OR UPDATE OR DELETE ON catalog.event_time_slot
    FOR EACH STATEMENT EXECUTE FUNCTION catalog.lock_publication_source();
CREATE TRIGGER publication_source_lock BEFORE INSERT OR UPDATE OR DELETE ON catalog.event_location
    FOR EACH STATEMENT EXECUTE FUNCTION catalog.lock_publication_source();
CREATE TRIGGER publication_source_lock BEFORE INSERT OR UPDATE OR DELETE ON catalog.event_image
    FOR EACH STATEMENT EXECUTE FUNCTION catalog.lock_publication_source();
CREATE TRIGGER publication_source_lock BEFORE INSERT OR UPDATE OR DELETE ON catalog.event_category
    FOR EACH STATEMENT EXECUTE FUNCTION catalog.lock_publication_source();
CREATE TRIGGER publication_source_lock BEFORE INSERT OR UPDATE OR DELETE ON catalog.event_tag
    FOR EACH STATEMENT EXECUTE FUNCTION catalog.lock_publication_source();
CREATE TRIGGER publication_source_lock BEFORE INSERT OR UPDATE OR DELETE ON catalog.event_weather
    FOR EACH STATEMENT EXECUTE FUNCTION catalog.lock_publication_source();
CREATE TRIGGER publication_source_lock BEFORE INSERT OR UPDATE OR DELETE ON publication.event_review
    FOR EACH STATEMENT EXECUTE FUNCTION catalog.lock_publication_source();
CREATE TRIGGER publication_source_lock BEFORE INSERT OR UPDATE OR DELETE ON publication.location_override
    FOR EACH STATEMENT EXECUTE FUNCTION catalog.lock_publication_source();
CREATE TRIGGER publication_source_lock BEFORE INSERT OR UPDATE OR DELETE ON catalog.event_tagging
    FOR EACH STATEMENT EXECUTE FUNCTION catalog.lock_publication_source();
