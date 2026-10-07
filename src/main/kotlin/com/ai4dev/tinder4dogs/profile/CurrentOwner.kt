package com.ai4dev.tinder4dogs.profile

import org.springframework.security.oauth2.core.oidc.user.OidcUser

/**
 * Who is asking.
 *
 * When Google is configured this is the OIDC `sub`, which is stable per account
 * and is what the dog row is keyed on.
 *
 * When it is NOT configured the application still has to run, because a
 * participant clones this repository before anybody has handed out an OAuth
 * client. In that case every request is the same local developer. This is a
 * development convenience and it is deliberately obvious rather than clever:
 * if you can reach the API without signing in, you are in this mode.
 */
object CurrentOwner {

    const val LOCAL_DEVELOPER_SUBJECT = "local-developer"

    fun subjectOf(principal: OidcUser?): String =
        principal?.subject ?: LOCAL_DEVELOPER_SUBJECT

    fun displayNameOf(principal: OidcUser?): String? =
        principal?.fullName ?: principal?.email
}
