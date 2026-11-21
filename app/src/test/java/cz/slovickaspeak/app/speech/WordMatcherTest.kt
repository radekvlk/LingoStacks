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

    @Test
    fun shortWordsAndLetterHomophones() {
        // "tea" matched by "t", "T", "tee", "tea", or within phrases like "a cup of t"
        val tea = word("Čaj", "tea")
        assertNotNull(WordMatcher.matches(listOf("t"), tea))
        assertNotNull(WordMatcher.matches(listOf("T"), tea))
        assertNotNull(WordMatcher.matches(listOf("tee"), tea))
        assertNotNull(WordMatcher.matches(listOf("tea"), tea))
        assertNotNull(WordMatcher.matches(listOf("a cup of t"), tea))
        assertNull(WordMatcher.matches(listOf("coffee"), tea))

        // Single letter homophones: you -> u, are -> r, be -> b, why -> y, see -> c
        val you = word("Ty", "you")
        assertNotNull(WordMatcher.matches(listOf("u"), you))
        assertNotNull(WordMatcher.matches(listOf("U"), you))

        val are = word("Být (jsou)", "are")
        assertNotNull(WordMatcher.matches(listOf("r"), are))
        assertNotNull(WordMatcher.matches(listOf("R"), are))

        val be = word("Být", "be")
        assertNotNull(WordMatcher.matches(listOf("b"), be))
        assertNotNull(WordMatcher.matches(listOf("B"), be))

        val why = word("Proč", "why")
        assertNotNull(WordMatcher.matches(listOf("y"), why))
        assertNotNull(WordMatcher.matches(listOf("Y"), why))

        val see = word("Vidět", "see")
        assertNotNull(WordMatcher.matches(listOf("c"), see))
        assertNotNull(WordMatcher.matches(listOf("C"), see))

        // Short word typical vowel & devoicing STT substitutions
        val cup = word("Šálek", "cup")
        assertNotNull(WordMatcher.matches(listOf("cap"), cup))
        assertNotNull(WordMatcher.matches(listOf("kap"), cup))

        val run = word("Běhat", "run")
        assertNotNull(WordMatcher.matches(listOf("ran"), run))

        val dog = word("Pes", "dog")
        assertNotNull(WordMatcher.matches(listOf("dock"), dog))
        assertNotNull(WordMatcher.matches(listOf("dok"), dog))

        val bed = word("Postel", "bed")
        assertNotNull(WordMatcher.matches(listOf("bet"), bed))

        // Czech devoicing and diacritic variants
        val teaCz = word("Čaj", "tea")
        assertNotNull(WordMatcher.matches(listOf("caj"), teaCz, expectCzech = true))

        val dubCz = word("Dub", "oak")
        assertNotNull(WordMatcher.matches(listOf("dup"), dubCz, expectCzech = true))

        // German, French, Spanish short word / letter homophones
        val teaDe = word("Čaj", "Tee")
        assertNotNull(WordMatcher.matches(listOf("t"), teaDe, language = LearningLanguage.GERMAN))
        assertNotNull(WordMatcher.matches(listOf("T"), teaDe, language = LearningLanguage.GERMAN))

        val teaFr = word("Čaj", "thé")
        assertNotNull(WordMatcher.matches(listOf("t"), teaFr, language = LearningLanguage.FRENCH))

        val teaEs = word("Čaj", "té")
        assertNotNull(WordMatcher.matches(listOf("t"), teaEs, language = LearningLanguage.SPANISH))
    }

    @Test
    fun contextualFoodPhrasesAndParenthesesStripping() {
        // eph47 with context in parentheses and broad accepted variants
        val delicious = word(
            czech = "To je výborné! (o jídle)",
            english = "It is delicious!",
            variants = listOf(
                "its delicious", "this is delicious", "it tastes great",
                "it tastes delicious", "it is great", "it's great",
                "that is great", "that's great", "this is great"
            )
        )

        // All natural translations should match
        assertNotNull(WordMatcher.matches(listOf("it is delicious"), delicious))
        assertNotNull(WordMatcher.matches(listOf("it's delicious"), delicious))
        assertNotNull(WordMatcher.matches(listOf("it is great"), delicious))
        assertNotNull(WordMatcher.matches(listOf("it's great"), delicious))
        assertNotNull(WordMatcher.matches(listOf("that's great"), delicious))
        assertNotNull(WordMatcher.matches(listOf("this is delicious"), delicious))
        assertNotNull(WordMatcher.matches(listOf("it tastes great"), delicious))

        // In reverse mode, speech in Czech shouldn't require saying "(o jídle)"
        assertNotNull(WordMatcher.matches(listOf("to je výborné"), delicious, expectCzech = true))
        assertNotNull(WordMatcher.matches(listOf("je to výborné"), delicious, expectCzech = true))
    }

    @Test
    fun irregularVerbsA2TripletMatching() {
        val putVerb = word("Položit / Dát (3 tvary)", "put – put – put")
        assertNotNull(WordMatcher.matches(listOf("put put put"), putVerb))

        val writeVerb = word("Psát (3 tvary)", "write – wrote – written", listOf("right wrote written"))
        assertNotNull(WordMatcher.matches(listOf("write wrote written"), writeVerb))
        assertNotNull(WordMatcher.matches(listOf("right wrote written"), writeVerb))

        val sellVerb = word("Prodat (3 tvary)", "sell – sold – sold")
        assertNotNull(WordMatcher.matches(listOf("sell sold sold"), sellVerb))
    }
}
