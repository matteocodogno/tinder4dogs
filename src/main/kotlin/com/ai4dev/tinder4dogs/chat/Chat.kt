package com.ai4dev.tinder4dogs.chat

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.Table
import java.time.Instant

@Entity
@Table(name = "chat")
class Chat(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    var id: Long? = null,

    @Column(name = "dog_a_id", nullable = false)
    var dogAId: Long = 0,

    @Column(name = "dog_b_id", nullable = false)
    var dogBId: Long = 0,

    @Column(name = "created_at", nullable = false)
    var createdAt: Instant = Instant.EPOCH,

    @Column(name = "last_message_at")
    var lastMessageAt: Instant? = null,
)
