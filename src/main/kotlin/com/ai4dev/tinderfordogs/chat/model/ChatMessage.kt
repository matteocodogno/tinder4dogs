package com.ai4dev.tinderfordogs.chat.model

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.Table
import java.time.Instant
import java.util.UUID

@Entity
@Table(name = "chat_message")
class ChatMessage(
    @Column(name = "thread_id", nullable = false)
    val threadId: UUID,

    @Column(name = "sender_owner_id", nullable = false)
    val senderOwnerId: UUID,

    @Column(name = "content", nullable = false, length = 2000)
    val content: String,

    @Column(name = "sent_at", nullable = false, updatable = false)
    val sentAt: Instant = Instant.now(),

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    val id: UUID? = null,
)
