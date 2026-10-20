--liquibase formatted sql

--changeset tinder4dogs:009-playdate-once
--comment: one live playdate per pair of dogs per day, so the same request twice creates one row
--
-- Partial: cancelled rows are left out, so a playdate that was cancelled can
-- be proposed again. The tool checks first and returns the existing row; this
-- index is what still holds when two requests arrive at the same instant.
CREATE UNIQUE INDEX ux_playdate_once
    ON playdate (from_dog_id, to_dog_id, scheduled_on)
    WHERE status <> 'CANCELLED';
--rollback DROP INDEX ux_playdate_once;
