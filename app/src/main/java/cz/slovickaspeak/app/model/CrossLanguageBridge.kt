package cz.slovickaspeak.app.model

import android.content.Context
import cz.slovickaspeak.app.data.ProgressStateHolder
import org.json.JSONObject
import java.util.concurrent.ConcurrentHashMap

/**
 * High-performance cross-language translation bridge for flashcards.
 *
 * Supports any of the 6 languages (Czech, English, Spanish, German, French, Italian)
 * acting as the prompt language (native) while learning any of the other 5 target languages (30 total combinations).
 */
object CrossLanguageBridge {

    private val promptCache = ConcurrentHashMap<LearningLanguage, Map<String, String>>()
    @Volatile
    private var appContext: Context? = null

    fun init(context: Context) {
        appContext = context.applicationContext
    }

    /**
     * Preloads or retrieves prompt translation dictionary for [lang].
     * Czech is the canonical baseline so it does not require a translation table.
     */
    fun getPromptMap(lang: LearningLanguage, context: Context? = null): Map<String, String> {
        if (lang == LearningLanguage.CZECH) return emptyMap()

        promptCache[lang]?.let { return it }

        val ctx = context ?: appContext
        val assetName = "prompts_${lang.code}.json"
        return try {
            val stream = ctx?.assets?.open(assetName)
                ?: java.io.File("src/main/assets/$assetName").takeIf { it.exists() }?.inputStream()
                ?: java.io.File("app/src/main/assets/$assetName").takeIf { it.exists() }?.inputStream()
                ?: return emptyMap()

            val text = stream.bufferedReader(Charsets.UTF_8).use { it.readText() }
            val map = parseFlatJson(text)
            promptCache.clear() // RAM optimization: Keep only active native language in memory
            promptCache[lang] = map
            map
        } catch (_: Exception) {
            emptyMap()
        }
    }

    private fun parseFlatJson(text: String): Map<String, String> {
        val map = HashMap<String, String>(5200)
        val pattern = Regex(""""((?:\\.|[^"\\])*)"\s*:\s*"((?:\\.|[^"\\])*)"""")
        for (match in pattern.findAll(text)) {
            val key = unescapeJson(match.groupValues[1])
            val value = unescapeJson(match.groupValues[2])
            map[key] = value
        }
        return map
    }

    private fun unescapeJson(s: String): String {
        return s.replace("\\\"", "\"")
            .replace("\\\\", "\\")
            .replace("\\n", "\n")
            .replace("\\r", "\r")
            .replace("\\t", "\t")
    }

    /**
     * Translates a Czech concept string into the user's native language.
     * If nativeLanguage is Czech or translation is unavailable, returns [czechText].
     */
    fun translate(czechText: String, nativeLang: LearningLanguage, context: Context? = null): String {
        if (nativeLang == LearningLanguage.CZECH || czechText.isBlank()) return czechText
        val map = getPromptMap(nativeLang, context)
        return map[czechText]?.takeIf { it.isNotBlank() } ?: czechText
    }

    /**
     * Returns the full list of decks customized for [nativeLang] -> [targetLang].
     */
    fun decksFor(
        nativeLang: LearningLanguage,
        targetLang: LearningLanguage,
        context: Context? = null
    ): List<VocabDeck> {
        // Case 1: Learning Czech
        if (targetLang == LearningLanguage.CZECH) {
            return when (nativeLang) {
                LearningLanguage.ENGLISH -> BuiltInVocabulary.czechDecks
                LearningLanguage.SPANISH -> reverseDecksToCzech(BuiltInVocabulary.spanishDecks, nativeLang)
                LearningLanguage.GERMAN -> reverseDecksToCzech(BuiltInVocabulary.germanDecks, nativeLang)
                LearningLanguage.FRENCH -> reverseDecksToCzech(BuiltInVocabulary.frenchDecks, nativeLang)
                LearningLanguage.ITALIAN -> reverseDecksToCzech(BuiltInVocabulary.italianDecks, nativeLang)
                LearningLanguage.PORTUGUESE -> reverseDecksToCzech(BuiltInVocabulary.portugueseDecks, nativeLang)
                LearningLanguage.CZECH -> BuiltInVocabulary.englishDecks // Fallback: default to English if both Czech
            }
        }

        val baseDecks = BuiltInVocabulary.decksForLanguage(targetLang)

        // Case 2: Native language is Czech - return standard target decks as-is
        if (nativeLang == LearningLanguage.CZECH) {
            return baseDecks
        }

        // Case 3: Native language is non-Czech (e.g. English, Spanish, German, French, Italian)
        // Card prompt (w.czech) is translated into nativeLang, while w.english remains the target word.
        val promptMap = getPromptMap(nativeLang, context)
        return baseDecks.map { deck ->
            val localizedTitle = promptMap[deck.title]?.takeIf { it.isNotBlank() } ?: deck.title
            val localizedSubtitle = promptMap[deck.subtitle]?.takeIf { it.isNotBlank() } ?: deck.subtitle
            deck.copy(
                title = localizedTitle,
                subtitle = localizedSubtitle,
                titleEn = if (nativeLang == LearningLanguage.ENGLISH) localizedTitle else deck.titleEn,
                subtitleEn = if (nativeLang == LearningLanguage.ENGLISH) localizedSubtitle else deck.subtitleEn,
                words = deck.words.map { word ->
                    val localizedPrompt = promptMap[word.czech]?.takeIf { it.isNotBlank() } ?: word.czech
                    word.copy(czech = localizedPrompt)
                }
            )
        }
    }

    private fun reverseDecksToCzech(
        sourceDecks: List<VocabDeck>,
        nativeLang: LearningLanguage
    ): List<VocabDeck> {
        return sourceDecks.map { deck ->
            deck.copy(
                id = "cz_rev_" + deck.id,
                title = deck.titleEn.ifBlank { deck.title },
                subtitle = if (deck.subtitleEn.isNotBlank()) deck.subtitleEn else "Learn Czech",
                titleEn = deck.titleEn.ifBlank { deck.title },
                subtitleEn = if (deck.subtitleEn.isNotBlank()) deck.subtitleEn else "Practice speaking Czech",
                language = LearningLanguage.CZECH,
                words = deck.words.map { w ->
                    VocabWord(
                        id = "cz_rev_" + w.id,
                        czech = w.english, // Prompt in user's native language (e.g. Spanish/German)
                        english = w.czech, // Target word in Czech
                        acceptedVariants = emptyList(),
                        phonetic = "",
                        emoji = w.emoji,
                        exampleSentence = w.exampleSentence
                    )
                }
            )
        }
    }
}
