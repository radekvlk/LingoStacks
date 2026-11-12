package cz.slovickaspeak.app.speech

import cz.slovickaspeak.app.model.LearningLanguage
import cz.slovickaspeak.app.model.VocabWord
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class WordMatcherTest {

    private fun word(
        czech: String,
        english: String,
        variants: List<String> = emptyList()
    ) = VocabWord(
        id = "test_${english.lowercase()}",
        czech = czech,
        english = english,
        emoji = "🧪",
        exampleSentence = "",
        phonetic = "",
        acceptedVariants = variants
    )

    @Test
    fun englishExactArticlesAndHomophones() {
        val dog = word("Pes", "dog")
        assertNotNull(WordMatcher.matches(listOf("dog"), dog))
        assertNotNull(WordMatcher.matches(listOf("the dog"), dog))
        assertNull(WordMatcher.matches(listOf("cat"), dog))

        val meet = word("Potkat", "meet")
        assertNotNull(WordMatcher.matches(listOf("meat"), meet))

        val sun = word("Slunce", "sun")
        assertNotNull(WordMatcher.matches(listOf("son"), sun))
    }

    @Test
    fun ukUsSpellingAndCompoundsAndOrdinals() {
        val color = word("Barva", "colour")
        assertNotNull(WordMatcher.matches(listOf("color"), color))

        val iceCream = word("Zmrzlina", "ice cream")
        assertNotNull(WordMatcher.matches(listOf("icecream"), iceCream))
        assertTrue(WordMatcher.isPrefixOfTarget(listOf("ice"), iceCream))

        val first = word("První", "first")
        assertNotNull(WordMatcher.matches(listOf("1st"), first))
    }

    @Test
    fun irregularVerbsPreserveLeadingBeAndTolerateMultiWordAccents() {
        val beVerb = word("Být (3 tvary)", "be – was – been")
        assertNotNull(WordMatcher.matches(listOf("be was been"), beVerb))
        assertEquals(listOf("be – was – been", "be was been"), WordMatcher.extractBiasingStrings(beVerb, expectCzech = false))

        val haveVerb = word("Mít (3 tvary)", "have – had – had")
        assertNotNull(WordMatcher.matches(listOf("have head head"), haveVerb, isFinal = true))

        val goVerb = word("Jít (3 tvary)", "go – went – gone")
        assertNotNull(WordMatcher.matches(listOf("go when gun"), goVerb, isFinal = true))
    }

    @Test
    fun spanishPhoneticTolerance() {
        val people = word("Lidé", "gente")
        assertNotNull(WordMatcher.matches(listOf("jente"), people, isFinal = true, language = LearningLanguage.SPANISH))

        val child = word("Dítě", "niño")
        assertNotNull(WordMatcher.matches(listOf("ninio"), child, isFinal = true, language = LearningLanguage.SPANISH))

        val hello = word("Ahoj", "hola")
        assertNotNull(WordMatcher.matches(listOf("ola"), hello, isFinal = true, language = LearningLanguage.SPANISH))

        val cheese = word("Sýr", "queso")
        assertNotNull(WordMatcher.matches(listOf("keso"), cheese, isFinal = true, language = LearningLanguage.SPANISH))
    }

    @Test
    fun czechBidirectionalDevoicingAndVowels() {
        val cheese = word("Sýr", "cheese")
        assertNotNull(WordMatcher.matches(listOf("sír"), cheese, isFinal = true, expectCzech = true))

        val ice = word("Led", "ice")
        assertNotNull(WordMatcher.matches(listOf("let"), ice, isFinal = true, expectCzech = true))

        val forest = word("Les", "forest")
        assertNull(WordMatcher.matches(listOf("strom"), forest, isFinal = true, expectCzech = true))
    }

    @Test
    fun germanPhoneticsUmlautsAndEszett() {
        val street = word("Ulice", "Straße")
        assertNotNull(WordMatcher.matches(listOf("Strasse"), street, isFinal = true, language = LearningLanguage.GERMAN))

        val dog = word("Pes", "Hund")
        assertNotNull(WordMatcher.matches(listOf("der Hund"), dog, isFinal = true, language = LearningLanguage.GERMAN))

        val houses = word("Domy", "Häuser")
        assertNotNull(WordMatcher.matches(listOf("Heuser"), houses, isFinal = true, language = LearningLanguage.GERMAN))

        val important = word("Důležitý", "wichtig")
        assertNotNull(WordMatcher.matches(listOf("wichtik"), important, isFinal = true, language = LearningLanguage.GERMAN))

        val irregular = word("Jít (3 tvary)", "gehen – ging – gegangen")
        assertNotNull(WordMatcher.matches(listOf("gehen ging gegangen"), irregular, isFinal = true, language = LearningLanguage.GERMAN))
    }

    @Test
    fun frenchAndItalianPhoneticsAndElisions() {
        val waterFr = word("Voda", "eau")
        assertNotNull(WordMatcher.matches(listOf("l'eau"), waterFr, isFinal = true, language = LearningLanguage.FRENCH))

        val heartFr = word("Srdce", "cœur")
        assertNotNull(WordMatcher.matches(listOf("coeur"), heartFr, isFinal = true, language = LearningLanguage.FRENCH))

        val helloFr = word("Dobrý den", "bonjour")
        assertNotNull(WordMatcher.matches(listOf("bonjur"), helloFr, isFinal = true, language = LearningLanguage.FRENCH))

        val waterIt = word("Voda", "acqua")
        assertNotNull(WordMatcher.matches(listOf("l'acqua"), waterIt, isFinal = true, language = LearningLanguage.ITALIAN))

        val familyIt = word("Rodina", "famiglia")
        assertNotNull(WordMatcher.matches(listOf("familja"), familyIt, isFinal = true, language = LearningLanguage.ITALIAN))

        val churchIt = word("Kostel", "chiesa")
        assertNotNull(WordMatcher.matches(listOf("kiesa"), churchIt, isFinal = true, language = LearningLanguage.ITALIAN))
    }

    @Test
    fun preventsPrematureShortPartialCollisions() {
        val cookie = word("Sušenka", "cookie")
        assertNull(WordMatcher.matches(listOf("cook"), cookie, isFinal = false))
        assertTrue(WordMatcher.isPrefixOfTarget(listOf("cook"), cookie))
        assertFalse(WordMatcher.isPrefixOfTarget(listOf("apple"), cookie))
    }
}
