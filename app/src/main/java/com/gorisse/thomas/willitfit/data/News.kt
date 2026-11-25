package com.gorisse.thomas.willitfit.data

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.Worker
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.gorisse.thomas.willitfit.MainActivity
import com.gorisse.thomas.willitfit.R
import java.time.ZoneId
import java.util.concurrent.TimeUnit

/**
 * News and tips: a new version's feature, and one reminder after a week away. Both are local:
 * WorkManager wakes the app on the phone, nothing comes from a server (the app has no network).
 * The rules are [NewsPolicy]; this is where they meet Android.
 */
object News {
    const val CHANNEL = "news"
    /** Which screen a note opens: [OPEN_DOOR], [OPEN_BOOT], [OPEN_HALL] or [OPEN_TAPE]. */
    const val EXTRA_OPEN = "com.gorisse.thomas.willitfit.extra.OPEN"
    const val OPEN_DOOR = "door"
    const val OPEN_BOOT = "boot"
    const val OPEN_HALL = "hall"
    const val OPEN_TAPE = "tape"
    private const val NOTIFICATION_ID = 1
    private const val KEY_KIND = "kind"

    fun ensureChannel(context: Context) {
        val channel = NotificationChannel(CHANNEL, context.getString(R.string.news_channel), NotificationManager.IMPORTANCE_DEFAULT)
        context.getSystemService(NotificationManager::class.java)?.createNotificationChannel(channel)
    }

    /** The person said yes, and Android lets the note through: the permission, the app, the channel. */
    fun allowed(context: Context, prefs: Prefs = Prefs(context)): Boolean {
        if (!prefs.newsOn) return false
        if (Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) return false
        val manager = NotificationManagerCompat.from(context)
        if (!manager.areNotificationsEnabled()) return false
        val channel = manager.getNotificationChannel(CHANNEL)
        return channel == null || channel.importance != NotificationManager.IMPORTANCE_NONE
    }

    /** The version's note says something only when its title and text were written for it. */
    fun hasWhatsNew(context: Context): Boolean =
        context.getString(R.string.whats_new_title).isNotBlank() && context.getString(R.string.whats_new_body).isNotBlank()

    fun state(prefs: Prefs) = NewsState(prefs.newsOn, prefs.lastOpenAt, prefs.updatedAt, prefs.newsSentAt)

    /** Play updated the app: the version's note, the next day at 18:00, if this version has one. */
    fun onUpdated(context: Context, now: Long = System.currentTimeMillis()) {
        val prefs = Prefs(context)
        prefs.updatedAt = now
        if (!hasWhatsNew(context)) return
        enqueue(context, NewsKind.WHATS_NEW, NewsPolicy.whatsNewAt(now, ZoneId.systemDefault()), now)
    }

    /** The app is open: the reminder moves to a week from now, so it never comes while it is used. */
    fun onOpened(context: Context, now: Long = System.currentTimeMillis()) {
        val prefs = Prefs(context)
        prefs.lastOpenAt = now
        // The person is here: a note still on screen has done its job.
        NotificationManagerCompat.from(context).cancel(NOTIFICATION_ID)
        if (prefs.newsOn) enqueue(context, NewsKind.REMINDER, NewsPolicy.reminderAt(now, ZoneId.systemDefault()), now)
    }

    /** Off, from Settings or from the note itself: nothing waits any more. */
    fun turnOff(context: Context) {
        Prefs(context).newsOn = false
        NotificationManagerCompat.from(context).cancel(NOTIFICATION_ID)
        runCatching {
            val work = WorkManager.getInstance(context)
            NewsKind.entries.forEach { work.cancelUniqueWork(it.name) }
        }
    }

    fun turnOn(context: Context) {
        Prefs(context).newsOn = true
        ensureChannel(context)
        onOpened(context)
    }

    internal fun enqueue(context: Context, kind: NewsKind, at: Long, now: Long) {
        val request = OneTimeWorkRequestBuilder<NewsWorker>()
            .setInitialDelay((at - now).coerceAtLeast(0L), TimeUnit.MILLISECONDS)
            .setInputData(workDataOf(KEY_KIND to kind.name))
            .build()
        runCatching { WorkManager.getInstance(context).enqueueUniqueWork(kind.name, ExistingWorkPolicy.REPLACE, request) }
            .onFailure { Log.w("WillItFit", "News not scheduled", it) }
    }

    /** Posts [kind]'s note, with its Turn off action. True when Android took it. */
    fun post(context: Context, kind: NewsKind): Boolean {
        ensureChannel(context)
        val (title, body, open) = when (kind) {
            NewsKind.WHATS_NEW -> Triple(
                context.getString(R.string.whats_new_title), context.getString(R.string.whats_new_body),
                context.getString(R.string.whats_new_open),
            )
            NewsKind.REMINDER -> Triple(context.getString(R.string.reminder_title), context.getString(R.string.reminder_body), OPEN_DOOR)
        }
        if (title.isBlank() || body.isBlank()) return false
        if (Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) return false
        val flags = PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        val openIntent = Intent(context, MainActivity::class.java)
            .putExtra(EXTRA_OPEN, open)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
        val offIntent = Intent(context, NewsOffReceiver::class.java)
        val notification = NotificationCompat.Builder(context, CHANNEL)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(title)
            .setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setContentIntent(PendingIntent.getActivity(context, kind.ordinal, openIntent, flags))
            .addAction(0, context.getString(R.string.news_turn_off), PendingIntent.getBroadcast(context, 0, offIntent, flags))
            .setAutoCancel(true)
            .setCategory(NotificationCompat.CATEGORY_RECOMMENDATION)
            .build()
        return runCatching { NotificationManagerCompat.from(context).notify(NOTIFICATION_ID, notification) }
            .onFailure { Log.w("WillItFit", "News not posted", it) }
            .isSuccess
    }
}

/** Wakes at the time [NewsPolicy] chose, asks it again, and posts, waits or lets go. */
class NewsWorker(context: Context, params: WorkerParameters) : Worker(context, params) {
    override fun doWork(): Result {
        val kind = inputData.getString("kind")?.let { runCatching { NewsKind.valueOf(it) }.getOrNull() } ?: return Result.success()
        // Both kinds may wake together: one at a time, so the cap counts the other's note.
        synchronized(News) {
            val prefs = Prefs(applicationContext)
            val now = System.currentTimeMillis()
            when (val decision = NewsPolicy.decide(kind, News.state(prefs), now, ZoneId.systemDefault())) {
                NewsPolicy.Decision.Post -> if (News.allowed(applicationContext, prefs) && News.post(applicationContext, kind)) {
                    prefs.newsSentAt = NewsPolicy.sent(prefs.newsSentAt, now)
                }
                is NewsPolicy.Decision.Later -> News.enqueue(applicationContext, kind, decision.at, now)
                NewsPolicy.Decision.Drop -> Unit
            }
        }
        return Result.success()
    }
}

/** Play updated the app (Android tells the app itself, even when nobody opens it). */
class AppUpdatedReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_MY_PACKAGE_REPLACED) News.onUpdated(context)
    }
}

/** "Turn off" on a note: news stop, without opening the app. */
class NewsOffReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) = News.turnOff(context)
}
