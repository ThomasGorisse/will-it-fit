package com.gorisse.thomas.willitfit.data

import java.time.Duration
import java.time.Instant
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZonedDateTime

/** The two notes the app may leave: a new version's feature, and one reminder after a week away. */
enum class NewsKind { WHATS_NEW, REMINDER }

/** What the rules need to know. Times are epoch milliseconds; 0 means never. */
data class NewsState(
    /** The person said yes to news (in the app: the offer card or Settings). */
    val on: Boolean,
    /** The app was last opened. */
    val lastOpenAt: Long,
    /** Play last updated the app. */
    val updatedAt: Long,
    /** The notes posted, newest last. Only the last 30 days matter. */
    val sentAt: List<Long>,
)

/**
 * When a note may go out. Everything is decided on the phone: no server, no network.
 *
 * - At most [MAX_PER_WINDOW] notes in any 30 days.
 * - Never on a day the app was opened: the person is already here.
 * - Only from [FIRST_HOUR] to [LAST_HOUR], local time.
 * - A new version's note only if the app was not opened since the update.
 */
object NewsPolicy {
    const val MAX_PER_WINDOW = 2
    val WINDOW: Duration = Duration.ofDays(30)
    const val FIRST_HOUR = 10
    /** Exclusive: 20:00 is already too late. */
    const val LAST_HOUR = 20
    /** A new version's note: the day after the update, at 18:00. */
    const val WHATS_NEW_HOUR = 18
    val REMINDER_AFTER: Duration = Duration.ofDays(7)

    sealed interface Decision {
        data object Post : Decision
        /** Not now, but the rules allow it at [at]. */
        data class Later(val at: Long) : Decision
        /** Not this one: it does not come back. */
        data object Drop : Decision
    }

    fun decide(kind: NewsKind, state: NewsState, now: Long, zone: ZoneId): Decision {
        if (!state.on) return Decision.Drop
        if (kind == NewsKind.WHATS_NEW && (state.updatedAt == 0L || state.lastOpenAt >= state.updatedAt)) return Decision.Drop
        if (sameDay(state.lastOpenAt, now, zone)) return Decision.Drop
        if (sentInWindow(state.sentAt, now) >= MAX_PER_WINDOW) return Decision.Drop
        val window = nextWindow(now, zone)
        return if (window > now) Decision.Later(window) else Decision.Post
    }

    /** [now] itself when it is within the hours, else the next [FIRST_HOUR]. */
    fun nextWindow(now: Long, zone: ZoneId): Long {
        val t = Instant.ofEpochMilli(now).atZone(zone)
        return when {
            t.hour < FIRST_HOUR -> t.at(FIRST_HOUR)
            t.hour >= LAST_HOUR -> t.plusDays(1).at(FIRST_HOUR)
            else -> now
        }
    }

    /** A new version's note: the next day at [WHATS_NEW_HOUR], local time. */
    fun whatsNewAt(updatedAt: Long, zone: ZoneId): Long =
        Instant.ofEpochMilli(updatedAt).atZone(zone).plusDays(1).at(WHATS_NEW_HOUR)

    /** The reminder: a week after the last opening, moved into the hours. */
    fun reminderAt(openedAt: Long, zone: ZoneId): Long = nextWindow(openedAt + REMINDER_AFTER.toMillis(), zone)

    fun sentInWindow(sentAt: List<Long>, now: Long): Int = sentAt.count { now - it in 0 until WINDOW.toMillis() }

    /** [sentAt] once a note went out at [now]; older than the window is forgotten. */
    fun sent(sentAt: List<Long>, now: Long): List<Long> =
        sentAt.filter { now - it in 0 until WINDOW.toMillis() } + now

    private fun sameDay(a: Long, b: Long, zone: ZoneId): Boolean =
        a != 0L && Instant.ofEpochMilli(a).atZone(zone).toLocalDate() == Instant.ofEpochMilli(b).atZone(zone).toLocalDate()

    private fun ZonedDateTime.at(hour: Int): Long =
        this.with(LocalTime.of(hour, 0)).toInstant().toEpochMilli()
}
