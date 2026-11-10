package cz.slovickaspeak.app.ui

import android.content.res.Configuration
import android.os.SystemClock
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
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
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
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
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
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
    var showHintTranslation by remember { mutableStateOf(false) }
    var usedHintOnCurrentCard by remember { mutableStateOf(false) }
    val wordsNeedingSecondConfirmation = remember(dailyGoalWords) { mutableStateListOf<String>() }
    var lastHeardText by remember { mutableStateOf<String?>(null) }
    var speechError by remember { mutableStateOf<String?>(null) }

    var isListening by remember { mutableStateOf(false) }
    var micAutoEnabled by remember { mutableStateOf(true) }
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

    fun triggerWrongSpokenFlash(
        heardSnippet: String,
        targetWordToPronounce: VocabWord? = null,
        pronounceCorrection: Boolean = true
    ) {
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

        if (pronounceCorrection && progressState.autoPronounceEnabled && !isReversedCardState && targetWordToPronounce != null) {
            speechControllerRef?.speakEnglish(targetWordToPronounce.english)
        }

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

            // Start pronunciation immediately in parallel so card animation NEVER freezes mid-flight
            if (shouldPronounce) {
                speechControllerRef?.speakEnglish(word.english) {
                    speechFinished = true
                }
            }

            if (method == AnswerMethod.SPOKEN_CORRECT) {
                // Card was in center: give a crisp celebratory pulse so the green flash registers smoothly
                cardScale.animateTo(1.045f, tween(80, easing = FastOutSlowInEasing))
                cardScale.animateTo(1.0f, tween(80, easing = FastOutSlowInEasing))
                delay(70L)
            }

            // 1. Current card immediately glides off-screen smoothly on GPU layer (zero mid-air freeze)
            launch {
                cardAlpha.animateTo(0f, tween(durationMillis = 195, easing = FastOutSlowInEasing))
            }
            launch {
                cardScale.animateTo(0.95f, tween(durationMillis = 210, easing = FastOutSlowInEasing))
            }
            offsetX.animateTo(
                targetValue = exitTargetX,
                animationSpec = tween(durationMillis = 215, easing = FastOutSlowInEasing)
            )

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
            val matched = WordMatcher.matches(candidates, word, isFinal, expectCzech = expectCzech)
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
                        triggerWrongSpokenFlash(snippet, targetWordToPronounce = word, pronounceCorrection = true)
                    } else if (!WordMatcher.isPrefixOfTarget(candidates, word, expectCzech = expectCzech)) {
                        // Only flash UI after a quiet 950ms pause if the session is still open in a noisy room;
                        // NEVER cancels the recognizer or triggers TTS so mid-word speech is never interrupted!
                        wrongWordDebounceJob = coroutineScope.launch {
                            delay(950L)
                            triggerWrongSpokenFlash(snippet, targetWordToPronounce = word, pronounceCorrection = false)
                        }
                    }
                }
            }
        }
    }

    val currentHandleSpeech by rememberUpdatedState(handleSpeechCandidates)
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
                    if (hasMicPermState && micAutoState) {
                        controller.startContinuousListening()
                    }
                }
                Lifecycle.Event.ON_PAUSE, Lifecycle.Event.ON_STOP -> {
                    // Immediately stop recognizer and restore phone volume when minimized to background
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

    LaunchedEffect(hasMicPermission) {
        if (hasMicPermission && micAutoEnabled) {
            speechControllerRef?.startContinuousListening()
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

        val isTranslationRevealed = showHintTranslation ||
                (feedbackState != CardFeedbackState.NEUTRAL && feedbackState != CardFeedbackState.SPOKEN_WRONG_FLASH)

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
        val handleDragDelta: (Float) -> Unit = { delta ->
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
                            onReplayWord = { wordText ->
                                speechControllerRef?.speakEnglish(wordText)
                            }
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
                        rmsLevel = rmsLevel,
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
                        offsetX = offsetX.value,
                        cardScale = cardScale.value,
                        cardAlpha = cardAlpha.value,
                        feedbackState = feedbackState,
                        showHintTranslation = isTranslationRevealed,
                        onToggleHint = handleToggleHint,
                        onPlayAudio = handlePlayAudio,
                        onDragDelta = handleDragDelta,
                        onDragEnd = handleDragEnd,
                        isReversed = isReversedCard,
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth()
                    )

                    BottomControlBar(
                        strings = strings,
                        isListening = isListening,
                        micAutoEnabled = micAutoEnabled,
                        rmsLevel = rmsLevel,
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
                    rmsLevel = rmsLevel,
                    expectCzech = isReversedCard
                )

                Spacer(modifier = Modifier.height(8.dp))

                Flashcard3DStackArea(
                    strings = strings,
                    deck = deck,
                    currentWord = currentWord,
                    nextWord = nextWord,
                    thirdWord = thirdWord,
                    offsetX = offsetX.value,
                    cardScale = cardScale.value,
                    cardAlpha = cardAlpha.value,
                    feedbackState = feedbackState,
                    showHintTranslation = isTranslationRevealed,
                    onToggleHint = handleToggleHint,
                    onPlayAudio = handlePlayAudio,
                    onDragDelta = handleDragDelta,
                    onDragEnd = handleDragEnd,
                    isReversed = isReversedCard,
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
                    rmsLevel = rmsLevel,
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
    onReplayWord: (String) -> Unit
) {
    val recent = results.asReversed().take(4)
    val spokenCount = results.count { it.method == AnswerMethod.SPOKEN_CORRECT }

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
                        "Tvé odpovědi se zobrazí zde — kliknutím si slovíčko kdykoliv znovu poslechneš."
                    } else {
                        "Your recent answers will appear here — tap any item to replay pronunciation."
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
                                .clickable { onReplayWord(item.word.english) }
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

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    // Auto-repeat pronunciation toggle button
                    val isAutoRepeatOn = progressState.autoPronounceEnabled
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = if (isAutoRepeatOn) AppleBlue.copy(alpha = 0.14f) else SurfaceBg,
                        border = BorderStroke(
                            width = if (isAutoRepeatOn) 1.2.dp else 0.5.dp,
                            color = if (isAutoRepeatOn) AppleBlue else CardBorderGray.copy(alpha = 0.6f)
                        ),
                        modifier = Modifier
                            .height(32.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .clickable {
                                progressState.setAutoPronounce(!isAutoRepeatOn)
                            }
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.VolumeUp,
                                contentDescription = if (strings.isCzech) "Automaticky zopakovat výslovnost" else "Repeat pronunciation",
                                tint = if (isAutoRepeatOn) AppleBlue else TextMuted,
                                modifier = Modifier.size(15.dp)
                            )
                            Text(
                                text = "AUTO",
                                fontSize = 10.5.sp,
                                fontWeight = if (isAutoRepeatOn) FontWeight.Bold else FontWeight.Medium,
                                color = if (isAutoRepeatOn) AppleBlue else TextMuted
                            )
                        }
                    }

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
    offsetX: Float,
    cardScale: Float,
    cardAlpha: Float,
    feedbackState: CardFeedbackState,
    showHintTranslation: Boolean,
    onToggleHint: () -> Unit,
    onPlayAudio: () -> Unit,
    onDragDelta: (Float) -> Unit,
    onDragEnd: () -> Unit,
    modifier: Modifier = Modifier,
    isReversed: Boolean = false
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
                        val prog = (abs(offsetX) / 380f).coerceIn(0f, 1f)
                        scaleX = 0.86f + 0.05f * prog
                        scaleY = 0.86f + 0.05f * prog
                        translationY = (26f - 10f * prog) * density
                        alpha = 0.45f + 0.25f * prog
                    }
            ) {}
        }

        // Second depth card in 3D stack (rises and scales up purely in GPU graphicsLayer)
        if (nextWord != null) {
            Surface(
                shape = RoundedCornerShape(34.dp),
                color = CardSurface,
                border = BorderStroke(0.5.dp, CardBorderGray.copy(alpha = 0.6f)),
                shadowElevation = 6.dp,
                modifier = Modifier
                    .fillMaxWidth()
                    .fillMaxHeight(0.97f)
                    .graphicsLayer {
                        val prog = (abs(offsetX) / 380f).coerceIn(0f, 1f)
                        scaleX = 0.92f + 0.06f * prog
                        scaleY = 0.92f + 0.06f * prog
                        translationY = (14f - 12f * prog) * density
                        alpha = 0.78f + 0.22f * prog
                    }
            ) {}
        }

        // Active Foreground Card (reads offsetX, scale, alpha ONLY in graphicsLayer)
        SwipeableVocabCard(
            strings = strings,
            language = deck.language,
            word = currentWord,
            deckLevelLabel = "${deck.language.flagEmoji} ${deck.level.label} • ${deck.localizedTitle(strings.isCzech)}",
            deckLevelColor = Color(deck.level.colorHex),
            offsetXProvider = { offsetX },
            scaleProvider = { cardScale },
            alphaProvider = { cardAlpha },
            feedbackState = feedbackState,
            showHintTranslation = showHintTranslation,
            onToggleHint = onToggleHint,
            onPlayAudio = onPlayAudio,
            onDragDelta = onDragDelta,
            onDragEnd = onDragEnd,
            isReversed = isReversed
        )
    }
}

@Composable
private fun AudioWaveVisualizer(
    color: Color,
    rmsLevel: Float = 0f,
    modifier: Modifier = Modifier
) {
    val infiniteTransition = rememberInfiniteTransition(label = "audioWaveBars")

    val h1 by infiniteTransition.animateFloat(
        initialValue = 4f, targetValue = 13f,
        animationSpec = infiniteRepeatable(tween(420, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "h1"
    )
    val h2 by infiniteTransition.animateFloat(
        initialValue = 14f, targetValue = 5f,
        animationSpec = infiniteRepeatable(tween(360, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "h2"
    )
    val h3 by infiniteTransition.animateFloat(
        initialValue = 6f, targetValue = 16f,
        animationSpec = infiniteRepeatable(tween(480, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "h3"
    )
    val h4 by infiniteTransition.animateFloat(
        initialValue = 13f, targetValue = 6f,
        animationSpec = infiniteRepeatable(tween(400, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "h4"
    )
    val h5 by infiniteTransition.animateFloat(
        initialValue = 5f, targetValue = 11f,
        animationSpec = infiniteRepeatable(tween(450, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "h5"
    )

    val boost = (rmsLevel * 1.5f).coerceIn(0f, 6f)

    Row(
        modifier = modifier.height(18.dp),
        horizontalArrangement = Arrangement.spacedBy(3.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        listOf(h1, h2, h3, h4, h5).forEach { baseH ->
            val finalHeight = (baseH + boost).coerceIn(4f, 18f)
            Box(
                modifier = Modifier
                    .width(3.dp)
                    .height(finalHeight.dp)
                    .clip(RoundedCornerShape(50))
                    .background(color)
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

            // Dynamic Audio Waveform (animované vlnění při poslechu jako v návrhu)
            if (micAutoEnabled && isListening && feedbackState == CardFeedbackState.NEUTRAL) {
                Spacer(modifier = Modifier.width(8.dp))
                AudioWaveVisualizer(
                    color = dotColor,
                    rmsLevel = rmsLevel
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

    Surface(
        shape = RoundedCornerShape(34.dp),
        color = cardBg,
        border = BorderStroke(
            if (feedbackState == CardFeedbackState.NEUTRAL && !showLeftSwipeBadge && !showRightSwipeBadge) 1.dp else 2.5.dp,
            cardBorder
        ),
        shadowElevation = 14.dp,
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
                alpha = alphaProvider()
            }
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
            // Top Row: Left/Right Swipe Badges + Center Level Badge
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
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
                            modifier = Modifier.padding(horizontal = 11.dp, vertical = 4.dp)
                        )
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
                    IconButton(
                        onClick = onPlayAudio,
                        modifier = Modifier
                            .size(34.dp)
                            .clip(CircleShape)
                            .background(SurfaceBg)
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.VolumeUp,
                            contentDescription = null,
                            tint = AppleBlue,
                            modifier = Modifier.size(18.dp)
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
                Surface(
                    shape = RoundedCornerShape(if (isUltraCompactCard) 18.dp else 24.dp),
                    color = SurfaceBg,
                    border = BorderStroke(1.dp, CardBorderGray.copy(alpha = 0.45f)),
                    shadowElevation = 4.dp,
                    modifier = Modifier.size(emojiBoxSize)
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
        }
    }
}

@Composable
private fun BottomControlBar(
    strings: UiStrings,
    isListening: Boolean,
    micAutoEnabled: Boolean,
    rmsLevel: Float,
    enabled: Boolean,
    onSwipeDontKnow: () -> Unit,
    onToggleMic: () -> Unit,
    onSwipeKnow: () -> Unit
) {
    // Continuous breathing animation runs ONLY when mic is active (0 Hz GPU idle when paused)
    val breathScale: Float
    val outerHaloScale: Float
    val outerHaloAlpha: Float
    if (micAutoEnabled) {
        val infiniteTransition = rememberInfiniteTransition(label = "micBreathing")
        breathScale = infiniteTransition.animateFloat(
            initialValue = 1.0f,
            targetValue = 1.14f,
            animationSpec = infiniteRepeatable(
                animation = tween(durationMillis = 1200, easing = FastOutSlowInEasing),
                repeatMode = RepeatMode.Reverse
            ),
            label = "breathScale"
        ).value
        outerHaloScale = infiniteTransition.animateFloat(
            initialValue = 1.0f,
            targetValue = 1.38f,
            animationSpec = infiniteRepeatable(
                animation = tween(durationMillis = 1200, easing = FastOutSlowInEasing),
                repeatMode = RepeatMode.Reverse
            ),
            label = "outerHaloScale"
        ).value
        outerHaloAlpha = infiniteTransition.animateFloat(
            initialValue = 0.28f,
            targetValue = 0.06f,
            animationSpec = infiniteRepeatable(
                animation = tween(durationMillis = 1200, easing = FastOutSlowInEasing),
                repeatMode = RepeatMode.Reverse
            ),
            label = "outerHaloAlpha"
        ).value
    } else {
        breathScale = 1.0f
        outerHaloScale = 1.0f
        outerHaloAlpha = 0f
    }

    val activeMicScale = if (micAutoEnabled) {
        (breathScale + rmsLevel * 0.22f).coerceAtMost(1.35f)
    } else {
        1.0f
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
                .padding(horizontal = 24.dp, vertical = 14.dp),
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

            // Center Enlarged Circular Microphone Button
            Box(
                modifier = Modifier.size(76.dp),
                contentAlignment = Alignment.Center
            ) {
                if (micAutoEnabled) {
                    // Outer breathing halo
                    Box(
                        modifier = Modifier
                            .size(66.dp)
                            .scale(outerHaloScale)
                            .clip(CircleShape)
                            .background(AppleBlue.copy(alpha = outerHaloAlpha))
                    )
                    // Inner breathing halo
                    Box(
                        modifier = Modifier
                            .size(66.dp)
                            .scale(breathScale * 1.08f)
                            .clip(CircleShape)
                            .background(AppleIndigo.copy(alpha = 0.16f))
                    )
                }

                Box(
                    modifier = Modifier
                        .size(66.dp)
                        .scale(activeMicScale)
                        .shadow(
                            elevation = if (micAutoEnabled) 12.dp else 6.dp,
                            shape = CircleShape,
                            spotColor = if (micAutoEnabled) AppleBlue.copy(alpha = 0.45f) else Color.Black.copy(alpha = 0.2f)
                        )
                        .clip(CircleShape)
                        .background(
                            if (micAutoEnabled) {
                                Brush.linearGradient(listOf(AppleBlue, AppleIndigo))
                            } else {
                                Brush.linearGradient(listOf(Color(0xFF636366), Color(0xFF48484A)))
                            }
                        )
                        .clickable(onClick = onToggleMic),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = if (micAutoEnabled) Icons.Default.Mic else Icons.Default.MicOff,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier
                            .size(31.dp)
                            .scale(if (micAutoEnabled) breathScale else 1f)
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

