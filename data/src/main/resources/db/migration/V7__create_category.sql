CREATE TABLE catalog.category
(
    id   BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    name TEXT NOT NULL,
    CONSTRAINT category_name_key UNIQUE (name)
);

INSERT INTO catalog.category (name)
VALUES ('Music & Nightlife'),
       ('Arts & Culture'),
       ('Sports'),
       ('Business & Careers'),
       ('Technology & Science'),
       ('Learning & Skills'),
       ('Nature & Sustainability'),
       ('Other');
