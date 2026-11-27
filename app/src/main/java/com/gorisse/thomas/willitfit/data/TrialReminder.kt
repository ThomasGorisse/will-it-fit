package com.gorisse.thomas.willitfit.data

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
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
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.util.concurrent.TimeUnit

/** One account note, independent of news, with no request for notification permission. */
object TrialReminder {
    private const val WORK = "premium_trial_reminder"
    private const val CHANNEL = "account"
    private const val NOTIFICATION_ID = 2
    const val OPEN_SETTINGS = "settings"

    /** KEEP survives repeated purchase reads without moving the reminder or posting twice. */
    fun schedule(context: Context, token: String, purchaseTime: Long, price: String) {
        val now = System.currentTimeMillis()
        val end = purchaseTime + TimeUnit.DAYS.toMillis(3)
        val prefs = Prefs(context)
        if (prefs.trialRemindedToken == token || now >= end) return
        val request = OneTimeWorkRequestBuilder<TrialReminderWorker>()
            .setInitialDelay(trialReminderAt(purchaseTime, now, ZoneId.systemDefault()) - now, TimeUnit.MILLISECONDS)
            .setInputData(workDataOf("token" to token, "end" to end, "price" to price))
            .build()
        runCatching { WorkManager.getInstance(context).enqueueUniqueWork(WORK, ExistingWorkPolicy.KEEP, request) }
    }

    /** A successful Play read with no purchase removes both the work and any old note. */
    fun cancel(context: Context) {
        runCatching { WorkManager.getInstance(context).cancelUniqueWork(WORK) }
        NotificationManagerCompat.from(context).cancel(NOTIFICATION_ID)
    }

    /** Checks permission again at delivery; a refusal never starts a permission flow. */
    fun post(context: Context, token: String, end: Long, price: String) {
        val prefs = Prefs(context)
        if (prefs.trialToken != token || prefs.trialRemindedToken == token || System.currentTimeMillis() >= end) return
        if (Build.VERSION.SDK_INT >= 33 && ContextCompat.checkSelfPermission(context,
                Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) return
        val manager = NotificationManagerCompat.from(context)
        if (!manager.areNotificationsEnabled()) return
        context.getSystemService(NotificationManager::class.java)?.createNotificationChannel(
            NotificationChannel(CHANNEL, context.getString(R.string.premium_account_channel), NotificationManager.IMPORTANCE_DEFAULT),
        )
        val intent = Intent(context, MainActivity::class.java).putExtra(News.EXTRA_OPEN, OPEN_SETTINGS)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
        val body = context.getString(R.string.premium_trial_reminder_body, price, localizedDate(end))
        val note = NotificationCompat.Builder(context, CHANNEL)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(context.getString(R.string.premium_trial_reminder_title))
            .setContentText(body).setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setContentIntent(PendingIntent.getActivity(context, 20, intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE))
            .setAutoCancel(true).setCategory(NotificationCompat.CATEGORY_REMINDER).build()
        runCatching { manager.notify(NOTIFICATION_ID, note) }.onSuccess { prefs.trialRemindedToken = token }
    }
}

/** The date follows the phone's language and time zone, including daylight saving. */
fun localizedDate(at: Long): String = DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM)
    .withLocale(java.util.Locale.getDefault()).format(Instant.ofEpochMilli(at).atZone(ZoneId.systemDefault()))

/** WorkManager wakes locally; the token must still be the trial we last read from Play. */
class TrialReminderWorker(context: Context, params: WorkerParameters) : Worker(context, params) {
    override fun doWork(): Result {
        val token = inputData.getString("token") ?: return Result.success()
        val price = inputData.getString("price") ?: return Result.success()
        TrialReminder.post(applicationContext, token, inputData.getLong("end", 0), price)
        return Result.success()
    }
}
