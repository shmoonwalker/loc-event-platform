CREATE TABLE hidden_event (
    event_id   uuid PRIMARY KEY, -- show id, publication.event_snapshot.loc_group_id
    hidden_by  uuid NOT NULL REFERENCES app_user (id),
    reason     varchar(1000) NOT NULL CHECK (length(trim(reason)) > 0),
    hidden_at  timestamptz NOT NULL DEFAULT now()
);
