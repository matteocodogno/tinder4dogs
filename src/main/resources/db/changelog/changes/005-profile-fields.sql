--liquibase formatted sql

--changeset tinder4dogs:005-profile-fields
--comment: the owner, and the three profile fields the chatbot will fill
--
-- Every column here is NULLABLE, and that is a design decision rather than
-- laziness. The profile is filled in by a human, in stages, and from Module 12
-- onwards partly by a model that is explicitly told to leave what it does not
-- know empty. A NOT NULL column would force that model to invent a value, which
-- is the single most common failure of this kind of feature.
--
-- owner_subject is the OIDC `sub` of the signed-in Google account. The seeded
-- demo dogs have no owner, which is correct: they are the population you search
-- against, not profiles anybody signed in to create.
--
-- EVERY STATEMENT IS "IF NOT EXISTS", and that is not defensive habit. In this
-- course the CODE resets at every module tag and the DATABASE does not: the
-- volume outlives every branch. A lab that added a column -- by hand, with a
-- migration that was never committed, or through an agent that was given a
-- database prompt -- leaves that column behind for good, and `ai4dev module N
-- start` does not and cannot undo it. A baseline migration that refuses to
-- start on a database carrying that residue is a baseline that fails for half
-- the room, on the morning, for a reason that has nothing to do with the module.
--
-- What this deliberately does NOT paper over: a column that already exists with
-- a DIFFERENT type is left exactly as it is, and Hibernate's schema validation
-- then refuses to start the application. That is the right place for the problem
-- to surface, and the fix there is a clean database rather than more SQL:
--
--     docker compose down -v && docker compose up -d
ALTER TABLE dog ADD COLUMN IF NOT EXISTS owner_subject VARCHAR(255);
ALTER TABLE dog ADD COLUMN IF NOT EXISTS owner_name    VARCHAR(100);
ALTER TABLE dog ADD COLUMN IF NOT EXISTS size          VARCHAR(10);
ALTER TABLE dog ADD COLUMN IF NOT EXISTS temperament   VARCHAR(20);

-- One dog per owner for v1. Several NULLs are allowed by a UNIQUE index in
-- PostgreSQL, which is what keeps the ownerless demo rows legal.
CREATE UNIQUE INDEX IF NOT EXISTS ux_dog_owner_subject ON dog (owner_subject);
--rollback DROP INDEX IF EXISTS ux_dog_owner_subject;
--rollback ALTER TABLE dog DROP COLUMN IF EXISTS temperament;
--rollback ALTER TABLE dog DROP COLUMN IF EXISTS size;
--rollback ALTER TABLE dog DROP COLUMN IF EXISTS owner_name;
--rollback ALTER TABLE dog DROP COLUMN IF EXISTS owner_subject;
