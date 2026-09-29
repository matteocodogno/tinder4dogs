package com.ai4dev.tinder4dogs.chat

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.Table
import java.time.Instant

@Entity
@Table(name = "message")
class Message(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    var id: Long? = null,

    @Column(name = "chat_id", nullable = false)
    var chatId: Long = 0,

    @Column(name = "sender_dog_id", nullable = false)
    var senderDogId: Long = 0,

    @Column(nullable = false, length = 2000)
    var text: String = "",

    @Column(name = "accepted_at", nullable = false)
    var acceptedAt: Instant = Instant.EPOCH,
)
