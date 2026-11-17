package cz.slovickaspeak.app.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.util.SizeF
import android.widget.RemoteViews
import cz.slovickaspeak.app.MainActivity
import cz.slovickaspeak.app.R
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class LingoStacksWidgetProvider : AppWidgetProvider() {

    override fun onUpdate(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetIds: IntArray
    ) {
        for (widgetId in appWidgetIds) {
            val options = appWidgetManager.getAppWidgetOptions(widgetId)
            val views = buildWidgetViews(context, options)
            appWidgetManager.updateAppWidget(widgetId, views)
        }
    }

    override fun onAppWidgetOptionsChanged(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetId: Int,
        newOptions: Bundle
    ) {
        super.onAppWidgetOptionsChanged(context, appWidgetManager, appWidgetId, newOptions)
        val views = buildWidgetViews(context, newOptions)
        appWidgetManager.updateAppWidget(appWidgetId, views)
    }

    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)
        if (intent.action == ACTION_UPDATE_WIDGETS) {
            updateAllWidgets(context)
        }
    }

    private data class WidgetData(
        val dailyGoal: Int,
        val todayCompleted: Int,
        val streakDays: Int,
        val streakText: String,
        val streakShortText: String,
        val flag: String,
        val langTitle: String,
        val reviewCount: Int,
        val statusMessage: String,
        val practiceButtonText: String
    )

    private data class WidgetIntents(
        val mainPendingIntent: PendingIntent,
        val practicePendingIntent: PendingIntent,
        val statsPendingIntent: PendingIntent
    )

    companion object {
        const val ACTION_UPDATE_WIDGETS = "cz.slovickaspeak.app.ACTION_UPDATE_WIDGETS"

        fun updateAllWidgets(context: Context) {
            val appContext = context.applicationContext
            val appWidgetManager = AppWidgetManager.getInstance(appContext) ?: return
            val componentName = ComponentName(appContext, LingoStacksWidgetProvider::class.java)
            val widgetIds = appWidgetManager.getAppWidgetIds(componentName)
            if (widgetIds != null && widgetIds.isNotEmpty()) {
                for (widgetId in widgetIds) {
                    val options = appWidgetManager.getAppWidgetOptions(widgetId)
                    val views = buildWidgetViews(appContext, options)
                    appWidgetManager.updateAppWidget(widgetId, views)
                }
            }
        }

        fun buildWidgetViews(context: Context, options: Bundle? = null): RemoteViews {
            val data = extractWidgetData(context)
            val intents = createWidgetPendingIntents(context)

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                val viewMapping = mapOf(
                    SizeF(50f, 50f) to build1x1Views(context, data, intents),
                    SizeF(130f, 50f) to buildWideCompactViews(context, data, intents),
                    SizeF(120f, 95f) to buildCompactViews(context, data, intents),
                    SizeF(210f, 95f) to buildFullViews(context, data, intents)
                )
                return RemoteViews(viewMapping)
            }

            if (options != null) {
                val minWidth = options.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH, 200)
                val minHeight = options.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_HEIGHT, 100)
                return selectViewsForSize(context, data, intents, minWidth, minHeight)
            }

            return buildFullViews(context, data, intents)
        }

        private fun selectViewsForSize(
            context: Context,
            data: WidgetData,
            intents: WidgetIntents,
            widthDp: Int,
            heightDp: Int
        ): RemoteViews {
            return when {
                widthDp < 110 && heightDp < 110 -> build1x1Views(context, data, intents)
                heightDp < 85 && widthDp >= 130 -> buildWideCompactViews(context, data, intents)
                widthDp < 200 || heightDp < 95 -> buildCompactViews(context, data, intents)
                else -> buildFullViews(context, data, intents)
            }
        }

        private fun extractWidgetData(context: Context): WidgetData {
            val prefs = context.getSharedPreferences("slovicka_speak_prefs", Context.MODE_PRIVATE)
            val dailyGoal = prefs.getInt("daily_goal", 20).coerceIn(5, 500)
            val todayKey = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date())
            val savedDate = prefs.getString("today_date", null)
            val todayCompleted = if (savedDate == todayKey) prefs.getInt("today_completed", 0) else 0
            val streakDays = prefs.getInt("streak_days", 0)

            val streakText = when {
                streakDays == 1 -> "🔥 1 den"
                streakDays in 2..4 -> "🔥 $streakDays dny"
                else -> "🔥 $streakDays dní"
            }
            val streakShortText = "🔥 $streakDays"

            val savedLang = prefs.getString("selected_learning_language", "ENGLISH") ?: "ENGLISH"
            val (flag, langTitle) = when (savedLang) {
                "GERMAN" -> "🇩🇪" to "LingoStacks • Němčina"
                "FRENCH" -> "🇫🇷" to "LingoStacks • Francouzština"
                "SPANISH" -> "🇪🇸" to "LingoStacks • Španělština"
                "ITALIAN" -> "🇮🇹" to "LingoStacks • Italština"
                "PORTUGUESE" -> "🇵🇹" to "LingoStacks • Portugalština"
                else -> "🇬🇧" to "LingoStacks • Angličtina"
            }

            val now = System.currentTimeMillis()
            val needsReviewSet = prefs.getStringSet("needs_review_word_ids", emptySet()) ?: emptySet()
            val rawSrs = prefs.getString("word_srs_stats_v1", null)
            val srsEntries = rawSrs?.split("\n")?.filter { it.isNotBlank() } ?: emptyList()
            val dueIds = mutableSetOf<String>()
            dueIds.addAll(needsReviewSet)
            for (entry in srsEntries) {
                val parts = entry.split("|")
                if (parts.size >= 4) {
                    val wId = parts[0]
                    val box = parts[1].toIntOrNull() ?: 0
                    val dueAt = parts[3].toLongOrNull() ?: 0L
                    if (box == 0 || dueAt <= now) {
                        dueIds.add(wId)
                    }
                }
            }

            val statusMessage = when {
                todayCompleted >= dailyGoal -> "Cíl splněn! Skvělá práce 🎉"
                todayCompleted > 0 -> {
                    val remaining = dailyGoal - todayCompleted
                    when {
                        remaining == 1 -> "Zbývá ještě 1 slovíčko 💪"
                        remaining in 2..4 -> "Zbývají ještě $remaining slovíčka 💪"
                        else -> "Zbývá ještě $remaining slovíček 💪"
                    }
                }
                else -> "Čas na dnešní procvičování! 🚀"
            }

            val buttonText = if (todayCompleted >= dailyGoal) "⚡ Trénovat navíc" else "⚡ Trénovat"

            return WidgetData(
                dailyGoal = dailyGoal,
                todayCompleted = todayCompleted,
                streakDays = streakDays,
                streakText = streakText,
                streakShortText = streakShortText,
                flag = flag,
                langTitle = langTitle,
                reviewCount = dueIds.size,
                statusMessage = statusMessage,
                practiceButtonText = buttonText
            )
        }

        private fun createWidgetPendingIntents(context: Context): WidgetIntents {
            val mainIntent = Intent(context, MainActivity::class.java).apply {
                action = Intent.ACTION_MAIN
                addCategory(Intent.CATEGORY_LAUNCHER)
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            }
            val mainPendingIntent = PendingIntent.getActivity(
                context,
                0,
                mainIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )

            val practiceIntent = Intent(context, MainActivity::class.java).apply {
                action = MainActivity.ACTION_PRACTICE
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            }
            val practicePendingIntent = PendingIntent.getActivity(
                context,
                1,
                practiceIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )

            val statsIntent = Intent(context, MainActivity::class.java).apply {
                action = MainActivity.ACTION_STATS
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            }
            val statsPendingIntent = PendingIntent.getActivity(
                context,
                2,
                statsIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )

            return WidgetIntents(mainPendingIntent, practicePendingIntent, statsPendingIntent)
        }

        /**
         * 1x1 Micro Layout (e.g. 1 cell x 1 cell)
         * Shows: Streak, Big Progress, Mini Bar, Label
         */
        private fun build1x1Views(context: Context, data: WidgetData, intents: WidgetIntents): RemoteViews {
            val views = RemoteViews(context.packageName, R.layout.widget_lingostacks_1x1)
            views.setTextViewText(R.id.widget_streak, data.streakShortText)
            views.setTextViewText(R.id.widget_progress_text, "${data.todayCompleted}/${data.dailyGoal}")
            views.setProgressBar(
                R.id.widget_progress_bar,
                data.dailyGoal,
                data.todayCompleted.coerceAtMost(data.dailyGoal),
                false
            )
            val subLabel = if (data.todayCompleted >= data.dailyGoal) "✨ Splněno" else "Denní cíl"
            views.setTextViewText(R.id.widget_status_text, subLabel)

            views.setOnClickPendingIntent(R.id.widget_container, intents.practicePendingIntent)
            return views
        }

        /**
         * 3x1 or 4x1 Wide Compact Ribbon Layout (1 row high, wide)
         * Shows: Title, Streak, Progress text, Quick Practice Button
         */
        private fun buildWideCompactViews(context: Context, data: WidgetData, intents: WidgetIntents): RemoteViews {
            val views = RemoteViews(context.packageName, R.layout.widget_lingostacks_wide_compact)
            views.setTextViewText(R.id.widget_title, "${data.flag} LingoStacks")
            views.setTextViewText(R.id.widget_streak, data.streakShortText)
            views.setTextViewText(R.id.widget_progress_text, "${data.todayCompleted} / ${data.dailyGoal}")
            views.setTextViewText(R.id.widget_btn_practice, data.practiceButtonText)

            views.setOnClickPendingIntent(R.id.widget_container, intents.mainPendingIntent)
            views.setOnClickPendingIntent(R.id.widget_btn_practice, intents.practicePendingIntent)
            return views
        }

        /**
         * 2x2 Medium Compact Layout
         * Shows: Header, Big Progress, Review Badge, Progress Bar, Status + Button
         */
        private fun buildCompactViews(context: Context, data: WidgetData, intents: WidgetIntents): RemoteViews {
            val views = RemoteViews(context.packageName, R.layout.widget_lingostacks_compact)
            views.setTextViewText(R.id.widget_title, "${data.flag} LingoStacks")
            views.setTextViewText(R.id.widget_streak, data.streakText)
            views.setTextViewText(R.id.widget_progress_text, "${data.todayCompleted} / ${data.dailyGoal}")

            if (data.reviewCount > 0) {
                views.setTextViewText(R.id.widget_review_badge, "${data.reviewCount} k opakování")
            } else {
                views.setTextViewText(R.id.widget_review_badge, "Vše zopakováno ✨")
            }

            views.setProgressBar(
                R.id.widget_progress_bar,
                data.dailyGoal,
                data.todayCompleted.coerceAtMost(data.dailyGoal),
                false
            )

            views.setTextViewText(R.id.widget_status_text, data.statusMessage)
            views.setTextViewText(R.id.widget_btn_practice, data.practiceButtonText)

            views.setOnClickPendingIntent(R.id.widget_container, intents.mainPendingIntent)
            views.setOnClickPendingIntent(R.id.widget_btn_practice, intents.practicePendingIntent)
            views.setOnClickPendingIntent(R.id.widget_streak, intents.statsPendingIntent)
            views.setOnClickPendingIntent(R.id.widget_progress_text, intents.statsPendingIntent)
            return views
        }

        /**
         * 3x2 / 4x2+ Full Rich Dashboard Layout
         * Shows: Full Title + Language, Streak, Large Goal, Review Badge, Progress Bar, Status Message, Action Button
         */
        private fun buildFullViews(context: Context, data: WidgetData, intents: WidgetIntents): RemoteViews {
            val views = RemoteViews(context.packageName, R.layout.widget_lingostacks)
            views.setTextViewText(R.id.widget_title, "${data.flag} ${data.langTitle}")
            views.setTextViewText(R.id.widget_streak, data.streakText)
            views.setTextViewText(R.id.widget_progress_text, "${data.todayCompleted} / ${data.dailyGoal}")

            if (data.reviewCount > 0) {
                views.setTextViewText(R.id.widget_review_badge, "${data.reviewCount} k opakování")
            } else {
                views.setTextViewText(R.id.widget_review_badge, "Vše zopakováno ✨")
            }

            views.setProgressBar(
                R.id.widget_progress_bar,
                data.dailyGoal,
                data.todayCompleted.coerceAtMost(data.dailyGoal),
                false
            )

            views.setTextViewText(R.id.widget_status_text, data.statusMessage)
            views.setTextViewText(R.id.widget_btn_practice, data.practiceButtonText)

            views.setOnClickPendingIntent(R.id.widget_container, intents.mainPendingIntent)
            views.setOnClickPendingIntent(R.id.widget_btn_practice, intents.practicePendingIntent)
            views.setOnClickPendingIntent(R.id.widget_streak, intents.statsPendingIntent)
            views.setOnClickPendingIntent(R.id.widget_progress_text, intents.statsPendingIntent)
            return views
        }
    }
}
