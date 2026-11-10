package cz.slovickaspeak.app.speech

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.media.AudioManager
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.speech.RecognitionListener
import android.speech.RecognitionService
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import cz.slovickaspeak.app.model.LearningLanguage
import cz.slovickaspeak.app.model.VocabWord
import java.text.Normalizer
import java.util.Locale
import kotlin.math.abs
import kotlin.math.min

object WordMatcher {

    private val leadingArticles = Regex("^(a|an|the|to|be|is|are|i|you|we|they|he|she|it|el|la|los|las|un|una|unos|unas|yo|tu|ella|nosotros|ellos)\\s+")
    private val combiningMarksRegex = Regex("\\p{InCombiningDiacriticalMarks}+")
    private val commonContractions = mapOf(
        "i'm" to "i am",
        "im" to "i am",
        "you're" to "you are",
        "youre" to "you are",
        "he's" to "he is",
        "she's" to "she is",
        "it's" to "it is",
        "we're" to "we are",
        "they're" to "they are",
        "theyre" to "they are",
        "i've" to "i have",
        "ive" to "i have",
        "you've" to "you have",
        "youve" to "you have",
        "we've" to "we have",
        "weve" to "we have",
        "they've" to "they have",
        "theyve" to "they have",
        "don't" to "do not",
        "dont" to "do not",
        "doesn't" to "does not",
        "doesnt" to "does not",
        "didn't" to "did not",
        "didnt" to "did not",
        "can't" to "cannot",
        "cant" to "cannot",
        "won't" to "will not",
        "wont" to "will not",
        "isn't" to "is not",
        "isnt" to "is not",
        "aren't" to "are not",
        "arent" to "are not",
        "wasn't" to "was not",
        "wasnt" to "was not",
        "weren't" to "were not",
        "werent" to "were not",
        "haven't" to "have not",
        "havent" to "have not",
        "hasn't" to "has not",
        "hasnt" to "has not",
        "let's" to "let us"
    )

    private val digitToWord = mapOf(
        "0" to "zero",
        "1" to "one",
        "2" to "two",
        "3" to "three",
        "4" to "four",
        "5" to "five",
        "6" to "six",
        "7" to "seven",
        "8" to "eight",
        "9" to "nine",
        "10" to "ten",
        "11" to "eleven",
        "12" to "twelve",
        "13" to "thirteen",
        "14" to "fourteen",
        "15" to "fifteen",
        "16" to "sixteen",
        "17" to "seventeen",
        "18" to "eighteen",
        "19" to "nineteen",
        "20" to "twenty",
        "30" to "thirty",
        "40" to "forty",
        "50" to "fifty",
        "60" to "sixty",
        "70" to "seventy",
        "80" to "eighty",
        "90" to "ninety",
        "100" to "hundred",
        "1000" to "thousand"
    )

    // Common single-word homophones and STT substitutions when spoken in isolation
    private val homophoneGroups = listOf(
        setOf("one", "won", "1"),
        setOf("two", "to", "too", "2"),
        setOf("three", "tree", "free", "3"),
        setOf("four", "for", "fore", "4"),
        setOf("eight", "ate", "8"),
        setOf("sun", "son"),
        setOf("sea", "see", "c"),
        setOf("eye", "i", "aye"),
        setOf("hour", "our"),
        setOf("meat", "meet"),
        setOf("weak", "week"),
        setOf("peace", "piece"),
        setOf("right", "write", "rite"),
        setOf("wear", "where", "ware"),
        setOf("weather", "whether"),
        setOf("wait", "weight"),
        setOf("whole", "hole"),
        setOf("wood", "would"),
        setOf("hear", "here"),
        setOf("break", "brake"),
        setOf("steak", "stake"),
        setOf("flour", "flower"),
        setOf("night", "knight"),
        setOf("pair", "pear"),
        setOf("hair", "hare"),
        setOf("bear", "bare"),
        setOf("dear", "deer"),
        setOf("fair", "fare"),
        setOf("heal", "heel"),
        setOf("steal", "steel"),
        setOf("tail", "tale"),
        setOf("way", "weigh"),
        setOf("which", "witch"),
        setOf("root", "route"),
        setOf("board", "bored"),
        setOf("aloud", "allowed"),
        setOf("buy", "by", "bye"),
        setOf("cell", "sell"),
        setOf("cent", "sent", "scent"),
        setOf("die", "dye"),
        setOf("knew", "new"),
        setOf("know", "no"),
        setOf("mail", "male"),
        setOf("main", "mane"),
        setOf("none", "nun"),
        setOf("pain", "pane"),
        setOf("plain", "plane"),
        setOf("rain", "reign", "rein"),
        setOf("read", "red"),
        setOf("role", "roll"),
        setOf("sail", "sale"),
        setOf("scene", "seen"),
        setOf("sole", "soul"),
        setOf("some", "sum"),
        setOf("stair", "stare"),
        setOf("sweet", "suite"),
        setOf("their", "there", "theyre"),
        setOf("threw", "through"),
        setOf("waist", "waste"),
        setOf("war", "worn", "wore"),
        setOf("warn", "worn"),
        setOf("work", "walk", "wok"),
        setOf("bird", "beard", "bert"),
        setOf("bed", "bad", "bat"),
        setOf("cat", "cut", "caught", "kat"),
        setOf("dog", "dock", "dug"),
        setOf("pig", "pick", "peak"),
        setOf("sheep", "ship", "cheap"),
        setOf("full", "fool"),
        setOf("pull", "pool"),
        setOf("hat", "hut", "hot"),
        setOf("heart", "hard", "hurt"),
        setOf("shirt", "short"),
        setOf("skirt", "skate"),
        setOf("think", "sink", "fink", "thing"),
        setOf("thank", "tank", "sank"),
        setOf("thin", "tin", "fin"),
        setOf("thick", "tick", "sick"),
        setOf("mouth", "mouse"),
        setOf("south", "sauce"),
        setOf("north", "norse"),
        setOf("bath", "bus", "bass"),
        // Irregular verb homophones & frequent STT substitutions
        setOf("saw", "so", "sore"),
        setOf("blew", "blue"),
        setOf("flew", "flu"),
        setOf("rode", "road"),
        setOf("wrote", "rote"),
        setOf("been", "bean", "bin"),
        setOf("made", "maid"),
        setOf("paid", "payed"),
        setOf("find", "fined"),
        setOf("caught", "court"),
        setOf("grown", "groan"),
        setOf("thrown", "throne"),
        setOf("led", "lead"),
        setOf("rung", "wrung"),
        // Spanish number words & common homophones (b/v, h silent, ll/y, c/s/z)
        setOf("zero", "cero", "0"),
        setOf("one", "won", "uno", "una", "1"),
        setOf("two", "to", "too", "dos", "2"),
        setOf("three", "tree", "free", "tres", "3"),
        setOf("four", "for", "fore", "cuatro", "4"),
        setOf("five", "cinco", "5"),
        setOf("six", "seis", "6"),
        setOf("seven", "siete", "7"),
        setOf("eight", "ate", "ocho", "8"),
        setOf("nine", "nueve", "9"),
        setOf("ten", "diez", "10"),
        setOf("eleven", "once", "11"),
        setOf("twelve", "doce", "12"),
        setOf("thirteen", "trece", "13"),
        setOf("fourteen", "catorce", "14"),
        setOf("fifteen", "quince", "15"),
        setOf("sixteen", "dieciseis", "16"),
        setOf("seventeen", "diecisiete", "17"),
        setOf("eighteen", "dieciocho", "18"),
        setOf("nineteen", "diecinueve", "19"),
        setOf("twenty", "veinte", "20"),
        setOf("thirty", "treinta", "30"),
        setOf("forty", "cuarenta", "40"),
        setOf("fifty", "cincuenta", "50"),
        setOf("sixty", "sesenta", "60"),
        setOf("seventy", "setenta", "70"),
        setOf("eighty", "ochenta", "80"),
        setOf("ninety", "noventa", "90"),
        setOf("hundred", "cien", "ciento", "100"),
        setOf("thousand", "mil", "1000"),
        setOf("hola", "ola"),
        setOf("haber", "a ver"),
        setOf("hecho", "echo"),
        setOf("hasta", "asta"),
        setOf("hora", "ora"),
        setOf("hoy", "oy"),
        setOf("hay", "ay", "ahi"),
        setOf("vaca", "baca"),
        setOf("vaso", "baso"),
        setOf("vino", "bino"),
        setOf("bello", "vello"),
        setOf("votar", "botar"),
        setOf("casa", "caza"),
        setOf("cocer", "coser"),
        setOf("cien", "sien"),
        setOf("cerrar", "serrar"),
        setOf("callo", "cayo")
    )

    fun stripAccents(input: String): String {
        val decomposed = Normalizer.normalize(input, Normalizer.Form.NFD)
        return combiningMarksRegex.replace(decomposed, "")
    }

    fun normalize(input: String, stripLeadingForeignArticles: Boolean = true): String {
        val rawWords = stripAccents(input)
            .lowercase(Locale.US)
            .replace("’", "'")
            .replace(Regex("[^a-z0-9'\\s]"), " ")
            .replace(Regex("\\s+"), " ")
            .trim()
            .split(" ")
            .filter { it.isNotEmpty() }

        val expandedWords = if (stripLeadingForeignArticles) {
            rawWords.flatMap { token ->
                val expanded = commonContractions[token]
                (expanded?.split(" ") ?: listOf(token.replace("'", "")))
                    .filter { it.isNotEmpty() }
            }
        } else {
            rawWords.map { it.replace("'", "") }.filter { it.isNotEmpty() }
        }

        val joined = expandedWords.joinToString(" ")
        return if (stripLeadingForeignArticles) {
            joined.replace(leadingArticles, "").trim()
        } else {
            joined
        }
    }

    /**
     * Produces a pronunciation skeleton for English and Spanish words with Czech-speaker accent tolerance.
     */
    fun phoneticCode(word: String): String {
        if (word.isEmpty()) return ""
        var s = stripAccents(word).lowercase(Locale.US).trim()
        s = digitToWord[s] ?: s

        if (s.length > 2 && s.startsWith("h")) {
            s = s.drop(1)
        }

        s = s.replace("ough", "o")
            .replace("augh", "af")
            .replace("ight", "it")
            .replace("tion", "sn")
            .replace("sion", "sn")
            .replace("ture", "cr")
            .replace("sure", "sr")
            .replace("kn", "n")
            .replace("wr", "r")
            .replace("wh", "v")
            .replace("gn", "n")
            .replace("ps", "s")
            .replace("ph", "f")
            .replace("gh", "")
            .replace("sh", "s")
            .replace("tch", "c")
            .replace("ch", "c")
            .replace("th", "t")
            .replace("qu", "k")
            .replace("gue", "ge")
            .replace("gui", "gi")
            .replace("ll", "i")
            .replace("rr", "r")
            .replace("ck", "k")
            .replace("dg", "j")
            .replace("ce", "se")
            .replace("ci", "si")
            .replace("cy", "sy")
            .replace("ee", "i")
            .replace("ea", "i")
            .replace("oo", "u")
            .replace("ou", "u")
            .replace("ow", "o")
            .replace("ai", "e")
            .replace("ay", "e")
            .replace("oa", "o")

        if (s.length > 3 && s.endsWith("e")) {
            s = s.dropLast(1)
        }
        if (s.length > 3 && s.endsWith("mb")) {
            s = s.dropLast(1)
        }

        val sb = StringBuilder()
        var prevChar = ' '
        for (ch in s) {
            val mapped = when (ch) {
                'w', 'v' -> 'v'
                'z' -> 's'
                'c', 'q' -> 'k'
                'y' -> 'i'
                'a', 'e' -> 'a'
                'd' -> 't'
                'b' -> 'p'
                'g', 'j' -> 'k'
                else -> ch
            }
            if (mapped != prevChar) {
                sb.append(mapped)
                prevChar = mapped
            }
        }
        return sb.toString()
    }

    /**
     * Consonant skeleton ignoring vowels, for short Czech-accented English words on final results
     * (e.g. "cup" -> "kap"/"cop", "run" -> "ran", "ask" -> "esk"/"usk").
     */
    private fun consonantSkeleton(word: String): String {
        val norm = digitToWord[word.lowercase(Locale.US)] ?: word.lowercase(Locale.US)
        val base = norm
            .replace("ph", "f")
            .replace("sh", "s")
            .replace("ch", "c")
            .replace("th", "t")
            .replace("qu", "k")
            .replace("ck", "k")
            .replace("gh", "")
        val sb = StringBuilder()
        var prev = ' '
        for (ch in base) {
            if (ch !in 'a'..'z' || ch in "aeiouyh") continue
            val mapped = when (ch) {
                'w', 'v' -> 'v'
                'z' -> 's'
                'c', 'q' -> 'k'
                'd' -> 't'
                'b' -> 'p'
                'g', 'j' -> 'k'
                else -> ch
            }
            if (mapped != prev) {
                sb.append(mapped)
                prev = mapped
            }
        }
        return sb.toString()
    }

    private fun areHomophones(a: String, b: String): Boolean {
        if (a == b) return true
        return homophoneGroups.any { group -> a in group && b in group }
    }

    private fun primaryCzechDisplay(targetWord: VocabWord): String {
        return targetWord.czech
            .replace(Regex("\\s*\\(.*?\\)"), "")
            .split("/", ";", ",")
            .firstOrNull { it.isNotBlank() }
            ?.trim()
            ?.ifEmpty { targetWord.czech }
            ?: targetWord.czech
    }

    /**
     * Builds all normalized acceptable target phrases (for either foreign or Czech expected answers).
     */
    private fun extractNormalizedTargets(targetWord: VocabWord, expectCzech: Boolean = false): List<String> {
        return if (expectCzech) {
            val withoutParens = targetWord.czech.replace(Regex("\\s*\\(.*?\\)"), " ").trim()
            val parts = withoutParens
                .split("/", ";", ",", "–", " - ")
                .map { it.trim() }
                .filter { it.isNotEmpty() }
            (listOf(withoutParens) + parts)
                .map { normalize(it, stripLeadingForeignArticles = false) }
                .filter { it.isNotEmpty() }
                .distinct()
        } else {
            val cleanPrimary = targetWord.english.replace(Regex("\\s*\\(.*?\\)"), " ").trim()
            val primaryParts = cleanPrimary.split("/", ";", ",").map { it.trim() }
            val variantParts = targetWord.acceptedVariants.flatMap {
                it.replace(Regex("\\s*\\(.*?\\)"), " ").split("/", ";", ",").map { p -> p.trim() }
            }
            (listOf(cleanPrimary) + primaryParts + variantParts)
                .map { normalize(it, stripLeadingForeignArticles = true) }
                .filter { it.isNotEmpty() }
                .distinct()
        }
    }

    /**
     * Builds unstripped surface biasing phrases for Android 13+ EXTRA_BIASING_STRINGS
     * so on-device/cloud ASR prioritizes the active vocabulary card over random homophones.
     */
    fun extractBiasingStrings(targetWord: VocabWord, expectCzech: Boolean): List<String> {
        val raw = if (expectCzech) {
            val cleaned = targetWord.czech.replace(Regex("\\s*\\(.*?\\)"), " ").trim()
            cleaned.split("/", ";", ",", "–", " - ")
        } else {
            val primary = targetWord.english.replace(Regex("\\s*\\(.*?\\)"), " ")
                .split("/", ";", ",")
            val variants = targetWord.acceptedVariants.flatMap {
                it.replace(Regex("\\s*\\(.*?\\)"), " ").split("/", ";", ",")
            }
            primary + variants
        }
        return raw.map { it.trim() }
            .filter { it.length >= 2 }
            .distinct()
            .take(10)
    }

    /**
     * Returns true if any candidate is currently a valid prefix or first word of the target word,
     * meaning the user is still mid-pronunciation (e.g. "straw" for "strawberry" or "washing" for "washing machine").
     */
    fun isPrefixOfTarget(spokenCandidates: List<String>, targetWord: VocabWord, expectCzech: Boolean = false): Boolean {
        val rawTargets = extractNormalizedTargets(targetWord, expectCzech)

        for (candidate in spokenCandidates) {
            val norm = normalize(candidate, stripLeadingForeignArticles = !expectCzech)
            if (norm.isEmpty()) continue
            val lastToken = norm.split(" ").lastOrNull() ?: norm
            for (target in rawTargets) {
                if (target.startsWith(norm) || (lastToken.length >= 2 && target.startsWith(lastToken))) {
                    return true
                }
            }
        }
        return false
    }

    /**
     * Extracts the latest spoken word/phrase from candidates so UI feedback shows the fresh attempt,
     * not stale earlier words.
     */
    fun extractLatestSpokenSnippet(spokenCandidates: List<String>): String? {
        val first = spokenCandidates.firstOrNull { it.isNotBlank() }?.trim() ?: return null
        val tokens = first.split(Regex("\\s+")).filter { it.isNotBlank() }
        return if (tokens.size <= 2) {
            first
        } else {
            tokens.takeLast(2).joinToString(" ")
        }
    }

    private data class CandidateInfo(
        val original: String,
        val norm: String,
        val words: List<String>,
        val phrases: List<String>
    )

    /**
     * Checks if any of the recognized speech candidates match the target English word
     * or any of its accepted variants.
     * Evaluates in strict confidence tiers across ALL candidates:
     * - Tier 1: Exact target matches (or target word in sentence)
     * - Tier 2: Homophones & number words (e.g. meat/meet, sun/son)
     * - Tier 3: Grammatical inflections (e.g. cookies/cookie, walked/walk)
     * - Tier 4: Exact phonetic skeleton match (Czech accent tolerance, e.g. w/v, c/k)
     * - Tier 5: Levenshtein & Near-Phonetic (STRICT: only on final results or non-prefixes,
     *           and requires phonetic length >= 5 so short words like cook/cookie never collide!)
     *
     * Returns the canonical target word / variant that was matched (e.g. "cookie"),
     * so UI feedback always correctly displays the intended word!
     */
    fun matches(
        spokenCandidates: List<String>,
        targetWord: VocabWord,
        isFinal: Boolean = true,
        expectCzech: Boolean = false
    ): String? {
        val rawTargets = extractNormalizedTargets(targetWord, expectCzech)
        val matchedDisplay = if (expectCzech) primaryCzechDisplay(targetWord) else targetWord.english

        val parsedCandidates = spokenCandidates.mapNotNull { cand ->
            val norm = normalize(cand, stripLeadingForeignArticles = !expectCzech)
            if (norm.isEmpty()) return@mapNotNull null
            val words = norm.split(" ").filter { it.isNotEmpty() }
            val phrases = mutableListOf(norm)
            if (words.size >= 2) {
                phrases.add(words.last())
                phrases.add(words.takeLast(2).joinToString(" "))
            }
            if (words.size >= 3) {
                phrases.add(words.takeLast(3).joinToString(" "))
            }
            CandidateInfo(original = cand, norm = norm, words = words, phrases = phrases.distinct())
        }

        if (parsedCandidates.isEmpty() || rawTargets.isEmpty()) return null

        // TIER 1: Exact matches across ALL candidates
        for (target in rawTargets) {
            val targetWords = target.split(" ").filter { it.isNotEmpty() }
            for (cand in parsedCandidates) {
                for (phrase in cand.phrases) {
                    if (phrase == target || cand.words.contains(target)) {
                        return matchedDisplay
                    }
                    if (target.contains(" ") && phrase.contains(target)) {
                        return matchedDisplay
                    }
                }
                if (targetWords.size >= 2 && cand.words.size >= targetWords.size) {
                    if (targetWords.all { cand.words.contains(it) }) {
                        return matchedDisplay
                    }
                }
            }
        }

        // TIER 2: Homophones & number words across ALL candidates
        for (target in rawTargets) {
            for (cand in parsedCandidates) {
                for (phrase in cand.phrases) {
                    if (areHomophones(phrase, target) || cand.words.any { areHomophones(it, target) }) {
                        return matchedDisplay
                    }
                }
            }
        }

        // TIER 3: Grammatical inflections (EN/ES suffixes OR Czech stem declensions)
        for (target in rawTargets) {
            if (expectCzech) {
                val targetWords = target.split(" ").filter { it.isNotEmpty() }
                if (targetWords.size == 1 && target.length >= 4) {
                    val stemLen = (target.length - 2).coerceAtLeast(3)
                    val targetStem = target.take(stemLen)
                    for (cand in parsedCandidates) {
                        if (cand.words.any { it.length >= stemLen && it.startsWith(targetStem) }) {
                            return matchedDisplay
                        }
                    }
                } else if (targetWords.size >= 2) {
                    val meaningfulTokens = targetWords.filter { it.length >= 2 }
                    if (meaningfulTokens.isNotEmpty()) {
                        for (cand in parsedCandidates) {
                            val allTokensMatched = meaningfulTokens.all { tw ->
                                val stemLen = if (tw.length >= 4) (tw.length - 2).coerceAtLeast(3) else tw.length
                                val stem = tw.take(stemLen)
                                cand.words.any { cw ->
                                    cw == tw || (cw.length >= stemLen && cw.startsWith(stem))
                                }
                            }
                            if (allTokensMatched) return matchedDisplay
                        }
                    }
                }
            } else {
                val inflections = listOf(
                    "${target}s",
                    "${target}es",
                    "${target}d",
                    "${target}ed",
                    "${target}ing",
                    target.removeSuffix("s"),
                    target.removeSuffix("es"),
                    target.removeSuffix("ed"),
                    target.removeSuffix("ing")
                ).filter { it.length >= 2 && it != target }

                for (cand in parsedCandidates) {
                    for (phrase in cand.phrases) {
                        if (phrase in inflections || cand.words.any { it in inflections }) {
                            return matchedDisplay
                        }
                    }
                }
            }
        }

        // TIER 4: Exact Phonetic Skeleton Match (foreign pronunciation tolerance)
        if (!expectCzech) {
            for (target in rawTargets) {
                val targetPhonetic = phoneticCode(target)
                if (targetPhonetic.length >= 2) {
                    for (cand in parsedCandidates) {
                        for (phrase in cand.phrases) {
                            val candPhonetic = phoneticCode(phrase)
                            if (candPhonetic == targetPhonetic || cand.words.any { phoneticCode(it) == targetPhonetic }) {
                                return matchedDisplay
                            }
                        }
                    }
                }
            }
        }

        // TIER 5: Loose Fuzzy / Near-Phonetic Tolerance
        val anyIsPrefix = parsedCandidates.any { cand ->
            rawTargets.any { target -> target.startsWith(cand.norm) || target.startsWith(cand.words.last()) }
        }

        if (isFinal || !anyIsPrefix) {
            for (target in rawTargets) {
                val targetPhonetic = phoneticCode(target)
                val targetWords = target.split(" ").filter { it.isNotEmpty() }

                for (cand in parsedCandidates) {
                    for (phrase in cand.phrases) {
                        if (!expectCzech && targetPhonetic.length >= 5) {
                            val candPhonetic = phoneticCode(phrase)
                            if (candPhonetic.length >= 4 &&
                                abs(candPhonetic.length - targetPhonetic.length) <= 1 &&
                                levenshtein(candPhonetic, targetPhonetic) <= 1
                            ) {
                                return matchedDisplay
                            }
                        }

                        if (!expectCzech && isFinal && !target.contains(" ")) {
                            val targetCons = consonantSkeleton(target)
                            if (targetCons.length >= 2) {
                                val candCons = consonantSkeleton(phrase)
                                if (candCons == targetCons && abs(phrase.length - target.length) <= 1) {
                                    return matchedDisplay
                                }
                                if (cand.words.any { cw ->
                                        cw.length >= 2 &&
                                            abs(cw.length - target.length) <= 1 &&
                                            consonantSkeleton(cw) == targetCons
                                    }
                                ) {
                                    return matchedDisplay
                                }
                            }
                        }

                        when {
                            target.length in 4..6 -> {
                                if (phrase.length in (target.length - 1)..(target.length + 1) &&
                                    levenshtein(phrase, target) <= 1 &&
                                    !target.startsWith(phrase)
                                ) {
                                    return matchedDisplay
                                }
                            }
                            target.length >= 7 -> {
                                val maxDist = if (target.length >= 9) 3 else 2
                                val stemLen = (target.length - 2).coerceAtLeast(5)
                                val targetStem = target.take(stemLen)
                                if (levenshtein(phrase, target) <= maxDist ||
                                    (phrase.length >= target.length - 1 && phrase.startsWith(targetStem))
                                ) {
                                    return matchedDisplay
                                }
                            }
                        }
                    }

                    if (targetWords.size >= 2 && cand.words.size >= targetWords.size) {
                        val matchedCount = targetWords.count { tw ->
                            cand.words.any { cw ->
                                cw == tw || areHomophones(cw, tw) ||
                                    (!expectCzech && phoneticCode(cw) == phoneticCode(tw)) ||
                                    (tw.length >= 5 && levenshtein(cw, tw) <= 1)
                            }
                        }
                        if (matchedCount >= targetWords.size) {
                            return matchedDisplay
                        }
                    }
                }
            }
        }

        return null
    }

    private fun levenshtein(lhs: CharSequence, rhs: CharSequence): Int {
        val lhsLen = lhs.length
        val rhsLen = rhs.length
        var cost = IntArray(lhsLen + 1) { it }
        var newCost = IntArray(lhsLen + 1)

        for (i in 1..rhsLen) {
            newCost[0] = i
            for (j in 1..lhsLen) {
                val match = if (lhs[j - 1] == rhs[i - 1]) 0 else 1
                val costReplace = cost[j - 1] + match
                val costInsert = cost[j] + 1
                val costDelete = newCost[j - 1] + 1
                newCost[j] = min(min(costInsert, costDelete), costReplace)
            }
            val swap = cost
            cost = newCost
            newCost = swap
        }
        return cost[lhsLen]
    }
}

class SpeechSessionController(
    private val context: Context,
    private val language: LearningLanguage = LearningLanguage.ENGLISH,
    private val listeningCallback: (Boolean) -> Unit,
    private val rmsCallback: (Float) -> Unit,
    private val partialHeardCallback: (List<String>) -> Unit,
    private val finalHeardCallback: (List<String>) -> Unit,
    private val errorCallback: (String?) -> Unit
) {
    private val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager
    private val mutedStreams = mutableSetOf<Int>()
    private val mainHandler = Handler(Looper.getMainLooper())
    private var speechRecognizer: SpeechRecognizer? = null
    private var tts: TextToSpeech? = null
    private var isTtsReady = false

    var shouldStayListening: Boolean = false
        private set

    @Volatile
    var listenInCzech: Boolean = false

    @Volatile
    var currentTargetBiasStrings: List<String> = emptyList()

    private var consecutiveEmptyNoMatch = 0
    private var silentCycleCount = 0
    private var lastReportedRms = -1f

    private var isCurrentlyListening = false
    private var isStartingListen = false
    private var isProcessingSpeech = false
    private var isPausedForTransition = false
    private var isPausedForTts = false
    private var isTtsSpeaking = false

    private var lastStartAttemptUptime = 0L
    private var cycleReadyUptime = 0L
    private var lastEndOfSpeechUptime = 0L
    private var lastCycleEndUptime = 0L
    private var consecutiveErrors = 0
    private val accumulatedPartialsInCycle = LinkedHashSet<String>()

    private val restartListenRunnable = Runnable {
        if (shouldStayListening && !isPausedForTts && !isPausedForTransition && !isTtsSpeaking) {
            if (!isCurrentlyListening && !isStartingListen && !isProcessingSpeech) {
                startSingleListenCycle()
            }
        }
    }

    private val transitionFallbackRunnable = Runnable {
        if (isPausedForTransition) {
            isPausedForTransition = false
            if (shouldStayListening && !isPausedForTts && !isTtsSpeaking) {
                if (!isCurrentlyListening && !isStartingListen && !isProcessingSpeech) {
                    startSingleListenCycle()
                }
            }
        }
    }

    private var pendingTtsOnDoneCallback: (() -> Unit)? = null
    @Volatile
    private var activeUtteranceId: String? = null

    private val ttsFallbackRunnable = Runnable {
        if (isTtsSpeaking) {
            activeUtteranceId = null
            isTtsSpeaking = false
            isPausedForTts = false
            val cb = pendingTtsOnDoneCallback
            pendingTtsOnDoneCallback = null
            cb?.invoke()
            if (shouldStayListening && !isPausedForTransition) {
                if (!isCurrentlyListening && !isStartingListen && !isProcessingSpeech) {
                    startSingleListenCycle()
                }
            }
        }
    }

    /**
     * Non-intrusive watchdog runnable that guarantees the recognizer never stays stuck,
     * while NEVER interrupting active speech decoding (isProcessingSpeech).
     */
    private val watchdogRunnable = object : Runnable {
        override fun run() {
            if (!shouldStayListening) return
            val now = SystemClock.uptimeMillis()

            if (!isPausedForTts && !isPausedForTransition && !isTtsSpeaking) {
                // Case 1: startListening was called > 2500ms ago but onReadyForSpeech never fired
                if (isStartingListen && now - lastStartAttemptUptime > 2500L) {
                    recreateRecognizerAndListen()
                }
                // Case 2: onEndOfSpeech fired > 3500ms ago and neither onResults nor onError arrived (true Binder hang)
                else if (isProcessingSpeech && lastEndOfSpeechUptime > 0L && now - lastEndOfSpeechUptime > 3500L) {
                    recreateRecognizerAndListen()
                }
                // Case 3: Recognizer has been completely idle for > 350ms when it should be listening
                else if (!isCurrentlyListening && !isStartingListen && !isProcessingSpeech && now - lastCycleEndUptime > 350L) {
                    startSingleListenCycle()
                }
            }

            val nextCheckDelay = if (isCurrentlyListening && !isStartingListen && !isProcessingSpeech) {
                2400L
            } else {
                1200L
            }
            mainHandler.removeCallbacks(this)
            mainHandler.postDelayed(this, nextCheckDelay)
        }
    }

    private fun scheduleWatchdog() {
        val delayMs = if (isCurrentlyListening && !isStartingListen && !isProcessingSpeech) 2400L else 1200L
        mainHandler.removeCallbacks(watchdogRunnable)
        mainHandler.postDelayed(watchdogRunnable, delayMs)
    }

    private fun emitRmsIfChanged(normalized: Float) {
        val clamped = normalized.coerceIn(0f, 1f)
        if (lastReportedRms == clamped) return
        if (clamped > 0f && lastReportedRms > 0f && kotlin.math.abs(clamped - lastReportedRms) < 0.04f) {
            return
        }
        lastReportedRms = clamped
        mainHandler.post { rmsCallback(clamped) }
    }

    private fun scheduleListenRestart(delayMs: Long) {
        mainHandler.removeCallbacks(restartListenRunnable)
        mainHandler.postDelayed(restartListenRunnable, delayMs)
    }

    init {
        tts = TextToSpeech(context.applicationContext) { status ->
            if (status == TextToSpeech.SUCCESS) {
                val targetLocale = if (language == LearningLanguage.SPANISH) {
                    Locale("es", "ES")
                } else {
                    Locale.US
                }
                tts?.language = targetLocale
                tts?.setSpeechRate(0.95f)
                tts?.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                    override fun onStart(utteranceId: String?) {
                        if (utteranceId != null && utteranceId == activeUtteranceId) {
                            isTtsSpeaking = true
                        }
                    }

                    override fun onDone(utteranceId: String?) {
                        if (activeUtteranceId != null && utteranceId != activeUtteranceId) return
                        activeUtteranceId = null
                        isTtsSpeaking = false
                        isPausedForTts = false
                        mainHandler.removeCallbacks(ttsFallbackRunnable)
                        val cb = pendingTtsOnDoneCallback
                        pendingTtsOnDoneCallback = null
                        mainHandler.post {
                            cb?.invoke()
                            // Allow 420ms after onDone so hardware speaker buffer & room echo fully decay before mic reopens
                            if (shouldStayListening && !isPausedForTransition) {
                                scheduleListenRestart(420L)
                            }
                        }
                    }

                    @Deprecated("Deprecated in Java")
                    override fun onError(utteranceId: String?) {
                        if (activeUtteranceId != null && utteranceId != activeUtteranceId) return
                        activeUtteranceId = null
                        isTtsSpeaking = false
                        isPausedForTts = false
                        mainHandler.removeCallbacks(ttsFallbackRunnable)
                        val cb = pendingTtsOnDoneCallback
                        pendingTtsOnDoneCallback = null
                        mainHandler.post {
                            cb?.invoke()
                            if (shouldStayListening && !isPausedForTransition) {
                                scheduleListenRestart(350L)
                            }
                        }
                    }
                })
                isTtsReady = true
            }
        }
    }

    private val restoreSystemSoundsRunnable = Runnable {
        restoreSystemSoundsNow()
    }

    private fun muteSystemSounds() {
        mainHandler.removeCallbacks(restoreSystemSoundsRunnable)
        val am = audioManager ?: return
        val candidateStreams = mutableListOf(
            AudioManager.STREAM_SYSTEM,
            AudioManager.STREAM_NOTIFICATION,
            AudioManager.STREAM_DTMF,
            AudioManager.STREAM_ACCESSIBILITY
        )
        try {
            if (am.ringerMode == AudioManager.RINGER_MODE_NORMAL) {
                candidateStreams.add(AudioManager.STREAM_RING)
            }
        } catch (_: Throwable) {
        }

        // Mute each earcon stream in its own try/catch so a DND SecurityException on
        // NOTIFICATION/RING never blocks muting SYSTEM/DTMF/ACCESSIBILITY.
        // Re-checks isStreamMute every cycle so system resets cannot restore beeps mid-session.
        // NEVER touches STREAM_MUSIC so TTS pronunciation and Samsung mic HAL remain active.
        for (stream in candidateStreams) {
            try {
                if (!am.isStreamMute(stream)) {
                    am.adjustStreamVolume(stream, AudioManager.ADJUST_MUTE, 0)
                    mutedStreams.add(stream)
                }
            } catch (_: Throwable) {
            }
        }
    }

    private fun restoreSystemSounds(delayMs: Long = 1500L) {
        mainHandler.removeCallbacks(restoreSystemSoundsRunnable)
        if (delayMs <= 0L) {
            restoreSystemSoundsNow()
        } else {
            mainHandler.postDelayed(restoreSystemSoundsRunnable, delayMs)
        }
    }

    private fun restoreSystemSoundsNow() {
        if (mutedStreams.isEmpty()) return
        val am = audioManager ?: return
        val toRestore = mutedStreams.toList()
        mutedStreams.clear()
        for (stream in toRestore) {
            try {
                if (am.isStreamMute(stream)) {
                    am.adjustStreamVolume(stream, AudioManager.ADJUST_UNMUTE, 0)
                }
            } catch (_: Throwable) {
            }
        }
    }

    fun speakEnglish(text: String, onDone: (() -> Unit)? = null) {
        speakWord(text, onDone)
    }

    fun speakWord(text: String, onDone: (() -> Unit)? = null) {
        if (!isTtsReady) {
            onDone?.invoke()
            return
        }
        val targetLocale = if (language == LearningLanguage.SPANISH) {
            Locale("es", "ES")
        } else {
            Locale.US
        }
        try {
            tts?.language = targetLocale
        } catch (_: Throwable) {
        }
        pendingTtsOnDoneCallback = onDone
        isTtsSpeaking = true
        isPausedForTts = true
        mainHandler.removeCallbacks(restartListenRunnable)
        mainHandler.removeCallbacks(ttsFallbackRunnable)
        if (shouldStayListening) {
            if (isCurrentlyListening || isStartingListen || isProcessingSpeech) {
                muteSystemSounds()
                try {
                    speechRecognizer?.cancel()
                } catch (_: Exception) {
                }
            }
            isCurrentlyListening = false
            isStartingListen = false
            isProcessingSpeech = false
            lastEndOfSpeechUptime = 0L
            accumulatedPartialsInCycle.clear()
        }
        val spokenText = text.replace(" – ", ", ").replace(" - ", ", ")
        val utteranceId = "vocab_tts_${System.nanoTime()}"
        activeUtteranceId = utteranceId
        tts?.speak(spokenText, TextToSpeech.QUEUE_FLUSH, null, utteranceId)
        val fallbackTimeoutMs = 1450L + (spokenText.length * 45L).coerceAtMost(2400L)
        mainHandler.postDelayed(ttsFallbackRunnable, fallbackTimeoutMs)
    }

    fun startContinuousListening() {
        silentCycleCount = 0
        muteSystemSounds()
        val alreadyActive = shouldStayListening && !isPausedForTts && !isPausedForTransition &&
            (isCurrentlyListening || isStartingListen || isProcessingSpeech)
        shouldStayListening = true
        isPausedForTts = false
        isPausedForTransition = false
        if (alreadyActive) {
            scheduleWatchdog()
            return
        }
        mainHandler.post {
            if (!isCurrentlyListening && !isStartingListen && !isProcessingSpeech) {
                startSingleListenCycle()
            }
            scheduleWatchdog()
        }
    }

    fun stopListening() {
        if (!shouldStayListening && !isCurrentlyListening && !isStartingListen && !isProcessingSpeech) {
            return
        }
        shouldStayListening = false
        isPausedForTts = false
        isPausedForTransition = false
        isStartingListen = false
        isCurrentlyListening = false
        isProcessingSpeech = false
        cycleReadyUptime = 0L
        silentCycleCount = 0
        lastReportedRms = -1f
        mainHandler.removeCallbacks(watchdogRunnable)
        mainHandler.removeCallbacks(restartListenRunnable)
        mainHandler.removeCallbacks(transitionFallbackRunnable)
        mainHandler.removeCallbacks(ttsFallbackRunnable)
        mainHandler.post {
            muteSystemSounds()
            try {
                speechRecognizer?.cancel()
            } catch (_: Exception) {
            }
            accumulatedPartialsInCycle.clear()
            // Keep system sounds muted for 1500ms so the recognizer teardown/cancel earcon is completely silenced!
            restoreSystemSounds(1500L)
            listeningCallback(false)
            lastReportedRms = 0f
            rmsCallback(0f)
        }
    }

    /**
     * Called at the start of a card transition.
     * We cancel the previous card's audio buffer immediately and ignore all incoming results
     * until the new card is fully presented on screen.
     */
    fun pauseForCardTransition() {
        if (!shouldStayListening) return
        isPausedForTransition = true
        silentCycleCount = 0
        mainHandler.removeCallbacks(restartListenRunnable)
        mainHandler.removeCallbacks(transitionFallbackRunnable)
        mainHandler.post {
            if (isCurrentlyListening || isStartingListen || isProcessingSpeech) {
                muteSystemSounds()
                try {
                    speechRecognizer?.cancel()
                } catch (_: Exception) {
                }
            }
            isCurrentlyListening = false
            isStartingListen = false
            isProcessingSpeech = false
            cycleReadyUptime = 0L
            accumulatedPartialsInCycle.clear()
            lastReportedRms = 0f
            rmsCallback(0f)
            mainHandler.postDelayed(transitionFallbackRunnable, 1200L)
        }
    }

    /**
     * Called as soon as the new card becomes active so a fresh recognition cycle starts
     * smoothly if the previous one finished.
     */
    fun startForNewCard() {
        if (!shouldStayListening) return
        silentCycleCount = 0
        mainHandler.removeCallbacks(transitionFallbackRunnable)
        mainHandler.removeCallbacks(restartListenRunnable)
        mainHandler.post {
            isPausedForTransition = false
            accumulatedPartialsInCycle.clear()
            if (!isPausedForTts && !isTtsSpeaking) {
                if (!isCurrentlyListening && !isStartingListen && !isProcessingSpeech) {
                    startSingleListenCycle()
                }
                scheduleWatchdog()
            }
        }
    }

    private fun recreateRecognizerAndListen() {
        mainHandler.removeCallbacks(restartListenRunnable)
        muteSystemSounds()
        try {
            speechRecognizer?.cancel()
            speechRecognizer?.destroy()
        } catch (_: Exception) {
        }
        speechRecognizer = null
        isCurrentlyListening = false
        isStartingListen = false
        isProcessingSpeech = false
        cycleReadyUptime = 0L
        lastEndOfSpeechUptime = 0L
        lastCycleEndUptime = SystemClock.uptimeMillis()
        accumulatedPartialsInCycle.clear()
        lastReportedRms = 0f
        rmsCallback(0f)
        if (shouldStayListening && !isPausedForTts && !isPausedForTransition && !isTtsSpeaking) {
            scheduleListenRestart(150L)
        }
    }

    private var useDefaultFallbackRecognizer = false

    private fun createOptimizedRecognizer(): SpeechRecognizer {
        if (!useDefaultFallbackRecognizer) {
            try {
                val pm = context.packageManager
                val services = pm.queryIntentServices(Intent(RecognitionService.SERVICE_INTERFACE), 0)
                // 1. Prioritize canonical GoogleRecognitionService
                val googleService = services.firstOrNull {
                    val name = it.serviceInfo?.name ?: ""
                    name.contains("GoogleRecognitionService", ignoreCase = true)
                } ?: services.firstOrNull {
                    val pkg = it.serviceInfo?.packageName ?: ""
                    val name = it.serviceInfo?.name ?: ""
                    pkg == "com.google.android.googlequicksearchbox" &&
                        !name.contains("SoundSearch", ignoreCase = true) &&
                        !name.contains("Music", ignoreCase = true)
                } ?: services.firstOrNull {
                    val pkg = it.serviceInfo?.packageName ?: ""
                    val name = it.serviceInfo?.name ?: ""
                    pkg.contains("google", ignoreCase = true) &&
                        !name.contains("SoundSearch", ignoreCase = true) &&
                        !name.contains("Music", ignoreCase = true)
                }

                if (googleService != null && googleService.serviceInfo != null) {
                    val comp = ComponentName(googleService.serviceInfo.packageName, googleService.serviceInfo.name)
                    return SpeechRecognizer.createSpeechRecognizer(context, comp)
                }
            } catch (_: Throwable) {
            }
        }
        return SpeechRecognizer.createSpeechRecognizer(context)
    }

    private fun ensureRecognizer() {
        if (speechRecognizer != null) return
        if (!SpeechRecognizer.isRecognitionAvailable(context)) {
            errorCallback("Rozpoznávání hlasu není na tomto zařízení dostupné.")
            return
        }
        speechRecognizer = createOptimizedRecognizer().apply {
            setRecognitionListener(object : RecognitionListener {
                override fun onReadyForSpeech(params: Bundle?) {
                    if (!shouldStayListening || isPausedForTts || isPausedForTransition) return
                    isStartingListen = false
                    isCurrentlyListening = true
                    isProcessingSpeech = false
                    cycleReadyUptime = SystemClock.uptimeMillis()
                    lastEndOfSpeechUptime = 0L
                    consecutiveErrors = 0
                    accumulatedPartialsInCycle.clear()
                    listeningCallback(true)
                    errorCallback(null)
                }

                override fun onBeginningOfSpeech() {
                    if (!shouldStayListening || isPausedForTts || isPausedForTransition) return
                    isStartingListen = false
                    isCurrentlyListening = true
                    isProcessingSpeech = false
                    silentCycleCount = 0
                    lastEndOfSpeechUptime = 0L
                }

                override fun onRmsChanged(rmsdB: Float) {
                    if (!shouldStayListening || isPausedForTts || isPausedForTransition) return
                    if (rmsdB > 1.8f) {
                        silentCycleCount = 0
                    }
                    // rmsdB ranges roughly -2..10 dB; noise-gate low room hiss below 1.2 dB so idle mic stays calm
                    val normalized = ((rmsdB - 1.2f) / 8.8f).coerceIn(0f, 1f)
                    emitRmsIfChanged(normalized)
                }

                override fun onBufferReceived(buffer: ByteArray?) {}

                override fun onEndOfSpeech() {
                    if (!shouldStayListening || isPausedForTts || isPausedForTransition) return
                    isStartingListen = false
                    isCurrentlyListening = false
                    isProcessingSpeech = true
                    lastEndOfSpeechUptime = SystemClock.uptimeMillis()
                    emitRmsIfChanged(0f)
                }

                override fun onError(error: Int) {
                    val now = SystemClock.uptimeMillis()
                    val cycleDurationMs = if (cycleReadyUptime > 0L) now - cycleReadyUptime else 0L
                    isStartingListen = false
                    isCurrentlyListening = false
                    isProcessingSpeech = false
                    cycleReadyUptime = 0L
                    lastEndOfSpeechUptime = 0L
                    lastCycleEndUptime = now
                    emitRmsIfChanged(0f)

                    if (!shouldStayListening || isPausedForTts || isPausedForTransition) {
                        accumulatedPartialsInCycle.clear()
                        listeningCallback(false)
                        return
                    }

                    if (error == SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS) {
                        accumulatedPartialsInCycle.clear()
                        errorCallback("Chybí oprávnění k mikrofonu.")
                        listeningCallback(false)
                        return
                    }

                    // Normal silence or short-utterance end: NOT a service failure!
                    if (error == SpeechRecognizer.ERROR_NO_MATCH ||
                        error == SpeechRecognizer.ERROR_SPEECH_TIMEOUT
                    ) {
                        consecutiveErrors = 0
                        val capturedPartials = accumulatedPartialsInCycle.toList()
                        accumulatedPartialsInCycle.clear()
                        if (capturedPartials.isNotEmpty()) {
                            consecutiveEmptyNoMatch = 0
                            silentCycleCount = 0
                            finalHeardCallback(capturedPartials)
                        } else {
                            silentCycleCount = (silentCycleCount + 1).coerceAtMost(10)
                            // Only treat NO_MATCH as a broken recognizer if the cycle died almost immediately (< 1.8s),
                            // never during genuine quiet pauses (avoids recreating the Binder service every 20s!).
                            if (error == SpeechRecognizer.ERROR_NO_MATCH && cycleDurationMs < 1800L) {
                                consecutiveEmptyNoMatch += 1
                                if (consecutiveEmptyNoMatch >= 4) {
                                    consecutiveEmptyNoMatch = 0
                                    useDefaultFallbackRecognizer = !useDefaultFallbackRecognizer
                                    recreateRecognizerAndListen()
                                    return
                                }
                            } else {
                                consecutiveEmptyNoMatch = 0
                            }
                        }
                        if (shouldStayListening && !isPausedForTts && !isPausedForTransition) {
                            val restartDelay = when {
                                silentCycleCount <= 2 -> 180L
                                silentCycleCount <= 5 -> 420L
                                else -> 850L
                            }
                            scheduleListenRestart(restartDelay)
                        }
                        return
                    }

                    accumulatedPartialsInCycle.clear()
                    consecutiveEmptyNoMatch = 0
                    consecutiveErrors += 1

                    if (error == SpeechRecognizer.ERROR_AUDIO) {
                        errorCallback("Mikrofon je zaneprázdněn nebo nedostupný.")
                        recreateRecognizerAndListen()
                        return
                    }

                    // If language is not supported or errors repeat, alternate engine (Google <-> Default)
                    if (error == 12 /* ERROR_LANGUAGE_NOT_SUPPORTED */ ||
                        error == 13 /* ERROR_LANGUAGE_UNAVAILABLE */ ||
                        error == SpeechRecognizer.ERROR_RECOGNIZER_BUSY ||
                        consecutiveErrors >= 3
                    ) {
                        consecutiveErrors = 0
                        useDefaultFallbackRecognizer = !useDefaultFallbackRecognizer
                        recreateRecognizerAndListen()
                        return
                    }

                    scheduleListenRestart(180L)
                }

                override fun onResults(results: Bundle?) {
                    val wasActive = isStartingListen || isCurrentlyListening || isProcessingSpeech
                    isStartingListen = false
                    isCurrentlyListening = false
                    isProcessingSpeech = false
                    cycleReadyUptime = 0L
                    lastEndOfSpeechUptime = 0L
                    lastCycleEndUptime = SystemClock.uptimeMillis()
                    consecutiveErrors = 0
                    emitRmsIfChanged(0f)

                    if (!shouldStayListening || isPausedForTts || isPausedForTransition || !wasActive) {
                        accumulatedPartialsInCycle.clear()
                        return
                    }

                    val finalCandidates = extractAllCandidates(results)
                    val combined = LinkedHashSet<String>().apply {
                        addAll(finalCandidates)
                        addAll(accumulatedPartialsInCycle)
                    }.toList()
                    accumulatedPartialsInCycle.clear()

                    if (combined.isNotEmpty()) {
                        consecutiveEmptyNoMatch = 0
                        silentCycleCount = 0
                        finalHeardCallback(combined)
                    }
                    if (shouldStayListening && !isPausedForTts && !isPausedForTransition) {
                        scheduleListenRestart(180L)
                    }
                }

                override fun onPartialResults(partialResults: Bundle?) {
                    if (!shouldStayListening || isPausedForTts || isPausedForTransition) return
                    if (!isStartingListen && !isCurrentlyListening && !isProcessingSpeech) return

                    val matches = extractAllCandidates(partialResults)
                    if (matches.isNotEmpty()) {
                        silentCycleCount = 0
                        accumulatedPartialsInCycle.addAll(matches)
                        partialHeardCallback(matches)
                    }
                }

                override fun onEvent(eventType: Int, params: Bundle?) {}
            })
        }
    }

    private fun extractAllCandidates(bundle: Bundle?): List<String> {
        if (bundle == null) return emptyList()
        val stable = bundle.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
            ?.filter { it.isNotBlank() }
            .orEmpty()
        val unstable = bundle.getStringArrayList("android.speech.extra.UNSTABLE_TEXT")
            ?.filter { it.isNotBlank() }
            .orEmpty()

        val combined = mutableListOf<String>()
        if (stable.isNotEmpty() && unstable.isNotEmpty()) {
            combined.add("${stable.first()} ${unstable.first()}".trim())
        }
        combined.addAll(unstable)
        combined.addAll(stable)
        return combined.distinct()
    }

    private fun startSingleListenCycle() {
        if (!shouldStayListening || isPausedForTts || isPausedForTransition || isTtsSpeaking) return
        if (isCurrentlyListening || isStartingListen || isProcessingSpeech) return
        mainHandler.removeCallbacks(restartListenRunnable)
        muteSystemSounds()
        ensureRecognizer()
        val recognizer = speechRecognizer ?: return
        isStartingListen = true
        lastStartAttemptUptime = SystemClock.uptimeMillis()
        accumulatedPartialsInCycle.clear()

        val localeTag = if (listenInCzech) "cs-CZ" else language.speechLocaleTag // e.g. "cs-CZ", "es-ES" or "en-US"
        val altLocaleTag = localeTag.replace('-', '_') // e.g. "cs_CZ", "es_ES" or "en_US"
        val langCode = if (listenInCzech) "cs" else language.code // e.g. "cs", "es" or "en"
        val targetLocale = when {
            listenInCzech -> Locale("cs", "CZ")
            language == LearningLanguage.SPANISH -> Locale("es", "ES")
            else -> Locale.US
        }

        try {
            Locale.setDefault(targetLocale)
        } catch (_: Throwable) {
        }

        val biasList = currentTargetBiasStrings
        val isShortTarget = !listenInCzech && (biasList.firstOrNull()?.split(Regex("\\s+"))?.size ?: 1) <= 2
        val languageModel = if (isShortTarget) {
            RecognizerIntent.LANGUAGE_MODEL_WEB_SEARCH
        } else {
            RecognizerIntent.LANGUAGE_MODEL_FREE_FORM
        }

        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, languageModel)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, localeTag)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_PREFERENCE, localeTag)
            putExtra(RecognizerIntent.EXTRA_ONLY_RETURN_LANGUAGE_PREFERENCE, true)
            putExtra("android.speech.extra.EXTRA_ADDITIONAL_LANGUAGES", arrayOf(localeTag, altLocaleTag, langCode))
            putExtra("android.speech.extra.LANGUAGE", localeTag)
            // Critical: setting EXTRA_PREFER_OFFLINE = false prevents the recognizer from failing
            // or falling back to the phone's system language (Czech/English) when the foreign offline pack isn't installed!
            putExtra(RecognizerIntent.EXTRA_PREFER_OFFLINE, false)
            putExtra(RecognizerIntent.EXTRA_CALLING_PACKAGE, context.packageName)
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
            putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 10)
            putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_MINIMUM_LENGTH_MILLIS, 1500L)
            putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_POSSIBLY_COMPLETE_SILENCE_LENGTH_MILLIS, 800L)
            putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_COMPLETE_SILENCE_LENGTH_MILLIS, 1100L)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && biasList.isNotEmpty()) {
                putStringArrayListExtra(RecognizerIntent.EXTRA_BIASING_STRINGS, ArrayList(biasList.take(10)))
            }
        }
        try {
            recognizer.startListening(intent)
        } catch (_: Exception) {
            isStartingListen = false
            scheduleListenRestart(180L)
        }
    }

    fun release() {
        shouldStayListening = false
        isPausedForTts = false
        isPausedForTransition = false
        isStartingListen = false
        isCurrentlyListening = false
        isProcessingSpeech = false
        mainHandler.removeCallbacksAndMessages(null)
        try {
            speechRecognizer?.destroy()
        } catch (_: Exception) {
        }
        speechRecognizer = null
        try {
            tts?.stop()
            tts?.shutdown()
        } catch (_: Exception) {
        }
        tts = null
        restoreSystemSounds(1350L)
    }
}
