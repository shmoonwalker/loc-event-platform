ALTER TABLE catalog.event
    ADD COLUMN content_hash TEXT;

COMMENT ON COLUMN catalog.event.content_hash IS
    'SHA-256 of normalized event content excluding raw object key and source timestamps; used to skip unchanged detail updates.';
