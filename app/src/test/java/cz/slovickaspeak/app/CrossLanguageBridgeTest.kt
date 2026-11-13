package cz.slovickaspeak.app

import cz.slovickaspeak.app.model.CrossLanguageBridge
import cz.slovickaspeak.app.model.LearningLanguage
import org.junit.Assert.*
import org.junit.Test

class CrossLanguageBridgeTest {

    @Test
    fun testAllLanguageCombinationsProduceValidDecks() {
        val languages = LearningLanguage.entries

        for (nativeLang in languages) {
            for (targetLang in languages) {
                if (nativeLang == targetLang) continue

                val decks = CrossLanguageBridge.decksFor(nativeLang, targetLang)
                assertTrue(
                    "Decks for $nativeLang -> $targetLang must not be empty",
                    decks.isNotEmpty()
                )

                val firstDeck = decks.first()
                assertEquals(
                    "Target language must match $targetLang",
                    targetLang,
                    firstDeck.language
                )
                assertTrue(
                    "Deck words must not be empty for $nativeLang -> $targetLang",
                    firstDeck.words.isNotEmpty()
                )

                val firstWord = firstDeck.words.first()
                assertTrue(
                    "Word ID must be non-blank for $nativeLang -> $targetLang",
                    firstWord.id.isNotBlank()
                )
                assertTrue(
                    "Word prompt must be non-blank for $nativeLang -> $targetLang",
                    firstWord.czech.isNotBlank()
                )
                assertTrue(
                    "Word target must be non-blank for $nativeLang -> $targetLang",
                    firstWord.english.isNotBlank()
                )
            }
        }
    }

    @Test
    fun testEnglishNativeSpeakerSeesEnglishPromptsForSpanish() {
        val decks = CrossLanguageBridge.decksFor(LearningLanguage.ENGLISH, LearningLanguage.SPANISH)
        val firstDeck = decks.first()
        val firstWord = firstDeck.words.first()

        // In Spanish deck, first word is "hola".
        // English native speaker should see an English prompt (e.g. "Hello" or "Good day"), NOT Czech "Ahoj / Dobrý den"
        assertEquals("hola", firstWord.english)
        assertFalse(
            "Prompt should not be Czech 'Ahoj / Dobrý den' for English speaker",
            firstWord.czech.contains("Dobrý den")
        )
    }

    @Test
    fun testSpanishNativeSpeakerLearningGerman() {
        val decks = CrossLanguageBridge.decksFor(LearningLanguage.SPANISH, LearningLanguage.GERMAN)
        val firstDeck = decks.first()
        val firstWord = firstDeck.words.first()

        assertEquals("Hallo", firstWord.english)
        assertFalse(
            "Prompt should not be Czech for Spanish speaker",
            firstWord.czech.contains("Dobrý den")
        )
    }

    @Test
    fun testSpanishSpeakerLearningCzechReversesDecks() {
        val decks = CrossLanguageBridge.decksFor(LearningLanguage.SPANISH, LearningLanguage.CZECH)
        assertTrue(decks.isNotEmpty())
        val firstDeck = decks.first()
        assertEquals(LearningLanguage.CZECH, firstDeck.language)

        val firstWord = firstDeck.words.first()
        // Target to speak is Czech
        assertEquals("Ahoj / Dobrý den", firstWord.english)
        // Prompt is in Spanish
        assertEquals("hola", firstWord.czech)
    }

    @Test
    fun testPortugueseNativeSpeakerLearningSpanish() {
        val decks = CrossLanguageBridge.decksFor(LearningLanguage.PORTUGUESE, LearningLanguage.SPANISH)
        val firstDeck = decks.first()
        val firstWord = firstDeck.words.first()

        assertEquals("hola", firstWord.english)
        assertFalse(
            "Prompt should not be Czech for Portuguese speaker",
            firstWord.czech.contains("Dobrý den")
        )
    }

    @Test
    fun testPortugueseSpeakerLearningCzechReversesDecks() {
        val decks = CrossLanguageBridge.decksFor(LearningLanguage.PORTUGUESE, LearningLanguage.CZECH)
        assertTrue(decks.isNotEmpty())
        val firstDeck = decks.first()
        assertEquals(LearningLanguage.CZECH, firstDeck.language)

        val firstWord = firstDeck.words.first()
        assertEquals(LearningLanguage.CZECH, firstDeck.language)
        assertTrue(firstWord.english.isNotBlank())
        assertTrue(firstWord.czech.isNotBlank())
    }
}
