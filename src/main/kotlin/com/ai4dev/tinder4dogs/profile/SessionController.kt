package com.ai4dev.tinder4dogs.profile

import org.springframework.security.core.annotation.AuthenticationPrincipal
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
 * It answers 200 either way rather than 401, so that the unauthenticated case
 * is an ordinary answer instead of an error the client has to interpret.
 */
@RestController
class SessionController {

    @GetMapping("/api/me")
    fun me(@AuthenticationPrincipal principal: OidcUser?): SessionResponse =
        SessionResponse(
            authenticated = principal != null,
            ownerName = CurrentOwner.displayNameOf(principal),
            email = principal?.email,
        )
}
