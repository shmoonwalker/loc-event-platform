-- object key of the uploaded profile picture in R2; null shows the preset
ALTER TABLE app_user ADD COLUMN avatar_key text;
