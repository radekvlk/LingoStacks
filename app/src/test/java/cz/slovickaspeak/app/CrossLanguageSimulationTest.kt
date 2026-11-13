package cz.slovickaspeak.app

import cz.slovickaspeak.app.model.LearningLanguage
import cz.slovickaspeak.app.model.VocabWord
import cz.slovickaspeak.app.speech.WordMatcher
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * End-to-end automated speech recognition simulation test suite across all 7 languages
 * (Czech, English, Spanish, German, French, Italian, Portuguese).
 *
 * Simulates real-world Google STT engine outputs:
 * - Direct matches
 * - Dropped diacritics / accents
 * - Preceding definite / indefinite spoken articles
 * - Homophones and phonetic transcriptions
 * - Cross-language reversed flashcards (any native language practicing foreign decks)
 * - Strict rejection of false positives (noise, unrelated words)
 */
class CrossLanguageSimulationTest {

    private fun vocab(
        id: String,
        nativePrompt: String,
        targetForeign: String,
        variants: List<String> = emptyList()
    ) = VocabWord(
        id = id,
        czech = nativePrompt, // In CrossLanguageBridge, czech holds the native prompt
        english = targetForeign, // english holds the target foreign word
        emoji = "🎯",
        exampleSentence = "",
        phonetic = "",
        acceptedVariants = variants
    )

    // ========================================================================
    // 1. FORWARD RECOGNITION (User speaks foreign language)
    // ========================================================================

    @Test
    fun englishSpeechSimulation() {
        val word = vocab("en_1", "jablko", "apple", listOf("red apple"))

        // Exact match
        assertNotNull(WordMatcher.matches(listOf("apple"), word, language = LearningLanguage.ENGLISH))
        // Article tolerance ("an apple", "the apple")
        assertNotNull(WordMatcher.matches(listOf("an apple"), word, language = LearningLanguage.ENGLISH))
        assertNotNull(WordMatcher.matches(listOf("the apple"), word, language = LearningLanguage.ENGLISH))
        // Synonym / variant
        assertNotNull(WordMatcher.matches(listOf("red apple"), word, language = LearningLanguage.ENGLISH))
        // Zero false positive
        assertNull(WordMatcher.matches(listOf("orange", "banana", "pear"), word, language = LearningLanguage.ENGLISH))
    }

    @Test
    fun spanishSpeechSimulation() {
        // "el camión" (truck), "el corazón" (heart) - testing accent stripping & leading articles
        val truck = vocab("es_1", "nálaďák", "camión", listOf("el camión"))
        assertNotNull(WordMatcher.matches(listOf("camión"), truck, language = LearningLanguage.SPANISH))
        assertNotNull(WordMatcher.matches(listOf("camion"), truck, language = LearningLanguage.SPANISH)) // Dropped accent
        assertNotNull(WordMatcher.matches(listOf("el camion"), truck, language = LearningLanguage.SPANISH)) // Article + dropped accent

        val hello = vocab("es_2", "ahoj", "hola")
        assertNotNull(WordMatcher.matches(listOf("hola"), hello, language = LearningLanguage.SPANISH))
        assertNotNull(WordMatcher.matches(listOf("ola"), hello, language = LearningLanguage.SPANISH)) // Silent H homophone

        val people = vocab("es_3", "lidé", "gente")
        assertNotNull(WordMatcher.matches(listOf("jente"), people, language = LearningLanguage.SPANISH)) // G/J phonetic

        assertNull(WordMatcher.matches(listOf("perro", "gato"), truck, language = LearningLanguage.SPANISH))
    }

    @Test
    fun germanSpeechSimulation() {
        val street = vocab("de_1", "ulice", "Straße", listOf("die Straße"))
        assertNotNull(WordMatcher.matches(listOf("Straße"), street, language = LearningLanguage.GERMAN))
        assertNotNull(WordMatcher.matches(listOf("Strasse"), street, language = LearningLanguage.GERMAN)) // ß -> ss
        assertNotNull(WordMatcher.matches(listOf("die Strasse"), street, language = LearningLanguage.GERMAN)) // Article + ss

        val apple = vocab("de_2", "jablko", "Apfel", listOf("der Apfel"))
        assertNotNull(WordMatcher.matches(listOf("der Apfel"), apple, language = LearningLanguage.GERMAN))
        assertNotNull(WordMatcher.matches(listOf("ein Apfel"), apple, language = LearningLanguage.GERMAN))

        val forWord = vocab("de_3", "pro", "für")
        assertNotNull(WordMatcher.matches(listOf("für"), forWord, language = LearningLanguage.GERMAN))
        assertNotNull(WordMatcher.matches(listOf("fuer"), forWord, language = LearningLanguage.GERMAN)) // Umlaut expansion

        assertNull(WordMatcher.matches(listOf("Katze", "Wasser"), street, language = LearningLanguage.GERMAN))
    }

    @Test
    fun frenchSpeechSimulation() {
        val water = vocab("fr_1", "voda", "eau", listOf("l'eau"))
        assertNotNull(WordMatcher.matches(listOf("eau"), water, language = LearningLanguage.FRENCH))
        assertNotNull(WordMatcher.matches(listOf("l'eau"), water, language = LearningLanguage.FRENCH))
        assertNotNull(WordMatcher.matches(listOf("de l'eau"), water, language = LearningLanguage.FRENCH))

        val boy = vocab("fr_2", "chlapec", "garçon", listOf("le garçon"))
        assertNotNull(WordMatcher.matches(listOf("garçon"), boy, language = LearningLanguage.FRENCH))
        assertNotNull(WordMatcher.matches(listOf("garcon"), boy, language = LearningLanguage.FRENCH)) // Ç stripped
        assertNotNull(WordMatcher.matches(listOf("le garcon"), boy, language = LearningLanguage.FRENCH))

        assertNull(WordMatcher.matches(listOf("maison", "soleil"), water, language = LearningLanguage.FRENCH))
    }

    @Test
    fun italianSpeechSimulation() {
        val church = vocab("it_1", "kostel", "chiesa", listOf("la chiesa"))
        assertNotNull(WordMatcher.matches(listOf("chiesa"), church, language = LearningLanguage.ITALIAN))
        assertNotNull(WordMatcher.matches(listOf("la chiesa"), church, language = LearningLanguage.ITALIAN))
        assertNotNull(WordMatcher.matches(listOf("kiesa"), church, language = LearningLanguage.ITALIAN)) // Phonetic sound key

        val water = vocab("it_2", "voda", "acqua", listOf("l'acqua"))
        assertNotNull(WordMatcher.matches(listOf("l'acqua"), water, language = LearningLanguage.ITALIAN))
        assertNotNull(WordMatcher.matches(listOf("acqua"), water, language = LearningLanguage.ITALIAN))

        assertNull(WordMatcher.matches(listOf("formaggio", "vino"), church, language = LearningLanguage.ITALIAN))
    }

    @Test
    fun portugueseSpeechSimulation() {
        // Nasal vowels (coração, irmão), articles (o, a, um, uma), lh/nh
        val heart = vocab("pt_1", "srdce", "coração", listOf("o coração"))
        assertNotNull(WordMatcher.matches(listOf("coração"), heart, language = LearningLanguage.PORTUGUESE))
        assertNotNull(WordMatcher.matches(listOf("coracao"), heart, language = LearningLanguage.PORTUGUESE)) // Accents stripped
        assertNotNull(WordMatcher.matches(listOf("o coracao"), heart, language = LearningLanguage.PORTUGUESE)) // Article stripped

        val brother = vocab("pt_2", "bratr", "irmão", listOf("o irmão"))
        assertNotNull(WordMatcher.matches(listOf("irmao"), brother, language = LearningLanguage.PORTUGUESE))
        assertNotNull(WordMatcher.matches(listOf("um irmao"), brother, language = LearningLanguage.PORTUGUESE))

        val son = vocab("pt_3", "syn", "filho", listOf("o filho"))
        assertNotNull(WordMatcher.matches(listOf("filho"), son, language = LearningLanguage.PORTUGUESE))
        assertNotNull(WordMatcher.matches(listOf("filio"), son, language = LearningLanguage.PORTUGUESE)) // lh phonetic tolerance

        assertNull(WordMatcher.matches(listOf("janela", "computador"), heart, language = LearningLanguage.PORTUGUESE))
    }

    @Test
    fun czechSpeechSimulation() {
        val cat = vocab("cz_1", "cat", "kočka")
        assertNotNull(WordMatcher.matches(listOf("kočka"), cat, language = LearningLanguage.CZECH))
        assertNotNull(WordMatcher.matches(listOf("kočku"), cat, language = LearningLanguage.CZECH)) // Declension tolerance
        assertNotNull(WordMatcher.matches(listOf("kocku"), cat, language = LearningLanguage.CZECH)) // Without accents

        val ice = vocab("cz_2", "ice", "led")
        assertNotNull(WordMatcher.matches(listOf("let"), ice, language = LearningLanguage.CZECH)) // Devoicing d -> t

        assertNull(WordMatcher.matches(listOf("strom", "pes"), cat, language = LearningLanguage.CZECH))
    }

    // ========================================================================
    // 2. REVERSED FLASHCARDS SIMULATION ACROSS CROSS-LANGUAGE PAIRS
    // ========================================================================

    @Test
    fun reversedCardsSpanishNativeLearningGerman() {
        // Spanish speaker learning German:
        // Foreign target word is "der Hund" (dog). Native prompt is "el perro / perro".
        val card = vocab("de_dog", "el perro / perro", "der Hund")

        // In reversed card: user sees "der Hund", must speak Spanish ("perro" or "el perro")
        val match1 = WordMatcher.matches(
            spokenCandidates = listOf("perro"),
            targetWord = card,
            expectCzech = true,
            language = LearningLanguage.SPANISH
        )
        assertNotNull("Spanish native speaking 'perro' for German card must match", match1)

        val match2 = WordMatcher.matches(
            spokenCandidates = listOf("el perro"),
            targetWord = card,
            expectCzech = true,
            language = LearningLanguage.SPANISH
        )
        assertNotNull("Spanish native speaking 'el perro' must match (article stripped)", match2)

        // Wrong word rejected
        val matchWrong = WordMatcher.matches(
            spokenCandidates = listOf("el gato"),
            targetWord = card,
            expectCzech = true,
            language = LearningLanguage.SPANISH
        )
        assertNull("Wrong Spanish word must be rejected", matchWrong)
    }

    @Test
    fun reversedCardsEnglishNativeLearningFrench() {
        // English native learning French:
        // Foreign target is "la pomme" (apple). Native prompt is "apple".
        val card = vocab("fr_apple", "apple / an apple", "la pomme")

        val match1 = WordMatcher.matches(
            spokenCandidates = listOf("apple"),
            targetWord = card,
            expectCzech = true,
            language = LearningLanguage.ENGLISH
        )
        assertNotNull("English native speaking 'apple' for French card must match", match1)

        val match2 = WordMatcher.matches(
            spokenCandidates = listOf("an apple"),
            targetWord = card,
            expectCzech = true,
            language = LearningLanguage.ENGLISH
        )
        assertNotNull(match2)

        val matchWrong = WordMatcher.matches(
            spokenCandidates = listOf("banana"),
            targetWord = card,
            expectCzech = true,
            language = LearningLanguage.ENGLISH
        )
        assertNull(matchWrong)
    }

    @Test
    fun reversedCardsGermanNativeLearningPortuguese() {
        // German native learning Portuguese:
        // Foreign target is "o livro" (book). Native prompt is "das Buch / Buch".
        val card = vocab("pt_book", "das Buch / Buch", "o livro")

        val match1 = WordMatcher.matches(
            spokenCandidates = listOf("Buch"),
            targetWord = card,
            expectCzech = true,
            language = LearningLanguage.GERMAN
        )
        assertNotNull("German native speaking 'Buch' for Portuguese card must match", match1)

        val match2 = WordMatcher.matches(
            spokenCandidates = listOf("das Buch"),
            targetWord = card,
            expectCzech = true,
            language = LearningLanguage.GERMAN
        )
        assertNotNull("German native speaking 'das Buch' must match", match2)

        val matchWrong = WordMatcher.matches(
            spokenCandidates = listOf("der Tisch"),
            targetWord = card,
            expectCzech = true,
            language = LearningLanguage.GERMAN
        )
        assertNull(matchWrong)
    }

    @Test
    fun reversedCardsPortugueseNativeLearningItalian() {
        // Portuguese native learning Italian:
        // Foreign target is "il gatto" (cat). Native prompt is "o gato / gato".
        val card = vocab("it_cat", "o gato / gato", "il gatto")

        val match1 = WordMatcher.matches(
            spokenCandidates = listOf("gato"),
            targetWord = card,
            expectCzech = true,
            language = LearningLanguage.PORTUGUESE
        )
        assertNotNull("Portuguese native speaking 'gato' for Italian card must match", match1)

        val match2 = WordMatcher.matches(
            spokenCandidates = listOf("o gato"),
            targetWord = card,
            expectCzech = true,
            language = LearningLanguage.PORTUGUESE
        )
        assertNotNull(match2)

        val matchWrong = WordMatcher.matches(
            spokenCandidates = listOf("o cachorro"),
            targetWord = card,
            expectCzech = true,
            language = LearningLanguage.PORTUGUESE
        )
        assertNull(matchWrong)
    }

    @Test
    fun reversedCardsFrenchNativeLearningSpanish() {
        // French native learning Spanish:
        // Foreign target is "la casa" (house). Native prompt is "la maison / maison".
        val card = vocab("es_house", "la maison / maison", "la casa")

        val match1 = WordMatcher.matches(
            spokenCandidates = listOf("maison"),
            targetWord = card,
            expectCzech = true,
            language = LearningLanguage.FRENCH
        )
        assertNotNull("French native speaking 'maison' for Spanish card must match", match1)

        val match2 = WordMatcher.matches(
            spokenCandidates = listOf("la maison"),
            targetWord = card,
            expectCzech = true,
            language = LearningLanguage.FRENCH
        )
        assertNotNull(match2)

        val matchWrong = WordMatcher.matches(
            spokenCandidates = listOf("la voiture"),
            targetWord = card,
            expectCzech = true,
            language = LearningLanguage.FRENCH
        )
        assertNull(matchWrong)
    }

    @Test
    fun reversedCardsCzechNativeLearningEnglish() {
        // Czech native learning English:
        // Foreign target is "cat". Native prompt is "Kočka / kočka".
        val card = vocab("en_cat", "Kočka / kočka", "cat")

        val match1 = WordMatcher.matches(
            spokenCandidates = listOf("kočka"),
            targetWord = card,
            expectCzech = true,
            language = LearningLanguage.CZECH
        )
        assertNotNull("Czech native speaking 'kočka' for English card must match", match1)

        val matchDeclined = WordMatcher.matches(
            spokenCandidates = listOf("kočku"),
            targetWord = card,
            expectCzech = true,
            language = LearningLanguage.CZECH
        )
        assertNotNull("Czech declension 'kočku' must match", matchDeclined)

        val matchWrong = WordMatcher.matches(
            spokenCandidates = listOf("strom"),
            targetWord = card,
            expectCzech = true,
            language = LearningLanguage.CZECH
        )
        assertNull(matchWrong)
    }

    @Test
    fun isPrefixOfTargetWorksAcrossAllLanguages() {
        val deCard = vocab("de_p", "kytka", "die Blume")
        assertTrue(WordMatcher.isPrefixOfTarget(listOf("die Blu"), deCard, language = LearningLanguage.GERMAN))
        assertTrue(WordMatcher.isPrefixOfTarget(listOf("Blu"), deCard, language = LearningLanguage.GERMAN))

        val esCard = vocab("es_p", "knihovna", "biblioteca")
        assertTrue(WordMatcher.isPrefixOfTarget(listOf("biblio"), esCard, language = LearningLanguage.SPANISH))

        val ptCard = vocab("pt_p", "počítač", "computador")
        assertTrue(WordMatcher.isPrefixOfTarget(listOf("compu"), ptCard, language = LearningLanguage.PORTUGUESE))
    }
}
