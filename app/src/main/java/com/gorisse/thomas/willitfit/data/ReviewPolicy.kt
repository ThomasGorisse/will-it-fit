package com.gorisse.thomas.willitfit.data

import java.util.concurrent.TimeUnit

/**
 * Where the app stands with Play's rating sheet. An answer is a door verdict seen or an AR photo
 * taken. A request counts once Play's flow has run to its end — never before Play answered.
 */
data class ReviewState(
    /** Answers the person got, in the app's life. */
    val answers: Int,
    /** Rating flows Play ran to their end. Play never says whether the sheet showed or was rated. */
    val requests: Int = 0,
    /** When the last of them ended, in epoch milliseconds; 0 when none did. */
    val lastRequestAt: Long = 0L,
    /** [answers] when the last of them ended. */
    val answersAtLastRequest: Int = 0,
)

/**
 * When to ask Play for its rating sheet: back home, after the second answer, then once more for
 * someone who keeps using the app — five answers and two months later. Never more than twice: Play
 * keeps its own quota too, and a third ask would only be noise.
 */
object ReviewPolicy {
    const val FIRST_AFTER_ANSWERS = 2
    const val AGAIN_AFTER_ANSWERS = 5
    val AGAIN_AFTER_MS: Long = TimeUnit.DAYS.toMillis(60)
    const val MAX_REQUESTS = 2

    fun shouldAsk(state: ReviewState, nowMs: Long): Boolean = when {
        state.requests >= MAX_REQUESTS -> false
        state.requests == 0 -> state.answers >= FIRST_AFTER_ANSWERS
        else -> state.answers - state.answersAtLastRequest >= AGAIN_AFTER_ANSWERS &&
            nowMs - state.lastRequestAt >= AGAIN_AFTER_MS
    }

    /** The state once Play's flow ended. */
    fun requested(state: ReviewState, nowMs: Long): ReviewState =
        state.copy(requests = state.requests + 1, lastRequestAt = nowMs, answersAtLastRequest = state.answers)

    /**
     * 3.0.2 stored only "asked once" (`review_asked`), set before Play even answered. Such a person
     * counts as asked once, from the day this build first reads it, so the second ask still waits
     * its five answers and two months.
     */
    fun migrated(answers: Int, legacyAsked: Boolean, nowMs: Long): ReviewState =
        if (legacyAsked) ReviewState(answers, requests = 1, lastRequestAt = nowMs, answersAtLastRequest = answers)
        else ReviewState(answers)
}
