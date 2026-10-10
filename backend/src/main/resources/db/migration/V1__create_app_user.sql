CREATE TABLE app_user (
    id            uuid         PRIMARY KEY DEFAULT uuidv7(),
    email         varchar(254) NOT NULL UNIQUE CHECK (email = lower(email)),
    password_hash text,        -- null for users who only sign in with Google
    display_name  varchar(100) NOT NULL CHECK (length(trim(display_name)) > 0),
    role          text         NOT NULL DEFAULT 'USER' CHECK (role IN ('USER', 'ADMIN')),
    created_at    timestamptz  NOT NULL DEFAULT now()
);
