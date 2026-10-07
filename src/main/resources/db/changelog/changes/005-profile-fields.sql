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
ALTER TABLE dog ADD COLUMN owner_subject VARCHAR(255);
ALTER TABLE dog ADD COLUMN owner_name    VARCHAR(100);
ALTER TABLE dog ADD COLUMN size          VARCHAR(10);
ALTER TABLE dog ADD COLUMN temperament   VARCHAR(20);

-- One dog per owner for v1. Several NULLs are allowed by a UNIQUE index in
-- PostgreSQL, which is what keeps the ownerless demo rows legal.
CREATE UNIQUE INDEX ux_dog_owner_subject ON dog (owner_subject);
--rollback DROP INDEX ux_dog_owner_subject;
--rollback ALTER TABLE dog DROP COLUMN temperament;
--rollback ALTER TABLE dog DROP COLUMN size;
--rollback ALTER TABLE dog DROP COLUMN owner_name;
--rollback ALTER TABLE dog DROP COLUMN owner_subject;
