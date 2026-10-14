package com.ai4dev.tinder4dogs.support

import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RestController

data class SupportAskRequest(val question: String)

/** One document the answer came from, as the interface sees it. */
data class SupportSourceResponse(
    val document: String,
    val title: String?,
    val section: String?,
    val updated: String?,
    val score: Double?,
)

/**
 * The answer, or the refusal, with the same shape for both.
 *
 * `answered` is first because it is the field a client must branch on and the
 * field a dashboard must count. A refusal arrives as a successful response with
 * `answered: false` and a reason, not as an error: it is an outcome the product
 * wants, and an interface that renders it as a failure teaches users that the
 * bot is broken when it is being careful.
 */
data class SupportAskResponse(
    val answered: Boolean,
    val answer: String,
    val sources: List<SupportSourceResponse>,
    val reason: String?,
    val promptVersion: String?,
)

/**
 * `POST /api/support/ask`: a question in, an answer with its sources out.
 *
 * Thin, like the profile chat's controller. Everything that decides what is
 * true lives in [SupportAskService], next to the retrieval that makes those
 * decisions checkable.
 *
 * ## It is behind authentication, and that was a decision
 *
 * This path is not in the permit-all list in the security configuration, so
 * once Google sign-in is configured it requires a session, like every other
 * API path except search and the session endpoint. A public support bot is a
 * reasonable product choice and it is a different product: every question is an
 * embedding call plus a generation, paid by whoever runs it, for anybody on the
 * internet who finds the URL. Making it public is a decision about abuse and
 * about the bill, and it belongs in the security configuration where the other
 * such decisions already are, not in an annotation here.
 */
@RestController
class SupportAskController(
    private val support: SupportAskService,
) {

    @PostMapping("/api/support/ask")
    fun ask(@RequestBody request: SupportAskRequest): ResponseEntity<SupportAskResponse> {
        val body = when (val outcome = support.ask(request.question)) {
            is SupportAnswer.Answered -> SupportAskResponse(
                answered = true,
                answer = outcome.answer,
                sources = outcome.sources.map {
                    SupportSourceResponse(
                        document = it.docId,
                        title = it.title,
                        section = it.section,
                        updated = it.updated,
                        score = it.score,
                    )
                },
                reason = null,
                promptVersion = outcome.promptVersion,
            )

            is SupportAnswer.Refused -> SupportAskResponse(
                answered = false,
                answer = outcome.message,
                sources = emptyList(),
                reason = outcome.reason.name,
                promptVersion = null,
            )
        }

        // The one status code that carries real information, as in the profile
        // chat: an installation with no gateway is a setup problem and must not
        // be presented as something the bot decided.
        if (!support.isAvailable()) {
            return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).body(body)
        }
        return ResponseEntity.ok(body)
    }
}
