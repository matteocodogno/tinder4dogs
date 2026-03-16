package com.ai4dev.tinderfordogs.match.model

import java.util.UUID

class DogNotFoundException(
    dogId: UUID,
) : RuntimeException("Dog not found: $dogId")
