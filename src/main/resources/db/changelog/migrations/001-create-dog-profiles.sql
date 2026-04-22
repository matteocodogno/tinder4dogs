--liquibase formatted sql

--changeset tinder-for-dogs:001-create-dog-profiles dbms:postgresql
CREATE TABLE IF NOT EXISTS dog_profiles
(
    id         UUID         NOT NULL DEFAULT gen_random_uuid() PRIMARY KEY,
    name       VARCHAR(100) NOT NULL,
    breed      VARCHAR(100) NOT NULL,
    size       VARCHAR(20)  NOT NULL,
    age        INT          NOT NULL,
    gender     VARCHAR(10)  NOT NULL,
    bio        VARCHAR(500),
    created_at TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ  NOT NULL DEFAULT now()
);
--rollback DROP TABLE dog_profiles;
