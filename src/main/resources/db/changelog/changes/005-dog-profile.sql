--liquibase formatted sql

--changeset tinder4dogs:005-add-dog-profile-columns
--comment: profile attributes on the dog itself, all nullable so existing rows keep loading and a new profile may start with the four original fields only. owner_id stays nullable until the owner-aware write path ships, so the current create flow keeps working.
ALTER TABLE dog
    ADD COLUMN owner_id      VARCHAR(100),
    ADD COLUMN size          VARCHAR(10),
    ADD COLUMN energy_level  VARCHAR(10),
    ADD COLUMN pedigree      BOOLEAN,
    ADD COLUMN neutered      BOOLEAN,
    ADD COLUMN health_status VARCHAR(20),
    ADD COLUMN removed_at    TIMESTAMP WITH TIME ZONE;
--rollback ALTER TABLE dog
--rollback     DROP COLUMN removed_at,
--rollback     DROP COLUMN health_status,
--rollback     DROP COLUMN neutered,
--rollback     DROP COLUMN pedigree,
--rollback     DROP COLUMN energy_level,
--rollback     DROP COLUMN size,
--rollback     DROP COLUMN owner_id;

--changeset tinder4dogs:005-create-dog-temperament
--comment: a dog's temperament values, one row each from the fixed vocabulary, stored as strings to match @Enumerated(EnumType.STRING)
CREATE TABLE dog_temperament (
    dog_id      BIGINT       NOT NULL,
    temperament VARCHAR(20)  NOT NULL,
    CONSTRAINT pk_dog_temperament PRIMARY KEY (dog_id, temperament),
    CONSTRAINT fk_dog_temperament_dog FOREIGN KEY (dog_id)
        REFERENCES dog (id) ON DELETE CASCADE
);
--rollback DROP TABLE dog_temperament;

--changeset tinder4dogs:005-create-dog-photo
--comment: ordered photo references, zero-based, position 0 being the primary photo. Only the reference is stored, never image data.
CREATE TABLE dog_photo (
    dog_id    BIGINT       NOT NULL,
    position  INT          NOT NULL,
    reference TEXT         NOT NULL,
    CONSTRAINT pk_dog_photo PRIMARY KEY (dog_id, position),
    CONSTRAINT fk_dog_photo_dog FOREIGN KEY (dog_id)
        REFERENCES dog (id) ON DELETE CASCADE
);
--rollback DROP TABLE dog_photo;

--changeset tinder4dogs:005-backfill-dog-owner-id
--comment: every existing dog gets a distinct synthetic owner derived from its own id, including the corrupt legacy row (Nonna, age -3), which is assigned like any other and whose age is not touched.
UPDATE dog SET owner_id = 'legacy-' || id;
--rollback UPDATE dog SET owner_id = NULL;

--changeset tinder4dogs:005-active-owner-unique-index
--comment: one active profile per owner. The index spans only rows that are not removed, so removing a profile frees the owner for a replacement while the removed row keeps its data.
CREATE UNIQUE INDEX uq_dog_owner_active ON dog (owner_id) WHERE removed_at IS NULL;
--rollback DROP INDEX uq_dog_owner_active;
