CREATE TABLE report (
    id            uuid PRIMARY KEY DEFAULT uuidv7(),
    target_type   varchar(16) NOT NULL CHECK (target_type IN ('EVENT', 'COMMENT')),
    target_id     uuid NOT NULL,
    reporter_id   uuid NOT NULL REFERENCES app_user (id) ON DELETE CASCADE,
    reason        varchar(32) NOT NULL CHECK (reason IN ('WRONG_INFORMATION', 'SPAM', 'INAPPROPRIATE', 'OTHER')),
    details       varchar(1000),
    created_at    timestamptz NOT NULL DEFAULT now(),
    resolved_at   timestamptz,
    resolved_by   uuid REFERENCES app_user (id) ON DELETE SET NULL,
    resolution    varchar(32),
    CHECK (resolved_at IS NOT NULL OR (resolved_by IS NULL AND resolution IS NULL))
);

-- one open report per member per event or comment; a resolved row does not block a new one
CREATE UNIQUE INDEX report_open_per_member_idx
    ON report (target_type, target_id, reporter_id)
    WHERE resolved_at IS NULL;
