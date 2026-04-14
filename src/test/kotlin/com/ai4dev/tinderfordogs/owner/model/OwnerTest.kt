package com.ai4dev.tinderfordogs.owner.model

import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class OwnerTest {

    @Test
    fun `OwnerStatus has exactly PENDING_VERIFICATION and ACTIVE values`() {
        val values = OwnerStatus.entries
        assertEquals(2, values.size)
        assertEquals(OwnerStatus.PENDING_VERIFICATION, values[0])
        assertEquals(OwnerStatus.ACTIVE, values[1])
    }

    @Test
    fun `new Owner defaults to PENDING_VERIFICATION status`() {
        val owner = buildOwner()
        assertEquals(OwnerStatus.PENDING_VERIFICATION, owner.status)
    }

    @Test
    fun `new Owner has non-null timestamps`() {
        val owner = buildOwner()
        assertNotNull(owner.createdAt)
        assertNotNull(owner.updatedAt)
    }

    @Test
    fun `new Owner has no photo by default`() {
        val owner = buildOwner()
        assertNull(owner.photoPath)
    }

    @Test
    fun `OwnerLocation defaults consentGiven to false`() {
        val location = OwnerLocation()
        assertFalse(location.consentGiven)
    }

    @Test
    fun `OwnerLocation stores city and country`() {
        val location = OwnerLocation(city = "Rome", country = "Italy")
        assertEquals("Rome", location.city)
        assertEquals("Italy", location.country)
    }

    @Test
    fun `OwnerLocation stores truncated coordinates with consent`() {
        val location = OwnerLocation(
            latitude = 41.895,
            longitude = 12.48,
            consentGiven = true,
        )
        assertEquals(41.895, location.latitude)
        assertEquals(12.48, location.longitude)
        assertEquals(true, location.consentGiven)
    }

    @Test
    fun `OwnerLocation nullable coordinates default to null`() {
        val location = OwnerLocation(city = "Milan", country = "Italy")
        assertNull(location.latitude)
        assertNull(location.longitude)
    }

    @Test
    fun `Owner stores email and name`() {
        val owner = buildOwner(email = "marco@example.com", name = "Marco")
        assertEquals("marco@example.com", owner.email)
        assertEquals("Marco", owner.name)
    }

    @Test
    fun `Owner id is non-null UUID`() {
        val owner = buildOwner()
        assertNotNull(owner.id)
    }

    // ---------- helpers ----------

    private fun buildOwner(
        email: String = "test@example.com",
        name: String = "Test User",
    ) = Owner(
        email = email,
        passwordHash = "\$2a\$12\$fakehash",
        name = name,
    )
}
