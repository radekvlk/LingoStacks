package cz.slovickaspeak.app.ui

import android.content.res.Configuration
import android.os.SystemClock
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.StartOffset
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.VolumeOff
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Lightbulb
import androidx.compose.material.icons.filled.LocalFireDepartment
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.MicOff
import androidx.compose.material.icons.filled.Psychology
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.platform.LocalView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import cz.slovickaspeak.app.model.AnswerMethod
import cz.slovickaspeak.app.model.CardAttemptResult
import cz.slovickaspeak.app.model.StudyDirection
import cz.slovickaspeak.app.model.VocabDeck
import cz.slovickaspeak.app.model.VocabWord
import cz.slovickaspeak.app.speech.SpeechSessionController
import cz.slovickaspeak.app.speech.WordMatcher
import cz.slovickaspeak.app.ui.theme.*
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlin.math.abs

private enum class CardFeedbackState {
    NEUTRAL,
    SPOKEN_WRONG_FLASH,
    SPOKEN_CORRECT,
    SWIPED_CORRECT,
    SWIPED_WRONG
}

private val AppleGlideEasing = CubicBezierEasing(0.22f, 1.0f, 0.36f, 1.0f)

@Composable
fun FlashcardSessionScreen(
    deck: VocabDeck,
    wordsToPractice: List<VocabWord> = deck.words,
    progressState: cz.slovickaspeak.app.data.ProgressStateHolder,
    hasMicPermission: Boolean,
    onRequestMicPermission: () -> Unit,
    onSessionFinished: (List<CardAttemptResult>) -> Unit,
    onCloseSession: () -> Unit
) {
    val context = LocalContext.current
    val strings = rememberAppStrings()
    val coroutineScope = rememberCoroutineScope()
    val dailyGoalWords = progressState.dailyGoalWords

    // Spaced Repetition Queue:
    // - Prioritizes words previously not known, then new words, then mastered words.
    // - Whenever a word is answered "Nevím" (isCorrect == false), it is re-inserted a few cards ahead
    //   so it keeps re-appearing until the user masters all target words!
    val sessionQueue = remember(wordsToPractice, dailyGoalWords) {
        mutableStateListOf<VocabWord>().apply {
            addAll(progressState.prioritizeWordsForSession(wordsToPractice, dailyGoalWords))
        }
    }

    var currentIndex by remember(dailyGoalWords) { mutableIntStateOf(0) }
    var masteredInSessionCount by remember(dailyGoalWords) { mutableIntStateOf(0) }
    val results = remember(dailyGoalWords) { mutableStateListOf<CardAttemptResult>() }
    var showGoalPicker by remember { mutableStateOf(false) }
    var showRecentWordsPanel by remember { mutableStateOf(false) }
    var toggleFeedbackPopup by remember { mutableStateOf<String?>(null) }
    var toggleFeedbackSeq by remember { mutableIntStateOf(0) }

    LaunchedEffect(toggleFeedbackSeq) {
        if (toggleFeedbackPopup != null) {
            delay(1900L)
            toggleFeedbackPopup = null
        }
    }

    val currentWord = sessionQueue.getOrNull(currentIndex)
    val nextWord = sessionQueue.getOrNull(currentIndex + 1)
    val thirdWord = sessionQueue.getOrNull(currentIndex + 2)
    val studyDirection = progressState.studyDirection
    val isReversedCard = when (studyDirection) {
        cz.slovickaspeak.app.model.StudyDirection.NATIVE_TO_FOREIGN -> false
        cz.slovickaspeak.app.model.StudyDirection.FOREIGN_TO_NATIVE -> true
        cz.slovickaspeak.app.model.StudyDirection.MIXED -> currentIndex % 2 == 1
    }
    val isReversedCardState by rememberUpdatedState(isReversedCard)

    var feedbackState by remember { mutableStateOf(CardFeedbackState.NEUTRAL) }
    var isTransitioningCard by remember { mutableStateOf(false) }
    var readbackWord by remember { mutableStateOf<VocabWord?>(null) }
    var readbackIsCorrect by remember { mutableStateOf(true) }
    var readbackIsReversed by remember { mutableStateOf(false) }
    var showHintTranslation by remember { mutableStateOf(false) }
    var usedHintOnCurrentCard by remember { mutableStateOf(false) }
    val wordsNeedingSecondConfirmation = remember(dailyGoalWords) { mutableStateListOf<String>() }
    var lastHeardText by remember { mutableStateOf<String?>(null) }
    var speechError by remember { mutableStateOf<String?>(null) }

    var isListening by remember { mutableStateOf(false) }
    var micAutoEnabled by remember { mutableStateOf(true) }
    var isAppResumed by remember { mutableStateOf(true) }
    var rmsLevel by remember { mutableFloatStateOf(0f) }

    // GPU-driven Animatables (read ONLY inside graphicsLayer draw phase to avoid layout thrashing)
    val offsetX = remember { Animatable(0f) }
    val cardScale = remember { Animatable(1f) }
    val cardAlpha = remember { Animatable(1f) }

    val currentWordState by rememberUpdatedState(currentWord)
    val feedbackStateUpdated by rememberUpdatedState(feedbackState)
    val isTransitioningUpdated by rememberUpdatedState(isTransitioningCard)
    val hasMicPermState by rememberUpdatedState(hasMicPermission)
    val micAutoState by rememberUpdatedState(micAutoEnabled)

    var speechControllerRef by remember { mutableStateOf<SpeechSessionController?>(null) }
    var wrongWordDebounceJob by remember { mutableStateOf<Job?>(null) }
    var wrongFlashResetJob by remember { mutableStateOf<Job?>(null) }
    var lastFlashedWrongWord by remember { mutableStateOf<String?>(null) }
    var cardActiveTimestamp by remember(dailyGoalWords) { mutableLongStateOf(SystemClock.uptimeMillis()) }

    val currentView = LocalView.current
    var screenAwakeTimeoutJob by remember { mutableStateOf<Job?>(null) }

    fun keepScreenActive() {
        try {
            currentView.keepScreenOn = true
        } catch (_: Throwable) {
        }
        screenAwakeTimeoutJob?.cancel()
        screenAwakeTimeoutJob = coroutineScope.launch {
            delay(50_000L) // 50 seconds without any card movement or speech -> allow display to sleep
            try {
                currentView.keepScreenOn = false
            } catch (_: Throwable) {
            }
        }
    }

    DisposableEffect(Unit) {
        keepScreenActive()
        onDispose {
            screenAwakeTimeoutJob?.cancel()
            try {
                currentView.keepScreenOn = false
            } catch (_: Throwable) {
            }
        }
    }

    fun triggerWrongSpokenFlash(heardSnippet: String) {
        keepScreenActive()
        val now = SystemClock.uptimeMillis()
        // Ignore stale audio that finished decoding from the previous card transition (within 450ms)
        if (now - cardActiveTimestamp < 450L) return

        val currentState = feedbackStateUpdated
        if (currentState != CardFeedbackState.NEUTRAL && currentState != CardFeedbackState.SPOKEN_WRONG_FLASH) return
        if (currentState == CardFeedbackState.SPOKEN_WRONG_FLASH &&
            lastFlashedWrongWord.equals(heardSnippet, ignoreCase = true)
        ) {
            return
        }

        wrongWordDebounceJob?.cancel()
        wrongFlashResetJob?.cancel()
        lastHeardText = heardSnippet
        lastFlashedWrongWord = heardSnippet
        feedbackState = CardFeedbackState.SPOKEN_WRONG_FLASH

        wrongFlashResetJob = coroutineScope.launch {
            // Subtle iOS horizontal error shake on GPU layer
            launch {
                offsetX.animateTo(-18f, tween(45, easing = FastOutSlowInEasing))
                offsetX.animateTo(18f, tween(45, easing = FastOutSlowInEasing))
                offsetX.animateTo(-11f, tween(45, easing = FastOutSlowInEasing))
                offsetX.animateTo(11f, tween(45, easing = FastOutSlowInEasing))
                offsetX.animateTo(0f, tween(55, easing = FastOutSlowInEasing))
            }
            delay(1100L)
            if (feedbackStateUpdated == CardFeedbackState.SPOKEN_WRONG_FLASH) {
                feedbackState = CardFeedbackState.NEUTRAL
                lastFlashedWrongWord = null
            }
        }
    }

    fun triggerCardResolution(
        isCorrect: Boolean,
        method: AnswerMethod,
        spokenMatch: String? = null
    ) {
        keepScreenActive()
        val word = currentWordState ?: return
        val currentState = feedbackStateUpdated
        if (isTransitioningUpdated || (currentState != CardFeedbackState.NEUTRAL && currentState != CardFeedbackState.SPOKEN_WRONG_FLASH)) return

        isTransitioningCard = true
        showRecentWordsPanel = false
        toggleFeedbackPopup = null
        wrongWordDebounceJob?.cancel()
        wrongFlashResetJob?.cancel()
        lastFlashedWrongWord = null

        feedbackState = when {
            method == AnswerMethod.SPOKEN_CORRECT -> CardFeedbackState.SPOKEN_CORRECT
            isCorrect -> CardFeedbackState.SWIPED_CORRECT
            else -> CardFeedbackState.SWIPED_WRONG
        }

        val cardWasReversed = isReversedCardState
        val shouldPronounce = if (cardWasReversed) {
            method == AnswerMethod.SWIPED_DONT_KNOW
        } else {
            method == AnswerMethod.SWIPED_DONT_KNOW || (isCorrect && progressState.autoPronounceEnabled)
        }

        if (hasMicPermState && micAutoState) {
            // Pause microphone immediately so TTS audio or transition noise never leaks into next card
            speechControllerRef?.pauseForCardTransition()
        }

        coroutineScope.launch {
            // Exit direction: Right (+1050f) for Correct/Know, Left (-1050f) for Wrong/Don't know
            val exitTargetX = if (isCorrect) 1050f else -1050f
            var speechFinished = !shouldPronounce

            // Start pronunciation immediately in parallel so card exit glides smoothly without any freeze
            if (shouldPronounce) {
                speechControllerRef?.speakEnglish(word.english) {
                    speechFinished = true
                }
            }

            if (method == AnswerMethod.SPOKEN_CORRECT) {
                // Quick pulse before gliding off-screen
                cardScale.animateTo(1.035f, tween(60, easing = FastOutSlowInEasing))
                cardScale.animateTo(1.0f, tween(60, easing = FastOutSlowInEasing))
            }

            // 1. Current card ALWAYS glides off-screen smoothly right away (never snaps back or freezes)
            launch {
                cardAlpha.animateTo(0f, tween(durationMillis = 185, easing = FastOutSlowInEasing))
            }
            launch {
                cardScale.animateTo(0.94f, tween(durationMillis = 195, easing = FastOutSlowInEasing))
            }
            offsetX.animateTo(
                targetValue = exitTargetX,
                animationSpec = tween(durationMillis = 200, easing = FastOutSlowInEasing)
            )

            // 2. If enabled in settings, pop a compact bounce readback card in the vacated center while TTS speaks
            val shouldShowReadbackOverlay = shouldPronounce && progressState.pauseCardWhileSpeaking
            if (shouldShowReadbackOverlay) {
                readbackIsCorrect = isCorrect
                readbackIsReversed = cardWasReversed
                readbackWord = word
                val holdStart = SystemClock.uptimeMillis()
                val dynamicMaxWaitMs = (2000L + word.english.length * 60L).coerceIn(2000L, 4500L)
                while (!speechFinished && SystemClock.uptimeMillis() - holdStart < dynamicMaxWaitMs) {
                    delay(40L)
                }
                val elapsedHold = SystemClock.uptimeMillis() - holdStart
                if (elapsedHold < 2000L) {
                    delay(2000L - elapsedHold)
                } else {
                    delay(220L)
                }
                readbackWord = null
                delay(115L)
            }

            val attempt = CardAttemptResult(
                word = word,
                isCorrect = isCorrect && !usedHintOnCurrentCard,
                method = method,
                spokenTranscript = spokenMatch ?: lastHeardText,
                wasReversed = cardWasReversed
            )
            results.add(attempt)
            progressState.recordCardAttempt(
                result = attempt,
                usedHint = usedHintOnCurrentCard,
                isReversed = cardWasReversed
            )

            when {
                !isCorrect -> {
                    // Stage 1 (Unknown): Mark for 2-stage confirmation and re-insert 3 cards ahead for immediate recall
                    if (word.id !in wordsNeedingSecondConfirmation) {
                        wordsNeedingSecondConfirmation.add(word.id)
                    }
                    val reinsertIndex = (currentIndex + 3).coerceAtMost(sessionQueue.size)
                    sessionQueue.add(reinsertIndex, word)
                }
                usedHintOnCurrentCard -> {
                    // Assisted Recall (used hint): Must be answered once without a hint 5 cards ahead before counting!
                    val reinsertIndex = (currentIndex + 5).coerceAtMost(sessionQueue.size)
                    sessionQueue.add(reinsertIndex, word)
                }
                word.id in wordsNeedingSecondConfirmation -> {
                    // Stage 2 (First unassisted recall after a mistake):
                    // Remove from first-check list and schedule one final confirmation 7 cards ahead!
                    wordsNeedingSecondConfirmation.remove(word.id)
                    val reinsertIndex = (currentIndex + 7).coerceAtMost(sessionQueue.size)
                    sessionQueue.add(reinsertIndex, word)
                }
                else -> {
                    // Unassisted mastery confirmed!
                    masteredInSessionCount += 1
                }
            }

            if (masteredInSessionCount >= dailyGoalWords || currentIndex + 1 >= sessionQueue.size) {
                // Only on the very last card: let final pronunciation finish before disposing SpeechController
                val startWait = SystemClock.uptimeMillis()
                while (!speechFinished && SystemClock.uptimeMillis() - startWait < 1000L) {
                    delay(40L)
                }
                speechControllerRef?.stopListening()
                progressState.recordSessionCompleted()
                onSessionFinished(results.toList())
            } else {
                // Reset feedback & hint SYNCHRONOUSLY with currentIndex so incoming card NEVER flashes previous revealed answer
                feedbackState = CardFeedbackState.NEUTRAL
                showHintTranslation = false
                usedHintOnCurrentCard = false
                lastHeardText = null
                lastFlashedWrongWord = null
                currentIndex += 1

                // 2. Next card enters from the OPPOSITE direction smoothly on GPU layer
                offsetX.snapTo(-exitTargetX * 0.55f)
                cardScale.snapTo(0.92f)
                cardAlpha.snapTo(0.15f)
                launch {
                    cardAlpha.animateTo(1f, tween(durationMillis = 200, easing = AppleGlideEasing))
                }
                launch {
                    cardScale.animateTo(1f, tween(durationMillis = 240, easing = AppleGlideEasing))
                }
                offsetX.animateTo(
                    targetValue = 0f,
                    animationSpec = tween(durationMillis = 240, easing = AppleGlideEasing)
                )

                // 3. ONLY AFTER the next card has completely arrived in the center:
                // Settle timestamp, enable speech acceptance, and start listening cleanly once TTS finishes!
                cardActiveTimestamp = SystemClock.uptimeMillis()
                isTransitioningCard = false
                keepScreenActive()
                val nextIsReversed = when (progressState.studyDirection) {
                    StudyDirection.NATIVE_TO_FOREIGN -> false
                    StudyDirection.FOREIGN_TO_NATIVE -> true
                    StudyDirection.MIXED -> (currentIndex % 2 == 1)
                }
                val willAutoSpeakOnArrival = nextIsReversed && progressState.autoPronounceEnabled
                if (hasMicPermState && micAutoState && !willAutoSpeakOnArrival) {
                    speechControllerRef?.startForNewCard()
                }
            }
        }
    }

    val handleSpeechCandidates: (List<String>, Boolean) -> Unit = { candidates, isFinal ->
        keepScreenActive()
        val word = currentWordState
        val state = feedbackStateUpdated
        val expectCzech = isReversedCardState
        val canAcceptSpeech = !isTransitioningUpdated &&
                (state == CardFeedbackState.NEUTRAL || state == CardFeedbackState.SPOKEN_WRONG_FLASH)
        if (word != null && canAcceptSpeech && candidates.isNotEmpty()) {
            val matched = WordMatcher.matches(
                spokenCandidates = candidates,
                targetWord = word,
                isFinal = isFinal,
                expectCzech = expectCzech,
                language = deck.language
            )
            if (matched != null) {
                wrongWordDebounceJob?.cancel()
                wrongFlashResetJob?.cancel()
                lastHeardText = matched
                triggerCardResolution(
                    isCorrect = true,
                    method = AnswerMethod.SPOKEN_CORRECT,
                    spokenMatch = matched
                )
            } else {
                val snippet = WordMatcher.extractLatestSpokenSnippet(candidates)
                if (!snippet.isNullOrBlank()) {
                    lastHeardText = snippet
                    wrongWordDebounceJob?.cancel()

                    if (isFinal) {
                        triggerWrongSpokenFlash(snippet)
                    } else if (!WordMatcher.isPrefixOfTarget(candidates, word, expectCzech = expectCzech)) {
                        // Wait well past Android's 750-900ms complete-silence window + ASR finalization latency
                        // before flashing red on a non-final partial (prevents red-then-green flash before onResults).
                        wrongWordDebounceJob = coroutineScope.launch {
                            delay(1750L)
                            triggerWrongSpokenFlash(snippet)
                        }
                    }
                }
            }
        }
    }

    val currentHandleSpeech by rememberUpdatedState(handleSpeechCandidates)
    val showGoalPickerUpdated by rememberUpdatedState(showGoalPicker)
    val showOtaDialogUpdated by rememberUpdatedState(progressState.showOtaDialog)
    val lifecycleOwner = LocalLifecycleOwner.current

    DisposableEffect(lifecycleOwner, deck.language) {
        val controller = SpeechSessionController(
            context = context,
            language = deck.language,
            listeningCallback = { listening -> isListening = listening },
            rmsCallback = { rms -> rmsLevel = rms },
            partialHeardCallback = { partials -> currentHandleSpeech(partials, false) },
            finalHeardCallback = { finals -> currentHandleSpeech(finals, true) },
            errorCallback = { err -> speechError = err }
        )
        speechControllerRef = controller

        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_RESUME -> {
                    isAppResumed = true
                    rmsLevel = 0f
                    keepScreenActive()
                    if (hasMicPermState && micAutoState && !showGoalPickerUpdated && !showOtaDialogUpdated) {
                        controller.startContinuousListening()
                    }
                }
                Lifecycle.Event.ON_PAUSE, Lifecycle.Event.ON_STOP -> {
                    // Immediately release mic DSP, waves, and screen wake lock when paused or minimized
                    isAppResumed = false
                    rmsLevel = 0f
                    screenAwakeTimeoutJob?.cancel()
                    try {
                        currentView.keepScreenOn = false
                    } catch (_: Throwable) {
                    }
                    controller.stopListening()
                }
                else -> {}
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)

        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            controller.release()
        }
    }

    val isModalBlocking = showGoalPicker || progressState.showOtaDialog
    val isMicVisualActive = micAutoEnabled && isAppResumed && !isModalBlocking

    LaunchedEffect(hasMicPermission, isModalBlocking, isAppResumed, micAutoEnabled, speechControllerRef) {
        val controller = speechControllerRef ?: return@LaunchedEffect
        if (isModalBlocking || !isAppResumed || !hasMicPermission || !micAutoEnabled) {
            rmsLevel = 0f
            controller.stopListening()
        } else {
            controller.startContinuousListening()
        }
    }

    LaunchedEffect(currentWord?.id, isReversedCard, speechControllerRef) {
        val controller = speechControllerRef ?: return@LaunchedEffect
        controller.listenInCzech = isReversedCard
        controller.currentTargetBiasStrings = if (currentWord != null) {
            WordMatcher.extractBiasingStrings(currentWord, expectCzech = isReversedCard)
        } else {
            emptyList()
        }
        if (isReversedCard && progressState.autoPronounceEnabled && currentWord != null) {
            delay(180L)
            controller.speakEnglish(currentWord.english) {
                if (hasMicPermState && micAutoState) {
                    controller.startForNewCard()
                }
            }
        }
    }

    if (currentWord == null) return

    val totalSessionCards = sessionQueue.size.coerceAtLeast(1)
    val sessionProgressFraction by animateFloatAsState(
        targetValue = ((currentIndex + 1).toFloat() / totalSessionCards.toFloat()).coerceIn(0f, 1f),
        animationSpec = tween(durationMillis = 350, easing = FastOutSlowInEasing),
        label = "sessionProgressBar"
    )

    if (showGoalPicker) {
        AppleFullSettingsDialog(
            progressState = progressState,
            onDismiss = { showGoalPicker = false }
        )
    }

    Scaffold(
        containerColor = SurfaceBg
    ) { innerPadding ->
        val configuration = LocalConfiguration.current
        val isLandscape = configuration.orientation == Configuration.ORIENTATION_LANDSCAPE ||
                (configuration.screenWidthDp > configuration.screenHeightDp)
        val isCardInteractive = !isTransitioningCard &&
                (feedbackState == CardFeedbackState.NEUTRAL || feedbackState == CardFeedbackState.SPOKEN_WRONG_FLASH)

        // Word translation only reveals on card front via Hint button (answered feedback uses the bounce readback overlay)
        val isTranslationRevealed = showHintTranslation

        val handleToggleHint: () -> Unit = {
            showHintTranslation = !showHintTranslation
            if (showHintTranslation) {
                usedHintOnCurrentCard = true
                speechControllerRef?.speakEnglish(currentWord.english)
            }
        }
        val handlePlayAudio: () -> Unit = {
            speechControllerRef?.speakEnglish(currentWord.english)
        }
        val handleReplayWordText: (String) -> Unit = { wordText ->
            speechControllerRef?.speakEnglish(wordText)
        }
        val handleToggleAutoPronounce: () -> Unit = {
            val next = !progressState.autoPronounceEnabled
            progressState.setAutoPronounce(next)
            showRecentWordsPanel = false
            toggleFeedbackPopup = when {
                next && strings.isCzech -> "🔊 Výslovnost po odpovědi zapnuta — správné slovo se vždy přečte"
                next -> "🔊 Auto pronunciation ON — speaks word after each answer"
                strings.isCzech -> "🔇 Výslovnost po odpovědi vypnuta — přečte se jen při Nevím"
                else -> "🔇 Auto pronunciation OFF — speaks only on Unknown"
            }
            toggleFeedbackSeq += 1
        }
        val handleTogglePauseWhileSpeaking: () -> Unit = {
            val next = !progressState.pauseCardWhileSpeaking
            progressState.updatePauseCardWhileSpeaking(next)
            showRecentWordsPanel = false
            toggleFeedbackPopup = when {
                next && strings.isCzech -> "👁️ Zobrazení slova zapnuto — mezi kartami se ukáže slovo s přepisem"
                next -> "👁️ Word display ON — shows word & phonetics between cards"
                strings.isCzech -> "🙈 Zobrazení slova vypnuto — rychlý přechod rovnou na další kartu"
                else -> "🙈 Word display OFF — fast jump straight to next card"
            }
            toggleFeedbackSeq += 1
        }
        val handleToggleRecentWordsPanel: () -> Unit = {
            toggleFeedbackPopup = null
            showRecentWordsPanel = !showRecentWordsPanel
        }
        val handleReopenPreviousWord: (VocabWord) -> Unit = reopen@{ targetWord ->
            if (isTransitioningUpdated) return@reopen
            showRecentWordsPanel = false
            toggleFeedbackPopup = null
            if (currentWordState?.id == targetWord.id) return@reopen

            isTransitioningCard = true
            wrongWordDebounceJob?.cancel()
            wrongFlashResetJob?.cancel()
            lastFlashedWrongWord = null
            feedbackState = CardFeedbackState.NEUTRAL
            showHintTranslation = false
            usedHintOnCurrentCard = false
            lastHeardText = null

            coroutineScope.launch {
                if (hasMicPermState && micAutoState) {
                    speechControllerRef?.pauseForCardTransition()
                }
                launch {
                    cardAlpha.animateTo(0f, tween(durationMillis = 130, easing = FastOutSlowInEasing))
                }
                offsetX.animateTo(320f, tween(durationMillis = 135, easing = FastOutSlowInEasing))

                sessionQueue.add(currentIndex, targetWord)

                offsetX.snapTo(-420f)
                cardScale.snapTo(0.93f)
                cardAlpha.snapTo(0.15f)
                launch {
                    cardAlpha.animateTo(1f, tween(durationMillis = 180, easing = AppleGlideEasing))
                }
                launch {
                    cardScale.animateTo(1f, tween(durationMillis = 210, easing = AppleGlideEasing))
                }
                offsetX.animateTo(0f, tween(durationMillis = 210, easing = AppleGlideEasing))

                cardActiveTimestamp = SystemClock.uptimeMillis()
                isTransitioningCard = false
                keepScreenActive()
                val reopenedReversed = when (progressState.studyDirection) {
                    StudyDirection.NATIVE_TO_FOREIGN -> false
                    StudyDirection.FOREIGN_TO_NATIVE -> true
                    StudyDirection.MIXED -> (currentIndex % 2 == 1)
                }
                if (hasMicPermState && micAutoState && !(reopenedReversed && progressState.autoPronounceEnabled)) {
                    speechControllerRef?.startForNewCard()
                }
            }
        }
        val handleDragDelta: (Float) -> Unit = { delta ->
            if (showRecentWordsPanel) {
                showRecentWordsPanel = false
            }
            if (toggleFeedbackPopup != null) {
                toggleFeedbackPopup = null
            }
            keepScreenActive()
            if (isCardInteractive) {
                coroutineScope.launch {
                    offsetX.snapTo(offsetX.value + delta)
                }
            }
        }
        val handleDragEnd: () -> Unit = {
            if (isCardInteractive) {
                val threshold = 190f
                when {
                    offsetX.value > threshold -> {
                        triggerCardResolution(
                            isCorrect = true,
                            method = AnswerMethod.SWIPED_KNOW
                        )
                    }
                    offsetX.value < -threshold -> {
                        triggerCardResolution(
                            isCorrect = false,
                            method = AnswerMethod.SWIPED_DONT_KNOW
                        )
                    }
                    else -> {
                        coroutineScope.launch {
                            offsetX.animateTo(
                                targetValue = 0f,
                                animationSpec = tween(durationMillis = 220, easing = AppleGlideEasing)
                            )
                        }
                    }
                }
            }
        }
        val handleToggleMic: () -> Unit = {
            if (!hasMicPermission) {
                micAutoEnabled = true
                onRequestMicPermission()
            } else {
                micAutoEnabled = !micAutoEnabled
                if (micAutoEnabled) {
                    speechControllerRef?.startContinuousListening()
                } else {
                    speechControllerRef?.stopListening()
                }
            }
        }

        if (isLandscape) {
            // Fold / Tablet Landscape (na sirku): Vlevo studijni kokpit (statistiky, kontext, historie, hlas), vpravo karta + tlacitka pod rukou
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .padding(horizontal = 24.dp, vertical = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(20.dp)
            ) {
                // Levy sloupec: Kokpit (TopBar + Zive statistiky + Kontext & Pamet + Posledni odpovedi + Hlasovy monitor)
                Column(
                    modifier = Modifier
                        .weight(0.95f)
                        .fillMaxHeight(),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    AppleSessionTopBar(
                        strings = strings,
                        progressState = progressState,
                        currentIndex = currentIndex,
                        totalSessionCards = totalSessionCards,
                        sessionProgressFraction = sessionProgressFraction,
                        onCloseSession = {
                            speechControllerRef?.stopListening()
                            onCloseSession()
                        },
                        onOpenSettings = { showGoalPicker = true }
                    )

                    LandscapeSessionTelemetryRow(
                        strings = strings,
                        results = results,
                        repeatPendingCount = wordsNeedingSecondConfirmation.size,
                        todayCompleted = progressState.todayCompletedWords,
                        dailyGoal = progressState.dailyGoalWords
                    )

                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth()
                            .verticalScroll(rememberScrollState()),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        WordMemoryInsightsCard(
                            strings = strings,
                            language = deck.language,
                            word = currentWord,
                            srsStat = progressState.getWordSrsStat(currentWord.id, isReversed = isReversedCard),
                            isRevealed = isTranslationRevealed,
                            onSpeakSentence = { sentence ->
                                speechControllerRef?.speakEnglish(sentence)
                            },
                            onRevealHint = handleToggleHint,
                            isReversed = isReversedCard
                        )

                        RecentAttemptsCard(
                            strings = strings,
                            results = results,
                            onReplayWord = handleReplayWordText,
                            onSelectWord = handleReopenPreviousWord
                        )
                    }

                    LiveSpeechBanner(
                        strings = strings,
                        language = deck.language,
                        isListening = isListening,
                        micAutoEnabled = micAutoEnabled,
                        lastHeardText = lastHeardText,
                        feedbackState = feedbackState,
                        speechError = speechError,
                        rmsLevelProvider = { rmsLevel },
                        animateMicWaves = isMicVisualActive,
                        expectCzech = isReversedCard
                    )
                }

                // Pravy sloupec: 3D balicek karticek + Ovladaci panel (Nevim / Mic / Vim) primo pod rukou
                Column(
                    modifier = Modifier
                        .weight(1.05f)
                        .fillMaxHeight(),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Flashcard3DStackArea(
                        strings = strings,
                        deck = deck,
                        currentWord = currentWord,
                        nextWord = nextWord,
                        thirdWord = thirdWord,
                        offsetXProvider = { offsetX.value },
                        cardScaleProvider = { cardScale.value },
                        cardAlphaProvider = { cardAlpha.value },
                        feedbackState = feedbackState,
                        showHintTranslation = isTranslationRevealed,
                        onToggleHint = handleToggleHint,
                        onPlayAudio = handlePlayAudio,
                        autoPronounceEnabled = progressState.autoPronounceEnabled,
                        onToggleAutoPronounce = handleToggleAutoPronounce,
                        pauseWhileSpeaking = progressState.pauseCardWhileSpeaking,
                        onTogglePauseWhileSpeaking = handleTogglePauseWhileSpeaking,
                        toggleFeedbackText = toggleFeedbackPopup,
                        onDismissToggleFeedback = { toggleFeedbackPopup = null },
                        recentResults = results,
                        showRecentWordsPanel = showRecentWordsPanel,
                        onToggleRecentWordsPanel = handleToggleRecentWordsPanel,
                        onReplayRecentWord = handleReplayWordText,
                        onReopenRecentWord = handleReopenPreviousWord,
                        onDragDelta = handleDragDelta,
                        onDragEnd = handleDragEnd,
                        isReversed = isReversedCard,
                        readbackWord = readbackWord,
                        readbackIsCorrect = readbackIsCorrect,
                        readbackIsReversed = readbackIsReversed,
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth()
                    )

                    BottomControlBar(
                        strings = strings,
                        isListening = isListening,
                        micAutoEnabled = micAutoEnabled,
                        rmsLevelProvider = { rmsLevel },
                        animateMicWaves = isMicVisualActive,
                        enabled = isCardInteractive,
                        onSwipeDontKnow = {
                            triggerCardResolution(
                                isCorrect = false,
                                method = AnswerMethod.SWIPED_DONT_KNOW
                            )
                        },
                        onToggleMic = handleToggleMic,
                        onSwipeKnow = {
                            triggerCardResolution(
                                isCorrect = true,
                                method = AnswerMethod.SWIPED_KNOW
                            )
                        }
                    )
                }
            }
        } else {
            // Portrait (na vysku): Plne roztazene pres celou sirku + kompaktni Memory & Context pod kartou
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .padding(horizontal = 18.dp, vertical = 8.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                AppleSessionTopBar(
                    strings = strings,
                    progressState = progressState,
                    currentIndex = currentIndex,
                    totalSessionCards = totalSessionCards,
                    sessionProgressFraction = sessionProgressFraction,
                    onCloseSession = {
                        speechControllerRef?.stopListening()
                        onCloseSession()
                    },
                    onOpenSettings = { showGoalPicker = true }
                )

                Spacer(modifier = Modifier.height(6.dp))

                LiveSpeechBanner(
                    strings = strings,
                    language = deck.language,
                    isListening = isListening,
                    micAutoEnabled = micAutoEnabled,
                    lastHeardText = lastHeardText,
                    feedbackState = feedbackState,
                    speechError = speechError,
                    rmsLevelProvider = { rmsLevel },
                    animateMicWaves = isMicVisualActive,
                    expectCzech = isReversedCard
                )

                Spacer(modifier = Modifier.height(8.dp))

                Flashcard3DStackArea(
                    strings = strings,
                    deck = deck,
                    currentWord = currentWord,
                    nextWord = nextWord,
                    thirdWord = thirdWord,
                    offsetXProvider = { offsetX.value },
                    cardScaleProvider = { cardScale.value },
                    cardAlphaProvider = { cardAlpha.value },
                    feedbackState = feedbackState,
                    showHintTranslation = isTranslationRevealed,
                    onToggleHint = handleToggleHint,
                    onPlayAudio = handlePlayAudio,
                    autoPronounceEnabled = progressState.autoPronounceEnabled,
                    onToggleAutoPronounce = handleToggleAutoPronounce,
                    pauseWhileSpeaking = progressState.pauseCardWhileSpeaking,
                    onTogglePauseWhileSpeaking = handleTogglePauseWhileSpeaking,
                    toggleFeedbackText = toggleFeedbackPopup,
                    onDismissToggleFeedback = { toggleFeedbackPopup = null },
                    recentResults = results,
                    showRecentWordsPanel = showRecentWordsPanel,
                    onToggleRecentWordsPanel = handleToggleRecentWordsPanel,
                    onReplayRecentWord = handleReplayWordText,
                    onReopenRecentWord = handleReopenPreviousWord,
                    onDragDelta = handleDragDelta,
                    onDragEnd = handleDragEnd,
                    isReversed = isReversedCard,
                    readbackWord = readbackWord,
                    readbackIsCorrect = readbackIsCorrect,
                    readbackIsReversed = readbackIsReversed,
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                )

                Spacer(modifier = Modifier.height(8.dp))

                WordMemoryInsightsCard(
                    strings = strings,
                    language = deck.language,
                    word = currentWord,
                    srsStat = progressState.getWordSrsStat(currentWord.id, isReversed = isReversedCard),
                    isRevealed = isTranslationRevealed,
                    onSpeakSentence = { sentence ->
                        speechControllerRef?.speakEnglish(sentence)
                    },
                    onRevealHint = handleToggleHint,
                    compact = true,
                    showSentence = configuration.screenHeightDp >= 640,
                    isReversed = isReversedCard
                )

                Spacer(modifier = Modifier.height(8.dp))

                BottomControlBar(
                    strings = strings,
                    isListening = isListening,
                    micAutoEnabled = micAutoEnabled,
                    rmsLevelProvider = { rmsLevel },
                    animateMicWaves = isMicVisualActive,
                    enabled = isCardInteractive,
                    onSwipeDontKnow = {
                        triggerCardResolution(
                            isCorrect = false,
                            method = AnswerMethod.SWIPED_DONT_KNOW
                        )
                    },
                    onToggleMic = handleToggleMic,
                    onSwipeKnow = {
                        triggerCardResolution(
                            isCorrect = true,
                            method = AnswerMethod.SWIPED_KNOW
                        )
                    }
                )
            }
        }
    }
}

private fun maskTargetWordInSentence(sentence: String, target: String): Pair<String, Boolean> {
    if (sentence.isBlank() || target.isBlank()) return sentence to false
    val cleanedTarget = target
        .replace(Regex("\\s*[–-]\\s*.*"), "")
        .replace(Regex("\\(.*?\\)"), "")
        .trim()
    val candidateTokens = cleanedTarget
        .split('/', ',', ';', ' ')
        .map { it.trim() }
        .filter { it.length >= 3 && it.lowercase() !in setOf("the", "and", "for", "los", "las", "una") }
        .distinct()
    if (candidateTokens.isEmpty()) return sentence to false

    var masked = sentence
    var didMask = false
    for (token in candidateTokens) {
        val pattern = Regex("(?i)\\b${Regex.escape(token)}\\w*")
        if (pattern.containsMatchIn(masked)) {
            masked = pattern.replace(masked, "______")
            didMask = true
        }
    }
    return masked to didMask
}

@Composable
private fun LandscapeSessionTelemetryRow(
    strings: UiStrings,
    results: List<CardAttemptResult>,
    repeatPendingCount: Int,
    todayCompleted: Int,
    dailyGoal: Int
) {
    val answered = results.size
    val correct = results.count { it.isCorrect }
    val accuracyText = if (answered > 0) "${(correct * 100) / answered}%" else "—"
    val streak = results.asReversed().takeWhile { it.isCorrect }.count()

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        TelemetryMiniCard(
            label = if (strings.isCzech) "Přesnost" else "Accuracy",
            value = accuracyText,
            accent = if (answered == 0) AppleBlue else AppleGreen,
            modifier = Modifier.weight(1f)
        )
        TelemetryMiniCard(
            label = if (strings.isCzech) "Série" else "Streak",
            value = "$streak",
            leadingIcon = Icons.Default.LocalFireDepartment,
            accent = if (streak > 0) AppleOrange else TextMuted,
            modifier = Modifier.weight(1f)
        )
        TelemetryMiniCard(
            label = if (repeatPendingCount > 0) {
                if (strings.isCzech) "Opakování" else "To repeat"
            } else {
                if (strings.isCzech) "Dnešní cíl" else "Daily goal"
            },
            value = if (repeatPendingCount > 0) "$repeatPendingCount×" else "$todayCompleted/$dailyGoal",
            accent = if (repeatPendingCount > 0) AppleOrange else AppleIndigo,
            modifier = Modifier.weight(1f)
        )
    }
}

@Composable
private fun TelemetryMiniCard(
    label: String,
    value: String,
    accent: Color,
    modifier: Modifier = Modifier,
    leadingIcon: androidx.compose.ui.graphics.vector.ImageVector? = null
) {
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = CardSurface,
        border = BorderStroke(0.5.dp, CardBorderGray.copy(alpha = 0.6f)),
        modifier = modifier
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 9.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(3.dp)
            ) {
                if (leadingIcon != null) {
                    Icon(
                        imageVector = leadingIcon,
                        contentDescription = null,
                        tint = accent,
                        modifier = Modifier.size(15.dp)
                    )
                }
                Text(
                    text = value,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    color = TextDark
                )
            }
            Text(
                text = label,
                fontSize = 11.sp,
                color = TextMuted,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun WordMemoryInsightsCard(
    strings: UiStrings,
    language: cz.slovickaspeak.app.model.LearningLanguage,
    word: VocabWord,
    srsStat: cz.slovickaspeak.app.data.WordSrsStat?,
    isRevealed: Boolean,
    onSpeakSentence: (String) -> Unit,
    onRevealHint: () -> Unit,
    compact: Boolean = false,
    showSentence: Boolean = true,
    isReversed: Boolean = false
) {
    val boxLevel = (srsStat?.box ?: 0).coerceIn(0, 5)
    val lapses = srsStat?.lapses ?: 0
    val statusLabel = when {
        srsStat == null -> if (strings.isCzech) "Nové slovíčko" else "New word"
        boxLevel == 0 -> if (strings.isCzech) "K procvičení" else "Needs review"
        boxLevel >= 4 -> if (strings.isCzech) "V dlouhodobé paměti" else "Mastered"
        else -> if (strings.isCzech) "Upevňuje se • Box $boxLevel/5" else "Learning • Box $boxLevel/5"
    }
    val statusColor = when {
        srsStat == null -> AppleBlue
        boxLevel == 0 -> AppleRed
        boxLevel >= 4 -> AppleGreen
        else -> AppleOrange
    }

    Surface(
        shape = RoundedCornerShape(if (compact) 16.dp else 20.dp),
        color = CardSurface,
        border = BorderStroke(0.5.dp, CardBorderGray.copy(alpha = 0.6f)),
        modifier = Modifier
            .fillMaxWidth()
            .shadow(4.dp, RoundedCornerShape(if (compact) 16.dp else 20.dp), spotColor = Color.Black.copy(alpha = 0.04f))
    ) {
        Column(
            modifier = Modifier.padding(
                horizontal = if (compact) 11.dp else 14.dp,
                vertical = if (compact) 7.dp else 12.dp
            ),
            verticalArrangement = Arrangement.spacedBy(if (compact) 5.dp else 10.dp)
        ) {
            if (compact) {
                // Ultra-compact Portrait header: ikona + nazev + 5 SRS dílků v jednom řádku + štítek
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(5.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Psychology,
                            contentDescription = null,
                            tint = statusColor,
                            modifier = Modifier.size(15.dp)
                        )
                        Text(
                            text = if (strings.isCzech) "Paměť" else "Memory",
                            fontSize = 11.5.sp,
                            fontWeight = FontWeight.Bold,
                            color = TextDark
                        )
                    }

                    Row(
                        modifier = Modifier.weight(1f),
                        horizontalArrangement = Arrangement.spacedBy(3.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        for (step in 1..5) {
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .height(4.dp)
                                    .clip(RoundedCornerShape(50))
                                    .background(if (step <= boxLevel) statusColor else SurfaceBg)
                            )
                        }
                    }

                    Surface(
                        shape = RoundedCornerShape(50),
                        color = statusColor.copy(alpha = 0.13f)
                    ) {
                        Text(
                            text = statusLabel,
                            fontSize = 10.5.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = statusColor,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                        )
                    }
                }
            } else {
                // Full Landscape header: Pametovy stav + SRS indikátor
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Psychology,
                            contentDescription = null,
                            tint = statusColor,
                            modifier = Modifier.size(18.dp)
                        )
                        Text(
                            text = if (strings.isCzech) "Paměť & Kontext" else "Memory & Context",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold,
                            color = TextDark
                        )
                    }

                    Surface(
                        shape = RoundedCornerShape(50),
                        color = statusColor.copy(alpha = 0.13f)
                    ) {
                        Text(
                            text = statusLabel,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = statusColor,
                            modifier = Modifier.padding(horizontal = 9.dp, vertical = 3.dp)
                        )
                    }
                }

                // 5-segment Leitner memory progress bar + optional lapses note
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(5.dp)
                ) {
                    for (step in 1..5) {
                        val active = step <= boxLevel
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .height(6.dp)
                                .clip(RoundedCornerShape(50))
                                .background(if (active) statusColor else SurfaceBg)
                        )
                    }
                    if (lapses > 0) {
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = if (strings.isCzech) "Chyby: $lapses" else "Lapses: $lapses",
                            fontSize = 11.sp,
                            color = TextMuted
                        )
                    }
                }
            }

            // Příkladová věta: zakrytý kontext před odpovědí -> plná věta + audio po odkrytí
            if (showSentence && word.exampleSentence.isNotBlank()) {
                val (maskedSentence, didMask) = remember(word.id, word.exampleSentence, word.english) {
                    maskTargetWordInSentence(word.exampleSentence, word.english)
                }

                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = SurfaceBg,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    if (isRevealed || isReversed) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { onSpeakSentence(word.exampleSentence) }
                                .padding(
                                    horizontal = 10.dp,
                                    vertical = if (compact) 5.dp else 10.dp
                                ),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "“${word.exampleSentence}”",
                                    fontSize = if (compact) 11.5.sp else 13.sp,
                                    fontStyle = FontStyle.Italic,
                                    color = TextDark,
                                    lineHeight = if (compact) 15.sp else 18.sp,
                                    maxLines = if (compact) 1 else 4,
                                    overflow = TextOverflow.Ellipsis
                                )
                                if (!compact && (word.phonetic.isNotBlank() || word.acceptedVariants.isNotEmpty())) {
                                    Spacer(modifier = Modifier.height(4.dp))
                                    val extraInfo = buildList {
                                        if (word.phonetic.isNotBlank()) add(word.phonetic)
                                        if (word.acceptedVariants.isNotEmpty()) {
                                            val prefix = if (strings.isCzech) "Uznávané:" else "Accepted:"
                                            add("$prefix ${word.acceptedVariants.take(3).joinToString(", ")}")
                                        }
                                    }.joinToString(" • ")
                                    Text(
                                        text = extraInfo,
                                        fontSize = 11.5.sp,
                                        color = TextMuted,
                                        maxLines = 2,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                            }
                            Box(
                                modifier = Modifier
                                    .size(if (compact) 24.dp else 32.dp)
                                    .clip(CircleShape)
                                    .background(AppleBlueTint),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.AutoMirrored.Filled.VolumeUp,
                                    contentDescription = null,
                                    tint = AppleBlue,
                                    modifier = Modifier.size(if (compact) 13.dp else 17.dp)
                                )
                            }
                        }
                    } else if (didMask) {
                        if (compact) {
                            Row(
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Lightbulb,
                                    contentDescription = null,
                                    tint = AppleBlue,
                                    modifier = Modifier.size(13.dp)
                                )
                                Text(
                                    text = "“$maskedSentence”",
                                    fontSize = 11.5.sp,
                                    fontStyle = FontStyle.Italic,
                                    color = TextDark,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        } else {
                            Column(
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                                verticalArrangement = Arrangement.spacedBy(2.dp)
                            ) {
                                Text(
                                    text = if (strings.isCzech) {
                                        "Kontextová nápověda (${language.localizedTitle(true).lowercase()})"
                                    } else {
                                        "Context clue (${language.localizedTitle(false)})"
                                    },
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = AppleBlue
                                )
                                Text(
                                    text = "“$maskedSentence”",
                                    fontSize = 13.sp,
                                    fontStyle = FontStyle.Italic,
                                    color = TextDark,
                                    lineHeight = 18.sp
                                )
                            }
                        }
                    } else {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { onRevealHint() }
                                .padding(horizontal = 10.dp, vertical = if (compact) 5.dp else 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Lightbulb,
                                contentDescription = null,
                                tint = AppleOrange,
                                modifier = Modifier.size(14.dp)
                            )
                            Text(
                                text = if (strings.isCzech) {
                                    "Zobrazit překlad a příkladovou větu"
                                } else {
                                    "Reveal translation and example sentence"
                                },
                                fontSize = if (compact) 11.5.sp else 12.5.sp,
                                color = TextMuted
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun RecentAttemptsCard(
    strings: UiStrings,
    results: List<CardAttemptResult>,
    onReplayWord: (String) -> Unit,
    onSelectWord: (VocabWord) -> Unit = {}
) {
    Surface(
        shape = RoundedCornerShape(20.dp),
        color = CardSurface,
        border = BorderStroke(0.5.dp, CardBorderGray.copy(alpha = 0.6f)),
        modifier = Modifier
            .fillMaxWidth()
            .shadow(4.dp, RoundedCornerShape(20.dp), spotColor = Color.Black.copy(alpha = 0.04f))
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            RecentAttemptsPanelContent(
                strings = strings,
                results = results,
                onReplayWord = onReplayWord,
                onSelectWord = onSelectWord
            )
        }
    }
}

@Composable
private fun RecentAttemptsPanelContent(
    strings: UiStrings,
    results: List<CardAttemptResult>,
    onReplayWord: (String) -> Unit,
    onSelectWord: (VocabWord) -> Unit
) {
    val recent = results.asReversed().distinctBy { it.word.id }.take(4)
    val spokenCount = results.count { it.method == AnswerMethod.SPOKEN_CORRECT }

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Icon(
                imageVector = Icons.Default.History,
                contentDescription = null,
                tint = AppleIndigo,
                modifier = Modifier.size(17.dp)
            )
            Text(
                text = if (strings.isCzech) "Poslední odpovědi" else "Recent Answers",
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold,
                color = TextDark
            )
        }

        if (results.isNotEmpty()) {
            Text(
                text = if (strings.isCzech) "$spokenCount hlasem" else "$spokenCount spoken",
                fontSize = 11.5.sp,
                fontWeight = FontWeight.Medium,
                color = TextMuted
            )
        }
    }

    if (recent.isEmpty()) {
        Text(
            text = if (strings.isCzech) {
                "Tvé odpovědi se zobrazí zde — kliknutím na slovo se vrátíš na jeho kartu."
            } else {
                "Your recent answers will appear here — tap any word to return to its card."
            },
            fontSize = 12.sp,
            color = TextMuted,
            lineHeight = 16.sp
        )
    } else {
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            recent.forEach { item ->
                val statusColor = if (item.isCorrect) AppleGreen else AppleRed
                val statusBg = if (item.isCorrect) AppleGreenTint else AppleRedTint
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(SurfaceBg)
                        .clickable { onSelectWord(item.word) }
                        .padding(horizontal = 10.dp, vertical = 7.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(22.dp)
                            .clip(CircleShape)
                            .background(statusBg),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = if (item.isCorrect) Icons.Default.Check else Icons.Default.Close,
                            contentDescription = null,
                            tint = statusColor,
                            modifier = Modifier.size(13.dp)
                        )
                    }

                    Column(modifier = Modifier.weight(1f)) {
                        val leadText = if (item.wasReversed) item.word.english else item.word.czech
                        val tailText = if (item.wasReversed) item.word.czech else item.word.english
                        Text(
                            text = "$leadText → $tailText",
                            fontSize = 12.5.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = TextDark,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        if (!item.spokenTranscript.isNullOrBlank()) {
                            Text(
                                text = if (strings.isCzech) {
                                    "Rozpoznáno: “${item.spokenTranscript}”"
                                } else {
                                    "Heard: “${item.spokenTranscript}”"
                                },
                                fontSize = 11.sp,
                                color = TextMuted,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }

                    Box(
                        modifier = Modifier
                            .size(28.dp)
                            .clip(CircleShape)
                            .clickable { onReplayWord(item.word.english) },
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.VolumeUp,
                            contentDescription = null,
                            tint = AppleBlue,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun AppleSessionTopBar(
    strings: UiStrings,
    progressState: cz.slovickaspeak.app.data.ProgressStateHolder,
    currentIndex: Int,
    totalSessionCards: Int,
    sessionProgressFraction: Float,
    onCloseSession: () -> Unit,
    onOpenSettings: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        shape = RoundedCornerShape(20.dp),
        color = CardSurface,
        border = BorderStroke(0.5.dp, CardBorderGray.copy(alpha = 0.6f)),
        modifier = modifier
            .fillMaxWidth()
            .shadow(4.dp, RoundedCornerShape(20.dp), spotColor = Color.Black.copy(alpha = 0.04f))
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                // Close button
                Box(
                    modifier = Modifier
                        .size(32.dp)
                        .clip(CircleShape)
                        .background(SurfaceBg)
                        .clickable { onCloseSession() },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = null,
                        tint = TextMuted,
                        modifier = Modifier.size(18.dp)
                    )
                }

                Text(
                    text = strings.sessionCardProgress(
                        (currentIndex + 1).coerceAtMost(totalSessionCards),
                        totalSessionCards,
                        progressState.todayCompletedWords
                    ),
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 13.sp,
                    color = TextDark
                )

                // Settings icon button
                Box(
                    modifier = Modifier
                        .size(32.dp)
                        .clip(CircleShape)
                        .background(SurfaceBg)
                        .clickable { onOpenSettings() },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Tune,
                        contentDescription = strings.settingsAndLeaderboard,
                        tint = TextMuted,
                        modifier = Modifier.size(17.dp)
                    )
                }
            }

            // Session Progress Bar
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(10.dp)
                    .clip(RoundedCornerShape(50))
                    .background(SurfaceBg)
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxHeight()
                        .fillMaxWidth(sessionProgressFraction.coerceIn(0f, 1f))
                        .clip(RoundedCornerShape(50))
                        .background(
                            Brush.horizontalGradient(
                                colors = listOf(AppleBlue, AppleIndigo)
                            )
                        )
                )
            }
        }
    }
}

@Composable
private fun Flashcard3DStackArea(
    strings: UiStrings,
    deck: VocabDeck,
    currentWord: VocabWord,
    nextWord: VocabWord?,
    thirdWord: VocabWord?,
    offsetXProvider: () -> Float,
    cardScaleProvider: () -> Float,
    cardAlphaProvider: () -> Float,
    feedbackState: CardFeedbackState,
    showHintTranslation: Boolean,
    onToggleHint: () -> Unit,
    onPlayAudio: () -> Unit,
    autoPronounceEnabled: Boolean = false,
    onToggleAutoPronounce: () -> Unit = {},
    pauseWhileSpeaking: Boolean = true,
    onTogglePauseWhileSpeaking: () -> Unit = {},
    toggleFeedbackText: String? = null,
    onDismissToggleFeedback: () -> Unit = {},
    recentResults: List<CardAttemptResult> = emptyList(),
    showRecentWordsPanel: Boolean = false,
    onToggleRecentWordsPanel: () -> Unit = {},
    onReplayRecentWord: (String) -> Unit = {},
    onReopenRecentWord: (VocabWord) -> Unit = {},
    onDragDelta: (Float) -> Unit,
    onDragEnd: () -> Unit,
    modifier: Modifier = Modifier,
    isReversed: Boolean = false,
    readbackWord: VocabWord? = null,
    readbackIsCorrect: Boolean = true,
    readbackIsReversed: Boolean = false
) {
    Box(
        modifier = modifier,
        contentAlignment = Alignment.Center
    ) {
        // Third depth card in 3D stack (transforms purely in GPU graphicsLayer)
        if (thirdWord != null) {
            Surface(
                shape = RoundedCornerShape(34.dp),
                color = CardSurface,
                border = BorderStroke(0.5.dp, CardBorderGray.copy(alpha = 0.4f)),
                modifier = Modifier
                    .fillMaxWidth()
                    .fillMaxHeight(0.97f)
                    .graphicsLayer {
                        val prog = (abs(offsetXProvider()) / 380f).coerceIn(0f, 1f)
                        scaleX = 0.86f + 0.05f * prog
                        scaleY = 0.86f + 0.05f * prog
                        translationY = (26f - 10f * prog) * density
                        alpha = 0.45f + 0.25f * prog
                    }
            ) {}
        }

        // Second depth card in 3D stack (rises and scales up purely in GPU graphicsLayer; shadow stays outside alpha saveLayer)
        if (nextWord != null) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .fillMaxHeight(0.97f)
                    .graphicsLayer {
                        val prog = (abs(offsetXProvider()) / 380f).coerceIn(0f, 1f)
                        scaleX = 0.92f + 0.06f * prog
                        scaleY = 0.92f + 0.06f * prog
                        translationY = (14f - 12f * prog) * density
                    }
                    .softRoundedShadow(
                        cornerRadius = 34.dp,
                        maxAlpha = 0.06f,
                        spreadDp = 10.dp,
                        offsetYDp = 4.dp,
                        alphaProvider = {
                            val prog = (abs(offsetXProvider()) / 380f).coerceIn(0f, 1f)
                            0.78f + 0.22f * prog
                        }
                    )
            ) {
                Surface(
                    shape = RoundedCornerShape(34.dp),
                    color = CardSurface,
                    border = BorderStroke(0.5.dp, CardBorderGray.copy(alpha = 0.6f)),
                    shadowElevation = 0.dp,
                    modifier = Modifier
                        .fillMaxSize()
                        .graphicsLayer {
                            val prog = (abs(offsetXProvider()) / 380f).coerceIn(0f, 1f)
                            alpha = 0.78f + 0.22f * prog
                        }
                ) {}
            }
        }

        // Active Foreground Card (reads offsetX, scale, alpha ONLY in graphicsLayer)
        SwipeableVocabCard(
            strings = strings,
            language = deck.language,
            word = currentWord,
            deckLevelLabel = "${deck.language.flagEmoji} ${deck.level.label} • ${deck.localizedTitle(strings.isCzech)}",
            deckLevelColor = Color(deck.level.colorHex),
            offsetXProvider = offsetXProvider,
            scaleProvider = cardScaleProvider,
            alphaProvider = cardAlphaProvider,
            feedbackState = feedbackState,
            showHintTranslation = showHintTranslation,
            onToggleHint = onToggleHint,
            onPlayAudio = onPlayAudio,
            autoPronounceEnabled = autoPronounceEnabled,
            onToggleAutoPronounce = onToggleAutoPronounce,
            pauseWhileSpeaking = pauseWhileSpeaking,
            onTogglePauseWhileSpeaking = onTogglePauseWhileSpeaking,
            toggleFeedbackText = toggleFeedbackText,
            onDismissToggleFeedback = onDismissToggleFeedback,
            recentResults = recentResults,
            showRecentWordsPanel = showRecentWordsPanel,
            onToggleRecentWordsPanel = onToggleRecentWordsPanel,
            onReplayRecentWord = onReplayRecentWord,
            onReopenRecentWord = onReopenRecentWord,
            onDragDelta = onDragDelta,
            onDragEnd = onDragEnd,
            isReversed = isReversed
        )

        // Compact Cupertino bounce card displaying spoken word + phonetic after card exits
        // Uses pure scaleIn/scaleOut (no alpha fade layer) + explicit RoundedCornerShape shadow so shadow corners stay rounded from frame 0
        AnimatedVisibility(
            visible = readbackWord != null,
            enter = scaleIn(
                initialScale = 0.76f,
                animationSpec = spring(
                    dampingRatio = Spring.DampingRatioMediumBouncy,
                    stiffness = Spring.StiffnessMedium
                )
            ),
            exit = scaleOut(
                targetScale = 0.85f,
                animationSpec = tween(110)
            )
        ) {
            readbackWord?.let { rw ->
                WordPronounceReadbackBubble(
                    strings = strings,
                    word = rw,
                    isCorrect = readbackIsCorrect,
                    isReversed = readbackIsReversed
                )
            }
        }
    }
}

@Composable
private fun WordPronounceReadbackBubble(
    strings: UiStrings,
    word: VocabWord,
    isCorrect: Boolean,
    isReversed: Boolean
) {
    val accentColor = if (isCorrect) AppleGreen else AppleRed
    val pillBg = if (isCorrect) AppleGreenTint else AppleRedTint
    val primaryText = if (isReversed) {
        word.czech.replace(Regex("\\s*\\(.*?\\)"), "").split("/", ";", ",").firstOrNull()?.trim().orEmpty().ifEmpty { word.czech }
    } else {
        word.english
    }
    val secondaryText = if (isReversed) word.english else word.czech
    val statusBadge = when {
        isCorrect && strings.isCzech -> "Výslovnost"
        isCorrect -> "Pronunciation"
        strings.isCzech -> "Zapamatuj si"
        else -> "Remember this"
    }

    Surface(
        shape = RoundedCornerShape(26.dp),
        color = CardElevatedSurface,
        border = BorderStroke(1.dp, accentColor.copy(alpha = 0.35f)),
        shadowElevation = 0.dp,
        modifier = Modifier
            .fillMaxWidth(0.84f)
            .padding(horizontal = 8.dp)
            .softRoundedShadow(cornerRadius = 26.dp, maxAlpha = 0.16f, spreadDp = 18.dp, offsetYDp = 7.dp)
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 22.dp, vertical = 20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Surface(
                shape = RoundedCornerShape(50),
                color = pillBg
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 5.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(5.dp)
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.VolumeUp,
                        contentDescription = null,
                        tint = accentColor,
                        modifier = Modifier.size(14.dp)
                    )
                    Text(
                        text = statusBadge,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        color = accentColor
                    )
                }
            }

            Text(
                text = primaryText,
                fontSize = 26.sp,
                fontWeight = FontWeight.Bold,
                color = TextDark,
                textAlign = TextAlign.Center,
                lineHeight = 31.sp
            )

            if (word.phonetic.isNotBlank()) {
                Text(
                    text = word.phonetic,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = AppleBlue,
                    textAlign = TextAlign.Center
                )
            }

            if (secondaryText.isNotBlank()) {
                Text(
                    text = secondaryText,
                    fontSize = 13.5.sp,
                    fontWeight = FontWeight.Medium,
                    color = TextMuted,
                    textAlign = TextAlign.Center,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

@Composable
private fun AudioWaveVisualizer(
    color: Color,
    rmsLevel: Float = 0f,
    rmsLevelProvider: () -> Float = { rmsLevel },
    animateWaves: Boolean = true,
    modifier: Modifier = Modifier
) {
    // Store State<Float> objects without reading .value during composition -> Canvas draws on GPU with zero layout/recomposition
    val h1State: State<Float>
    val h2State: State<Float>
    val h3State: State<Float>
    val h4State: State<Float>
    val h5State: State<Float>

    if (animateWaves) {
        val infiniteTransition = rememberInfiniteTransition(label = "audioWaveBars")
        h1State = infiniteTransition.animateFloat(
            initialValue = 4f, targetValue = 13f,
            animationSpec = infiniteRepeatable(tween(420, easing = FastOutSlowInEasing), RepeatMode.Reverse),
            label = "h1"
        )
        h2State = infiniteTransition.animateFloat(
            initialValue = 14f, targetValue = 5f,
            animationSpec = infiniteRepeatable(tween(360, easing = FastOutSlowInEasing), RepeatMode.Reverse),
            label = "h2"
        )
        h3State = infiniteTransition.animateFloat(
            initialValue = 6f, targetValue = 16f,
            animationSpec = infiniteRepeatable(tween(480, easing = FastOutSlowInEasing), RepeatMode.Reverse),
            label = "h3"
        )
        h4State = infiniteTransition.animateFloat(
            initialValue = 13f, targetValue = 6f,
            animationSpec = infiniteRepeatable(tween(400, easing = FastOutSlowInEasing), RepeatMode.Reverse),
            label = "h4"
        )
        h5State = infiniteTransition.animateFloat(
            initialValue = 5f, targetValue = 11f,
            animationSpec = infiniteRepeatable(tween(450, easing = FastOutSlowInEasing), RepeatMode.Reverse),
            label = "h5"
        )
    } else {
        h1State = remember { mutableFloatStateOf(5f) }
        h2State = remember { mutableFloatStateOf(8f) }
        h3State = remember { mutableFloatStateOf(11f) }
        h4State = remember { mutableFloatStateOf(8f) }
        h5State = remember { mutableFloatStateOf(5f) }
    }

    val boostAnim = remember { Animatable(0f) }
    LaunchedEffect(animateWaves, rmsLevelProvider) {
        if (!animateWaves) {
            boostAnim.snapTo(0f)
        } else {
            snapshotFlow { (rmsLevelProvider() * 8.0f).coerceIn(0f, 9f) }
                .collectLatest { targetBoost ->
                    val duration = if (targetBoost > boostAnim.value) 150 else 280
                    boostAnim.animateTo(
                        targetValue = targetBoost,
                        animationSpec = tween(durationMillis = duration, easing = FastOutSlowInEasing)
                    )
                }
        }
    }

    Canvas(
        modifier = modifier
            .width(27.dp)
            .height(18.dp)
    ) {
        val barWidthPx = 3.dp.toPx()
        val gapPx = 3.dp.toPx()
        val corner = CornerRadius(barWidthPx / 2f, barWidthPx / 2f)
        val boost = boostAnim.value
        val maxH = size.height
        val heights = floatArrayOf(h1State.value, h2State.value, h3State.value, h4State.value, h5State.value)
        for (i in heights.indices) {
            val hPx = (heights[i] + boost).coerceIn(4f, 18f).dp.toPx().coerceAtMost(maxH)
            val x = i * (barWidthPx + gapPx)
            val y = (maxH - hPx) * 0.5f
            drawRoundRect(
                color = color,
                topLeft = Offset(x, y),
                size = Size(barWidthPx, hPx),
                cornerRadius = corner
            )
        }
    }
}

@Composable
private fun LiveSpeechBanner(
    strings: UiStrings,
    language: cz.slovickaspeak.app.model.LearningLanguage,
    isListening: Boolean,
    micAutoEnabled: Boolean,
    lastHeardText: String?,
    feedbackState: CardFeedbackState,
    speechError: String?,
    rmsLevel: Float = 0f,
    rmsLevelProvider: () -> Float = { rmsLevel },
    animateMicWaves: Boolean = micAutoEnabled,
    expectCzech: Boolean = false
) {
    val bgColor by animateColorAsState(
        targetValue = when (feedbackState) {
            CardFeedbackState.SPOKEN_CORRECT, CardFeedbackState.SWIPED_CORRECT -> AppleGreenTint
            CardFeedbackState.SWIPED_WRONG, CardFeedbackState.SPOKEN_WRONG_FLASH -> AppleRedTint
            CardFeedbackState.NEUTRAL -> CardSurface
        },
        label = "bannerBg"
    )

    val dotColor = when (feedbackState) {
        CardFeedbackState.SPOKEN_CORRECT, CardFeedbackState.SWIPED_CORRECT -> AppleGreen
        CardFeedbackState.SWIPED_WRONG, CardFeedbackState.SPOKEN_WRONG_FLASH -> AppleRed
        CardFeedbackState.NEUTRAL -> if (micAutoEnabled) AppleGreen else TextMuted
    }

    Surface(
        shape = RoundedCornerShape(50),
        color = bgColor,
        border = BorderStroke(0.5.dp, CardBorderGray.copy(alpha = 0.6f)),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 9.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center
        ) {
            Box(
                modifier = Modifier
                    .size(8.dp)
                    .clip(CircleShape)
                    .background(dotColor)
            )

            // Dynamic Audio Waveform (Canvas GPU draw phase with zero measure/layout churn)
            if (micAutoEnabled && isListening && feedbackState == CardFeedbackState.NEUTRAL) {
                Spacer(modifier = Modifier.width(8.dp))
                AudioWaveVisualizer(
                    color = dotColor,
                    rmsLevelProvider = rmsLevelProvider,
                    animateWaves = animateMicWaves
                )
            }

            Spacer(modifier = Modifier.width(8.dp))

            val statusText = when {
                feedbackState == CardFeedbackState.SPOKEN_CORRECT ->
                    strings.bannerSpokenCorrect(lastHeardText ?: "")
                feedbackState == CardFeedbackState.SPOKEN_WRONG_FLASH ->
                    strings.bannerSpokenWrong(lastHeardText ?: "")
                feedbackState == CardFeedbackState.SWIPED_CORRECT ->
                    strings.bannerMarkedKnow
                feedbackState == CardFeedbackState.SWIPED_WRONG ->
                    strings.bannerMarkedDontKnow
                speechError != null -> speechError
                !micAutoEnabled ->
                    strings.bannerMicOff
                !lastHeardText.isNullOrBlank() ->
                    strings.bannerHeard(lastHeardText)
                isListening ->
                    if (expectCzech) strings.bannerListeningCzech else strings.bannerListeningForLanguage(language)
                else ->
                    strings.bannerMicReady
            }

            Text(
                text = statusText,
                fontSize = 13.sp,
                fontWeight = FontWeight.Medium,
                color = TextDark,
                textAlign = TextAlign.Center
            )
        }
    }
}

private fun Modifier.softRoundedShadow(
    cornerRadius: Dp,
    maxAlpha: Float = 0.11f,
    spreadDp: Dp = 16.dp,
    offsetYDp: Dp = 6.dp,
    layers: Int = 6,
    alphaProvider: () -> Float = { 1f }
): Modifier = drawBehind {
    val externalAlpha = alphaProvider().coerceIn(0f, 1f)
    if (externalAlpha <= 0.001f) return@drawBehind
    val baseRadius = cornerRadius.toPx()
    val maxSpread = spreadDp.toPx()
    val offsetY = offsetYDp.toPx()
    for (i in layers downTo 1) {
        val fraction = i.toFloat() / layers.toFloat()
        val extra = maxSpread * fraction
        val layerAlpha = ((maxAlpha / layers) * (1.6f - fraction * 0.85f) * externalAlpha)
        drawRoundRect(
            color = Color.Black.copy(alpha = layerAlpha.coerceIn(0f, maxAlpha)),
            topLeft = Offset(-extra * 0.5f, -extra * 0.3f + offsetY * fraction),
            size = Size(size.width + extra, size.height + extra),
            cornerRadius = CornerRadius(baseRadius + extra * 0.5f, baseRadius + extra * 0.5f)
        )
    }
}

@Composable
private fun CardCornerToggleIcon(
    isEnabled: Boolean,
    enabledIcon: androidx.compose.ui.graphics.vector.ImageVector,
    disabledIcon: androidx.compose.ui.graphics.vector.ImageVector,
    contentDescription: String,
    onClick: () -> Unit
) {
    Surface(
        shape = CircleShape,
        color = if (isEnabled) AppleBlue.copy(alpha = 0.14f) else SurfaceBg,
        border = BorderStroke(
            width = if (isEnabled) 1.2.dp else 0.5.dp,
            color = if (isEnabled) AppleBlue else CardBorderGray.copy(alpha = 0.6f)
        ),
        modifier = Modifier
            .size(34.dp)
            .clip(CircleShape)
            .clickable(onClick = onClick)
    ) {
        Box(contentAlignment = Alignment.Center) {
            Icon(
                imageVector = if (isEnabled) enabledIcon else disabledIcon,
                contentDescription = contentDescription,
                tint = if (isEnabled) AppleBlue else TextMuted,
                modifier = Modifier.size(20.dp)
            )
        }
    }
}

@Composable
private fun SwipeableVocabCard(
    strings: UiStrings,
    language: cz.slovickaspeak.app.model.LearningLanguage,
    word: VocabWord,
    deckLevelLabel: String,
    deckLevelColor: Color,
    offsetXProvider: () -> Float,
    scaleProvider: () -> Float,
    alphaProvider: () -> Float,
    feedbackState: CardFeedbackState,
    showHintTranslation: Boolean,
    onToggleHint: () -> Unit,
    onPlayAudio: () -> Unit,
    autoPronounceEnabled: Boolean = false,
    onToggleAutoPronounce: () -> Unit = {},
    pauseWhileSpeaking: Boolean = true,
    onTogglePauseWhileSpeaking: () -> Unit = {},
    toggleFeedbackText: String? = null,
    onDismissToggleFeedback: () -> Unit = {},
    recentResults: List<CardAttemptResult> = emptyList(),
    showRecentWordsPanel: Boolean = false,
    onToggleRecentWordsPanel: () -> Unit = {},
    onReplayRecentWord: (String) -> Unit = {},
    onReopenRecentWord: (VocabWord) -> Unit = {},
    onDragDelta: (Float) -> Unit,
    onDragEnd: () -> Unit,
    isReversed: Boolean = false
) {
    // Derived states so swipe badges only recompose when crossing the 75px threshold, NOT every frame!
    val showLeftSwipeBadge by remember {
        derivedStateOf { offsetXProvider() < -75f }
    }
    val showRightSwipeBadge by remember {
        derivedStateOf { offsetXProvider() > 75f }
    }

    val cardBg by animateColorAsState(
        targetValue = when (feedbackState) {
            CardFeedbackState.SPOKEN_CORRECT, CardFeedbackState.SWIPED_CORRECT -> AppleGreenTint
            CardFeedbackState.SWIPED_WRONG, CardFeedbackState.SPOKEN_WRONG_FLASH -> AppleRedTint
            CardFeedbackState.NEUTRAL -> CardSurface
        },
        animationSpec = tween(160),
        label = "cardBg"
    )

    val cardBorder by animateColorAsState(
        targetValue = when {
            feedbackState == CardFeedbackState.SPOKEN_CORRECT ||
                    feedbackState == CardFeedbackState.SWIPED_CORRECT ||
                    showRightSwipeBadge -> AppleGreen
            feedbackState == CardFeedbackState.SWIPED_WRONG ||
                    feedbackState == CardFeedbackState.SPOKEN_WRONG_FLASH ||
                    showLeftSwipeBadge -> AppleRed
            else -> CardBorderGray.copy(alpha = 0.55f)
        },
        animationSpec = tween(160),
        label = "cardBorder"
    )

    // Draw shadow outside the alpha graphicsLayer so saveLayer never clips shadow spread to a sharp rectangle
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .fillMaxHeight(0.97f)
            .graphicsLayer {
                val x = offsetXProvider()
                val s = scaleProvider()
                translationX = x
                translationY = -abs(x) * 0.035f
                rotationZ = (x / 52f).coerceIn(-12f, 12f)
                scaleX = s
                scaleY = s
            }
            .softRoundedShadow(
                cornerRadius = 34.dp,
                maxAlpha = 0.11f,
                spreadDp = 16.dp,
                offsetYDp = 6.dp,
                alphaProvider = alphaProvider
            )
            .pointerInput(word.id) {
                detectHorizontalDragGestures(
                    onDragEnd = { onDragEnd() },
                    onDragCancel = { onDragEnd() },
                    onHorizontalDrag = { change, dragAmount ->
                        change.consume()
                        onDragDelta(dragAmount)
                    }
                )
            }
    ) {
        Surface(
            shape = RoundedCornerShape(34.dp),
            color = cardBg,
            border = BorderStroke(
                if (feedbackState == CardFeedbackState.NEUTRAL && !showLeftSwipeBadge && !showRightSwipeBadge) 1.dp else 2.5.dp,
                cardBorder
            ),
            shadowElevation = 0.dp,
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer {
                    alpha = alphaProvider()
                }
        ) {
        BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
            val isUltraCompactCard = maxHeight < 290.dp
            val isCompactCard = maxHeight < 350.dp
            val cardVertPadding = when {
                isUltraCompactCard -> 10.dp
                isCompactCard -> 12.dp
                else -> 14.dp
            }
            val emojiBoxSize = when {
                isUltraCompactCard -> 50.dp
                isCompactCard -> 64.dp
                else -> 84.dp
            }
            val emojiFontSize = when {
                isUltraCompactCard -> 27.sp
                isCompactCard -> 34.sp
                else -> 44.sp
            }
            val hintSlotMinHeight = when {
                isUltraCompactCard -> 46.dp
                isCompactCard -> 54.dp
                else -> 64.dp
            }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 18.dp, vertical = cardVertPadding),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            // Top Row: Left/Right Swipe Badges + Word Display & Auto-Pronounce Corner Toggles
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(modifier = Modifier.weight(1f)) {
                    if (showLeftSwipeBadge ||
                        feedbackState == CardFeedbackState.SWIPED_WRONG ||
                        feedbackState == CardFeedbackState.SPOKEN_WRONG_FLASH
                    ) {
                        Surface(
                            color = AppleRed,
                            shape = RoundedCornerShape(50)
                        ) {
                            Text(
                                text = if (feedbackState == CardFeedbackState.SPOKEN_WRONG_FLASH) {
                                    strings.badgeTryAgain
                                } else {
                                    strings.badgeDontKnow
                                },
                                color = Color.White,
                                fontWeight = FontWeight.Bold,
                                fontSize = 12.sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 5.dp)
                            )
                        }
                    } else {
                        Surface(
                            color = deckLevelColor.copy(alpha = 0.12f),
                            shape = RoundedCornerShape(50)
                        ) {
                            Text(
                                text = deckLevelLabel,
                                color = deckLevelColor,
                                fontWeight = FontWeight.Bold,
                                fontSize = 11.sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.padding(horizontal = 11.dp, vertical = 4.dp)
                            )
                        }
                    }
                }

                if (showRightSwipeBadge ||
                    feedbackState == CardFeedbackState.SPOKEN_CORRECT ||
                    feedbackState == CardFeedbackState.SWIPED_CORRECT
                ) {
                    Surface(
                        color = AppleGreen,
                        shape = RoundedCornerShape(50)
                    ) {
                        Text(
                            text = if (feedbackState == CardFeedbackState.SPOKEN_CORRECT) {
                                strings.badgeSpoken
                            } else {
                                strings.badgeKnow
                            },
                            color = Color.White,
                            fontWeight = FontWeight.Bold,
                            fontSize = 12.sp,
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 5.dp)
                        )
                    }
                } else {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        CardCornerToggleIcon(
                            isEnabled = pauseWhileSpeaking,
                            enabledIcon = Icons.Default.Visibility,
                            disabledIcon = Icons.Default.VisibilityOff,
                            contentDescription = if (strings.isCzech) {
                                "Zobrazit slovo mezi kartami"
                            } else {
                                "Show word readback between cards"
                            },
                            onClick = onTogglePauseWhileSpeaking
                        )
                        CardCornerToggleIcon(
                            isEnabled = autoPronounceEnabled,
                            enabledIcon = Icons.AutoMirrored.Filled.VolumeUp,
                            disabledIcon = Icons.AutoMirrored.Filled.VolumeOff,
                            contentDescription = if (strings.isCzech) {
                                "Automaticky zopakovat výslovnost"
                            } else {
                                "Repeat pronunciation automatically"
                            },
                            onClick = onToggleAutoPronounce
                        )
                        CardCornerToggleIcon(
                            isEnabled = !showRecentWordsPanel,
                            enabledIcon = Icons.Default.History,
                            disabledIcon = Icons.Default.History,
                            contentDescription = if (strings.isCzech) {
                                "Předchozí slova"
                            } else {
                                "Recent words"
                            },
                            onClick = onToggleRecentWordsPanel
                        )
                    }
                }
            }

            // 2. Middle Content (Weighted + height-adaptive so Czech prompt is NEVER clipped)
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .padding(vertical = 2.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                val emojiCorner = if (isUltraCompactCard) 18.dp else 24.dp
                Surface(
                    shape = RoundedCornerShape(emojiCorner),
                    color = SurfaceBg,
                    border = BorderStroke(1.dp, CardBorderGray.copy(alpha = 0.45f)),
                    shadowElevation = 0.dp,
                    modifier = Modifier
                        .size(emojiBoxSize)
                        .softRoundedShadow(
                            cornerRadius = emojiCorner,
                            maxAlpha = 0.08f,
                            spreadDp = 8.dp,
                            offsetYDp = 3.dp
                        )
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(
                                Brush.radialGradient(
                                    colors = listOf(CardElevatedSurface, SurfaceBg)
                                )
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = word.emoji,
                            fontSize = emojiFontSize
                        )
                    }
                }

                Spacer(modifier = Modifier.height(if (isUltraCompactCard) 5.dp else 8.dp))

                Text(
                    text = if (isReversed) strings.cardPromptSpeakCzech else strings.cardPromptSpeakLanguage(language),
                    fontSize = if (isUltraCompactCard) 10.sp else 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = AppleBlue,
                    letterSpacing = 1.1.sp
                )

                Spacer(modifier = Modifier.height(if (isUltraCompactCard) 2.dp else 4.dp))

                val frontWordText = if (isReversed) word.english else word.czech
                Text(
                    text = frontWordText,
                    fontSize = when {
                        isUltraCompactCard && frontWordText.length > 22 -> 19.sp
                        isUltraCompactCard -> 22.sp
                        frontWordText.length > 34 -> 20.sp
                        frontWordText.length > 22 -> 23.sp
                        else -> 26.sp
                    },
                    fontWeight = FontWeight.ExtraBold,
                    color = TextDark,
                    textAlign = TextAlign.Center,
                    lineHeight = when {
                        isUltraCompactCard -> 25.sp
                        frontWordText.length > 34 -> 25.sp
                        frontWordText.length > 22 -> 28.sp
                        else -> 32.sp
                    },
                    letterSpacing = (-0.4).sp,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(horizontal = 8.dp)
                )
                if (isReversed && word.phonetic.isNotEmpty() && !isUltraCompactCard) {
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = word.phonetic,
                        fontSize = 12.5.sp,
                        color = TextMuted
                    )
                }
            }

            // 3. Dedicated Bottom Slot: either Hint pill OR Translation card
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = hintSlotMinHeight),
                contentAlignment = Alignment.Center
            ) {
                androidx.compose.animation.AnimatedVisibility(
                    visible = !showHintTranslation,
                    enter = fadeIn(tween(150)) + scaleIn(initialScale = 0.92f, animationSpec = tween(150)),
                    exit = fadeOut(tween(120)) + scaleOut(targetScale = 0.92f, animationSpec = tween(120))
                ) {
                    Surface(
                        shape = RoundedCornerShape(50),
                        color = SurfaceBg,
                        border = BorderStroke(0.5.dp, CardBorderGray.copy(alpha = 0.6f)),
                        modifier = Modifier
                            .clip(RoundedCornerShape(50))
                            .clickable(onClick = onToggleHint)
                    ) {
                        Row(
                            modifier = Modifier.padding(
                                horizontal = 18.dp,
                                vertical = if (isUltraCompactCard) 8.dp else 10.dp
                            ),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(7.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Lightbulb,
                                contentDescription = null,
                                tint = AppleOrange,
                                modifier = Modifier.size(16.dp)
                            )
                            Text(
                                text = strings.showHintButton,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = TextDark
                            )
                        }
                    }
                }

                androidx.compose.animation.AnimatedVisibility(
                    visible = showHintTranslation,
                    enter = fadeIn(tween(180)) + scaleIn(initialScale = 0.94f, animationSpec = tween(180, easing = AppleGlideEasing)),
                    exit = fadeOut(tween(130)) + scaleOut(targetScale = 0.94f, animationSpec = tween(130))
                ) {
                    Surface(
                        shape = RoundedCornerShape(20.dp),
                        color = SurfaceBg,
                        border = BorderStroke(0.5.dp, CardBorderGray),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier.padding(
                                horizontal = 14.dp,
                                vertical = if (isCompactCard) 8.dp else 12.dp
                            ),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(2.dp)
                        ) {
                            val revealedText = if (isReversed) word.czech else word.english
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally)
                            ) {
                                Text(
                                    text = revealedText,
                                    fontSize = when {
                                        revealedText.length > 26 -> 18.sp
                                        isCompactCard || revealedText.length > 18 -> 20.sp
                                        else -> 24.sp
                                    },
                                    lineHeight = when {
                                        revealedText.length > 26 -> 22.sp
                                        isCompactCard || revealedText.length > 18 -> 24.sp
                                        else -> 28.sp
                                    },
                                    fontWeight = FontWeight.Bold,
                                    color = AppleBlue,
                                    textAlign = TextAlign.Center,
                                    modifier = Modifier.weight(1f, fill = false)
                                )
                                if (!isReversed) {
                                    IconButton(
                                        onClick = onPlayAudio,
                                        modifier = Modifier.size(26.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.AutoMirrored.Filled.VolumeUp,
                                            contentDescription = null,
                                            tint = AppleBlue
                                        )
                                    }
                                }
                            }
                            if (!isReversed && word.phonetic.isNotEmpty()) {
                                Text(
                                    text = word.phonetic,
                                    fontSize = if (isCompactCard) 12.5.sp else 14.sp,
                                    color = TextMuted
                                )
                            }
                            if (!isCompactCard && word.exampleSentence.isNotEmpty()) {
                                Text(
                                    text = "“${word.exampleSentence}”",
                                    fontSize = 12.sp,
                                    fontStyle = FontStyle.Italic,
                                    color = TextMuted,
                                    textAlign = TextAlign.Center,
                                    maxLines = 2
                                )
                            }
                        }
                    }
                }
            }
        }

            if (showRecentWordsPanel) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                            onClick = onToggleRecentWordsPanel
                        )
                )
            }

            AnimatedVisibility(
                visible = showRecentWordsPanel,
                enter = scaleIn(
                    initialScale = 0.92f,
                    animationSpec = spring(
                        dampingRatio = 0.78f,
                        stiffness = Spring.StiffnessMedium
                    )
                ),
                exit = scaleOut(
                    targetScale = 0.94f,
                    animationSpec = tween(120)
                ),
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(top = cardVertPadding + 42.dp, start = 14.dp, end = 14.dp)
            ) {
                Surface(
                    shape = RoundedCornerShape(20.dp),
                    color = CardElevatedSurface,
                    border = BorderStroke(0.5.dp, CardBorderGray.copy(alpha = 0.85f)),
                    shadowElevation = 0.dp,
                    modifier = Modifier
                        .fillMaxWidth()
                        .softRoundedShadow(
                            cornerRadius = 20.dp,
                            maxAlpha = 0.14f,
                            spreadDp = 12.dp,
                            offsetYDp = 5.dp
                        )
                ) {
                    Column(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        RecentAttemptsPanelContent(
                            strings = strings,
                            results = recentResults,
                            onReplayWord = onReplayRecentWord,
                            onSelectWord = onReopenRecentWord
                        )
                    }
                }
            }

            AnimatedVisibility(
                visible = !showRecentWordsPanel && !toggleFeedbackText.isNullOrBlank(),
                enter = scaleIn(
                    initialScale = 0.90f,
                    animationSpec = spring(
                        dampingRatio = 0.72f,
                        stiffness = Spring.StiffnessMedium
                    )
                ),
                exit = scaleOut(
                    targetScale = 0.92f,
                    animationSpec = tween(120)
                ),
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(top = cardVertPadding + 42.dp, start = 14.dp, end = 14.dp)
            ) {
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = CardElevatedSurface,
                    border = BorderStroke(0.8.dp, AppleBlue.copy(alpha = 0.35f)),
                    shadowElevation = 0.dp,
                    modifier = Modifier
                        .softRoundedShadow(
                            cornerRadius = 16.dp,
                            maxAlpha = 0.12f,
                            spreadDp = 10.dp,
                            offsetYDp = 4.dp
                        )
                        .clickable { onDismissToggleFeedback() }
                ) {
                    Text(
                        text = toggleFeedbackText ?: "",
                        fontSize = 12.5.sp,
                        fontWeight = FontWeight.Medium,
                        color = TextDark,
                        lineHeight = 17.sp,
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 9.dp)
                    )
                }
            }
        }
        }
    }
}

@Composable
private fun BottomControlBar(
    strings: UiStrings,
    isListening: Boolean,
    micAutoEnabled: Boolean,
    rmsLevel: Float = 0f,
    rmsLevelProvider: () -> Float = { rmsLevel },
    animateMicWaves: Boolean = micAutoEnabled,
    enabled: Boolean,
    onSwipeDontKnow: () -> Unit,
    onToggleMic: () -> Unit,
    onSwipeKnow: () -> Unit
) {
    // Store animated states without reading .value during composition -> zero per-frame recomposition!
    val smoothRmsAnim = remember { Animatable(0f) }
    LaunchedEffect(animateMicWaves, rmsLevelProvider) {
        if (!animateMicWaves) {
            smoothRmsAnim.snapTo(0f)
        } else {
            snapshotFlow { rmsLevelProvider().coerceIn(0f, 1f) }
                .collectLatest { target ->
                    val duration = if (target > smoothRmsAnim.value) 170 else 340
                    smoothRmsAnim.animateTo(
                        targetValue = target,
                        animationSpec = tween(durationMillis = duration, easing = FastOutSlowInEasing)
                    )
                }
        }
    }

    val isVoiceActive by remember(animateMicWaves) {
        derivedStateOf { animateMicWaves && rmsLevelProvider() > 0.10f }
    }
    val voicePulseScaleState = animateFloatAsState(
        targetValue = if (isVoiceActive) 1.11f else 1.0f,
        animationSpec = spring(
            dampingRatio = 0.80f,
            stiffness = 175f
        ),
        label = "voicePulseScale"
    )
    val iconVoiceScaleState = animateFloatAsState(
        targetValue = if (isVoiceActive) 1.05f else 1.0f,
        animationSpec = spring(
            dampingRatio = 0.78f,
            stiffness = 185f
        ),
        label = "iconVoiceScale"
    )

    // Paired closely-spaced circular ripples triggered ONLY during active speech (0 CPU when quiet)
    val voiceWaveProgress = remember { Animatable(0f) }
    LaunchedEffect(animateMicWaves) {
        if (!animateMicWaves) {
            voiceWaveProgress.snapTo(0f)
            return@LaunchedEffect
        }
        snapshotFlow { isVoiceActive || smoothRmsAnim.value > 0.02f || voicePulseScaleState.value > 1.005f }
            .collectLatest { speaking ->
                if (speaking) {
                    voiceWaveProgress.snapTo(0f)
                    var cycleBase = 0f
                    while (true) {
                        voiceWaveProgress.animateTo(
                            targetValue = cycleBase + 1f,
                            animationSpec = tween(durationMillis = 1050, easing = LinearEasing)
                        )
                        cycleBase += 1f
                    }
                } else {
                    voiceWaveProgress.snapTo(0f)
                }
            }
    }

    val breathScaleState: State<Float>
    val wave1State: State<Float>
    val wave2State: State<Float>
    if (animateMicWaves) {
        val infiniteTransition = rememberInfiniteTransition(label = "micBreathing")
        breathScaleState = infiniteTransition.animateFloat(
            initialValue = 1.0f,
            targetValue = 1.04f,
            animationSpec = infiniteRepeatable(
                animation = tween(durationMillis = 1600, easing = FastOutSlowInEasing),
                repeatMode = RepeatMode.Reverse
            ),
            label = "breathScale"
        )
        wave1State = infiniteTransition.animateFloat(
            initialValue = 0f,
            targetValue = 1f,
            animationSpec = infiniteRepeatable(
                animation = tween(durationMillis = 2200, easing = LinearOutSlowInEasing),
                repeatMode = RepeatMode.Restart
            ),
            label = "micWave1"
        )
        wave2State = infiniteTransition.animateFloat(
            initialValue = 0f,
            targetValue = 1f,
            animationSpec = infiniteRepeatable(
                animation = tween(durationMillis = 2200, easing = LinearOutSlowInEasing),
                repeatMode = RepeatMode.Restart,
                initialStartOffset = StartOffset(1100)
            ),
            label = "micWave2"
        )
    } else {
        breathScaleState = remember { mutableFloatStateOf(1.0f) }
        wave1State = remember { mutableFloatStateOf(0f) }
        wave2State = remember { mutableFloatStateOf(0f) }
    }

    val activeMicBrush = remember(AppleBlue, AppleIndigo) {
        Brush.linearGradient(listOf(AppleBlue, AppleIndigo))
    }
    val inactiveMicBrush = remember {
        Brush.linearGradient(listOf(Color(0xFF636366), Color(0xFF48484A)))
    }

    Surface(
        shape = RoundedCornerShape(40.dp),
        color = CardSurface,
        border = BorderStroke(0.5.dp, CardBorderGray.copy(alpha = 0.6f)),
        modifier = Modifier
            .fillMaxWidth()
            .shadow(10.dp, RoundedCornerShape(40.dp), spotColor = Color.Black.copy(alpha = 0.06f))
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp, vertical = 13.dp),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Left Button: Nevím (✕) - schová se při zapnutém mikrofonu, naskočí s bounce efektem při vypnutí
            AnimatedVisibility(
                visible = !micAutoEnabled,
                enter = slideInHorizontally(
                    initialOffsetX = { it },
                    animationSpec = spring(dampingRatio = 0.52f, stiffness = Spring.StiffnessMediumLow)
                ) + scaleIn(
                    initialScale = 0.25f,
                    animationSpec = spring(dampingRatio = 0.52f, stiffness = Spring.StiffnessMediumLow)
                ) + fadeIn(tween(180)),
                exit = slideOutHorizontally(
                    targetOffsetX = { it },
                    animationSpec = tween(160)
                ) + scaleOut(targetScale = 0.25f, animationSpec = tween(160)) + fadeOut(tween(130))
            ) {
                Box(
                    modifier = Modifier
                        .padding(end = 24.dp)
                        .size(64.dp)
                        .shadow(6.dp, CircleShape, spotColor = AppleRed.copy(alpha = 0.18f))
                        .clip(CircleShape)
                        .background(AppleRedTint)
                        .clickable(enabled = enabled, onClick = onSwipeDontKnow),
                    contentAlignment = Alignment.Center
                ) {
                    Surface(
                        shape = CircleShape,
                        color = CardElevatedSurface,
                        border = BorderStroke(1.2.dp, AppleRed.copy(alpha = 0.55f)),
                        modifier = Modifier.size(52.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = Icons.Default.Close,
                                contentDescription = strings.badgeDontKnow,
                                tint = AppleRed.copy(alpha = 0.88f),
                                modifier = Modifier.size(24.dp)
                            )
                        }
                    }
                }
            }

            // Center Circular Microphone Button with circular outward fading ripples that expand further on voice
            Box(
                modifier = Modifier
                    .size(96.dp)
                    .drawBehind {
                        if (animateMicWaves) {
                            val buttonRadius = 32.dp.toPx()
                            val voice = smoothRmsAnim.value
                            val pulseNorm = ((voicePulseScaleState.value - 1f) / 0.11f).coerceIn(0f, 1f)
                            val energy = maxOf(voice, pulseNorm * 0.8f).coerceIn(0f, 1f)
                            val strokePx = 1.5.dp.toPx()

                            // Calm idle breathing waves fade down smoothly when active paired speech waves take over
                            val idleMask = (1f - energy * 0.85f).coerceIn(0f, 1f)
                            if (idleMask > 0.02f) {
                                val idleExtra = 15.dp.toPx()
                                val idleIntensity = 0.28f * idleMask
                                val p1 = wave1State.value
                                val p2 = wave2State.value
                                for (i in 0..1) {
                                    val p = if (i == 0) p1 else p2
                                    val fade = (1f - p) * (1f - p)
                                    val r = buttonRadius * 0.90f + (buttonRadius * 0.10f + idleExtra) * p
                                    val a = (idleIntensity * fade).coerceIn(0f, 1f)
                                    drawCircle(color = AppleBlue.copy(alpha = a * 0.45f), radius = r)
                                    drawCircle(
                                        color = AppleIndigo.copy(alpha = a * 0.65f),
                                        radius = r,
                                        style = Stroke(width = strokePx)
                                    )
                                }
                            }

                            // Two concentric circular waves closely following one behind the other during speech
                            if (energy > 0.01f) {
                                val t = voiceWaveProgress.value
                                val voiceExtra = (20f + 8f * energy).dp.toPx()
                                val voiceIntensity = 0.34f * energy
                                val phases = if (t >= 0.24f) {
                                    floatArrayOf(t % 1f, (t - 0.24f) % 1f)
                                } else {
                                    floatArrayOf(t)
                                }
                                for (rawP in phases) {
                                    val eased = 1f - (1f - rawP) * (1f - rawP)
                                    val fade = (1f - eased) * (1f - eased)
                                    val r = buttonRadius * 0.92f + (buttonRadius * 0.08f + voiceExtra) * eased
                                    val a = (voiceIntensity * fade).coerceIn(0f, 1f)
                                    drawCircle(color = AppleBlue.copy(alpha = a * 0.42f), radius = r)
                                    drawCircle(
                                        color = AppleIndigo.copy(alpha = a * 0.72f),
                                        radius = r,
                                        style = Stroke(width = strokePx)
                                    )
                                }
                            }
                        }
                    },
                contentAlignment = Alignment.Center
            ) {
                Box(
                    modifier = Modifier
                        .size(64.dp)
                        .graphicsLayer {
                            val s = breathScaleState.value * (voicePulseScaleState.value + smoothRmsAnim.value * 0.02f)
                            scaleX = s
                            scaleY = s
                        }
                        .shadow(
                            elevation = if (micAutoEnabled) 10.dp else 6.dp,
                            shape = CircleShape,
                            spotColor = if (micAutoEnabled) AppleBlue.copy(alpha = 0.42f) else Color.Black.copy(alpha = 0.2f)
                        )
                        .clip(CircleShape)
                        .background(if (micAutoEnabled) activeMicBrush else inactiveMicBrush)
                        .clickable(onClick = onToggleMic),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = if (micAutoEnabled) Icons.Default.Mic else Icons.Default.MicOff,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier
                            .size(28.dp)
                            .graphicsLayer {
                                val glyphScale = if (micAutoEnabled) iconVoiceScaleState.value else 1.0f
                                scaleX = glyphScale
                                scaleY = glyphScale
                            }
                    )
                }
            }

            // Right Button: Vím (✓) - schová se při zapnutém mikrofonu, naskočí s bounce efektem při vypnutí
            AnimatedVisibility(
                visible = !micAutoEnabled,
                enter = slideInHorizontally(
                    initialOffsetX = { -it },
                    animationSpec = spring(dampingRatio = 0.52f, stiffness = Spring.StiffnessMediumLow)
                ) + scaleIn(
                    initialScale = 0.25f,
                    animationSpec = spring(dampingRatio = 0.52f, stiffness = Spring.StiffnessMediumLow)
                ) + fadeIn(tween(180)),
                exit = slideOutHorizontally(
                    targetOffsetX = { -it },
                    animationSpec = tween(160)
                ) + scaleOut(targetScale = 0.25f, animationSpec = tween(160)) + fadeOut(tween(130))
            ) {
                Box(
                    modifier = Modifier
                        .padding(start = 24.dp)
                        .size(64.dp)
                        .shadow(6.dp, CircleShape, spotColor = AppleGreen.copy(alpha = 0.18f))
                        .clip(CircleShape)
                        .background(AppleGreenTint)
                        .clickable(enabled = enabled, onClick = onSwipeKnow),
                    contentAlignment = Alignment.Center
                ) {
                    Surface(
                        shape = CircleShape,
                        color = CardElevatedSurface,
                        border = BorderStroke(1.2.dp, AppleGreen.copy(alpha = 0.55f)),
                        modifier = Modifier.size(52.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = Icons.Default.Check,
                                contentDescription = strings.badgeKnow,
                                tint = AppleGreen.copy(alpha = 0.88f),
                                modifier = Modifier.size(24.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}

@androidx.compose.ui.tooling.preview.Preview(showBackground = true, widthDp = 390, heightDp = 800)
@Composable
fun FlashcardSessionScreenPreview() {
    val sampleDeck = cz.slovickaspeak.app.model.BuiltInVocabulary.decks.first()
    val strings = getUiStrings(true)
    SlovickaSpeakTheme {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(SurfaceBg)
                .padding(horizontal = 20.dp, vertical = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            LiveSpeechBanner(
                strings = strings,
                language = sampleDeck.language,
                isListening = true,
                micAutoEnabled = true,
                lastHeardText = null,
                feedbackState = CardFeedbackState.NEUTRAL,
                speechError = null
            )
            Spacer(modifier = Modifier.height(12.dp))
            Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.Center) {
                SwipeableVocabCard(
                    strings = strings,
                    language = sampleDeck.language,
                    word = sampleDeck.words.first(),
                    deckLevelLabel = "A1 • Jídlo a nápoje",
                    deckLevelColor = AppleGreen,
                    offsetXProvider = { 0f },
                    scaleProvider = { 1f },
                    alphaProvider = { 1f },
                    feedbackState = CardFeedbackState.NEUTRAL,
                    showHintTranslation = true,
                    onToggleHint = {},
                    onPlayAudio = {},
                    onDragDelta = {},
                    onDragEnd = {}
                )
            }
            Spacer(modifier = Modifier.height(12.dp))
            BottomControlBar(
                strings = strings,
                isListening = true,
                micAutoEnabled = false,
                rmsLevel = 0.5f,
                enabled = true,
                onSwipeDontKnow = {},
                onToggleMic = {},
                onSwipeKnow = {}
            )
        }
    }
}

