package com.ai4dev.tinderfordogs.chat.service

import java.util.UUID

class ChatThreadNotFoundException(matchId: UUID) :
    RuntimeException("Chat thread not found for matchId: $matchId")
