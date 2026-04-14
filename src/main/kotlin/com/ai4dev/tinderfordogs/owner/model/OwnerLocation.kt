package com.ai4dev.tinderfordogs.owner.model

import jakarta.persistence.Embeddable

@Embeddable
data class OwnerLocation(
    val city: String? = null,
    val country: String? = null,
    val latitude: Double? = null,
    val longitude: Double? = null,
    val consentGiven: Boolean = false,
)
