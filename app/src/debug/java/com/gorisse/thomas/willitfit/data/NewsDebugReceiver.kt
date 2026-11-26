package com.gorisse.thomas.willitfit.data

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log

/** Debug builds only: posts a note now, past the hours and the caps, to see what it looks like. */
class NewsDebugReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val kind = intent.getStringExtra("kind")?.let { runCatching { NewsKind.valueOf(it) }.getOrNull() } ?: NewsKind.REMINDER
        Log.i("WillItFit", "debug news $kind: allowed=${News.allowed(context)} posted=${News.allowed(context) && News.post(context, kind)}")
    }
}
