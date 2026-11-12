package cz.slovickaspeak.app.model

enum class LearningLanguage(
    val code: String,
    val speechLocaleTag: String,
    val flagEmoji: String,
    val titleCs: String,
    val titleEn: String
) {
    ENGLISH("en", "en-US", "🇬🇧", "Angličtina", "English"),
    SPANISH("es", "es-ES", "🇪🇸", "Španělština", "Spanish"),
    GERMAN("de", "de-DE", "🇩🇪", "Němčina", "German"),
    FRENCH("fr", "fr-FR", "🇫🇷", "Francouzština", "French"),
    ITALIAN("it", "it-IT", "🇮🇹", "Italština", "Italian"),
    CZECH("cs", "cs-CZ", "🇨🇿", "Čeština", "Czech");

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
            NATIVE_TO_FOREIGN -> "CZ → $foreignCode"
            FOREIGN_TO_NATIVE -> "$foreignCode → CZ"
            MIXED -> if (isCzech) "Střídavě oba směry" else "Mixed both ways"
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

    val germanDecks: List<VocabDeck> by lazy {
        GermanVocabularyPart1.decks + GermanVocabularyPart2.decks + GermanVocabularyPart3.decks + GermanVocabularyPart4.decks
    }

    val frenchDecks: List<VocabDeck> by lazy {
        FrenchVocabularyPart1.decks + FrenchVocabularyPart2.decks + FrenchVocabularyPart3.decks + FrenchVocabularyPart4.decks
    }

    val italianDecks: List<VocabDeck> by lazy {
        ItalianVocabularyPart1.decks + ItalianVocabularyPart2.decks + ItalianVocabularyPart3.decks + ItalianVocabularyPart4.decks
    }

    val czechDecks: List<VocabDeck> by lazy {
        englishDecks.map { engDeck ->
            engDeck.copy(
                id = "cz_" + engDeck.id,
                title = engDeck.titleEn.ifBlank { engDeck.title },
                subtitle = if (engDeck.subtitleEn.isNotBlank()) engDeck.subtitleEn else "Learn Czech",
                titleEn = engDeck.titleEn.ifBlank { engDeck.title },
                subtitleEn = if (engDeck.subtitleEn.isNotBlank()) engDeck.subtitleEn else "Practice speaking Czech out loud",
                language = LearningLanguage.CZECH,
                words = engDeck.words.map { w ->
                    VocabWord(
                        id = "cz_" + w.id,
                        czech = w.english,
                        english = w.czech,
                        acceptedVariants = emptyList(),
                        phonetic = "",
                        emoji = w.emoji,
                        exampleSentence = w.exampleSentence
                    )
                }
            )
        }
    }

    val decks: List<VocabDeck> by lazy {
        englishDecks + spanishDecks + germanDecks + frenchDecks + italianDecks + czechDecks
    }

    fun decksForLanguage(language: LearningLanguage): List<VocabDeck> = when (language) {
        LearningLanguage.ENGLISH -> englishDecks
        LearningLanguage.SPANISH -> spanishDecks
        LearningLanguage.GERMAN -> germanDecks
        LearningLanguage.FRENCH -> frenchDecks
        LearningLanguage.ITALIAN -> italianDecks
        LearningLanguage.CZECH -> czechDecks
    }
}
