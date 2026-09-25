ALTER TABLE catalog.event
    ADD COLUMN collected_at TIMESTAMPTZ;

COMMENT ON COLUMN catalog.event.collected_at IS
    'Collection time from the accepted raw snapshot; fallback ordering when source update times are absent.';
