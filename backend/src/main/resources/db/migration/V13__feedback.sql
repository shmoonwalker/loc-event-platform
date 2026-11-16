CREATE TABLE feedback (
    id            uuid PRIMARY KEY DEFAULT uuidv7(),
    user_id       uuid REFERENCES app_user (id) ON DELETE SET NULL,
    message       varchar(2000) NOT NULL CHECK (length(trim(message)) > 0),
    contact_email varchar(254),
    created_at    timestamptz NOT NULL DEFAULT now(),
    handled_at    timestamptz,
    handled_by    uuid REFERENCES app_user (id) ON DELETE SET NULL,
    CHECK (handled_at IS NOT NULL OR handled_by IS NULL)
);
