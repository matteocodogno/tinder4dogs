--liquibase formatted sql

--changeset tinder-for-dogs:002-add-dog-interests dbms:postgresql
CREATE TABLE IF NOT EXISTS dog_interests
(
    dog_profile_id UUID         NOT NULL REFERENCES dog_profiles (id) ON DELETE CASCADE,
    interest       VARCHAR(100) NOT NULL
);

--rollback DROP TABLE dog_interests;
