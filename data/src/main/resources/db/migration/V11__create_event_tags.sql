CREATE TABLE catalog.event_tag
(
    event_id BIGINT NOT NULL REFERENCES catalog.event (id) ON DELETE CASCADE,
    tag      TEXT   NOT NULL,
    origin   TEXT   NOT NULL CHECK (origin IN ('CATEGORY', 'GEMINI')),
    PRIMARY KEY (event_id, tag)
);

COMMENT ON TABLE catalog.event_tag IS
    'Closed discovery tags. CATEGORY comes from the Loc category. GEMINI comes from the description.';

CREATE TABLE catalog.event_tagging
(
    event_id            BIGINT PRIMARY KEY REFERENCES catalog.event (id) ON DELETE CASCADE,
    content_fingerprint TEXT,
    prompt_version      INTEGER     NOT NULL,
    gemini_status       TEXT        NOT NULL CHECK (gemini_status IN ('NOT_APPLICABLE', 'SUCCEEDED', 'FAILED')),
    next_check_at       TIMESTAMPTZ,
    attempts            INTEGER     NOT NULL DEFAULT 0 CHECK (attempts >= 0),
    last_attempt_at     TIMESTAMPTZ,
    last_error          TEXT,
    tagged_at           TIMESTAMPTZ
);

COMMENT ON COLUMN catalog.event_tagging.content_fingerprint IS
    'Hash of title and description. Gemini runs again only when this or the prompt version changes.';

COMMENT ON COLUMN catalog.event_tagging.gemini_status IS
    'NOT_APPLICABLE means the description was too thin for Gemini. Category tags can still exist.';

CREATE INDEX event_tagging_due_idx
    ON catalog.event_tagging (gemini_status, next_check_at);
