package cz.slovickaspeak.app

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.*
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import cz.slovickaspeak.app.model.BuiltInVocabulary
import cz.slovickaspeak.app.model.CardAttemptResult
import cz.slovickaspeak.app.model.VocabDeck
import cz.slovickaspeak.app.model.VocabWord
import cz.slovickaspeak.app.ui.DeckSelectionScreen
import cz.slovickaspeak.app.ui.FlashcardSessionScreen
import cz.slovickaspeak.app.ui.SessionSummaryScreen
import cz.slovickaspeak.app.ui.theme.SlovickaSpeakTheme

private sealed interface AppScreen {
    data object DeckList : AppScreen
    data class ActiveSession(
        val deck: VocabDeck,
        val words: List<VocabWord> = deck.words,
        val sessionId: Long = System.currentTimeMillis()
    ) : AppScreen
    data class Summary(
        val deck: VocabDeck,
        val results: List<CardAttemptResult>
    ) : AppScreen
}

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        cz.slovickaspeak.app.model.CrossLanguageBridge.init(applicationContext)

        setContent {
            val progressState = remember {
                cz.slovickaspeak.app.data.ProgressStateHolder(this@MainActivity)
            }

            DisposableEffect(this@MainActivity, progressState) {
                val observer = LifecycleEventObserver { _, event ->
                    if (event == Lifecycle.Event.ON_RESUME) {
                        progressState.checkNewDayRollover(alsoRefreshCloud = true)
                        if (BuildConfig.ENABLE_OTA_UPDATES) {
                            progressState.retryPendingOtaInstallIfPermitted()
                            progressState.checkForOtaUpdate(manual = false)
                        }
                    }
                }
                this@MainActivity.lifecycle.addObserver(observer)
                onDispose {
                    this@MainActivity.lifecycle.removeObserver(observer)
                }
            }

            SlovickaSpeakTheme(darkTheme = progressState.isDarkMode) {
                var hasMicPermission by remember {
                    mutableStateOf(
                        ContextCompat.checkSelfPermission(
                            this,
                            Manifest.permission.RECORD_AUDIO
                        ) == PackageManager.PERMISSION_GRANTED
                    )
                }

                val permissionLauncher = rememberLauncherForActivityResult(
                    contract = ActivityResultContracts.RequestPermission()
                ) { granted ->
                    hasMicPermission = granted
                }

                LaunchedEffect(Unit) {
                    if (!hasMicPermission) {
                        permissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                    }
                }

                if (BuildConfig.ENABLE_OTA_UPDATES && progressState.showOtaDialog) {
                    cz.slovickaspeak.app.ui.OtaUpdateModalDialog(
                        progressState = progressState,
                        onDismiss = { progressState.closeOtaModal() }
                    )
                }

                var currentScreen by remember { mutableStateOf<AppScreen>(AppScreen.DeckList) }

                when (val screen = currentScreen) {
                    is AppScreen.DeckList -> {
                        val activeDecks = remember(progressState.nativeLanguage, progressState.selectedLanguage) {
                            cz.slovickaspeak.app.model.CrossLanguageBridge.decksFor(
                                nativeLang = progressState.nativeLanguage,
                                targetLang = progressState.selectedLanguage,
                                context = this@MainActivity
                            )
                        }
                        DeckSelectionScreen(
                            decks = activeDecks,
                            progressState = progressState,
                            onSelectDeck = { selectedDeck ->
                                if (!hasMicPermission) {
                                    permissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                                }
                                currentScreen = AppScreen.ActiveSession(
                                    deck = selectedDeck,
                                    words = selectedDeck.words
                                )
                            }
                        )
                    }

                    is AppScreen.ActiveSession -> {
                        key(screen.sessionId) {
                            FlashcardSessionScreen(
                                deck = screen.deck,
                                wordsToPractice = screen.words,
                                progressState = progressState,
                                hasMicPermission = hasMicPermission,
                                onRequestMicPermission = {
                                    permissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                                },
                                onSessionFinished = { sessionResults ->
                                    currentScreen = AppScreen.Summary(
                                        deck = screen.deck,
                                        results = sessionResults
                                    )
                                },
                                onCloseSession = {
                                    currentScreen = AppScreen.DeckList
                                }
                            )
                        }
                    }

                    is AppScreen.Summary -> {
                        SessionSummaryScreen(
                            deck = screen.deck,
                            results = screen.results,
                            onRetryMissed = { missedWords ->
                                progressState.updateDailyGoal(missedWords.size)
                                currentScreen = AppScreen.ActiveSession(
                                    deck = screen.deck,
                                    words = missedWords
                                )
                            },
                            onRestartAll = {
                                currentScreen = AppScreen.ActiveSession(
                                    deck = screen.deck,
                                    words = screen.deck.words
                                )
                            },
                            onBackToDecks = {
                                currentScreen = AppScreen.DeckList
                            }
                        )
                    }
                }
            }
        }
    }
}
