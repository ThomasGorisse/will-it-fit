package com.gorisse.thomas.willitfit.data

import android.content.Context
import android.content.Intent
import androidx.core.content.pm.ShortcutInfoCompat
import androidx.core.content.pm.ShortcutManagerCompat
import androidx.core.graphics.drawable.IconCompat
import com.gorisse.thomas.willitfit.MainActivity
import com.gorisse.thomas.willitfit.R
import com.gorisse.thomas.willitfit.ui.label
import com.gorisse.thomas.willitfit.ui.thumbnail

fun publishSizeShortcuts(context: Context, prefs: Prefs) {
    val entries = SizeShortcuts.entries(
        prefs.recentSizes, prefs.unit, ShortcutManagerCompat.getMaxShortcutCountPerActivity(context),
    ) { context.getString(it.label()) }
    val shortcuts = entries.mapIndexed { rank, entry ->
        val intent = Intent(context, MainActivity::class.java).setAction(SizeShortcuts.ACTION)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
        entry.extras.forEach { (key, value) ->
            when (value) {
                is Float -> intent.putExtra(key, value)
                is String -> intent.putExtra(key, value)
            }
        }
        ShortcutInfoCompat.Builder(context, entry.id)
            .setShortLabel(entry.shortLabel)
            .setLongLabel(entry.longLabel)
            .setRank(rank)
            .setIcon(IconCompat.createWithResource(context, entry.preset?.thumbnail() ?: R.drawable.ic_history))
            .setIntent(intent)
            .build()
    }
    if (shortcuts.isEmpty()) ShortcutManagerCompat.removeAllDynamicShortcuts(context)
    else ShortcutManagerCompat.setDynamicShortcuts(context, shortcuts)
}

@Suppress("DEPRECATION")
fun Intent.shortcutDimensions() = runCatching {
    SizeShortcuts.dimensions(action) { extras?.get(it) }
}.getOrNull()

@Suppress("DEPRECATION")
fun Intent.sizeShortcutId() = runCatching {
    SizeShortcuts.usedId { extras?.get(it) }
}.getOrNull()
