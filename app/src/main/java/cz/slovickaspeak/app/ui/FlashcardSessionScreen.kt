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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Lightbulb
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.MicOff
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import cz.slovickaspeak.app.model.AnswerMethod
import cz.slovickaspeak.app.model.CardAttemptResult
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

    var feedbackState by remember { mutableStateOf(CardFeedbackState.NEUTRAL) }
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

    fun triggerWrongSpokenFlash(heardSnippet: String, targetWordToPronounce: VocabWord? = null) {
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

        if (progressState.autoPronounceEnabled && targetWordToPronounce != null) {
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
        if (currentState != CardFeedbackState.NEUTRAL && currentState != CardFeedbackState.SPOKEN_WRONG_FLASH) return

        wrongWordDebounceJob?.cancel()
        wrongFlashResetJob?.cancel()
        lastFlashedWrongWord = null

        feedbackState = when {
            method == AnswerMethod.SPOKEN_CORRECT -> CardFeedbackState.SPOKEN_CORRECT
            isCorrect -> CardFeedbackState.SWIPED_CORRECT
            else -> CardFeedbackState.SWIPED_WRONG
        }

        val shouldPronounce = method == AnswerMethod.SWIPED_DONT_KNOW ||
            (isCorrect && progressState.autoPronounceEnabled)

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
                spokenTranscript = spokenMatch ?: lastHeardText
            )
            results.add(attempt)
            progressState.recordCardAttempt(attempt, usedHint = usedHintOnCurrentCard)

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
                currentIndex += 1
                showHintTranslation = false
                usedHintOnCurrentCard = false
                lastHeardText = null
                lastFlashedWrongWord = null

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
                feedbackState = CardFeedbackState.NEUTRAL
                keepScreenActive()
                if (hasMicPermState && micAutoState) {
                    speechControllerRef?.startForNewCard()
                }
            }
        }
    }

    val handleSpeechCandidates: (List<String>, Boolean) -> Unit = { candidates, isFinal ->
        keepScreenActive()
        val word = currentWordState
        val state = feedbackStateUpdated
        val canAcceptSpeech = state == CardFeedbackState.NEUTRAL || state == CardFeedbackState.SPOKEN_WRONG_FLASH
        if (word != null && canAcceptSpeech && candidates.isNotEmpty()) {
            val matched = WordMatcher.matches(candidates, word, isFinal)
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
                        triggerWrongSpokenFlash(snippet, targetWordToPronounce = word)
                    } else if (!WordMatcher.isPrefixOfTarget(candidates, word)) {
                        // Only flash UI after a quiet 950ms pause if the session is still open in a noisy room;
                        // NEVER cancels the recognizer so mid-word speech is never interrupted!
                        wrongWordDebounceJob = coroutineScope.launch {
                            delay(950L)
                            triggerWrongSpokenFlash(snippet, targetWordToPronounce = word)
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
        val isCardInteractive = feedbackState == CardFeedbackState.NEUTRAL ||
                feedbackState == CardFeedbackState.SPOKEN_WRONG_FLASH

        if (isLandscape) {
            // Fold / Tablet Landscape (na sirku): 2 sloupce (Vlevo velka 3D karta, vpravo ovladani a mikrofon)
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .padding(horizontal = 24.dp, vertical = 10.dp),
                horizontalArrangement = Arrangement.spacedBy(18.dp)
            ) {
                // Levy sloupec: 3D balicek karticek s dostatkem vysky i sirky
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
                    showHintTranslation = showHintTranslation ||
                            (feedbackState != CardFeedbackState.NEUTRAL && feedbackState != CardFeedbackState.SPOKEN_WRONG_FLASH),
                    onToggleHint = {
                        showHintTranslation = !showHintTranslation
                        if (showHintTranslation) {
                            usedHintOnCurrentCard = true
                            speechControllerRef?.speakEnglish(currentWord.english)
                        }
                    },
                    onPlayAudio = {
                        speechControllerRef?.speakEnglish(currentWord.english)
                    },
                    onDragDelta = { delta ->
                        keepScreenActive()
                        if (isCardInteractive) {
                            coroutineScope.launch {
                                offsetX.snapTo(offsetX.value + delta)
                            }
                        }
                    },
                    onDragEnd = {
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
                    },
                    modifier = Modifier
                        .weight(1.15f)
                        .fillMaxHeight()
                )

                // Pravy sloupec: Horni lista + Zivy mikrofonni stav + Spodni tlacitkovy dock
                Column(
                    modifier = Modifier
                        .weight(0.85f)
                        .fillMaxHeight(),
                    verticalArrangement = Arrangement.SpaceBetween,
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

                    Spacer(modifier = Modifier.height(10.dp))

                    LiveSpeechBanner(
                        strings = strings,
                        language = deck.language,
                        isListening = isListening,
                        micAutoEnabled = micAutoEnabled,
                        lastHeardText = lastHeardText,
                        feedbackState = feedbackState,
                        speechError = speechError,
                        rmsLevel = rmsLevel
                    )

                    Spacer(modifier = Modifier.weight(1f))

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
                        onToggleMic = {
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
                        },
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
            // Portrait (na vysku): Plne roztazene pres celou sirku podle zadani
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .padding(horizontal = 20.dp, vertical = 12.dp),
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

                Spacer(modifier = Modifier.height(10.dp))

                LiveSpeechBanner(
                    strings = strings,
                    language = deck.language,
                    isListening = isListening,
                    micAutoEnabled = micAutoEnabled,
                    lastHeardText = lastHeardText,
                    feedbackState = feedbackState,
                    speechError = speechError,
                    rmsLevel = rmsLevel
                )

                Spacer(modifier = Modifier.height(12.dp))

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
                    showHintTranslation = showHintTranslation ||
                            (feedbackState != CardFeedbackState.NEUTRAL && feedbackState != CardFeedbackState.SPOKEN_WRONG_FLASH),
                    onToggleHint = {
                        showHintTranslation = !showHintTranslation
                        if (showHintTranslation) {
                            usedHintOnCurrentCard = true
                            speechControllerRef?.speakEnglish(currentWord.english)
                        }
                    },
                    onPlayAudio = {
                        speechControllerRef?.speakEnglish(currentWord.english)
                    },
                    onDragDelta = { delta ->
                        keepScreenActive()
                        if (isCardInteractive) {
                            coroutineScope.launch {
                                offsetX.snapTo(offsetX.value + delta)
                            }
                        }
                    },
                    onDragEnd = {
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
                    },
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                )

                Spacer(modifier = Modifier.height(14.dp))

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
                    onToggleMic = {
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
                    },
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
    modifier: Modifier = Modifier
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
            onDragEnd = onDragEnd
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
    rmsLevel: Float = 0f
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
                    strings.bannerListeningForLanguage(language)
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
    onDragEnd: () -> Unit
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
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 20.dp, vertical = 14.dp),
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

            // 2. Middle Content (Weighted so it is centered and NEVER collides with bottom slot!)
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .padding(vertical = 4.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                // 3D Glass Emoji Container (adapted for fold/compact screens: 88.dp)
                Surface(
                    shape = RoundedCornerShape(26.dp),
                    color = SurfaceBg,
                    border = BorderStroke(1.dp, CardBorderGray.copy(alpha = 0.45f)),
                    shadowElevation = 4.dp,
                    modifier = Modifier.size(88.dp)
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
                            fontSize = 46.sp
                        )
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                Text(
                    text = strings.cardPromptSpeakLanguage(language),
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = AppleBlue,
                    letterSpacing = 1.2.sp
                )

                Spacer(modifier = Modifier.height(4.dp))

                Text(
                    text = word.czech,
                    fontSize = when {
                        word.czech.length > 34 -> 20.sp
                        word.czech.length > 22 -> 23.sp
                        else -> 26.sp
                    },
                    fontWeight = FontWeight.ExtraBold,
                    color = TextDark,
                    textAlign = TextAlign.Center,
                    lineHeight = when {
                        word.czech.length > 34 -> 25.sp
                        word.czech.length > 22 -> 28.sp
                        else -> 32.sp
                    },
                    letterSpacing = (-0.4).sp,
                    modifier = Modifier.padding(horizontal = 8.dp)
                )
            }

            // 3. Dedicated Bottom Slot: either Hint pill OR Translation card
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 68.dp),
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
                            modifier = Modifier.padding(horizontal = 20.dp, vertical = 11.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(7.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Lightbulb,
                                contentDescription = null,
                                tint = AppleOrange,
                                modifier = Modifier.size(17.dp)
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
                        shape = RoundedCornerShape(22.dp),
                        color = SurfaceBg,
                        border = BorderStroke(0.5.dp, CardBorderGray),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(3.dp)
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally)
                            ) {
                                Text(
                                    text = word.english,
                                    fontSize = when {
                                        word.english.length > 26 -> 18.sp
                                        word.english.length > 18 -> 21.sp
                                        else -> 24.sp
                                    },
                                    lineHeight = when {
                                        word.english.length > 26 -> 23.sp
                                        word.english.length > 18 -> 26.sp
                                        else -> 29.sp
                                    },
                                    fontWeight = FontWeight.Bold,
                                    color = AppleBlue,
                                    textAlign = TextAlign.Center,
                                    modifier = Modifier.weight(1f, fill = false)
                                )
                                IconButton(
                                    onClick = onPlayAudio,
                                    modifier = Modifier.size(28.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.AutoMirrored.Filled.VolumeUp,
                                        contentDescription = null,
                                        tint = AppleBlue
                                    )
                                }
                            }
                            if (word.phonetic.isNotEmpty()) {
                                Text(
                                    text = word.phonetic,
                                    fontSize = 14.sp,
                                    color = TextMuted
                                )
                            }
                            if (word.exampleSentence.isNotEmpty()) {
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
    // Continuous breathing animation ("dýchání ikony") when listening is active
    val infiniteTransition = rememberInfiniteTransition(label = "micBreathing")
    val breathScale by infiniteTransition.animateFloat(
        initialValue = 1.0f,
        targetValue = 1.14f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1200, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "breathScale"
    )
    val outerHaloScale by infiniteTransition.animateFloat(
        initialValue = 1.0f,
        targetValue = 1.38f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1200, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "outerHaloScale"
    )
    val outerHaloAlpha by infiniteTransition.animateFloat(
        initialValue = 0.28f,
        targetValue = 0.06f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1200, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "outerHaloAlpha"
    )

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

