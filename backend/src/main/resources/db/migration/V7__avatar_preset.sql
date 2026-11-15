ALTER TABLE app_user ADD COLUMN avatar_preset text;

-- existing accounts get a random preset, like new ones
UPDATE app_user
SET avatar_preset = (ARRAY ['SUN', 'MOON', 'LEAF', 'WAVE', 'MOUNTAIN', 'STAR', 'FLOWER', 'CLOUD'])[floor(random() * 8)::int + 1];

ALTER TABLE app_user ALTER COLUMN avatar_preset SET NOT NULL;
