CREATE TABLE refresh_token (
    id           uuid        PRIMARY KEY DEFAULT uuidv7(),
    user_id      uuid        NOT NULL REFERENCES app_user (id) ON DELETE CASCADE,
    token_hash   bytea       NOT NULL UNIQUE CHECK (octet_length(token_hash) = 32),
    family_id    uuid        NOT NULL,
    expires_at   timestamptz NOT NULL,
    revoked_at   timestamptz,
    replaced_by  uuid,
    created_at   timestamptz NOT NULL DEFAULT now()
);

CREATE INDEX refresh_token_user_id_idx ON refresh_token (user_id);
CREATE INDEX refresh_token_family_id_idx ON refresh_token (family_id);
