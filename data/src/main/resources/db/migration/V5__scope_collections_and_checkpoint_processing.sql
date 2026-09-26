ALTER TABLE catalog.collection_run
    ADD COLUMN full_source BOOLEAN NOT NULL DEFAULT FALSE,
    ADD COLUMN country_code TEXT,
    ADD COLUMN scope_starts_at TIMESTAMPTZ,
    ADD COLUMN scope_ends_at TIMESTAMPTZ,
    ADD CONSTRAINT collection_run_scope_dates_check CHECK (
        (scope_starts_at IS NULL AND scope_ends_at IS NULL)
        OR (scope_starts_at IS NOT NULL AND scope_ends_at IS NOT NULL AND scope_starts_at < scope_ends_at)
    );

-- Old runs have no trustworthy saved scope; do not infer one from current configuration.
CREATE INDEX collection_run_completed_source_idx
    ON catalog.collection_run (source, started_at DESC, id DESC)
    WHERE status = 'COMPLETED';

CREATE TABLE catalog.processed_raw_object
(
    source TEXT NOT NULL,
    raw_object_key TEXT NOT NULL,
    processed_at TIMESTAMPTZ NOT NULL,
    PRIMARY KEY (source, raw_object_key)
);
