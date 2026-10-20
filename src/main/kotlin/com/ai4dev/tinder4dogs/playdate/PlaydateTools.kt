package com.ai4dev.tinder4dogs.playdate

import com.ai4dev.tinder4dogs.dog.DogRepository
import com.ai4dev.tinder4dogs.match.MatchScoreService
import java.time.LocalDate
import java.time.format.DateTimeParseException
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
    private val playdates: PlaydateRepository,
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

    /**
     * Creates a playdate in status DRAFT.
     *
     * The arguments are written by the model, and the model writes what the
     * user asked: every one is checked here, in code. A bad value returns
     * `REJECTED: <reason>` instead of throwing, so the agent can read the reason
     * and ask the user again. The park needs no check: the enum in the schema
     * already limits what the model can send.
     */
    @Tool(description = "Creates a playdate proposal (status DRAFT) between two dogs. Returns CREATED id=<id>, or REJECTED: <reason>.")
    fun proposePlaydate(
        @ToolParam(description = "Id of the user's dog") fromDogId: Long,
        @ToolParam(description = "Id of the other dog, from findMatches") withDogId: Long,
        @ToolParam(description = "Day of the playdate, ISO format yyyy-MM-dd") date: String,
        @ToolParam(description = "Park where the dogs meet") park: Park,
        toolContext: ToolContext,
    ): String {
        val owner = ownerOf(toolContext)
        log.info(
            "tool=propose_playdate owner={} fromDogId={} withDogId={} date={} park={}",
            owner, fromDogId, withDogId, date, park,
        )

        val day = try {
            LocalDate.parse(date)
        } catch (failure: DateTimeParseException) {
            return rejected("date must be in the format yyyy-MM-dd")
        }
        val today = LocalDate.now()
        if (day.isBefore(today.plusDays(1)) || day.isAfter(today.plusDays(MAX_DAYS_AHEAD))) {
            return rejected("date must be between tomorrow and $MAX_DAYS_AHEAD days from today")
        }
        if (!dogs.existsById(fromDogId)) return rejected("dog $fromDogId does not exist")
        if (!dogs.existsById(withDogId)) return rejected("dog $withDogId does not exist")
        if (fromDogId == withDogId) return rejected("a dog cannot meet itself")

        val saved = playdates.save(
            Playdate(
                fromDogId = fromDogId,
                toDogId = withDogId,
                scheduledOn = day,
                park = park,
                status = PlaydateStatus.DRAFT,
                createdBy = owner,
            ),
        )
        log.info("tool=propose_playdate result=CREATED id={}", saved.id)
        return "CREATED id=${saved.id}"
    }

    private fun rejected(reason: String): String {
        log.info("tool result=REJECTED: {}", reason)
        return "REJECTED: $reason"
    }

    private fun ownerOf(context: ToolContext): String = context.context[OWNER] as String

    companion object {
        /** The key under which PlaydateAgent puts the owner's subject. */
        const val OWNER = "owner"

        const val MAX_DAYS_AHEAD = 14L
    }
}
