--liquibase formatted sql

--changeset tinder-for-dogs:002-add-city-to-dog-profiles dbms:postgresql
ALTER TABLE dog_profiles
    ADD COLUMN IF NOT EXISTS city VARCHAR(100);
--rollback ALTER TABLE dog_profiles DROP COLUMN city;
