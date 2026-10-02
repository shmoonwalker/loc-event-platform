-- Source organizer facts stay on the event. Stable Loc IDs are minted in publication.
CREATE TABLE catalog.event_organizer
(
    id              BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    event_id        BIGINT  NOT NULL REFERENCES catalog.event (id) ON DELETE CASCADE,
    organizer_index INTEGER NOT NULL CHECK (organizer_index >= 0),
    external_id     TEXT    NOT NULL CHECK (btrim(external_id) <> ''),
    name            TEXT    NOT NULL CHECK (btrim(name) <> ''),
    description     TEXT,
    site            TEXT,
    CONSTRAINT event_organizer_event_index_key UNIQUE (event_id, organizer_index),
    CONSTRAINT event_organizer_event_external_id_key UNIQUE (event_id, external_id)
);

COMMENT ON TABLE catalog.event_organizer IS
    'Source organizer identity and display fields for one catalog event. Loc UUIDs live in publication.';
COMMENT ON COLUMN catalog.event_organizer.external_id IS
    'Ticketmaster promoter id, or the RVO organizer name. Identity is (event.source, external_id).';
COMMENT ON COLUMN catalog.event_organizer.site IS
    'Source-provided location or website. Empty when the source did not send one.';

-- RVO identity is the name, so existing names can become organizer rows without a rematch.
INSERT INTO catalog.event_organizer (event_id, organizer_index, external_id, name)
SELECT event_id,
       (row_number() OVER (PARTITION BY event_id ORDER BY first_seen) - 1),
       external_id,
       name
FROM (
         SELECT e.id              AS event_id,
                btrim(t.name)     AS external_id,
                btrim(t.name)     AS name,
                min(t.ord)        AS first_seen
         FROM catalog.event e
                  CROSS JOIN LATERAL unnest(e.organizer_names) WITH ORDINALITY AS t(name, ord)
         WHERE e.source = 'rvo'
           AND t.name IS NOT NULL
           AND btrim(t.name) <> ''
         GROUP BY e.id, btrim(t.name)
     ) rvo_organizers;

-- Ticketmaster identity is the promoter id, which names-only rows do not have.
ALTER TABLE catalog.event DROP COLUMN organizer_names;

-- Durable identities: intentionally no cascading foreign keys to the catalogue.
CREATE TABLE publication.organizer_identity
(
    loc_organizer_id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    source           TEXT NOT NULL,
    external_id      TEXT NOT NULL,
    UNIQUE (source, external_id)
);

COMMENT ON TABLE publication.organizer_identity IS
    'One Loc organizer UUID per source identity, the same type as loc_event_id. Reused when the same source key returns on later imports.';
COMMENT ON COLUMN publication.organizer_identity.loc_organizer_id IS
    'Stable product organizer id. UUID, matching publication.event_identity.loc_event_id.';

CREATE TRIGGER publication_source_lock BEFORE INSERT OR UPDATE OR DELETE ON catalog.event_organizer
    FOR EACH STATEMENT EXECUTE FUNCTION catalog.lock_publication_source();
