package com.ai4dev.tinder4dogs.playdate

import com.ai4dev.tinder4dogs.dog.DogRepository
import com.ai4dev.tinder4dogs.match.MatchScoreService
import org.slf4j.LoggerFactory
import org.springframework.ai.chat.model.ToolContext
import org.springframework.ai.tool.annotation.Tool
import org.springframework.ai.tool.annotation.ToolParam
import org.springframework.stereotype.Component

data class MatchCandidate(
    val dogId: Long,
    val name: String,
    val breed: String,
    val age: Int,
    val score: Double,
)

/**
 * The tools the playdate agent can call.
 *
 * The model sees each method's name, its description and the schema of its
 * parameters. It never sees [ToolContext]: the application puts the owner
 * there, so who is acting is not something the model can write.
 *
 * Every call logs one `tool=` line, so the console shows how many tools one
 * chat message really used.
 *
 * No @Transactional here on purpose: it would turn this bean into a proxy, and
 * the tools would be read off the proxy instead of this class.
 */
@Component
class PlaydateTools(
    private val dogs: DogRepository,
    private val scores: MatchScoreService,
) {

    private val log = LoggerFactory.getLogger(javaClass)

    @Tool(description = "Lists the dogs most compatible with the user's dog, best first.")
    fun findMatches(
        @ToolParam(description = "How many dogs to return, from 1 to 10") limit: Int,
        toolContext: ToolContext,
    ): List<MatchCandidate> {
        val owner = ownerOf(toolContext)
        log.info("tool=find_matches owner={} limit={}", owner, limit)

        val mine = dogs.findByOwnerSubject(owner)
        if (mine == null || !scores.canScore(mine)) {
            log.info("tool=find_matches result=EMPTY: the owner has no dog profile that can be scored")
            return emptyList()
        }

        // The same ranking as MatchController.bestFor.
        return dogs.findAll()
            .filter { it.id != mine.id && scores.canScore(it) }
            .map { MatchCandidate(it.id!!, it.name, it.breed, it.age, scores.score(mine, it)) }
            .sortedByDescending { it.score }
            .take(limit.coerceIn(1, 10))
    }

    private fun ownerOf(context: ToolContext): String = context.context[OWNER] as String

    companion object {
        /** The key under which PlaydateAgent puts the owner's subject. */
        const val OWNER = "owner"
    }
}
