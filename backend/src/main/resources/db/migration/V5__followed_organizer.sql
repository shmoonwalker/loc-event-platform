CREATE TABLE followed_organizer (
                             user_id   uuid        NOT NULL REFERENCES app_user (id) ON DELETE CASCADE,
                             organizer_id uuid NOT NULL ,
                             followed_at timestamptz NOT NULL DEFAULT now(),
                             PRIMARY KEY (user_id, organizer_id)
);

CREATE INDEX followed_organizer_user_id_followed_at_idx ON followed_organizer(user_id, followed_at DESC);