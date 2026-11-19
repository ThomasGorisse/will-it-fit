package com.gorisse.thomas.willitfit.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.concurrent.TimeUnit

class ReviewPolicyTest {

    private val day = TimeUnit.DAYS.toMillis(1)
    private val t0 = 1_800_000_000_000L

    @Test
    fun `not before the second answer`() {
        assertFalse(ReviewPolicy.shouldAsk(ReviewState(answers = 0), t0))
        assertFalse(ReviewPolicy.shouldAsk(ReviewState(answers = 1), t0))
        assertTrue(ReviewPolicy.shouldAsk(ReviewState(answers = 2), t0))
    }

    @Test
    fun `a request Play could not run is not counted, so the next pause asks again`() {
        // Nothing is recorded until the flow ended: the state is the same, and so is the answer.
        val state = ReviewState(answers = 3)
        assertTrue(ReviewPolicy.shouldAsk(state, t0))
        assertTrue(ReviewPolicy.shouldAsk(state, t0 + 1))
    }

    @Test
    fun `once Play ran the flow, the next ask waits five answers and two months`() {
        val asked = ReviewPolicy.requested(ReviewState(answers = 2), t0)
        assertEquals(ReviewState(answers = 2, requests = 1, lastRequestAt = t0, answersAtLastRequest = 2), asked)

        // Many answers, too soon.
        assertFalse(ReviewPolicy.shouldAsk(asked.copy(answers = 30), t0 + 59 * day))
        // Long enough, too few answers.
        assertFalse(ReviewPolicy.shouldAsk(asked.copy(answers = 6), t0 + 365 * day))
        // Both.
        assertTrue(ReviewPolicy.shouldAsk(asked.copy(answers = 7), t0 + 60 * day))
    }

    @Test
    fun `never a third time`() {
        var state = ReviewPolicy.requested(ReviewState(answers = 2), t0)
        state = ReviewPolicy.requested(state.copy(answers = 7), t0 + 60 * day)
        assertEquals(2, state.requests)
        assertFalse(ReviewPolicy.shouldAsk(state.copy(answers = 1_000), t0 + 10 * 365 * day))
    }

    @Test
    fun `a clock set back does not ask early`() {
        val asked = ReviewPolicy.requested(ReviewState(answers = 2), t0)
        assertFalse(ReviewPolicy.shouldAsk(asked.copy(answers = 50), t0 - 100 * day))
    }

    @Test
    fun `someone 3_0_2 already asked counts as asked once, from the update`() {
        val migrated = ReviewPolicy.migrated(answers = 9, legacyAsked = true, nowMs = t0)
        assertEquals(ReviewState(answers = 9, requests = 1, lastRequestAt = t0, answersAtLastRequest = 9), migrated)
        assertFalse(ReviewPolicy.shouldAsk(migrated, t0))
        assertFalse(ReviewPolicy.shouldAsk(migrated.copy(answers = 14), t0 + 59 * day))
        assertTrue(ReviewPolicy.shouldAsk(migrated.copy(answers = 14), t0 + 60 * day))
    }

    @Test
    fun `someone 3_0_2 never asked starts from zero requests, answers kept`() {
        val migrated = ReviewPolicy.migrated(answers = 1, legacyAsked = false, nowMs = t0)
        assertEquals(ReviewState(answers = 1), migrated)
        assertTrue(ReviewPolicy.shouldAsk(migrated.copy(answers = 2), t0))
    }
}
