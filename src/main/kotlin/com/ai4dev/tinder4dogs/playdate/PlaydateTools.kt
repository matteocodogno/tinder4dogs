package com.ai4dev.tinder4dogs.playdate

import com.ai4dev.tinder4dogs.dog.DogRepository
import com.ai4dev.tinder4dogs.match.MatchScoreService
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeParseException
import org.slf4j.LoggerFactory
import org.springframework.ai.chat.model.ToolContext
import org.springframework.ai.tool.annotation.Tool
import org.springframework.ai.tool.annotation.ToolParam
import org.springframework.dao.DataIntegrityViolationException
import org.springframework.stereotype.Component

data class MatchCandidate(
    val dogId: Long,
    val name: String,
    val breed: String,
    val age: Int,
    val score: Double,
    /** Written by the other dog's owner. Owners want to read it before choosing. */
    val bio: String?,
)

data class PlaydateSummary(
    val playdateId: Long,
    val withDog: String,
    val date: LocalDate,
    val park: Park,
    val status: PlaydateStatus,
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
            .map { MatchCandidate(it.id!!, it.name, it.breed, it.age, scores.score(mine, it), it.bio) }
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
    @Tool(description = "Creates a playdate proposal (status DRAFT) between the user's dog and another dog. Returns CREATED id=<id>, or REJECTED: <reason>.")
    fun proposePlaydate(
        @ToolParam(description = "Id of the other dog, from findMatches") withDogId: Long,
        @ToolParam(description = "Day of the playdate, ISO format yyyy-MM-dd") date: String,
        @ToolParam(description = "Park where the dogs meet") park: Park,
        toolContext: ToolContext,
    ): String {
        val owner = ownerOf(toolContext)
        log.info("tool=propose_playdate owner={} withDogId={} date={} park={}", owner, withDogId, date, park)

        // Whose dog is decided by the session, never by an argument. Before
        // LAB 5 this was a parameter, fromDogId, and the user could choose it.
        val fromDogId = dogs.findByOwnerSubject(owner)?.id
            ?: return rejected("the user has no dog profile yet")

        val day = try {
            LocalDate.parse(date)
        } catch (failure: DateTimeParseException) {
            return rejected("date must be in the format yyyy-MM-dd")
        }
        val today = LocalDate.now()
        if (day.isBefore(today.plusDays(1)) || day.isAfter(today.plusDays(MAX_DAYS_AHEAD))) {
            return rejected("date must be between tomorrow and $MAX_DAYS_AHEAD days from today")
        }
        if (!dogs.existsById(withDogId)) return rejected("dog $withDogId does not exist")
        if (fromDogId == withDogId) return rejected("a dog cannot meet itself")

        // Idempotent: the same request twice leaves the same state as once.
        val existing = playdates.findFirstByFromDogIdAndToDogIdAndScheduledOnAndStatusNot(
            fromDogId, withDogId, day, PlaydateStatus.CANCELLED,
        )
        if (existing != null) {
            log.info("tool=propose_playdate result=ALREADY_EXISTS id={}", existing.id)
            return "ALREADY_EXISTS id=${existing.id}"
        }

        // A guarantee, in code: whatever the model was persuaded to do, it cannot
        // propose more than this many playdates per owner per day. Checked after
        // ALREADY_EXISTS, so repeating a request never uses up the limit.
        val since = LocalDate.now().atStartOfDay(ZoneId.systemDefault()).toInstant()
        if (playdates.countByCreatedByAndCreatedAtGreaterThanEqual(owner, since) >= MAX_PROPOSALS_PER_DAY) {
            return rejected("daily limit of $MAX_PROPOSALS_PER_DAY proposals reached")
        }

        val saved = try {
            playdates.save(
                Playdate(
                    fromDogId = fromDogId,
                    toDogId = withDogId,
                    scheduledOn = day,
                    park = park,
                    status = PlaydateStatus.DRAFT,
                    createdBy = owner,
                ),
            )
        } catch (race: DataIntegrityViolationException) {
            // Two requests at the same instant: the unique index let one through.
            return rejected("this playdate already exists")
        }
        log.info("tool=propose_playdate result=CREATED id={}", saved.id)
        return "CREATED id=${saved.id}"
    }

    /**
     * The playdates of the user's dog, so the agent can find an id to cancel.
     * Needed because the conversation keeps only text, not earlier tool results.
     */
    @Tool(description = "Lists the playdates of the user's dog, newest first, with their ids.")
    fun listPlaydates(toolContext: ToolContext): List<PlaydateSummary> {
        val owner = ownerOf(toolContext)
        log.info("tool=list_playdates owner={}", owner)
        val dogId = dogs.findByOwnerSubject(owner)?.id ?: return emptyList()
        val rows = playdates.findByFromDogIdOrToDogIdOrderByIdDesc(dogId, dogId)
        val names = dogs.findAllById(rows.flatMap { listOf(it.fromDogId, it.toDogId) }.toSet())
            .associate { it.id to it.name }
        return rows.map {
            val other = if (it.fromDogId == dogId) it.toDogId else it.fromDogId
            PlaydateSummary(it.id!!, names[other] ?: "?", it.scheduledOn, it.park, it.status)
        }
    }

    /**
     * Cancels a playdate. Reversible by design: the row is kept, with its status
     * and the moment it was cancelled.
     */
    @Tool(description = "Cancels a playdate. Returns CANCELLED id=<id>, or REJECTED: <reason>.")
    fun cancelPlaydate(
        @ToolParam(description = "Id of the playdate, from listPlaydates") playdateId: Long,
        toolContext: ToolContext,
    ): String {
        val owner = ownerOf(toolContext)
        log.info("tool=cancel_playdate owner={} playdateId={}", owner, playdateId)

        val playdate = playdates.findById(playdateId).orElse(null)
            ?: return rejected("playdate $playdateId does not exist")
        // Authority from the session: only the owner who created it can cancel it.
        if (playdate.createdBy != owner) return rejected("not your playdate")
        if (playdate.status == PlaydateStatus.CANCELLED) {
            log.info("tool=cancel_playdate result=ALREADY_CANCELLED id={}", playdateId)
            return "ALREADY_CANCELLED id=$playdateId"
        }

        playdate.status = PlaydateStatus.CANCELLED
        playdate.cancelledAt = Instant.now()
        playdates.save(playdate)
        log.info("tool=cancel_playdate result=CANCELLED id={}", playdateId)
        return "CANCELLED id=$playdateId"
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

        const val MAX_PROPOSALS_PER_DAY = 3L
    }
}
