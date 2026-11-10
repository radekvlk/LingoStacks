package cz.slovickaspeak.app.model

enum class LearningLanguage(
    val code: String,
    val speechLocaleTag: String,
    val flagEmoji: String,
    val titleCs: String,
    val titleEn: String
) {
    ENGLISH("en", "en-US", "🇬🇧", "Angličtina", "English"),
    SPANISH("es", "es-ES", "🇪🇸", "Španělština", "Spanish");

    fun localizedTitle(isCzech: Boolean): String = if (isCzech) titleCs else titleEn
}

enum class CefrLevel(
    val label: String,
    val description: String,
    val descriptionEn: String,
    val colorHex: Long
) {
    A1("A1", "Začátečník", "Beginner", 0xFF34C759),
    A2("A2", "Mírně pokročilý", "Elementary", 0xFF007AFF),
    B1("B1", "Středně pokročilý", "Intermediate", 0xFFFF9500),
    B2("B2", "Vyšší středně pokročilý", "Upper Intermediate", 0xFFAF52DE),
    C1("C1", "Pokročilý", "Advanced", 0xFFFF2D55),
    C2("C2", "Expert / Rodilý mluvčí", "Mastery / Native", 0xFF5856D6);

    fun localizedDescription(isCzech: Boolean): String = if (isCzech) description else descriptionEn
}

data class VocabWord(
    val id: String,
    val czech: String,
    val english: String,
    val acceptedVariants: List<String> = emptyList(),
    val phonetic: String = "",
    val emoji: String = "📘",
    val exampleSentence: String = ""
)

data class VocabDeck(
    val id: String,
    val title: String,
    val subtitle: String,
    val emoji: String,
    val level: CefrLevel,
    val words: List<VocabWord>,
    val titleEn: String = title,
    val subtitleEn: String = subtitle,
    val language: LearningLanguage = LearningLanguage.ENGLISH
) {
    fun localizedTitle(isCzech: Boolean): String = if (isCzech) title else titleEn
    fun localizedSubtitle(isCzech: Boolean): String = if (isCzech) subtitle else subtitleEn
}

enum class StudyDirection {
    NATIVE_TO_FOREIGN, // CZ -> EN / ES
    FOREIGN_TO_NATIVE, // EN / ES -> CZ
    MIXED;             // Střídání obou směrů

    fun shortLabel(language: LearningLanguage): String {
        val foreignCode = language.code.uppercase()
        return when (this) {
            NATIVE_TO_FOREIGN -> "CZ → $foreignCode"
            FOREIGN_TO_NATIVE -> "$foreignCode → CZ"
            MIXED -> "CZ ⇄ $foreignCode"
        }
    }

    fun localizedDescription(isCzech: Boolean, language: LearningLanguage): String {
        val foreignCode = language.code.uppercase()
        return when (this) {
            NATIVE_TO_FOREIGN -> if (isCzech) "Z češtiny do $foreignCode (aktivní)" else "Czech → $foreignCode (active)"
            FOREIGN_TO_NATIVE -> if (isCzech) "Z $foreignCode do češtiny (poslech)" else "$foreignCode → Czech (passive)"
            MIXED -> if (isCzech) "Střídavě oba směry (Mix)" else "Mixed both ways"
        }
    }
}

enum class AnswerMethod {
    SPOKEN_CORRECT,
    SWIPED_KNOW,
    SWIPED_DONT_KNOW
}

data class CardAttemptResult(
    val word: VocabWord,
    val isCorrect: Boolean,
    val method: AnswerMethod,
    val spokenTranscript: String? = null,
    val wasReversed: Boolean = false
)

object BuiltInVocabulary {
    val englishDecks: List<VocabDeck> by lazy {
        VocabularyBankPart1.decks + VocabularyBankPart2.decks + VocabularyBankPart3.decks + VocabularyBankPart4.decks
    }

    val spanishDecks: List<VocabDeck> by lazy {
        SpanishVocabularyPart1.decks + SpanishVocabularyPart2.decks + SpanishVocabularyPart3.decks + SpanishVocabularyPart4.decks
    }

    val decks: List<VocabDeck> by lazy {
        englishDecks + spanishDecks
    }

    fun decksForLanguage(language: LearningLanguage): List<VocabDeck> = when (language) {
        LearningLanguage.ENGLISH -> englishDecks
        LearningLanguage.SPANISH -> spanishDecks
    }
}
