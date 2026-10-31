CREATE TABLE publication.event_group (
    group_key TEXT PRIMARY KEY,
    loc_group_id UUID NOT NULL UNIQUE DEFAULT gen_random_uuid()
);

ALTER TABLE publication.event_snapshot
    ADD COLUMN loc_group_id UUID;

CREATE INDEX event_snapshot_loc_group_id
    ON publication.event_snapshot(loc_group_id);
