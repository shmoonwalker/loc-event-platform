ALTER TABLE catalog.event
    ADD COLUMN source_active BOOLEAN NOT NULL DEFAULT TRUE,
    ADD COLUMN last_seen_at TIMESTAMPTZ,
    ADD COLUMN source_presence_checked_at TIMESTAMPTZ;

CREATE TABLE catalog.collection_run
(
    id UUID PRIMARY KEY,
    source TEXT NOT NULL,
    started_at TIMESTAMPTZ NOT NULL,
    completed_at TIMESTAMPTZ,
    status TEXT NOT NULL CHECK (status IN ('RUNNING', 'COMPLETED', 'FAILED')),
    event_ids TEXT[] NOT NULL DEFAULT '{}'
);
