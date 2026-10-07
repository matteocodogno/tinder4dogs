package com.ai4dev.tinder4dogs.config

import org.springframework.beans.factory.ObjectProvider
import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.security.config.annotation.web.builders.HttpSecurity
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity
import org.springframework.security.config.annotation.web.invoke
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository
import org.springframework.security.web.SecurityFilterChain
import org.springframework.security.web.csrf.CookieCsrfTokenRepository
import org.springframework.security.web.csrf.CsrfTokenRequestAttributeHandler
import org.springframework.web.cors.CorsConfiguration
import org.springframework.web.cors.CorsConfigurationSource
import org.springframework.web.cors.UrlBasedCorsConfigurationSource

/**
 * One filter chain, two modes.
 *
 * Google sign-in is configured only from the environment, so a fresh clone has
 * no client registration at all. Rather than fail to start, this chain asks
 * whether a [ClientRegistrationRepository] exists and, if it does not, lets
 * every request through as the local developer. That keeps the repository
 * runnable before an OAuth client has been handed out, and it is deliberately
 * obvious: if you reach the API without signing in, you are in that mode.
 */
@Configuration
@EnableWebSecurity
class SecurityConfig(
    @Value("\${app.spa.origin}") private val spaOrigin: String,
) {

    /**
     * CORS has to be configured inside Spring Security, not next to it. The
     * pre-flight request carries no cookies, so a security chain that runs
     * first sees an unauthenticated request and rejects it before the CORS
     * handling is ever reached.
     */
    @Bean
    fun corsConfigurationSource(): CorsConfigurationSource {
        val policy = CorsConfiguration()
        policy.allowedOrigins = listOf(spaOrigin)
        policy.allowedMethods = listOf("GET", "POST", "PUT", "DELETE", "OPTIONS")
        policy.allowedHeaders = listOf("*")
        policy.allowCredentials = true

        val source = UrlBasedCorsConfigurationSource()
        source.registerCorsConfiguration("/**", policy)
        return source
    }

    @Bean
    fun filterChain(
        http: HttpSecurity,
        clientRegistrations: ObjectProvider<ClientRegistrationRepository>,
    ): SecurityFilterChain {
        val googleIsConfigured = clientRegistrations.ifAvailable != null

        http {
            cors { }
            csrf {
                // A cookie the browser application can read, and the plain
                // request handler. The BREACH-protecting Xor handler expects
                // the ENCODED token back, and a browser application only ever
                // has the plain value, which is the one mistake this pairing
                // exists to avoid.
                //
                // Spring Security 7 bundles the correct pair behind
                // `csrf { spa() }`. Prefer that if it resolves on your version:
                // the reference documents it in Kotlin, but the CsrfDsl API
                // index does not list the function, so this file uses the two
                // properties both sources agree on.
                csrfTokenRepository = CookieCsrfTokenRepository.withHttpOnlyFalse()
                csrfTokenRequestHandler = CsrfTokenRequestAttributeHandler()
            }
            authorizeHttpRequests {
                authorize("/", permitAll)
                authorize("/error", permitAll)
                authorize("/oauth2/**", permitAll)
                authorize("/login/**", permitAll)
                authorize("/api/me", permitAll)
                authorize("/api/search", permitAll)
                if (googleIsConfigured) {
                    authorize(anyRequest, authenticated)
                } else {
                    authorize(anyRequest, permitAll)
                }
            }
            if (googleIsConfigured) {
                oauth2Login { }
            }
        }
        return http.build()
    }
}
