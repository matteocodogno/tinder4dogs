--liquibase formatted sql

--changeset tinder4dogs:001-create-chat-thread
CREATE TABLE chat_thread (
    id                    UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
    match_id              UUID         NOT NULL UNIQUE,
    owner_a_id            UUID         NOT NULL,
    owner_b_id            UUID         NOT NULL,
    last_message_preview  VARCHAR(100),
    last_message_at       TIMESTAMPTZ,
    created_at            TIMESTAMPTZ  NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_chat_thread_match_id   ON chat_thread(match_id);
CREATE INDEX idx_chat_thread_owner_a_id ON chat_thread(owner_a_id);
CREATE INDEX idx_chat_thread_owner_b_id ON chat_thread(owner_b_id);

--rollback DROP INDEX idx_chat_thread_owner_b_id;
--rollback DROP INDEX idx_chat_thread_owner_a_id;
--rollback DROP INDEX idx_chat_thread_match_id;
--rollback DROP TABLE chat_thread;

--changeset tinder4dogs:001-create-chat-message
CREATE TABLE chat_message (
    id                UUID          PRIMARY KEY DEFAULT gen_random_uuid(),
    thread_id         UUID          NOT NULL REFERENCES chat_thread(id) ON DELETE CASCADE,
    sender_owner_id   UUID          NOT NULL,
    content           VARCHAR(2000) NOT NULL,
    sent_at           TIMESTAMPTZ   NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_chat_message_thread_sent ON chat_message(thread_id, sent_at ASC);

--rollback DROP INDEX idx_chat_message_thread_sent;
--rollback DROP TABLE chat_message;
