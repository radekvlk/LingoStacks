package cz.slovickaspeak.app.shortcuts

import android.content.Context
import android.content.Intent
import androidx.core.content.pm.ShortcutInfoCompat
import androidx.core.content.pm.ShortcutManagerCompat
import androidx.core.graphics.drawable.IconCompat
import cz.slovickaspeak.app.MainActivity
import cz.slovickaspeak.app.R

object AppShortcutsHelper {
    fun updateDynamicShortcuts(context: Context, streakDays: Int = 0) {
        try {
            val practiceIntent = Intent(context, MainActivity::class.java).apply {
                action = MainActivity.ACTION_PRACTICE
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            }
            val practiceShortcut = ShortcutInfoCompat.Builder(context, "practice")
                .setShortLabel(context.getString(R.string.shortcut_practice_short))
                .setLongLabel(context.getString(R.string.shortcut_practice_long))
                .setIcon(IconCompat.createWithResource(context, R.drawable.ic_shortcut_practice))
                .setIntent(practiceIntent)
                .setRank(1)
                .build()

            val statsIntent = Intent(context, MainActivity::class.java).apply {
                action = MainActivity.ACTION_STATS
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            }
            val statsLabel = if (streakDays > 0) "Statistiky (🔥 $streakDays)" else context.getString(R.string.shortcut_stats_long)
            val statsShortcut = ShortcutInfoCompat.Builder(context, "stats")
                .setShortLabel(context.getString(R.string.shortcut_stats_short))
                .setLongLabel(statsLabel)
                .setIcon(IconCompat.createWithResource(context, R.drawable.ic_shortcut_stats))
                .setIntent(statsIntent)
                .setRank(2)
                .build()

            val lastLessonIntent = Intent(context, MainActivity::class.java).apply {
                action = MainActivity.ACTION_LAST_LESSON
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            }
            val lastLessonShortcut = ShortcutInfoCompat.Builder(context, "last_lesson")
                .setShortLabel(context.getString(R.string.shortcut_last_lesson_short))
                .setLongLabel(context.getString(R.string.shortcut_last_lesson_long))
                .setIcon(IconCompat.createWithResource(context, R.drawable.ic_shortcut_last_lesson))
                .setIntent(lastLessonIntent)
                .setRank(3)
                .build()

            ShortcutManagerCompat.setDynamicShortcuts(
                context,
                listOf(practiceShortcut, statsShortcut, lastLessonShortcut)
            )
        } catch (_: Exception) {
            // Ignore if launcher doesn't support shortcuts
        }
    }
}
