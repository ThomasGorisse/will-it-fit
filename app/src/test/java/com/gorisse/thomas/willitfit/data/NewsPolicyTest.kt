package com.gorisse.thomas.willitfit.data

import com.gorisse.thomas.willitfit.data.NewsPolicy.Decision
import java.time.LocalDateTime
import java.time.ZoneId
import java.util.concurrent.TimeUnit
import org.junit.Assert.assertEquals
import org.junit.Test

class NewsPolicyTest {
    private val paris = ZoneId.of("Europe/Paris")
    private fun at(day: Int, hour: Int, minute: Int = 0) =
        LocalDateTime.of(2026, 12, day, hour, minute).atZone(paris).toInstant().toEpochMilli()
    private val day = TimeUnit.DAYS.toMillis(1)

    private fun state(on: Boolean = true, opened: Long = at(1, 12), updated: Long = 0L, sent: List<Long> = emptyList()) =
        NewsState(on, opened, updated, sent)

    @Test fun `off says nothing`() =
        assertEquals(Decision.Drop, NewsPolicy.decide(NewsKind.REMINDER, state(on = false), at(8, 12), paris))

    @Test fun `the reminder goes out a week after the last opening, in the hours`() {
        assertEquals(at(8, 12), NewsPolicy.reminderAt(at(1, 12), paris))
        assertEquals(Decision.Post, NewsPolicy.decide(NewsKind.REMINDER, state(), at(8, 12), paris))
    }

    @Test fun `opened late in the evening, the reminder waits for ten the next morning`() =
        assertEquals(at(9, 10), NewsPolicy.reminderAt(at(1, 22, 30), paris))

    @Test fun `before ten or from eight in the evening, it waits for the hours`() {
        assertEquals(Decision.Later(at(8, 10)), NewsPolicy.decide(NewsKind.REMINDER, state(), at(8, 7), paris))
        assertEquals(Decision.Later(at(9, 10)), NewsPolicy.decide(NewsKind.REMINDER, state(), at(8, 20), paris))
        assertEquals(Decision.Post, NewsPolicy.decide(NewsKind.REMINDER, state(), at(8, 19, 59), paris))
    }

    @Test fun `never on a day the app was opened`() =
        assertEquals(Decision.Drop, NewsPolicy.decide(NewsKind.REMINDER, state(opened = at(8, 9)), at(8, 12), paris))

    @Test fun `two notes in thirty days, then nothing`() {
        val two = listOf(at(1, 12) - 20 * day, at(1, 12) - 2 * day)
        assertEquals(Decision.Drop, NewsPolicy.decide(NewsKind.REMINDER, state(sent = two), at(8, 12), paris))
        val oneOld = listOf(at(8, 12) - 31 * day, at(8, 12) - 2 * day)
        assertEquals(Decision.Post, NewsPolicy.decide(NewsKind.REMINDER, state(sent = oneOld), at(8, 12), paris))
    }

    @Test fun `sent forgets what is older than thirty days`() =
        assertEquals(listOf(at(8, 12) - 2 * day, at(8, 12)), NewsPolicy.sent(listOf(at(8, 12) - 40 * day, at(8, 12) - 2 * day), at(8, 12)))

    @Test fun `the version's note comes the next day at six in the evening`() {
        assertEquals(at(4, 18), NewsPolicy.whatsNewAt(at(3, 2, 15), paris))
        val updated = state(opened = at(1, 12), updated = at(3, 2, 15))
        assertEquals(Decision.Post, NewsPolicy.decide(NewsKind.WHATS_NEW, updated, at(4, 18), paris))
    }

    @Test fun `no version's note once the app was opened after the update`() {
        val opened = state(opened = at(3, 9), updated = at(3, 2, 15))
        assertEquals(Decision.Drop, NewsPolicy.decide(NewsKind.WHATS_NEW, opened, at(4, 18), paris))
    }

    @Test fun `no version's note without an update`() =
        assertEquals(Decision.Drop, NewsPolicy.decide(NewsKind.WHATS_NEW, state(updated = 0L), at(4, 18), paris))

    @Test fun `never opened since the install still gets a note`() =
        assertEquals(Decision.Post, NewsPolicy.decide(NewsKind.REMINDER, state(opened = 0L), at(8, 12), paris))
}
