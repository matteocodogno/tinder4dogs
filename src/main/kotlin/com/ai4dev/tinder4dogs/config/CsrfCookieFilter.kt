package com.ai4dev.tinder4dogs.config

import jakarta.servlet.FilterChain
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.springframework.security.web.csrf.CsrfToken
import org.springframework.web.filter.OncePerRequestFilter

/**
 * Makes the CSRF cookie actually appear.
 *
 * Spring Security defers the CSRF token: the repository writes the cookie when
 * something READS the token, and a plain GET never does. The consequence is
 * precise and was measured on this application before this filter existed:
 *
 *   - `GET /api/me` returned no cookie at all
 *   - the first POST of a session therefore carried no token and was refused
 *   - the 403 response wrote the cookie on its way out
 *   - every later POST worked
 *
 * So the feature appeared to work, and what it actually did was fail once per
 * session, invisibly, before starting to work. In a room of twenty people that
 * is twenty first messages that error, get retried, and succeed, with nobody
 * able to say why.
 *
 * Reading the token here, on every request, is what forces the cookie out in
 * time for the first unsafe request to carry it.
 */
class CsrfCookieFilter : OncePerRequestFilter() {

    override fun doFilterInternal(
        request: HttpServletRequest,
        response: HttpServletResponse,
        filterChain: FilterChain,
    ) {
        // The getToken() call is the point. Its value is not needed; asking for
        // it is what resolves the deferred token and saves the cookie.
        (request.getAttribute(CsrfToken::class.java.name) as? CsrfToken)?.token
        filterChain.doFilter(request, response)
    }
}
