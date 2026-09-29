--liquibase formatted sql

--changeset tinder4dogs:005-add-dog-location
--comment: an optional location for the dog, one coordinate pair in two columns
--
-- Nullable on purpose: a dog without a location simply never appears in a
-- nearby listing. Both-null-or-both-present is the entity's job (a single
-- optional location), not the schema's -- the same stance as age: the
-- schema stays permissive and the application copes.
ALTER TABLE dog
    ADD COLUMN latitude  DOUBLE PRECISION,
    ADD COLUMN longitude DOUBLE PRECISION;
--rollback ALTER TABLE dog DROP COLUMN longitude, DROP COLUMN latitude;
