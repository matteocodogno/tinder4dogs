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
@Table(name = "chat_thread")
class ChatThread(
    @Column(name = "match_id", nullable = false, unique = true)
    val matchId: UUID,

    @Column(name = "owner_a_id", nullable = false)
    val ownerAId: UUID,

    @Column(name = "owner_b_id", nullable = false)
    val ownerBId: UUID,

    @Column(name = "last_message_preview", length = 100)
    var lastMessagePreview: String? = null,

    @Column(name = "last_message_at")
    var lastMessageAt: Instant? = null,

    @Column(name = "created_at", nullable = false, updatable = false)
    val createdAt: Instant = Instant.now(),

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    val id: UUID? = null,
)
