-- One definition of the group key, shared by PublicationService and the backfill below.
CREATE FUNCTION publication.event_group_key(
    p_source TEXT,
    p_external_id TEXT,
    p_external_venue_id TEXT,
    p_title TEXT
) RETURNS TEXT
    LANGUAGE SQL
    IMMUTABLE
AS $$
SELECT CASE
           -- RVO sends no venue ID (RvoEventMapper), so its events can never form a SHOW key.
           WHEN p_source = 'rvo'
               OR NULLIF(BTRIM(p_external_venue_id), '') IS NULL
               OR NULLIF(BTRIM(REGEXP_REPLACE(p_title, '[[:space:]]+', ' ', 'g')), '') IS NULL
               THEN jsonb_build_array('EVENT', p_source, p_external_id)::text
           ELSE jsonb_build_array(
                   'SHOW',
                   p_source,
                   BTRIM(p_external_venue_id),
                   LOWER(BTRIM(REGEXP_REPLACE(p_title, '[[:space:]]+', ' ', 'g')))
                )::text
           END
$$;

CREATE TABLE publication.event_group (
    group_key TEXT PRIMARY KEY,
    loc_group_id UUID NOT NULL UNIQUE DEFAULT gen_random_uuid()
);

ALTER TABLE publication.event_snapshot
    ADD COLUMN loc_group_id UUID;

INSERT INTO publication.event_group (group_key)
SELECT DISTINCT publication.event_group_key(i.source, i.external_id, l.external_venue_id, e.title)
FROM publication.event_snapshot s
         JOIN publication.event_identity i ON i.loc_event_id = s.loc_event_id
         LEFT JOIN catalog.event e ON e.source = i.source AND e.external_id = i.external_id
         LEFT JOIN catalog.event_location l ON l.event_id = e.id
ON CONFLICT (group_key) DO NOTHING;

UPDATE publication.event_snapshot s
SET loc_group_id = g.loc_group_id,
    payload = jsonb_set(s.payload, '{locGroupId}', to_jsonb(g.loc_group_id::text), true)
FROM publication.event_identity i
         LEFT JOIN catalog.event e ON e.source = i.source AND e.external_id = i.external_id
         LEFT JOIN catalog.event_location l ON l.event_id = e.id
         JOIN publication.event_group g
              ON g.group_key = publication.event_group_key(i.source, i.external_id, l.external_venue_id, e.title)
WHERE s.loc_event_id = i.loc_event_id;

ALTER TABLE publication.event_snapshot
    ALTER COLUMN loc_group_id SET NOT NULL,
    ADD CONSTRAINT event_snapshot_loc_group_id_fkey
        FOREIGN KEY (loc_group_id) REFERENCES publication.event_group (loc_group_id);

CREATE INDEX event_snapshot_loc_group_id
    ON publication.event_snapshot (loc_group_id);

-- SELECT * was expanded when V12 created the view; recreate it so loc_group_id is included.
CREATE OR REPLACE VIEW publication.discoverable_events AS
    SELECT * FROM publication.event_snapshot WHERE discoverable
        AND (payload->>'validUntil')::timestamptz > CURRENT_TIMESTAMP;
