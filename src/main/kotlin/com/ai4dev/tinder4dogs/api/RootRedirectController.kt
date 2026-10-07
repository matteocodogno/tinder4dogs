package com.ai4dev.tinder4dogs.api

import java.net.URI
import org.springframework.beans.factory.annotation.Value
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RestController

/**
 * Sends the browser back to the single-page application.
 *
 * This exists because of where OAuth2 login lands. After a successful sign-in
 * Spring Security redirects to the saved request, or to "/" when there is none,
 * and "/" on this backend serves nothing: the user interface is a separate
 * application on another origin.
 *
 * Spring documents no first-party way to redirect to a cross-origin SPA after
 * `oauth2Login` succeeds, so rather than guess at a success handler, "/" simply
 * bounces. One redirect, no security configuration, nothing to get wrong.
 */
@RestController
class RootRedirectController(
    @Value("\${app.spa.origin}") private val spaOrigin: String,
) {

    @GetMapping("/")
    fun toSpa(): ResponseEntity<Void> =
        ResponseEntity.status(302).location(URI.create(spaOrigin)).build()
}
