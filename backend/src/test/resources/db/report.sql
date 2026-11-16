-- Test stand-in for app.report. The Flyway migration belongs to issue 95; do not copy this into db/migration.
CREATE TABLE IF NOT EXISTS app.report (
    id           uuid        PRIMARY KEY DEFAULT uuidv7(),
    target_type  text        NOT NULL CHECK (target_type IN ('EVENT', 'COMMENT')),
    target_id    uuid        NOT NULL,
    reporter_id  uuid        NOT NULL REFERENCES app.app_user (id),
    reason       text        NOT NULL CHECK (reason IN ('WRONG_INFORMATION', 'SPAM', 'INAPPROPRIATE', 'OTHER')),
    details      text,
    created_at   timestamptz NOT NULL DEFAULT now(),
    resolved_at  timestamptz,
    resolved_by  uuid        REFERENCES app.app_user (id),
    resolution   text
);
