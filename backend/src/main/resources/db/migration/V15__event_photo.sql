CREATE TABLE event_photo (
    id           uuid PRIMARY KEY DEFAULT uuidv7(),
    event_id     uuid NOT NULL, -- the show (publication loc_group_id), not one occurrence
    uploader_id  uuid NOT NULL REFERENCES app_user (id) ON DELETE CASCADE,
    status       varchar(16) NOT NULL CHECK (status IN ('PENDING', 'APPROVED', 'REJECTED')),
    created_at   timestamptz NOT NULL DEFAULT now()
);

-- public album and the admin queue, newest first
CREATE INDEX event_photo_event_status_created_idx
    ON event_photo (event_id, status, created_at DESC, id DESC);

-- the ten-photo cap counts pending and approved only
CREATE INDEX event_photo_open_per_member_idx
    ON event_photo (uploader_id, event_id)
    WHERE status IN ('PENDING', 'APPROVED');

-- PHOTO fits the existing varchar(16). EVENT and COMMENT stay valid.
ALTER TABLE report DROP CONSTRAINT report_target_type_check;

ALTER TABLE report ADD CONSTRAINT report_target_type_check
    CHECK (target_type IN ('EVENT', 'COMMENT', 'PHOTO'));
