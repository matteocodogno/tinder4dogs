package com.ai4dev.tinder4dogs.chat

/**
 * Asks for a chat to exist between two dogs that have just matched.
 *
 * This is the single interface between the swipe/match flow (F-07/F-08) and the chat package.
 * The emitter of this event takes on every obligation below; the chat package relies on all of
 * them and on nothing else.
 *
 * **When to emit.** Exactly once, at the end of a successful swipe that produced a *mutual* match,
 * and only after that match has been durably committed. A swipe that does not close a mutual match
 * emits nothing, and a match that is rolled back must never have emitted.
 *
 * **Payload.** [dogAId] and [dogBId] are **unordered**: the emitter may pass the pair in either
 * order, and the same pair passed in either order refers to the same chat. Both dogs must exist and
 * must be **distinct**.
 *
 * **Delivery.** Synchronous and in-process, via Spring's `ApplicationEventPublisher`. The consumer
 * runs on the publishing thread, so the emitter must not assume the call is free — but it may
 * assume it is fast and that it returns.
 *
 * **Idempotency.** Consumer-side. The same pair always resolves to the same chat, so a redelivered
 * or duplicated event creates nothing new. The emitter does not need to deduplicate, and the
 * consumer tolerates it if the emitter fails to.
 *
 * **Rejection.** An invalid event (unknown dog, or the same dog named twice) is logged at WARN by
 * the consumer, which then creates nothing and returns normally. The consumer never throws back
 * into the emitter's flow: a committed match must never fail because chat creation declined. This
 * is a deliberate deviation from the `require` convention, contained within this package.
 *
 * **Schema evolution.** An in-process class owned and versioned by the chat package. Any rename or
 * reshape of this event is a revalidation trigger for the F-07/F-08 flow.
 */
data class CreateChatEvent(val dogAId: Long, val dogBId: Long)
