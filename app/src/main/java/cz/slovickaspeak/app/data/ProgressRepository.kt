package cz.slovickaspeak.app.data

import android.content.Context
import android.os.Handler
import android.os.Looper
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import cz.slovickaspeak.app.model.AnswerMethod
import cz.slovickaspeak.app.model.BuiltInVocabulary
import cz.slovickaspeak.app.model.CardAttemptResult
import org.json.JSONObject
import java.io.BufferedReader
import java.io.File
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale
import java.util.concurrent.Executors
import kotlin.math.abs
import kotlin.math.roundToInt

data class GoogleUserProfile(
    val displayName: String,
    val email: String,
    val inviteCode: String
)

enum class LeaderboardPeriod {
    TODAY,
    WEEK
}

data class FriendProgress(
    val id: String,
    val displayName: String,
    val emailOrCode: String,
    val todayCompleted: Int,
    val dailyGoal: Int,
    val streakDays: Int,
    val totalSpoken: Int,
    val statusText: String = "Aktivní dnes",
    val isCurrentUser: Boolean = false,
    val masteredCount: Int = 0,
    val totalCorrectCount: Int = 0,
    val completedSessions: Int = 0,
    val accuracyPercent: Int = 0,
    val catalogWordCount: Int = 0,
    val languageCode: String = "en",
    val lastActiveDate: String = "",
    val todayXp: Int = todayCompleted,
    val weeklyCompleted: Int = todayCompleted,
    val weeklyXp: Int = todayCompleted
)

data class WordSrsStat(
    val wordId: String,
    val box: Int,          // Leitner box 0 (Failed/Unknown) .. 5 (Long-term Mastered)
    val lapses: Int,       // Number of times the user did not know this word
    val dueAtMillis: Long  // Timestamp when the word is due for review
)

data class CefrLevelStat(
    val level: cz.slovickaspeak.app.model.CefrLevel,
    val masteredCount: Int,
    val totalCount: Int
)

data class LanguageCompleteStats(
    val language: cz.slovickaspeak.app.model.LearningLanguage,
    val totalWords: Int,
    val activeMasteredCount: Int,
    val passiveMasteredCount: Int,
    val dueForReviewCount: Int,
    val needsPracticeCount: Int,
    val levelStats: List<CefrLevelStat>
)

data class DeckMemorySummary(
    val totalCount: Int,
    val masteredCount: Int,
    val learningCount: Int,
    val needsReviewCount: Int
) {
    val unseenCount: Int
        get() = (totalCount - masteredCount - learningCount - needsReviewCount).coerceAtLeast(0)
    val learnedAnyCount: Int
        get() = masteredCount + learningCount
    val progressPercent: Int
        get() = if (totalCount == 0) 0 else ((learnedAnyCount.toFloat() / totalCount) * 100f).roundToInt()
}

class ProgressStateHolder(context: Context) {
    private val appContext = context.applicationContext
    private val prefs = context.getSharedPreferences("slovicka_speak_prefs", Context.MODE_PRIVATE)
    private val ioExecutor = Executors.newSingleThreadExecutor()
    private val prefsExecutor = Executors.newSingleThreadExecutor()
    private val mainHandler = Handler(Looper.getMainLooper())

    // GitHub Unified OTA Update State
    var otaRepoSlug by mutableStateOf(
        prefs.getString("ota_github_repo", OtaUpdater.DEFAULT_GITHUB_REPO) ?: OtaUpdater.DEFAULT_GITHUB_REPO
    )
        private set

    var availableOtaRelease by mutableStateOf<OtaReleaseInfo?>(null)
        private set

    var isCheckingOta by mutableStateOf(false)
        private set

    var isDownloadingOta by mutableStateOf(false)
        private set

    var otaDownloadFraction by mutableFloatStateOf(0f)
        private set

    var otaDownloadedMbText by mutableStateOf("")
        private set

    var otaStatusMessage by mutableStateOf<String?>(null)
        private set

    var downloadedOtaApkFile by mutableStateOf<File?>(null)
        private set

    var otaNeedsInstallPermission by mutableStateOf(false)
        private set

    var showOtaDialog by mutableStateOf(false)
        private set

    var otaBannerDismissed by mutableStateOf(false)
        private set

    private var lastOtaCheckMillis = prefs.getLong("last_ota_check_millis", 0L)
    private var lastCloudPushMillis = 0L

    private fun dateKeyForOffsetDays(offsetDays: Int): String {
        val cal = Calendar.getInstance()
        if (offsetDays != 0) cal.add(Calendar.DAY_OF_YEAR, offsetDays)
        return SimpleDateFormat("yyyy-MM-dd", Locale.US).format(cal.time)
    }

    private val todayKey: String
        get() = dateKeyForOffsetDays(0)

    private val yesterdayKey: String
        get() = dateKeyForOffsetDays(-1)

    private fun currentWeekStartKey(): String {
        val cal = Calendar.getInstance()
        val dow = cal.get(Calendar.DAY_OF_WEEK)
        val daysSinceMonday = (dow - Calendar.MONDAY + 7) % 7
        if (daysSinceMonday > 0) cal.add(Calendar.DAY_OF_YEAR, -daysSinceMonday)
        return SimpleDateFormat("yyyy-MM-dd", Locale.US).format(cal.time)
    }

    var dailyGoalWords by mutableIntStateOf(prefs.getInt("daily_goal", 20).coerceIn(5, 200))
        private set

    var isDarkMode by mutableStateOf(prefs.getBoolean("is_dark_mode", false)).apply {
        cz.slovickaspeak.app.ui.theme.isDarkThemeActive = value
    }
        private set

    var autoPronounceEnabled by mutableStateOf(prefs.getBoolean("auto_pronounce_enabled", true))
        private set

    var pauseCardWhileSpeaking by mutableStateOf(prefs.getBoolean("pause_card_while_speaking", true))
        private set

    var studyReminderEnabled by mutableStateOf(prefs.getBoolean("study_reminder_enabled", false))
        private set

    var studyReminderHour by mutableIntStateOf(prefs.getInt("study_reminder_hour", 19).coerceIn(0, 23))
        private set

    var studyReminderMinute by mutableIntStateOf(prefs.getInt("study_reminder_minute", 0).coerceIn(0, 59))
        private set

    var selectedLanguage by mutableStateOf(
        prefs.getString("selected_learning_language", null)?.let { saved ->
            cz.slovickaspeak.app.model.LearningLanguage.entries.find { it.name == saved }
        } ?: cz.slovickaspeak.app.model.LearningLanguage.ENGLISH
    )
        private set

    var hasSelectedInitialLanguage by mutableStateOf(
        prefs.getBoolean("has_selected_initial_language", false)
    )
        private set

    val appVersionName: String by lazy {
        try {
            @Suppress("DEPRECATION")
            val pInfo = context.packageManager.getPackageInfo(context.packageName, 0)
            pInfo.versionName ?: "1.2.0"
        } catch (_: Exception) {
            "1.2.0"
        }
    }

    var selectedCefrLevel by mutableStateOf<cz.slovickaspeak.app.model.CefrLevel?>(
        prefs.getString("selected_cefr_level", null)?.let { saved ->
            cz.slovickaspeak.app.model.CefrLevel.entries.find { it.name == saved }
        }
    )
        private set

    var studyDirection by mutableStateOf(
        prefs.getString("study_direction", null)?.let { saved ->
            cz.slovickaspeak.app.model.StudyDirection.entries.find { it.name == saved }
        } ?: cz.slovickaspeak.app.model.StudyDirection.NATIVE_TO_FOREIGN
    )
        private set

    var todayCompletedWords by mutableIntStateOf(loadTodayCompleted())
        private set

    var todayBaseXp by mutableIntStateOf(loadTodayBaseXp())
        private set

    val todayXp: Int
        get() = todayBaseXp + if (todayCompletedWords >= dailyGoalWords) 5 else 0

    // Maps "yyyy-MM-dd" -> Pair(wordsCompleted, dailyTotalXp) for the active Mon-Sun week
    private val weeklyDailyStats = loadWeeklyDailyStats().apply {
        val today = todayKey
        val existingToday = this[today]
        val computedTodayXp = todayXp
        if (existingToday == null || existingToday.first < todayCompletedWords || existingToday.second < computedTodayXp) {
            this[today] = maxOf(todayCompletedWords, existingToday?.first ?: 0) to maxOf(computedTodayXp, existingToday?.second ?: 0)
        }
    }

    val weeklyCompletedWords: Int
        get() {
            val weekStart = currentWeekStartKey()
            return weeklyDailyStats.entries.sumOf { (date, pair) -> if (date >= weekStart) pair.first else 0 }
        }

    val weeklyXp: Int
        get() {
            val weekStart = currentWeekStartKey()
            return weeklyDailyStats.entries.sumOf { (date, pair) -> if (date >= weekStart) pair.second else 0 }
        }

    var totalAttempts by mutableIntStateOf(prefs.getInt("total_attempts", 0))
        private set

    var totalCorrect by mutableIntStateOf(prefs.getInt("total_correct", 0))
        private set

    var totalSpokenCorrect by mutableIntStateOf(prefs.getInt("total_spoken", 0))
        private set

    var completedSessions by mutableIntStateOf(prefs.getInt("completed_sessions", 0))
        private set

    var streakDays by mutableIntStateOf(loadInitialStreakDays())
        private set

    // Persistent set of word IDs that the user previously did NOT know (re-offered first until mastered!)
    private val needsReviewWordIds = mutableSetOf<String>().apply {
        addAll(prefs.getStringSet("needs_review_word_ids", emptySet()) ?: emptySet())
    }

    // Persistent set of word IDs that the user has already mastered
    private val masteredWordIds = mutableSetOf<String>().apply {
        addAll(prefs.getStringSet("mastered_word_ids", emptySet()) ?: emptySet())
    }

    // Detailed Leitner + SM-2 Spaced Repetition stats per wordId
    private val wordSrsMap = mutableMapOf<String, WordSrsStat>().apply {
        val raw = prefs.getString("word_srs_stats_v1", null)
        if (!raw.isNullOrBlank()) {
            raw.split("\n").forEach { line ->
                val parts = line.split("|")
                if (parts.size >= 4) {
                    val wId = parts[0]
                    val box = parts[1].toIntOrNull() ?: 0
                    val lapses = parts[2].toIntOrNull() ?: 0
                    val dueAt = parts[3].toLongOrNull() ?: 0L
                    put(wId, WordSrsStat(wId, box, lapses, dueAt))
                }
            }
        }
    }

    // Reactive counter so UI recomposes when deck mastery changes
    var srsRevision by mutableIntStateOf(0)
        private set

    private fun srsKey(wordId: String, isReversed: Boolean): String =
        if (isReversed) "${wordId}__rev" else wordId

    private fun isWordMastered(wordId: String, useReversed: Boolean): Boolean {
        val key = srsKey(wordId, useReversed)
        val stat = wordSrsMap[key]
        return (stat != null && stat.box >= 1 && key !in needsReviewWordIds) ||
            (!useReversed && stat == null && wordId in masteredWordIds && key !in needsReviewWordIds)
    }

    private fun isWordNeedsReview(wordId: String, useReversed: Boolean, now: Long): Boolean {
        val key = srsKey(wordId, useReversed)
        val stat = wordSrsMap[key]
        return key in needsReviewWordIds || (stat != null && (stat.box == 0 || stat.dueAtMillis <= now))
    }

    fun getWordSrsStat(wordId: String, isReversed: Boolean = false): WordSrsStat? {
        @Suppress("UNUSED_EXPRESSION")
        srsRevision
        return wordSrsMap[srsKey(wordId, isReversed)]
    }

    fun getDeckMasteredCount(deck: cz.slovickaspeak.app.model.VocabDeck): Int {
        @Suppress("UNUSED_EXPRESSION")
        srsRevision
        val useReversed = studyDirection == cz.slovickaspeak.app.model.StudyDirection.FOREIGN_TO_NATIVE
        return deck.words.count { word -> isWordMastered(word.id, useReversed) }
    }

    fun getDeckNeedsReviewCount(deck: cz.slovickaspeak.app.model.VocabDeck): Int {
        @Suppress("UNUSED_EXPRESSION")
        srsRevision
        val now = System.currentTimeMillis()
        val useReversed = studyDirection == cz.slovickaspeak.app.model.StudyDirection.FOREIGN_TO_NATIVE
        return deck.words.count { word -> isWordNeedsReview(word.id, useReversed, now) }
    }

    fun getDeckMemorySummary(deck: cz.slovickaspeak.app.model.VocabDeck): DeckMemorySummary {
        @Suppress("UNUSED_EXPRESSION")
        srsRevision
        val now = System.currentTimeMillis()
        val useReversed = studyDirection == cz.slovickaspeak.app.model.StudyDirection.FOREIGN_TO_NATIVE
        var mastered = 0
        var learning = 0
        var needsReview = 0

        for (word in deck.words) {
            val key = srsKey(word.id, useReversed)
            val stat = wordSrsMap[key]
            when {
                isWordNeedsReview(word.id, useReversed, now) -> needsReview++
                stat != null && stat.box >= 4 -> mastered++
                (stat != null && stat.box in 1..3) || (!useReversed && stat == null && word.id in masteredWordIds) -> learning++
            }
        }
        return DeckMemorySummary(
            totalCount = deck.words.size,
            masteredCount = mastered,
            learningCount = learning,
            needsReviewCount = needsReview
        )
    }

    fun getLanguageCompleteStats(language: cz.slovickaspeak.app.model.LearningLanguage): LanguageCompleteStats {
        @Suppress("UNUSED_EXPRESSION")
        srsRevision
        val now = System.currentTimeMillis()
        val decks = BuiltInVocabulary.decksForLanguage(language)
        val useReversedForLevels = studyDirection == cz.slovickaspeak.app.model.StudyDirection.FOREIGN_TO_NATIVE

        var totalWords = 0
        var activeMastered = 0
        var passiveMastered = 0
        var dueCount = 0
        var practiceCount = 0

        val levelStats = cz.slovickaspeak.app.model.CefrLevel.entries.map { level ->
            val levelWords = decks.filter { it.level == level }.flatMap { it.words }
            val levelMastered = levelWords.count { isWordMastered(it.id, useReversedForLevels) }
            CefrLevelStat(level = level, masteredCount = levelMastered, totalCount = levelWords.size)
        }

        decks.forEach { deck ->
            deck.words.forEach { word ->
                totalWords++
                if (isWordMastered(word.id, useReversed = false)) activeMastered++
                if (isWordMastered(word.id, useReversed = true)) passiveMastered++
                val activeKey = srsKey(word.id, useReversedForLevels)
                val stat = wordSrsMap[activeKey]
                if (activeKey in needsReviewWordIds || (stat != null && stat.box == 0)) {
                    practiceCount++
                } else if (stat != null && stat.dueAtMillis <= now) {
                    dueCount++
                }
            }
        }

        return LanguageCompleteStats(
            language = language,
            totalWords = totalWords,
            activeMasteredCount = activeMastered,
            passiveMasteredCount = passiveMastered,
            dueForReviewCount = dueCount,
            needsPracticeCount = practiceCount,
            levelStats = levelStats
        )
    }

    /**
     * Multi-tier Spaced Repetition (Leitner + SM-2) Session Queue Builder:
     * 1. Tier 1 (Critical / Unknown): Words in Box 0 or needsReviewWordIds, ordered by highest lapse count first.
     * 2. Tier 2 (Due for Review): Learned words whose review interval has expired (dueAtMillis <= now), most overdue first.
     * 3. Tier 3 (Fresh New Vocabulary): Unseen words in the deck (shuffled so every session introduces fresh words).
     * 4. Tier 4 (Early Consolidation): Learned words not yet due, ordered by lowest Leitner box (1..5) and earliest dueAtMillis.
     */
    fun prioritizeWordsForSession(words: List<cz.slovickaspeak.app.model.VocabWord>, targetCount: Int): List<cz.slovickaspeak.app.model.VocabWord> {
        if (words.isEmpty()) return emptyList()
        val now = System.currentTimeMillis()
        val useReversed = studyDirection == cz.slovickaspeak.app.model.StudyDirection.FOREIGN_TO_NATIVE

        val tier1Critical = words.filter { w ->
            val key = srsKey(w.id, useReversed)
            val st = wordSrsMap[key]
            key in needsReviewWordIds || (st != null && st.box == 0)
        }.shuffled().sortedByDescending { w -> wordSrsMap[srsKey(w.id, useReversed)]?.lapses ?: 1 }

        val tier1Ids = tier1Critical.map { it.id }.toSet()

        val tier2Due = words.filter { w ->
            val key = srsKey(w.id, useReversed)
            val st = wordSrsMap[key]
            w.id !in tier1Ids && st != null && st.box >= 1 && st.dueAtMillis <= now
        }.shuffled().sortedBy { w -> wordSrsMap[srsKey(w.id, useReversed)]?.dueAtMillis ?: 0L }

        val tier2Ids = tier2Due.map { it.id }.toSet()

        val tier3Unseen = words.filter { w ->
            val key = srsKey(w.id, useReversed)
            w.id !in tier1Ids && w.id !in tier2Ids && key !in wordSrsMap && (useReversed || w.id !in masteredWordIds)
        }.shuffled()

        val tier3Ids = tier3Unseen.map { it.id }.toSet()

        val tier4Learned = words.filter { w ->
            w.id !in tier1Ids && w.id !in tier2Ids && w.id !in tier3Ids
        }.shuffled().sortedWith(
            compareBy<cz.slovickaspeak.app.model.VocabWord> { wordSrsMap[srsKey(it.id, useReversed)]?.box ?: 1 }
                .thenBy { wordSrsMap[srsKey(it.id, useReversed)]?.dueAtMillis ?: Long.MAX_VALUE }
        )

        val prioritizedUnique = tier1Critical + tier2Due + tier3Unseen + tier4Learned

        if (targetCount <= prioritizedUnique.size) {
            return prioritizedUnique.take(targetCount)
        }
        // If user set daily goal larger than a single deck's size (e.g. >80 words), fill with shuffled rounds without immediate repeats
        val result = mutableListOf<cz.slovickaspeak.app.model.VocabWord>()
        result.addAll(prioritizedUnique)
        while (result.size < targetCount) {
            val nextBatch = words.shuffled().toMutableList()
            if (nextBatch.size > 1 && nextBatch.first().id == result.lastOrNull()?.id) {
                val first = nextBatch.removeAt(0)
                nextBatch.add(first)
            }
            result.addAll(nextBatch.take(targetCount - result.size))
        }
        return result
    }

    var googleUser by mutableStateOf<GoogleUserProfile?>(loadSavedUser())
        private set

    private fun isCzechLocale(): Boolean {
        val lang = Locale.getDefault().language.lowercase(Locale.US)
        return lang in listOf("cs", "sk", "ces", "cze")
    }

    var isSyncingCloud by mutableStateOf(false)
        private set

    var lastSyncStatus by mutableStateOf(
        if (isCzechLocale()) "Online synchronizace připravena" else "Online sync ready"
    )
        private set

    val friends = mutableStateListOf<FriendProgress>().apply {
        addAll(loadSavedFriends())
    }

    val accuracyPercent: Int
        get() = if (totalAttempts == 0) 100 else ((totalCorrect.toFloat() / totalAttempts) * 100).roundToInt()

    /**
     * Returns the Daily Leaderboard ("Denní žebříček") containing ONLY real users
     * who have connected their account (googleUser != null or verified cloud friends with an account),
     * sorted by todayCompleted descending.
     */
    private fun getActiveLanguageMasteredCount(): Int {
        @Suppress("UNUSED_EXPRESSION")
        srsRevision
        val decks = BuiltInVocabulary.decksForLanguage(selectedLanguage)
        return decks.sumOf { deck ->
            deck.words.count { word ->
                val stat = wordSrsMap[word.id]
                (stat != null && stat.box >= 1 && word.id !in needsReviewWordIds) ||
                    (stat == null && word.id in masteredWordIds && word.id !in needsReviewWordIds)
            }
        }
    }

    private fun getActiveLanguageCatalogCount(): Int {
        return BuiltInVocabulary.decksForLanguage(selectedLanguage).sumOf { it.words.size }
    }

    val dailyLeaderboard: List<FriendProgress>
        get() = buildLeaderboard(LeaderboardPeriod.TODAY)

    val weeklyLeaderboard: List<FriendProgress>
        get() = buildLeaderboard(LeaderboardPeriod.WEEK)

    private fun buildLeaderboard(period: LeaderboardPeriod): List<FriendProgress> {
        val user = googleUser
        if (user == null || !user.email.contains("@")) {
            return emptyList()
        }

        val isCs = isCzechLocale()
        val youTag = if (isCs) "Vy" else "You"
        val currentLastActive = prefs.getString("last_study_date", null)
            ?: if (todayCompletedWords > 0) todayKey else ""

        val meList = listOf(
            FriendProgress(
                id = "me_${user.inviteCode}",
                displayName = "${user.displayName} ($youTag)",
                emailOrCode = user.inviteCode,
                todayCompleted = todayCompletedWords,
                dailyGoal = dailyGoalWords,
                streakDays = streakDays,
                totalSpoken = totalSpokenCorrect,
                statusText = if (isCs) "Váš dnešní výkon" else "Your progress today",
                isCurrentUser = true,
                masteredCount = getActiveLanguageMasteredCount(),
                totalCorrectCount = totalCorrect,
                completedSessions = completedSessions,
                accuracyPercent = accuracyPercent,
                catalogWordCount = getActiveLanguageCatalogCount(),
                languageCode = selectedLanguage.code,
                lastActiveDate = currentLastActive,
                todayXp = todayXp,
                weeklyCompleted = weeklyCompletedWords,
                weeklyXp = weeklyXp
            )
        )

        val myCode = user.inviteCode
        val myEmail = user.email

        // Exclude any legacy demo/anonymous entries
        val realOthers = friends.filter { friend ->
            !friend.id.startsWith("demo_") &&
                    !friend.displayName.startsWith("Hráč (") &&
                    !friend.displayName.equals("Petra Nováková", ignoreCase = true) &&
                    !friend.displayName.equals("Martin Dvořák", ignoreCase = true) &&
                    !friend.emailOrCode.equals(myCode, ignoreCase = true) &&
                    !friend.emailOrCode.equals(myEmail, ignoreCase = true)
        }

        val comparator = when (period) {
            LeaderboardPeriod.TODAY -> compareByDescending<FriendProgress> { it.todayXp }
                .thenByDescending { it.accuracyPercent }
                .thenByDescending { it.streakDays }
                .thenByDescending { it.todayCompleted }
            LeaderboardPeriod.WEEK -> compareByDescending<FriendProgress> { it.weeklyXp }
                .thenByDescending { it.accuracyPercent }
                .thenByDescending { it.streakDays }
                .thenByDescending { it.weeklyCompleted }
        }

        return (meList + realOthers).sortedWith(comparator)
    }

    init {
        ioExecutor.execute { OtaUpdater.cleanupCachedUpdates(appContext) }
        refreshFromCloud()
        checkForOtaUpdate(manual = false)
        if (studyReminderEnabled) {
            cz.slovickaspeak.app.notifications.StudyReminderScheduler.scheduleOrCancel(
                context = appContext,
                enabled = true,
                hour = studyReminderHour,
                minute = studyReminderMinute
            )
        }
    }

    fun getOrCreateDeviceInviteCode(): String {
        val existing = prefs.getString("device_invite_code", null)
        if (!existing.isNullOrBlank()) return existing
        val generated = "SLOV-${(1000..9999).random()}"
        prefs.edit().putString("device_invite_code", generated).apply()
        return generated
    }

    private fun loadTodayCompleted(): Int {
        val currentDay = todayKey
        val savedDate = prefs.getString("today_date", null)
        val savedCount = prefs.getInt("today_completed", 0)
        // Migrate legacy last_study_date if user studied prior to v1.5.0
        if (prefs.getString("last_study_date", null) == null && savedCount > 0 && savedDate != null) {
            prefs.edit().putString("last_study_date", savedDate).apply()
        }
        return if (savedDate == currentDay) {
            savedCount
        } else if (savedDate == null && savedCount > 0) {
            prefs.edit()
                .putString("today_date", currentDay)
                .putString("last_study_date", currentDay)
                .apply()
            savedCount
        } else {
            // New calendar day: reset daily counter at local midnight
            prefs.edit()
                .putString("today_date", currentDay)
                .putInt("today_completed", 0)
                .apply()
            0
        }
    }

    private fun loadTodayBaseXp(): Int {
        val currentDay = todayKey
        val savedDate = prefs.getString("today_xp_date", null)
        val savedXp = prefs.getInt("today_base_xp", -1)
        return when {
            savedDate == currentDay && savedXp >= 0 -> savedXp
            savedDate == null && todayCompletedWords > 0 -> {
                // Graceful migration for users who studied earlier today prior to XP scoring
                prefs.edit()
                    .putString("today_xp_date", currentDay)
                    .putInt("today_base_xp", todayCompletedWords)
                    .apply()
                todayCompletedWords
            }
            else -> {
                prefs.edit()
                    .putString("today_xp_date", currentDay)
                    .putInt("today_base_xp", 0)
                    .apply()
                0
            }
        }
    }

    private fun loadWeeklyDailyStats(): MutableMap<String, Pair<Int, Int>> {
        val weekStart = currentWeekStartKey()
        val raw = prefs.getString("weekly_daily_log_v1", null) ?: return mutableMapOf()
        val result = mutableMapOf<String, Pair<Int, Int>>()
        raw.split("\n").forEach { line ->
            val parts = line.split("|")
            if (parts.size >= 3) {
                val date = parts[0]
                if (date >= weekStart) {
                    val words = parts[1].toIntOrNull() ?: 0
                    val xp = parts[2].toIntOrNull() ?: words
                    result[date] = words to xp
                }
            }
        }
        return result
    }

    private fun updateTodayInWeeklyLog() {
        val weekStart = currentWeekStartKey()
        weeklyDailyStats.keys.removeAll { it < weekStart }
        weeklyDailyStats[todayKey] = todayCompletedWords to todayXp
    }

    private fun loadInitialStreakDays(): Int {
        val lastStudy = prefs.getString("last_study_date", null) ?: return if (prefs.getInt("today_completed", 0) > 0) 1 else 0
        val currentDay = todayKey
        val prevDay = yesterdayKey
        return if (lastStudy == currentDay || lastStudy == prevDay) {
            prefs.getInt("streak_days", 1).coerceAtLeast(1)
        } else {
            prefs.edit().putInt("streak_days", 0).apply()
            0
        }
    }

    /**
     * Checks if the calendar day has changed since the last saved date (e.g. app kept alive in memory overnight).
     * Resets todayCompletedWords at midnight and refreshes streak state.
     */
    fun checkNewDayRollover(alsoRefreshCloud: Boolean = false) {
        val currentDay = todayKey
        val weekStart = currentWeekStartKey()
        val prevDay = yesterdayKey
        val savedDate = prefs.getString("today_date", null)
        if (savedDate != currentDay) {
            todayCompletedWords = 0
            todayBaseXp = 0
            weeklyDailyStats.keys.removeAll { it < weekStart }
            val lastStudy = prefs.getString("last_study_date", null)
            if (lastStudy != currentDay && lastStudy != prevDay) {
                streakDays = 0
            }
            prefs.edit()
                .putString("today_date", currentDay)
                .putInt("today_completed", 0)
                .putString("today_xp_date", currentDay)
                .putInt("today_base_xp", 0)
                .putInt("streak_days", streakDays)
                .apply()
        }
        // Reset yesterday's todayCompleted/todayXp on cached friend cards when day rolls over
        for (i in friends.indices) {
            val f = friends[i]
            val resetDaily = f.lastActiveDate.isNotEmpty() && f.lastActiveDate != currentDay && (f.todayCompleted > 0 || f.todayXp > 0)
            val resetWeekly = f.lastActiveDate.isNotEmpty() && f.lastActiveDate < weekStart && (f.weeklyCompleted > 0 || f.weeklyXp > 0)
            if (resetDaily || resetWeekly) {
                friends[i] = f.copy(
                    todayCompleted = if (resetDaily) 0 else f.todayCompleted,
                    todayXp = if (resetDaily) 0 else f.todayXp,
                    weeklyCompleted = if (resetWeekly) 0 else f.weeklyCompleted,
                    weeklyXp = if (resetWeekly) 0 else f.weeklyXp
                )
            }
        }
        if (alsoRefreshCloud) {
            refreshFromCloud()
        }
    }

    fun updateDailyGoal(newGoal: Int) {
        val clamped = newGoal.coerceIn(5, 200)
        dailyGoalWords = clamped
        updateTodayInWeeklyLog()
        prefs.edit().putInt("daily_goal", clamped).apply()
        saveStats()
        pushMyProgressToCloud()
    }

    fun updateDarkMode(dark: Boolean) {
        isDarkMode = dark
        cz.slovickaspeak.app.ui.theme.isDarkThemeActive = dark
        prefs.edit().putBoolean("is_dark_mode", dark).apply()
    }

    fun setAutoPronounce(enabled: Boolean) {
        autoPronounceEnabled = enabled
        prefs.edit().putBoolean("auto_pronounce_enabled", enabled).apply()
    }

    fun updatePauseCardWhileSpeaking(enabled: Boolean) {
        pauseCardWhileSpeaking = enabled
        prefs.edit().putBoolean("pause_card_while_speaking", enabled).apply()
    }

    fun updateStudyReminder(
        enabled: Boolean,
        hour: Int = studyReminderHour,
        minute: Int = studyReminderMinute
    ) {
        val h = hour.coerceIn(0, 23)
        val m = minute.coerceIn(0, 59)
        studyReminderEnabled = enabled
        studyReminderHour = h
        studyReminderMinute = m
        prefs.edit()
            .putBoolean("study_reminder_enabled", enabled)
            .putInt("study_reminder_hour", h)
            .putInt("study_reminder_minute", m)
            .apply()
        cz.slovickaspeak.app.notifications.StudyReminderScheduler.scheduleOrCancel(
            context = appContext,
            enabled = enabled,
            hour = h,
            minute = m
        )
    }

    fun updateSelectedLanguage(language: cz.slovickaspeak.app.model.LearningLanguage) {
        selectedLanguage = language
        hasSelectedInitialLanguage = true
        prefs.edit()
            .putString("selected_learning_language", language.name)
            .putBoolean("has_selected_initial_language", true)
            .apply()
        pushMyProgressToCloud()
    }

    fun selectInitialLanguage(language: cz.slovickaspeak.app.model.LearningLanguage) {
        updateSelectedLanguage(language)
    }

    fun updateSelectedCefrLevel(level: cz.slovickaspeak.app.model.CefrLevel?) {
        selectedCefrLevel = level
        if (level == null) {
            prefs.edit().remove("selected_cefr_level").apply()
        } else {
            prefs.edit().putString("selected_cefr_level", level.name).apply()
        }
    }

    fun updateStudyDirection(direction: cz.slovickaspeak.app.model.StudyDirection) {
        studyDirection = direction
        prefs.edit().putString("study_direction", direction.name).apply()
        srsRevision += 1
    }

    fun recordCardAttempt(
        result: CardAttemptResult,
        usedHint: Boolean = false,
        isReversed: Boolean = result.wasReversed
    ) {
        checkNewDayRollover(alsoRefreshCloud = false)
        totalAttempts += 1
        val now = System.currentTimeMillis()
        val srsWordKey = srsKey(result.word.id, isReversed)
        val prevStat = wordSrsMap[srsWordKey]

        if (result.isCorrect) {
            totalCorrect += 1
            if (usedHint) {
                // User had to open the hint ("Zobrazit nápovědu") before answering:
                // Treat as assisted recall -> keep in needsReview and due soon (2 hours) until answered without hint!
                val lapses = prevStat?.lapses ?: 0
                wordSrsMap[srsWordKey] = WordSrsStat(
                    wordId = srsWordKey,
                    box = 0,
                    lapses = lapses,
                    dueAtMillis = now + 2L * 3600_000L
                )
                needsReviewWordIds.add(srsWordKey)
            } else {
                // Unassisted correct recall!
                val currentDay = todayKey
                val lastStudy = prefs.getString("last_study_date", null)
                if (lastStudy != currentDay) {
                    streakDays = if (lastStudy == yesterdayKey) (streakDays + 1).coerceAtLeast(1) else 1
                    prefs.edit().putString("last_study_date", currentDay).apply()
                }
                todayCompletedWords += 1
                todayBaseXp += if (result.method == AnswerMethod.SPOKEN_CORRECT) 2 else 1
                updateTodayInWeeklyLog()
                needsReviewWordIds.remove(srsWordKey)
                if (!isReversed) {
                    masteredWordIds.add(result.word.id)
                }

                val prevBox = prevStat?.box ?: 0
                // Speaking out loud strengthens memory faster than manual swipe
                val nextBox = when {
                    prevBox == 0 -> 1
                    result.method == AnswerMethod.SPOKEN_CORRECT -> (prevBox + 2).coerceAtMost(5)
                    else -> (prevBox + 1).coerceAtMost(5)
                }
                val intervalMillis = when (nextBox) {
                    1 -> 6L * 3600_000L          // 6 hours
                    2 -> 24L * 3600_000L         // 1 day
                    3 -> 3L * 24L * 3600_000L    // 3 days
                    4 -> 7L * 24L * 3600_000L    // 7 days
                    else -> 21L * 24L * 3600_000L // 21 days (Long-term memory)
                }
                wordSrsMap[srsWordKey] = WordSrsStat(
                    wordId = srsWordKey,
                    box = nextBox,
                    lapses = prevStat?.lapses ?: 0,
                    dueAtMillis = now + intervalMillis
                )
            }
        } else {
            // Word was NOT known -> reset Leitner box to 0, increment lapse counter, and keep in needsReview
            val newLapses = (prevStat?.lapses ?: 0) + 1
            wordSrsMap[srsWordKey] = WordSrsStat(
                wordId = srsWordKey,
                box = 0,
                lapses = newLapses,
                dueAtMillis = now
            )
            needsReviewWordIds.add(srsWordKey)
            if (!isReversed) {
                masteredWordIds.remove(result.word.id)
            }
        }
        if (result.method == AnswerMethod.SPOKEN_CORRECT) {
            totalSpokenCorrect += 1
        }
        srsRevision += 1
        saveStats()
        pushMyProgressToCloud(force = false)
    }

    fun recordSessionCompleted() {
        completedSessions += 1
        saveStats()
        pushMyProgressToCloud()
        refreshFromCloud()
    }

    private fun normalizeKnownLeaderboardName(rawName: String): String {
        val trimmed = rawName.trim()
        return if (trimmed.equals("Sichyngrova", ignoreCase = true)) "Lenka S." else trimmed
    }

    private fun formatDefaultDisplayName(displayName: String, email: String): String {
        val raw = displayName.trim().ifEmpty {
            email.substringBefore("@").replace(Regex("[._-]+"), " ").trim()
        }
        val mappedKnown = normalizeKnownLeaderboardName(raw)
        if (mappedKnown != raw) return mappedKnown
        val parts = raw.split(Regex("\\s+")).filter { it.isNotBlank() }
        if (parts.isEmpty()) return email.substringBefore("@").ifEmpty { "User" }
        val firstName = parts.first().replaceFirstChar { it.uppercase() }
        if (parts.size >= 2) {
            val surnameInitial = parts.last().firstOrNull { it.isLetterOrDigit() }?.uppercaseChar()
            if (surnameInitial != null) {
                return "$firstName $surnameInitial."
            }
        }
        return firstName
    }

    fun signInWithGoogle(displayName: String, email: String) {
        val cleanEmail = email.trim().lowercase(Locale.US)
        val cleanName = formatDefaultDisplayName(displayName, cleanEmail)
        val codeSuffix = abs(cleanEmail.hashCode()) % 9000 + 1000
        val profile = GoogleUserProfile(
            displayName = cleanName,
            email = cleanEmail,
            inviteCode = "SLOV-$codeSuffix"
        )
        googleUser = profile
        prefs.edit()
            .putString("google_name", profile.displayName)
            .putString("google_email", profile.email)
            .putString("google_code", profile.inviteCode)
            .apply()
        pushMyProgressToCloud()
        refreshFromCloud()
    }

    fun signOutGoogle() {
        googleUser = null
        prefs.edit()
            .remove("google_name")
            .remove("google_email")
            .remove("google_code")
            .apply()
    }

    fun updateDisplayName(newName: String) {
        val trimmed = newName.trim()
        if (trimmed.isEmpty()) return
        val current = googleUser ?: return
        val updated = current.copy(displayName = trimmed)
        googleUser = updated
        prefs.edit().putString("google_name", trimmed).apply()
        pushMyProgressToCloud()
        refreshFromCloud()
    }

    fun inviteAndAddFriend(emailOrCode: String) {
        val trimmed = emailOrCode.trim()
        if (trimmed.isEmpty()) return

        val normalizedCode = if (trimmed.contains("@")) {
            val suffix = abs(trimmed.lowercase(Locale.US).hashCode()) % 9000 + 1000
            "SLOV-$suffix"
        } else {
            trimmed.uppercase(Locale.US)
        }

        val namePart = if (trimmed.contains("@")) {
            trimmed.substringBefore("@").replace(".", " ")
                .replaceFirstChar { it.uppercase() }
        } else {
            normalizedCode
        }

        if (friends.none { it.emailOrCode.equals(normalizedCode, ignoreCase = true) }) {
            val newFriend = FriendProgress(
                id = "f_${System.currentTimeMillis()}",
                displayName = namePart,
                emailOrCode = normalizedCode,
                todayCompleted = 0,
                dailyGoal = 20,
                streakDays = 1,
                totalSpoken = 0,
                statusText = if (isCzechLocale()) "Čeká na synchronizaci..." else "Waiting for sync..."
            )
            friends.add(0, newFriend)
            saveFriends()
        }
        refreshFromCloud()
    }

    fun removeFriend(friendId: String) {
        friends.removeAll { it.id == friendId }
        saveFriends()
    }

    /**
     * Publishes the user's current progress to the cloud ONLY if the user has connected
     * a real account (googleUser != null with email), so the leaderboard only contains real connected people.
     */
    fun pushMyProgressToCloud(force: Boolean = true) {
        val user = googleUser ?: return
        if (!user.email.contains("@")) return
        val now = System.currentTimeMillis()
        if (!force && now - lastCloudPushMillis < 25_000L) return
        lastCloudPushMillis = now

        val code = user.inviteCode
        val name = user.displayName
        val email = user.email
        val currentMastered = getActiveLanguageMasteredCount()
        val catalogTotal = getActiveLanguageCatalogCount()
        val lastActive = prefs.getString("last_study_date", null)
            ?: if (todayCompletedWords > 0) todayKey else ""

        val payload = JSONObject().apply {
            put("code", code.uppercase(Locale.US))
            put("name", name)
            put("email", email.lowercase(Locale.US))
            put("date", todayKey)
            put("todayCompleted", todayCompletedWords)
            put("todayXp", todayXp)
            put("weeklyCompleted", weeklyCompletedWords)
            put("weeklyXp", weeklyXp)
            put("dailyGoal", dailyGoalWords)
            put("streakDays", streakDays)
            put("totalSpoken", totalSpokenCorrect)
            put("masteredCount", currentMastered)
            put("totalCorrect", totalCorrect)
            put("completedSessions", completedSessions)
            put("accuracyPercent", accuracyPercent)
            put("catalogWordCount", catalogTotal)
            put("languageCode", selectedLanguage.code)
            put("lastActiveDate", lastActive)
            put("ts", System.currentTimeMillis())
        }.toString()

        val userTopic = "slovicka_user_${code.uppercase(Locale.US).replace(Regex("[^A-Z0-9]"), "")}"
        val boardTopic = "lingowords_real_board_v1"

        ioExecutor.execute {
            try {
                postJsonToTopic(userTopic, payload)
                postJsonToTopic(boardTopic, payload)
                mainHandler.post {
                    lastSyncStatus = if (isCzechLocale()) "Synchronizováno online ✓" else "Synced online ✓"
                }
            } catch (_: Exception) {
                mainHandler.post {
                    lastSyncStatus = if (isCzechLocale()) {
                        "Režim offline (synchronizuje se po připojení)"
                    } else {
                        "Offline mode (will sync when connected)"
                    }
                }
            }
        }
    }

    /**
     * Fetches the latest progress from the global real-users daily board and invited friends' channels.
     */
    fun refreshFromCloud() {
        checkNewDayRollover(alsoRefreshCloud = false)
        // Battery saver: skip cloud network calls when not signed in
        val user = googleUser
        if (user == null || !user.email.contains("@")) return
        if (isSyncingCloud) return
        isSyncingCloud = true

        val watchedCodes = friends.map { it.emailOrCode.uppercase(Locale.US) }.toSet()

        ioExecutor.execute {
            val latestByCode = mutableMapOf<String, FriendProgress>()

            try {
                // 1. Read shared daily leaderboard topic for real connected users
                fetchTopicEntries("lingowords_real_board_v1", latestByCode)

                // 2. Read each specifically invited friend's topic
                for (code in watchedCodes) {
                    val clean = code.replace(Regex("[^A-Z0-9]"), "")
                    if (clean.isNotEmpty()) {
                        fetchTopicEntries("slovicka_user_$clean", latestByCode)
                    }
                }

                mainHandler.post {
                    val myCode = (googleUser?.inviteCode ?: "").uppercase(Locale.US)
                    val myRemote = if (myCode.isNotEmpty()) latestByCode[myCode] else null
                    if (myRemote != null && myRemote.lastActiveDate == todayKey) {
                        var updatedLocal = false
                        if (myRemote.todayCompleted > todayCompletedWords) {
                            todayCompletedWords = myRemote.todayCompleted
                            updatedLocal = true
                        }
                        if (myRemote.todayXp > todayXp) {
                            val goalBonus = if (todayCompletedWords >= dailyGoalWords) 5 else 0
                            todayBaseXp = (myRemote.todayXp - goalBonus).coerceAtLeast(todayCompletedWords)
                            updatedLocal = true
                        }
                        if (updatedLocal) {
                            updateTodayInWeeklyLog()
                            prefs.edit()
                                .putString("today_date", todayKey)
                                .putInt("today_completed", todayCompletedWords)
                                .putString("today_xp_date", todayKey)
                                .putInt("today_base_xp", todayBaseXp)
                                .apply()
                        }
                    }
                    for ((code, remoteFriend) in latestByCode) {
                        if (code == myCode) continue
                        val existingIdx = friends.indexOfFirst {
                            it.emailOrCode.equals(code, ignoreCase = true)
                        }
                        if (existingIdx >= 0) {
                            friends[existingIdx] = remoteFriend
                        } else {
                            friends.add(remoteFriend)
                        }
                    }
                    saveFriends()
                    lastSyncStatus = if (isCzechLocale()) {
                        "Online žebříček aktualizován ✓"
                    } else {
                        "Online leaderboard updated ✓"
                    }
                    isSyncingCloud = false
                }
            } catch (_: Exception) {
                mainHandler.post {
                    isSyncingCloud = false
                }
            }
        }
    }

    private fun postJsonToTopic(topic: String, jsonPayload: String) {
        val url = URL("https://ntfy.sh/$topic")
        val conn = (url.openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"
            connectTimeout = 5000
            readTimeout = 5000
            doOutput = true
            setRequestProperty("Content-Type", "text/plain; charset=utf-8")
        }
        conn.outputStream.use { os ->
            os.write(jsonPayload.toByteArray(Charsets.UTF_8))
        }
        conn.inputStream.close()
        conn.disconnect()
    }

    private fun fetchTopicEntries(topic: String, outMap: MutableMap<String, FriendProgress>) {
        val url = URL("https://ntfy.sh/$topic/json?poll=1&since=7d")
        val conn = (url.openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            connectTimeout = 5000
            readTimeout = 5000
        }
        val currentDay = todayKey
        val weekStart = currentWeekStartKey()
        // Track max per-day values per user across Mon..Today so older clients also get accurate weekly sums
        val weekDailyByCode = mutableMapOf<String, MutableMap<String, Pair<Int, Int>>>()

        BufferedReader(InputStreamReader(conn.inputStream, Charsets.UTF_8)).use { reader ->
            var line: String?
            while (reader.readLine().also { line = it } != null) {
                val rawLine = line?.trim() ?: continue
                if (rawLine.isEmpty()) continue
                try {
                    val envelope = JSONObject(rawLine)
                    if (envelope.optString("event") != "message") continue
                    val msgStr = envelope.optString("message", "")
                    if (!msgStr.startsWith("{")) continue
                    val data = JSONObject(msgStr)
                    val code = data.optString("code", "").uppercase(Locale.US)
                    val email = data.optString("email", "").trim()
                    val name = normalizeKnownLeaderboardName(data.optString("name", ""))
                    // Only accept real users who have connected an email account
                    if (code.isBlank() || !email.contains("@") || name.isBlank() || name.startsWith("Hráč (")) continue

                    val date = data.optString("date", "")
                    val goalVal = data.optInt("dailyGoal", 20).coerceAtLeast(1)
                    val msgCompleted = data.optInt("todayCompleted", 0)
                    val fallbackDayXp = msgCompleted + if (msgCompleted >= goalVal) 5 else 0
                    val msgXp = data.optInt("todayXp", fallbackDayXp)

                    if (date >= weekStart && date.length == 10) {
                        val userDays = weekDailyByCode.getOrPut(code) { mutableMapOf() }
                        val prevDay = userDays[date]
                        userDays[date] = maxOf(msgCompleted, prevDay?.first ?: 0) to maxOf(msgXp, prevDay?.second ?: 0)
                    }

                    val todayVal = if (date == currentDay) msgCompleted else 0
                    val todayXpVal = if (date == currentDay) msgXp else 0
                    val explicitWeeklyCompleted = if (date >= weekStart) data.optInt("weeklyCompleted", msgCompleted) else 0
                    val explicitWeeklyXp = if (date >= weekStart) data.optInt("weeklyXp", msgXp) else 0
                    val summedDays = weekDailyByCode[code]?.values
                    val weeklyWordsVal = maxOf(explicitWeeklyCompleted, summedDays?.sumOf { it.first } ?: 0)
                    val weeklyXpVal = maxOf(explicitWeeklyXp, summedDays?.sumOf { it.second } ?: 0)

                    val streakVal = data.optInt("streakDays", 0)
                    val spokenVal = data.optInt("totalSpoken", 0)
                    val masteredVal = data.optInt("masteredCount", spokenVal)
                    val totalCorrectVal = data.optInt("totalCorrect", spokenVal)
                    val sessionsVal = data.optInt("completedSessions", 0)
                    val accuracyVal = data.optInt("accuracyPercent", 0)
                    val catalogVal = data.optInt("catalogWordCount", 1430)
                    val langCode = data.optString("languageCode", "en")
                    val lastActive = data.optString("lastActiveDate", date).ifEmpty { date }

                    outMap[code] = FriendProgress(
                        id = "cloud_$code",
                        displayName = name,
                        emailOrCode = code,
                        todayCompleted = todayVal,
                        dailyGoal = goalVal,
                        streakDays = streakVal,
                        totalSpoken = spokenVal,
                        statusText = if (todayVal >= goalVal) "Cíl splněn ✓" else "Dnes $todayVal/$goalVal slov",
                        masteredCount = masteredVal,
                        totalCorrectCount = totalCorrectVal,
                        completedSessions = sessionsVal,
                        accuracyPercent = accuracyVal,
                        catalogWordCount = catalogVal,
                        languageCode = langCode,
                        lastActiveDate = lastActive,
                        todayXp = todayXpVal,
                        weeklyCompleted = weeklyWordsVal,
                        weeklyXp = weeklyXpVal
                    )
                } catch (_: Exception) {
                }
            }
        }
        conn.disconnect()
    }

    private fun saveStats() {
        // Snapshot lightweight references on Main thread; run full string serialization & disk write off-thread
        val dayKeySnapshot = todayKey
        val todayCompletedSnapshot = todayCompletedWords
        val todayBaseXpSnapshot = todayBaseXp
        val totalAttemptsSnapshot = totalAttempts
        val totalCorrectSnapshot = totalCorrect
        val totalSpokenSnapshot = totalSpokenCorrect
        val completedSessionsSnapshot = completedSessions
        val streakSnapshot = streakDays
        val srsSnapshot = wordSrsMap.values.toList()
        val weeklySnapshot = weeklyDailyStats.toMap()
        val reviewSetSnapshot = needsReviewWordIds.toSet()
        val masteredSetSnapshot = masteredWordIds.toSet()

        prefsExecutor.execute {
            val serializedSrs = srsSnapshot.joinToString("\n") {
                "${it.wordId}|${it.box}|${it.lapses}|${it.dueAtMillis}"
            }
            val serializedWeeklyLog = weeklySnapshot.entries.joinToString("\n") { (date, pair) ->
                "$date|${pair.first}|${pair.second}"
            }
            prefs.edit()
                .putString("today_date", dayKeySnapshot)
                .putInt("today_completed", todayCompletedSnapshot)
                .putString("today_xp_date", dayKeySnapshot)
                .putInt("today_base_xp", todayBaseXpSnapshot)
                .putString("weekly_daily_log_v1", serializedWeeklyLog)
                .putInt("total_attempts", totalAttemptsSnapshot)
                .putInt("total_correct", totalCorrectSnapshot)
                .putInt("total_spoken", totalSpokenSnapshot)
                .putInt("completed_sessions", completedSessionsSnapshot)
                .putInt("streak_days", streakSnapshot)
                .putStringSet("needs_review_word_ids", reviewSetSnapshot)
                .putStringSet("mastered_word_ids", masteredSetSnapshot)
                .putString("word_srs_stats_v1", serializedSrs)
                .apply()
        }
    }

    private fun loadSavedUser(): GoogleUserProfile? {
        val rawName = prefs.getString("google_name", null) ?: return null
        val email = prefs.getString("google_email", null) ?: return null
        if (!email.contains("@")) return null
        val code = prefs.getString("google_code", "SLOV-4821") ?: "SLOV-4821"
        val migrated = prefs.getBoolean("lenka_name_migrated_v1", false)
        val effectiveName = if (!migrated && rawName.trim().equals("Sichyngrova", ignoreCase = true)) {
            prefs.edit()
                .putString("google_name", "Lenka S.")
                .putBoolean("lenka_name_migrated_v1", true)
                .apply()
            "Lenka S."
        } else {
            rawName
        }
        return GoogleUserProfile(effectiveName, email, code)
    }

    private fun loadSavedFriends(): List<FriendProgress> {
        val raw = prefs.getString("friends_serialized", null)
        if (raw.isNullOrBlank()) {
            return emptyList()
        }
        val currentDay = todayKey
        val weekStart = currentWeekStartKey()
        return raw.split("\n").mapNotNull { line ->
            val parts = line.split("|")
            if (parts.size >= 7) {
                val id = parts[0]
                val name = normalizeKnownLeaderboardName(parts[1])
                val code = parts[2]
                // Filter out any previously saved demo entries
                if (id.startsWith("demo_") ||
                    code == "SLOV-6104" ||
                    code == "SLOV-7392" ||
                    name.equals("Petra Nováková", ignoreCase = true) ||
                    name.equals("Martin Dvořák", ignoreCase = true)
                ) {
                    null
                } else {
                    val savedLastActive = parts.getOrNull(13).orEmpty()
                    val savedToday = parts[3].toIntOrNull() ?: 0
                    val isSameDay = savedLastActive.isEmpty() || savedLastActive == currentDay
                    val isSameWeek = savedLastActive.isEmpty() || savedLastActive >= weekStart
                    val effectiveToday = if (isSameDay) savedToday else 0
                    val effectiveTodayXp = if (isSameDay) (parts.getOrNull(14)?.toIntOrNull() ?: effectiveToday) else 0
                    val effectiveWeeklyCompleted = if (isSameWeek) (parts.getOrNull(15)?.toIntOrNull() ?: savedToday) else 0
                    val effectiveWeeklyXp = if (isSameWeek) (parts.getOrNull(16)?.toIntOrNull() ?: effectiveTodayXp) else 0
                    val spoken = parts[6].toIntOrNull() ?: 0
                    FriendProgress(
                        id = id,
                        displayName = name,
                        emailOrCode = code,
                        todayCompleted = effectiveToday,
                        dailyGoal = parts[4].toIntOrNull() ?: 20,
                        streakDays = parts[5].toIntOrNull() ?: 0,
                        totalSpoken = spoken,
                        masteredCount = parts.getOrNull(7)?.toIntOrNull() ?: spoken,
                        totalCorrectCount = parts.getOrNull(8)?.toIntOrNull() ?: spoken,
                        completedSessions = parts.getOrNull(9)?.toIntOrNull() ?: 0,
                        accuracyPercent = parts.getOrNull(10)?.toIntOrNull() ?: 0,
                        catalogWordCount = parts.getOrNull(11)?.toIntOrNull() ?: 1430,
                        languageCode = parts.getOrNull(12)?.ifEmpty { "en" } ?: "en",
                        lastActiveDate = savedLastActive,
                        todayXp = effectiveTodayXp,
                        weeklyCompleted = effectiveWeeklyCompleted,
                        weeklyXp = effectiveWeeklyXp
                    )
                }
            } else null
        }
    }

    private fun saveFriends() {
        val serialized = friends
            .filter { !it.id.startsWith("demo_") }
            .joinToString("\n") {
                "${it.id}|${it.displayName}|${it.emailOrCode}|${it.todayCompleted}|${it.dailyGoal}|${it.streakDays}|${it.totalSpoken}|${it.masteredCount}|${it.totalCorrectCount}|${it.completedSessions}|${it.accuracyPercent}|${it.catalogWordCount}|${it.languageCode}|${it.lastActiveDate}|${it.todayXp}|${it.weeklyCompleted}|${it.weeklyXp}"
            }
        prefs.edit().putString("friends_serialized", serialized).apply()
    }

    fun updateOtaRepoSlug(rawInput: String) {
        val normalized = OtaUpdater.normalizeRepoSlug(rawInput)
        if (normalized.isBlank()) return
        otaRepoSlug = normalized
        prefs.edit().putString("ota_github_repo", normalized).apply()
        otaStatusMessage = null
        availableOtaRelease = null
        downloadedOtaApkFile = null
        checkForOtaUpdate(manual = true)
    }

    fun openOtaModal() {
        showOtaDialog = true
    }

    fun closeOtaModal() {
        showOtaDialog = false
    }

    fun dismissOtaBanner() {
        otaBannerDismissed = true
    }

    fun checkForOtaUpdate(manual: Boolean = false) {
        if (isCheckingOta || isDownloadingOta) return
        val now = System.currentTimeMillis()
        // Battery & radio optimization: check automatically at most once every 4 hours (even across restarts/resumes)
        // and skip if we already detected a newer release waiting to be installed.
        if (!manual && (availableOtaRelease != null || now - lastOtaCheckMillis < 4L * 3600_000L)) return

        val slug = otaRepoSlug
        val isCs = isCzechLocale()
        isCheckingOta = true
        if (manual) {
            otaStatusMessage = if (isCs) "Kontroluji GitHub Releases..." else "Checking GitHub Releases..."
        }

        ioExecutor.execute {
            val result = OtaUpdater.checkLatestRelease(slug, isCs)
            mainHandler.post {
                isCheckingOta = false
                val checkedAt = System.currentTimeMillis()
                lastOtaCheckMillis = checkedAt
                prefs.edit().putLong("last_ota_check_millis", checkedAt).apply()
                when (result) {
                    is OtaCheckResult.UpdateAvailable -> {
                        val prevVersion = availableOtaRelease?.versionName
                        availableOtaRelease = result.release
                        if (prevVersion != result.release.versionName) {
                            otaBannerDismissed = false
                        }
                        otaStatusMessage = if (isCs) {
                            "Nová verze v${result.release.versionName} je připravena!"
                        } else {
                            "New version v${result.release.versionName} is ready!"
                        }
                        if (manual) {
                            showOtaDialog = true
                        }
                    }
                    is OtaCheckResult.UpToDate -> {
                        availableOtaRelease = null
                        if (manual) {
                            otaStatusMessage = if (isCs) {
                                "Máte nejnovější verzi (v${result.currentVersion}) ✓"
                            } else {
                                "You are up to date (v${result.currentVersion}) ✓"
                            }
                        }
                    }
                    is OtaCheckResult.RepoNotReady -> {
                        availableOtaRelease = null
                        if (manual) {
                            otaStatusMessage = result.message
                        }
                    }
                    is OtaCheckResult.Error -> {
                        if (manual) {
                            otaStatusMessage = result.message
                        }
                    }
                }
            }
        }
    }

    fun downloadAndInstallOta() {
        val release = availableOtaRelease ?: return
        if (isDownloadingOta) return

        val cached = downloadedOtaApkFile
        if (cached != null && cached.exists() && cached.name.contains(release.versionName)) {
            triggerOtaInstall(cached)
            return
        }

        val isCs = isCzechLocale()
        isDownloadingOta = true
        otaDownloadFraction = 0f
        otaDownloadedMbText = ""
        otaNeedsInstallPermission = false
        otaStatusMessage = if (isCs) "Stahuji aktualizaci..." else "Downloading update..."

        ioExecutor.execute {
            try {
                val apkFile = OtaUpdater.downloadApk(appContext, release) { downloaded, total ->
                    val frac = if (total > 0) (downloaded.toFloat() / total.toFloat()).coerceIn(0f, 1f) else 0f
                    val dlMb = String.format(Locale.US, "%.1f", downloaded / (1024f * 1024f))
                    val totMb = if (total > 0) String.format(Locale.US, "%.1f", total / (1024f * 1024f)) else "?"
                    mainHandler.post {
                        otaDownloadFraction = frac
                        otaDownloadedMbText = "$dlMb / $totMb MB"
                    }
                }
                mainHandler.post {
                    isDownloadingOta = false
                    otaDownloadFraction = 1f
                    downloadedOtaApkFile = apkFile
                    otaStatusMessage = if (isCs) "Staženo! Spouštím instalátor..." else "Downloaded! Opening installer..."
                    triggerOtaInstall(apkFile)
                }
            } catch (e: Exception) {
                mainHandler.post {
                    isDownloadingOta = false
                    otaStatusMessage = if (isCs) {
                        "Stažení selhalo: ${e.message ?: e.javaClass.simpleName}"
                    } else {
                        "Download failed: ${e.message ?: e.javaClass.simpleName}"
                    }
                }
            }
        }
    }

    fun triggerOtaInstall(apkFile: File? = downloadedOtaApkFile) {
        val file = apkFile ?: return
        val isCs = isCzechLocale()
        when (OtaUpdater.installApk(appContext, file)) {
            OtaInstallResult.LAUNCHED_INSTALLER -> {
                otaNeedsInstallPermission = false
            }
            OtaInstallResult.NEEDS_INSTALL_PERMISSION -> {
                otaNeedsInstallPermission = true
                otaStatusMessage = if (isCs) {
                    "Povolte prosím instalaci z tohoto zdroje pro LingoWords."
                } else {
                    "Please allow installing updates from this source for LingoWords."
                }
            }
            OtaInstallResult.ERROR -> {
                otaStatusMessage = if (isCs) {
                    "Nepodařilo se otevřít instalátor APK."
                } else {
                    "Could not open APK installer."
                }
            }
        }
    }

    fun openOtaInstallPermissionSettings() {
        try {
            appContext.startActivity(OtaUpdater.createUnknownSourcesSettingsIntent(appContext))
        } catch (_: Exception) {
        }
    }

    fun retryPendingOtaInstallIfPermitted() {
        if (!otaNeedsInstallPermission) return
        val file = downloadedOtaApkFile ?: return
        if (android.os.Build.VERSION.SDK_INT < android.os.Build.VERSION_CODES.O ||
            appContext.packageManager.canRequestPackageInstalls()
        ) {
            otaNeedsInstallPermission = false
            triggerOtaInstall(file)
        }
    }
}
