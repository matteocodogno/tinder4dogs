package com.ai4dev.tinder4dogs.profile

import org.springframework.beans.factory.ObjectProvider
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository
import org.springframework.security.oauth2.core.oidc.user.OidcUser
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RestController

data class SessionResponse(
    val authenticated: Boolean,
    val ownerName: String?,
    val email: String?,
)

/**
 * What the single-page application asks on load to decide which screen to show.
 *
 * It answers 200 either way rather than 401, so that "nobody is signed in" is an
 * ordinary answer instead of an error the client has to interpret.
 *
 * The part worth reading: **no principal has two completely different causes**,
 * and this endpoint has to tell them apart or the user interface lies.
 *
 *  - Google IS configured and you have not signed in: you are not
 *    authenticated, and the application shows you the sign-in screen.
 *  - Google is NOT configured, because nobody has handed out an OAuth client
 *    yet: every request is already the local developer as far as the profile
 *    and the search are concerned. Reporting "not authenticated" here would
 *    park the interface on a sign-in screen whose button leads nowhere, in
 *    front of an API that is wide open. That is not a safer answer, it is a
 *    wrong one.
 */
@RestController
class SessionController(
    private val clientRegistrations: ObjectProvider<ClientRegistrationRepository>,
) {

    @GetMapping("/api/me")
    fun me(@AuthenticationPrincipal principal: OidcUser?): SessionResponse {
        if (principal != null) {
            return SessionResponse(
                authenticated = true,
                ownerName = CurrentOwner.displayNameOf(principal),
                email = principal.email,
            )
        }

        if (clientRegistrations.ifAvailable != null) {
            return SessionResponse(authenticated = false, ownerName = null, email = null)
        }

        return SessionResponse(
            authenticated = true,
            ownerName = "local developer",
            email = null,
        )
    }
}
