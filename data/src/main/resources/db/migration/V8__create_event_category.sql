-- Other is a real category. An event is linked to it only when no other category applies.
CREATE TABLE catalog.event_category
(
    event_id    BIGINT NOT NULL REFERENCES catalog.event (id) ON DELETE CASCADE,
    category_id BIGINT NOT NULL REFERENCES catalog.category (id),
    PRIMARY KEY (event_id, category_id)
);
