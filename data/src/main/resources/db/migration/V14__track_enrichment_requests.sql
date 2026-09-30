-- Immutable request reservations make external API budgets count actual attempts,
-- including retries, instead of counting only the latest state on an event row.
CREATE TABLE catalog.enrichment_request
(
    id            BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    kind          TEXT        NOT NULL CHECK (kind IN ('GEMINI', 'WEATHER')),
    event_id      BIGINT REFERENCES catalog.event (id) ON DELETE SET NULL,
    time_slot_id  BIGINT REFERENCES catalog.event_time_slot (id) ON DELETE SET NULL,
    attempted_at  TIMESTAMPTZ NOT NULL,
    outcome       TEXT
);

CREATE INDEX enrichment_request_kind_attempted_at_idx
    ON catalog.enrichment_request (kind, attempted_at);

-- A too-short description is now decided from the current text, not stored as a final state.
DELETE FROM catalog.event_tagging WHERE gemini_status = 'NOT_APPLICABLE';

ALTER TABLE catalog.event_tagging DROP CONSTRAINT event_tagging_gemini_status_check;
ALTER TABLE catalog.event_tagging ADD CONSTRAINT event_tagging_gemini_status_check
    CHECK (gemini_status IN ('PENDING', 'SUCCEEDED', 'FAILED', 'GAVE_UP'));

COMMENT ON COLUMN catalog.event_tagging.gemini_status IS
    'PENDING is queued or deferred. SUCCEEDED and GAVE_UP are final: the event is never sent to Gemini again.';

COMMENT ON COLUMN catalog.event_tagging.content_fingerprint IS
    'Hash of the title and description that were sent. A later change does not trigger another request.';

-- Import now writes local tags for every event. Rows imported earlier get their category tag here;
-- source genre tags follow on the next import.
INSERT INTO catalog.event_tag (event_id, tag, origin)
SELECT ec.event_id,
       CASE c.name
           WHEN 'Music & Nightlife' THEN 'live-music'
           WHEN 'Arts & Culture' THEN 'arts'
           WHEN 'Sports' THEN 'sports'
           WHEN 'Business & Careers' THEN 'business'
           WHEN 'Technology & Science' THEN 'technology'
           WHEN 'Learning & Skills' THEN 'learning'
           WHEN 'Nature & Sustainability' THEN 'nature'
           ELSE 'other'
           END,
       'CATEGORY'
FROM catalog.event_category ec
         JOIN catalog.category c ON c.id = ec.category_id
ON CONFLICT (event_id, tag) DO NOTHING;
