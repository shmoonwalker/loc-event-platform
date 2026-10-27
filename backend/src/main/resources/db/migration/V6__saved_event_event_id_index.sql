-- the primary key starts with user_id, so counting saves of one event needs its own index
CREATE INDEX saved_event_event_id_idx ON saved_event (event_id);
