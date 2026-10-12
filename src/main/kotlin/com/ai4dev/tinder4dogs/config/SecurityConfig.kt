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
import org.springframework.security.web.csrf.CsrfFilter
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

        // Spring Security 7's purpose-built preset for a single-page application:
        // a cookie-based repository, plus a request handler that resolves the
        // plain token value rather than the BREACH-encoded one, which is what a
        // browser application actually has.
        //
        // Called through the Java configurer rather than the Kotlin DSL on
        // purpose. The reference manual prints `csrf { spa() }` in Kotlin, the
        // CsrfDsl API index does not list the function, and the two cannot both
        // be right. CsrfConfigurer.spa() is documented since 7.0, so this goes
        // through the method that is certain to be there.
        http.csrf { csrf -> csrf.spa() }

        // And the cookie has to exist before the first unsafe request, not after
        // it has been refused once. See CsrfCookieFilter.
        http.addFilterAfter(CsrfCookieFilter(), CsrfFilter::class.java)

        http {
            cors { }
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
