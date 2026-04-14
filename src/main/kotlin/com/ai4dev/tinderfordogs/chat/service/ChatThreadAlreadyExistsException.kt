package com.ai4dev.tinderfordogs.chat.service

import java.util.UUID

class ChatThreadAlreadyExistsException(matchId: UUID) :
    RuntimeException("Chat thread already exists for matchId: $matchId")
