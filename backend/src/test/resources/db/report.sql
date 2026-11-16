-- Test stand-in for Flyway V11__report.sql, which issue 95 owns.
-- Not a migration: the application creates app.report on the issue 95 branch.
CREATE TABLE IF NOT EXISTS app.report (
    id           uuid PRIMARY KEY DEFAULT uuidv7(),
    target_type  varchar NOT NULL CHECK (target_type IN ('EVENT', 'COMMENT', 'PHOTO')),
    target_id    uuid NOT NULL,
    reporter_id  uuid NOT NULL REFERENCES app.app_user (id),
    reason       varchar NOT NULL CHECK (reason IN ('WRONG_INFORMATION', 'SPAM', 'INAPPROPRIATE', 'OTHER')),
    details      varchar(1000),
    created_at   timestamptz NOT NULL DEFAULT now(),
    resolved_at  timestamptz,
    resolved_by  uuid REFERENCES app.app_user (id),
    resolution   varchar(32)
);

-- one open report per member per item
CREATE UNIQUE INDEX IF NOT EXISTS report_one_open_per_reporter
    ON app.report (target_type, target_id, reporter_id)
    WHERE resolved_at IS NULL;
