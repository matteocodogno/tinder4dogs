--liquibase formatted sql

--changeset tinder4dogs:005-create-dog-mode
--comment: a dog's active modes, one row each, mirroring dog_preference
CREATE TABLE dog_mode (
    dog_id BIGINT      NOT NULL,
    mode   VARCHAR(10) NOT NULL,
    CONSTRAINT pk_dog_mode PRIMARY KEY (dog_id, mode),
    CONSTRAINT fk_dog_mode_dog FOREIGN KEY (dog_id)
        REFERENCES dog (id) ON DELETE CASCADE
);
--rollback DROP TABLE dog_mode;

--changeset tinder4dogs:006-seed-play-mode
--comment: every dog from before modes existed is looking for a playmate.
--
-- Modes are introduced with the invariant that a dog always has at least one
-- active mode. The rows that predate the column cannot choose, so they are
-- seeded with the default goal: play. Any of them can gain mating mode later,
-- per dog, once its mating details exist (F-02).
INSERT INTO dog_mode (dog_id, mode)
SELECT id, 'PLAY' FROM dog;
--rollback DELETE FROM dog_mode WHERE mode = 'PLAY';

--changeset tinder4dogs:007-add-mating-details
--comment: the self-declared mating details, nullable until mating mode is activated
--
-- These are bridging storage for F-02, which will own their input rules. F-03
-- only checks their presence when a dog activates mating mode, so they stay
-- nullable here on purpose.
ALTER TABLE dog
    ADD COLUMN pedigree     VARCHAR(200),
    ADD COLUMN neutered     BOOLEAN,
    ADD COLUMN health_status VARCHAR(200);
--rollback ALTER TABLE dog
--     DROP COLUMN pedigree,
--     DROP COLUMN neutered,
--     DROP COLUMN health_status;
