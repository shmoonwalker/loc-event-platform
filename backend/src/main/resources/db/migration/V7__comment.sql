CREATE TABLE comment (
    id            uuid          PRIMARY KEY DEFAULT uuidv7(),
    event_id      uuid          NOT NULL, -- the show (publication loc_group_id), not one occurrence
    parent_id     uuid          REFERENCES comment (id),
    user_id       uuid          REFERENCES app_user (id) ON DELETE SET NULL,
    body          varchar(2000) NOT NULL CHECK (length(trim(body)) > 0),
    official      boolean       NOT NULL DEFAULT false,
    created_at    timestamptz   NOT NULL DEFAULT now(),
    edited_at     timestamptz,
    deleted_at    timestamptz,
    deleted_by    uuid          REFERENCES app_user (id) ON DELETE SET NULL,
    delete_reason text,
    CHECK (deleted_at IS NOT NULL OR (deleted_by IS NULL AND delete_reason IS NULL))
);

-- top-level comments of one event, newest first
CREATE INDEX comment_event_id_created_at_idx ON comment (event_id, created_at DESC) WHERE parent_id IS NULL;
-- replies of a page of top-level comments
CREATE INDEX comment_parent_id_created_at_idx ON comment (parent_id, created_at) WHERE parent_id IS NOT NULL;
