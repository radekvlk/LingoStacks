package cz.slovickaspeak.app.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalConfiguration
import cz.slovickaspeak.app.model.LearningLanguage
import java.util.Locale

data class UiStrings(
    val isCzech: Boolean,
    val appHeaderSubtitle: String,
    val appHeaderTitle: String,
    val learningLanguageTitle: String,
    val allLevelsLabel: String,
    val remainingOfGoal: (Int, Int) -> String,
    val remainingWordsOfGoal: (Int, Int) -> String,
    val dailyGoalProgress: (Int, Int) -> String,
    val sessionCardProgress: (Int, Int, Int) -> String,
    val sessionMasteryProgress: (Int, Int, Int) -> String,
    val wordsInDeck: (Int) -> String,
    val settingsAndLeaderboard: String,
    val tabGoalAndStats: String,
    val tabLeaderboardAndFriends: String,
    val appearanceTitle: String,
    val lightModeLabel: String,
    val darkModeLabel: String,
    val dailyGoalTitle: String,
    val wordsCountBadge: (Int) -> String,
    val dailyGoalSliderHint: String,
    val englishLevelTitle: String,
    val languageLevelTitle: (LearningLanguage) -> String,
    val allLevelsButton: String,
    val allStatsTitle: String,
    val statStreakDays: (Int) -> String,
    val statStreakLabel: String,
    val statAccuracyLabel: String,
    val statSpokenLabel: String,
    val statTodayGoalLabel: String,
    val statTotalLearnedLabel: String,
    val statCompletedLessonsLabel: String,
    val leaderboardTitle: String,
    val leaderboardSubtitle: String,
    val leaderboardEmptyHint: String,
    val leaderboardRowSub: (Int, Int) -> String,
    val leaderboardWordsToday: (Int) -> String,
    val inviteFriendTitle: String,
    val inviteFriendHint: (String) -> String,
    val inviteInputPlaceholder: String,
    val shareInviteButton: String,
    val shareChooserTitle: String,
    val shareMessage: (String) -> String,
    val googleSectionTitle: String,
    val googleSignOutHint: (String) -> String,
    val googleSignInButton: String,
    val googleManualPrompt: String,
    val googleNameLabel: String,
    val googleEmailLabel: String,
    val googleConfirmButton: String,
    val googleYourInviteCode: (String) -> String,
    val googleSignOutButton: String,
    val googleEditNameButton: String,
    val googleSaveNameButton: String,
    val googleCancelButton: String,
    val cardPromptSpeakEnglish: String,
    val cardPromptSpeakCzech: String,
    val cardPromptSpeakLanguage: (LearningLanguage) -> String,
    val showHintButton: String,
    val badgeDontKnow: String,
    val badgeTryAgain: String,
    val badgeKnow: String,
    val badgeSpoken: String,
    val bannerSpokenCorrect: (String) -> String,
    val bannerSpokenWrong: (String) -> String,
    val bannerMarkedKnow: String,
    val bannerMarkedDontKnow: String,
    val bannerMicOff: String,
    val bannerHeard: (String) -> String,
    val bannerListening: String,
    val bannerListeningCzech: String,
    val bannerListeningForLanguage: (LearningLanguage) -> String,
    val bannerMicReady: String,
    val summaryCompleteTitle: String,
    val summaryAccuracy: String,
    val summarySpoken: String,
    val summaryMissed: String,
    val summaryWordsOverview: String,
    val summaryRetryMissed: (Int) -> String,
    val summaryRestartAll: String,
    val summaryBackToDecks: String,
    val methodSpoken: String,
    val methodSwipedKnow: String,
    val methodSwipedDontKnow: String,
    val appVersionInfo: (String) -> String,
    val initialLanguageWelcomeTitle: String,
    val initialLanguageWelcomeSubtitle: String,
    val initialLanguageChooseHint: String,
    val initialLanguageConfirmButton: String
)

val czechStrings = UiStrings(
    isCzech = true,
    appHeaderSubtitle = "LINGOSTACKS",
    appHeaderTitle = "LingoStacks",
    learningLanguageTitle = "🌍 Jazyk k učení",
    allLevelsLabel = "Všechny úrovně (A1–C2)",
    remainingOfGoal = { rem, total -> "Zbývá $rem z $total" },
    remainingWordsOfGoal = { rem, total -> "Zbývá $rem z $total slovíček" },
    dailyGoalProgress = { done, goal ->
        if (done >= goal) "Dnes: $done / $goal slov (Cíl splněn! 🎉)" else "Dnes: $done / $goal slov (zbývá ${goal - done})"
    },
    sessionCardProgress = { current, total, todayDone ->
        "Karta $current z $total • Dnes $todayDone slov"
    },
    sessionMasteryProgress = { mastered, total, remaining ->
        if (remaining <= 0) "Zvládnuto: $mastered / $total slov • Dokončeno! 🎉"
        else "Zvládnuto: $mastered / $total slov (zbývá $remaining)"
    },
    wordsInDeck = { count -> "$count slovíček v sadě" },
    settingsAndLeaderboard = "Nastavení a statistiky",
    tabGoalAndStats = "⚙️ Nastavení",
    tabLeaderboardAndFriends = "📊 Statistiky",
    appearanceTitle = "🌗 Vzhled aplikace (Light / Dark)",
    lightModeLabel = "☀️ Světlý (Light)",
    darkModeLabel = "🌙 Tmavý (Dark)",
    dailyGoalTitle = "Počet slovíček (denní cíl)",
    wordsCountBadge = { n -> "$n slovíček" },
    dailyGoalSliderHint = "Posuňte lištu (5 až 200 slovíček) pro nastavení denního cíle:",
    englishLevelTitle = "🇬🇧 Úroveň angličtiny (A1 – C2)",
    languageLevelTitle = { lang ->
        when (lang) {
            LearningLanguage.SPANISH -> "🇪🇸 Úroveň španělštiny (A1 – C2)"
            LearningLanguage.GERMAN -> "🇩🇪 Úroveň němčiny (A1 – C2)"
            LearningLanguage.FRENCH -> "🇫🇷 Úroveň francouzštiny (A1 – C2)"
            LearningLanguage.ITALIAN -> "🇮🇹 Úroveň italštiny (A1 – C2)"
            LearningLanguage.PORTUGUESE -> "🇵🇹 Úroveň portugalštiny (A1 – C2)"
            LearningLanguage.ENGLISH -> "🇬🇧 Úroveň angličtiny (A1 – C2)"
            LearningLanguage.CZECH -> "🇨🇿 Úroveň češtiny (A1 – C2)"
        }
    },
    allLevelsButton = "Všechny úrovně (A1 – C2)",
    allStatsTitle = "Veškeré statistiky učení",
    statStreakDays = { d -> "$d dní" },
    statStreakLabel = "Denní řada",
    statAccuracyLabel = "Úspěšnost",
    statSpokenLabel = "Vysloveno hlasem",
    statTodayGoalLabel = "Dnešní cíl",
    statTotalLearnedLabel = "Celkem naučeno",
    statCompletedLessonsLabel = "Dokončené lekce",
    leaderboardTitle = "🏆 Denní žebříček",
    leaderboardSubtitle = "Pouze reální uživatelé propojení s Google účtem",
    leaderboardEmptyHint = "Zatím není připojen žádný účet. Přihlaste se níže svým Google účtem, abyste se zobrazili v denním žebříčku reálných uživatelů.",
    leaderboardRowSub = { streak, spoken -> "🔥 $streak dní • 🎙️ $spoken vysloveno" },
    leaderboardWordsToday = { n -> "$n slov dnes" },
    inviteFriendTitle = "Pozvat přítele ke sdílení pokroku",
    inviteFriendHint = { code -> "Váš kód je $code. Zadejte kód nebo Gmail přítele s propojeným účtem a uvidíte se v denním žebříčku:" },
    inviteInputPlaceholder = "Kód SLOV-... nebo Gmail",
    shareInviteButton = "Sdílet pozvánku příteli",
    shareChooserTitle = "Pozvat přítele ke sdílení pokroku",
    shareMessage = { code -> "Pojď se se mnou učit slovíčka v aplikaci LingoStacks a porovnat se v denním žebříčku! Zadej v Nastavení můj kód: $code" },
    googleSectionTitle = "Google účet a profil v žebříčku",
    googleSignOutHint = { _ -> "Pro zařazení do denního žebříčku se prosím propojte s Google účtem:" },
    googleSignInButton = "Přihlásit se přes Google",
    googleManualPrompt = "Zadejte svůj Google účet:",
    googleNameLabel = "Jméno v žebříčku",
    googleEmailLabel = "Gmail adresa",
    googleConfirmButton = "Potvrdit a synchronizovat",
    googleYourInviteCode = { code -> "Váš kód: $code" },
    googleSignOutButton = "Odhlásit",
    googleEditNameButton = "Změnit jméno",
    googleSaveNameButton = "Uložit",
    googleCancelButton = "Zrušit",
    cardPromptSpeakEnglish = "ŘEKNI ANGLICKY",
    cardPromptSpeakCzech = "ŘEKNI ČESKY",
    cardPromptSpeakLanguage = { lang ->
        when (lang) {
            LearningLanguage.SPANISH -> "ŘEKNI ŠPANĚLSKY"
            LearningLanguage.GERMAN -> "ŘEKNI NĚMECKY"
            LearningLanguage.FRENCH -> "ŘEKNI FRANCOUZSKY"
            LearningLanguage.ITALIAN -> "ŘEKNI ITALSKY"
            LearningLanguage.PORTUGUESE -> "ŘEKNI PORTUGALSKY"
            LearningLanguage.ENGLISH -> "ŘEKNI ANGLICKY"
            LearningLanguage.CZECH -> "ŘEKNI ČESKY"
        }
    },
    showHintButton = "Zobrazit nápovědu",
    badgeDontKnow = "Nevím ✗",
    badgeTryAgain = "Zkus znovu ✗",
    badgeKnow = "Vím ✓",
    badgeSpoken = "Vysloveno ✓",
    bannerSpokenCorrect = { heard -> "Správně vysloveno: „$heard“" },
    bannerSpokenWrong = { heard -> "✗ Slyšeno: „$heard“ – zkus to znovu" },
    bannerMarkedKnow = "Označeno jako Vím",
    bannerMarkedDontKnow = "Označeno jako Nevím",
    bannerMicOff = "Mikrofon vypnutý – použij tlačítka nebo posuň kartu",
    bannerHeard = { heard -> "Slyšeno: „$heard“" },
    bannerListening = "Poslouchám – rovnou řekni slovo anglicky",
    bannerListeningCzech = "Poslouchám – řekni překlad česky",
    bannerListeningForLanguage = { lang ->
        when (lang) {
            LearningLanguage.SPANISH -> "Poslouchám – rovnou řekni slovo španělsky"
            LearningLanguage.GERMAN -> "Poslouchám – rovnou řekni slovo německy"
            LearningLanguage.FRENCH -> "Poslouchám – rovnou řekni slovo francouzsky"
            LearningLanguage.ITALIAN -> "Poslouchám – rovnou řekni slovo italsky"
            LearningLanguage.PORTUGUESE -> "Poslouchám – rovnou řekni slovo portugalsky"
            LearningLanguage.ENGLISH -> "Poslouchám – rovnou řekni slovo anglicky"
            LearningLanguage.CZECH -> "Poslouchám – rovnou řekni slovo česky"
        }
    },
    bannerMicReady = "Mikrofon připraven – mluv nebo posuň kartu",
    summaryCompleteTitle = "Lekce dokončena!",
    summaryAccuracy = "Úspěšnost",
    summarySpoken = "Vysloveno 🎙️",
    summaryMissed = "Chybělo",
    summaryWordsOverview = "Přehled slovíček z lekce",
    summaryRetryMissed = { n -> "Procvičit znovu chybná slovíčka ($n)" },
    summaryRestartAll = "Celá sada",
    summaryBackToDecks = "Přehled sad",
    methodSpoken = "Vysloveno hlasem 🎙️",
    methodSwipedKnow = "Označeno: Vím 👉",
    methodSwipedDontKnow = "Označeno: Nevím 👈",
    appVersionInfo = { v -> "LingoStacks v$v" },
    initialLanguageWelcomeTitle = "Vítejte v LingoStacks!",
    initialLanguageWelcomeSubtitle = "Který jazyk se chcete učit?",
    initialLanguageChooseHint = "Volbu můžete kdykoliv změnit v Nastavení.",
    initialLanguageConfirmButton = "Začít se učit"
)

val englishStrings = UiStrings(
    isCzech = false,
    appHeaderSubtitle = "LINGOSTACKS",
    appHeaderTitle = "LingoStacks",
    learningLanguageTitle = "🌍 Language to Learn",
    allLevelsLabel = "All Levels (A1–C2)",
    remainingOfGoal = { rem, total -> "$rem of $total left" },
    remainingWordsOfGoal = { rem, total -> "$rem of $total words left" },
    dailyGoalProgress = { done, goal ->
        if (done >= goal) "Today: $done / $goal words (Goal reached! 🎉)" else "Today: $done / $goal words (${goal - done} left)"
    },
    sessionCardProgress = { current, total, todayDone ->
        "Card $current of $total • Today $todayDone words"
    },
    sessionMasteryProgress = { mastered, total, remaining ->
        if (remaining <= 0) "Mastered: $mastered / $total words • Completed! 🎉"
        else "Mastered: $mastered / $total words ($remaining left)"
    },
    wordsInDeck = { count -> "$count words in deck" },
    settingsAndLeaderboard = "Settings & Stats",
    tabGoalAndStats = "⚙️ Settings",
    tabLeaderboardAndFriends = "📊 Stats",
    appearanceTitle = "🌗 Appearance (Light / Dark)",
    lightModeLabel = "☀️ Light",
    darkModeLabel = "🌙 Dark",
    dailyGoalTitle = "Daily Word Goal",
    wordsCountBadge = { n -> "$n words" },
    dailyGoalSliderHint = "Slide the bar (5 to 200 words) to set your daily goal:",
    englishLevelTitle = "🇬🇧 English Level (A1 – C2)",
    languageLevelTitle = { lang ->
        when (lang) {
            LearningLanguage.SPANISH -> "🇪🇸 Spanish Level (A1 – C2)"
            LearningLanguage.GERMAN -> "🇩🇪 German Level (A1 – C2)"
            LearningLanguage.FRENCH -> "🇫🇷 French Level (A1 – C2)"
            LearningLanguage.ITALIAN -> "🇮🇹 Italian Level (A1 – C2)"
            LearningLanguage.PORTUGUESE -> "🇵🇹 Portuguese Level (A1 – C2)"
            LearningLanguage.ENGLISH -> "🇬🇧 English Level (A1 – C2)"
            LearningLanguage.CZECH -> "🇨🇿 Czech Level (A1 – C2)"
        }
    },
    allLevelsButton = "All Levels (A1 – C2)",
    allStatsTitle = "Learning Statistics",
    statStreakDays = { d -> "$d days" },
    statStreakLabel = "Daily Streak",
    statAccuracyLabel = "Accuracy",
    statSpokenLabel = "Spoken by Voice",
    statTodayGoalLabel = "Today's Goal",
    statTotalLearnedLabel = "Total Learned",
    statCompletedLessonsLabel = "Completed Lessons",
    leaderboardTitle = "🏆 Daily Leaderboard",
    leaderboardSubtitle = "Real users connected via Google accounts",
    leaderboardEmptyHint = "No accounts connected yet. Sign in below with your Google account to appear on the daily leaderboard.",
    leaderboardRowSub = { streak, spoken -> "🔥 $streak days • 🎙️ $spoken spoken" },
    leaderboardWordsToday = { n -> "$n words today" },
    inviteFriendTitle = "Invite a Friend to Share Progress",
    inviteFriendHint = { code -> "Your code is $code. Enter a friend's code or connected Gmail to see each other on the daily leaderboard:" },
    inviteInputPlaceholder = "Code SLOV-... or Gmail",
    shareInviteButton = "Share Invite with a Friend",
    shareChooserTitle = "Invite a Friend to Share Progress",
    shareMessage = { code -> "Join me learning vocabulary in LingoStacks and compete on the daily leaderboard! Enter my code in Settings: $code" },
    googleSectionTitle = "Google Account & Leaderboard Profile",
    googleSignOutHint = { _ -> "To join the daily leaderboard, please connect your Google account:" },
    googleSignInButton = "Sign in with Google",
    googleManualPrompt = "Enter your Google account details:",
    googleNameLabel = "Leaderboard Display Name",
    googleEmailLabel = "Gmail Address",
    googleConfirmButton = "Confirm & Sync",
    googleYourInviteCode = { code -> "Your code: $code" },
    googleSignOutButton = "Sign out",
    googleEditNameButton = "Edit Name",
    googleSaveNameButton = "Save",
    googleCancelButton = "Cancel",
    cardPromptSpeakEnglish = "SPEAK IN ENGLISH",
    cardPromptSpeakCzech = "SPEAK IN CZECH",
    cardPromptSpeakLanguage = { lang ->
        when (lang) {
            LearningLanguage.SPANISH -> "SPEAK IN SPANISH"
            LearningLanguage.GERMAN -> "SPEAK IN GERMAN"
            LearningLanguage.FRENCH -> "SPEAK IN FRENCH"
            LearningLanguage.ITALIAN -> "SPEAK IN ITALIAN"
            LearningLanguage.PORTUGUESE -> "SPEAK IN PORTUGUESE"
            LearningLanguage.ENGLISH -> "SPEAK IN ENGLISH"
            LearningLanguage.CZECH -> "SPEAK IN CZECH"
        }
    },
    showHintButton = "Show hint",
    badgeDontKnow = "Don't know ✗",
    badgeTryAgain = "Try again ✗",
    badgeKnow = "Know ✓",
    badgeSpoken = "Spoken ✓",
    bannerSpokenCorrect = { heard -> "Correct pronunciation: \"$heard\"" },
    bannerSpokenWrong = { heard -> "✗ Heard: \"$heard\" – try again" },
    bannerMarkedKnow = "Marked as Know",
    bannerMarkedDontKnow = "Marked as Don't Know",
    bannerMicOff = "Microphone OFF – use buttons or swipe card",
    bannerHeard = { heard -> "Heard: \"$heard\"" },
    bannerListening = "Listening – speak the English word now",
    bannerListeningCzech = "Listening – speak the translation in Czech",
    bannerListeningForLanguage = { lang ->
        when (lang) {
            LearningLanguage.SPANISH -> "Listening – speak the Spanish word now"
            LearningLanguage.GERMAN -> "Listening – speak the German word now"
            LearningLanguage.FRENCH -> "Listening – speak the French word now"
            LearningLanguage.ITALIAN -> "Listening – speak the Italian word now"
            LearningLanguage.PORTUGUESE -> "Listening – speak the Portuguese word now"
            LearningLanguage.ENGLISH -> "Listening – speak the English word now"
            LearningLanguage.CZECH -> "Listening – speak the Czech word now"
        }
    },
    bannerMicReady = "Microphone ready – speak or swipe card",
    summaryCompleteTitle = "Lesson completed!",
    summaryAccuracy = "Accuracy",
    summarySpoken = "Spoken 🎙️",
    summaryMissed = "Needs review",
    summaryWordsOverview = "Lesson Words Overview",
    summaryRetryMissed = { n -> "Practice missed words ($n)" },
    summaryRestartAll = "Restart all",
    summaryBackToDecks = "Back to decks",
    methodSpoken = "Spoken by voice 🎙️",
    methodSwipedKnow = "Marked: Know 👉",
    methodSwipedDontKnow = "Marked: Don't Know 👈",
    appVersionInfo = { v -> "LingoStacks v$v" },
    initialLanguageWelcomeTitle = "Welcome to LingoStacks!",
    initialLanguageWelcomeSubtitle = "Which language do you want to learn?",
    initialLanguageChooseHint = "You can change your target language anytime in Settings.",
    initialLanguageConfirmButton = "Start Learning"
)

val spanishStrings = UiStrings(
    isCzech = false,
    appHeaderSubtitle = "LINGOSTACKS",
    appHeaderTitle = "LingoStacks",
    learningLanguageTitle = "🌍 Idioma a aprender",
    allLevelsLabel = "Todos los niveles (A1–C2)",
    remainingOfGoal = { rem, total -> "Quedan $rem de $total" },
    remainingWordsOfGoal = { rem, total -> "Quedan $rem de $total palabras" },
    dailyGoalProgress = { done, goal ->
        if (done >= goal) "Hoy: $done / $goal palabras (¡Meta cumplida! 🎉)" else "Hoy: $done / $goal palabras (quedan ${goal - done})"
    },
    sessionCardProgress = { current, total, todayDone ->
        "Tarjeta $current de $total • Hoy $todayDone palabras"
    },
    sessionMasteryProgress = { mastered, total, remaining ->
        if (remaining <= 0) "Dominadas: $mastered / $total palabras • ¡Completado! 🎉"
        else "Dominadas: $mastered / $total palabras (quedan $remaining)"
    },
    wordsInDeck = { count -> "$count palabras en el mazo" },
    settingsAndLeaderboard = "Ajustes y estadísticas",
    tabGoalAndStats = "⚙️ Ajustes",
    tabLeaderboardAndFriends = "📊 Estadísticas",
    appearanceTitle = "🌗 Apariencia (Claro / Oscuro)",
    lightModeLabel = "☀️ Claro",
    darkModeLabel = "🌙 Oscuro",
    dailyGoalTitle = "Meta diaria de palabras",
    wordsCountBadge = { n -> "$n palabras" },
    dailyGoalSliderHint = "Desliza la barra (5 a 200 palabras) para configurar tu meta diaria:",
    englishLevelTitle = "🇬🇧 Nivel de inglés (A1 – C2)",
    languageLevelTitle = { lang -> "${lang.flagEmoji} Nivel de ${lang.titleEn} (A1 – C2)" },
    allLevelsButton = "Todos los niveles (A1 – C2)",
    allStatsTitle = "Estadísticas de aprendizaje",
    statStreakDays = { d -> "$d días" },
    statStreakLabel = "Racha diaria",
    statAccuracyLabel = "Precisión",
    statSpokenLabel = "Pronunciadas por voz",
    statTodayGoalLabel = "Meta de hoy",
    statTotalLearnedLabel = "Total aprendidas",
    statCompletedLessonsLabel = "Lecciones completadas",
    leaderboardTitle = "🏆 Clasificación diaria",
    leaderboardSubtitle = "Usuarios reales conectados con cuenta de Google",
    leaderboardEmptyHint = "Aún no hay cuentas conectadas. Inicia sesión abajo con Google para aparecer en la clasificación.",
    leaderboardRowSub = { streak, spoken -> "🔥 $streak días • 🎙️ $spoken pronunciadas" },
    leaderboardWordsToday = { n -> "$n palabras hoy" },
    inviteFriendTitle = "Invitar a un amigo",
    inviteFriendHint = { code -> "Tu código es $code. Introduce el código o Gmail de un amigo para veros en la clasificación:" },
    inviteInputPlaceholder = "Código SLOV-... o Gmail",
    shareInviteButton = "Compartir invitación con un amigo",
    shareChooserTitle = "Invitar a un amigo a compartir progreso",
    shareMessage = { code -> "¡Aprende vocabulario conmigo en LingoStacks y compite en la clasificación diaria! Introduce mi código en Ajustes: $code" },
    googleSectionTitle = "Cuenta de Google y perfil",
    googleSignOutHint = { _ -> "Para participar en la clasificación diaria, conecta tu cuenta de Google:" },
    googleSignInButton = "Iniciar sesión con Google",
    googleManualPrompt = "Introduce los datos de tu cuenta de Google:",
    googleNameLabel = "Nombre en la clasificación",
    googleEmailLabel = "Dirección de Gmail",
    googleConfirmButton = "Confirmar y sincronizar",
    googleYourInviteCode = { code -> "Tu código: $code" },
    googleSignOutButton = "Cerrar sesión",
    googleEditNameButton = "Editar nombre",
    googleSaveNameButton = "Guardar",
    googleCancelButton = "Cancelar",
    cardPromptSpeakEnglish = "HABLA EN INGLÉS",
    cardPromptSpeakCzech = "HABLA EN CHECO",
    cardPromptSpeakLanguage = { lang -> "HABLA EN ${lang.titleEn.uppercase()}" },
    showHintButton = "Mostrar pista",
    badgeDontKnow = "No sé ✗",
    badgeTryAgain = "Intenta de nuevo ✗",
    badgeKnow = "Sé ✓",
    badgeSpoken = "Pronunciado ✓",
    bannerSpokenCorrect = { heard -> "¡Correcto! Has dicho: «$heard»" },
    bannerSpokenWrong = { heard -> "✗ Escuchado: «$heard» – intenta de nuevo" },
    bannerMarkedKnow = "Marcado como Sé",
    bannerMarkedDontKnow = "Marcado como No sé",
    bannerMicOff = "Micrófono desactivado – usa los botones o desliza la tarjeta",
    bannerHeard = { heard -> "Escuchado: «$heard»" },
    bannerListening = "Escuchando – di la palabra ahora",
    bannerListeningCzech = "Escuchando – di la traducción en checo",
    bannerListeningForLanguage = { lang -> "Escuchando – di la palabra en ${lang.titleEn} ahora" },
    bannerMicReady = "Micrófono listo – habla o desliza la tarjeta",
    summaryCompleteTitle = "¡Lección completada!",
    summaryAccuracy = "Precisión",
    summarySpoken = "Pronunciadas 🎙️",
    summaryMissed = "Por repasar",
    summaryWordsOverview = "Resumen de palabras de la lección",
    summaryRetryMissed = { n -> "Practicar palabras falladas ($n)" },
    summaryRestartAll = "Reiniciar todo",
    summaryBackToDecks = "Volver a mazos",
    methodSpoken = "Pronunciado con voz 🎙️",
    methodSwipedKnow = "Marcado: Sé 👉",
    methodSwipedDontKnow = "Marcado: No sé 👈",
    appVersionInfo = { v -> "LingoStacks v$v" },
    initialLanguageWelcomeTitle = "¡Bienvenido a LingoStacks!",
    initialLanguageWelcomeSubtitle = "¿Qué idioma quieres aprender?",
    initialLanguageChooseHint = "Puedes cambiar tu idioma objetivo en cualquier momento en Ajustes.",
    initialLanguageConfirmButton = "Empezar a aprender"
)

val germanStrings = UiStrings(
    isCzech = false,
    appHeaderSubtitle = "LINGOSTACKS",
    appHeaderTitle = "LingoStacks",
    learningLanguageTitle = "🌍 Zielsprache zum Lernen",
    allLevelsLabel = "Alle Niveaus (A1–C2)",
    remainingOfGoal = { rem, total -> "Noch $rem von $total" },
    remainingWordsOfGoal = { rem, total -> "Noch $rem von $total Wörtern" },
    dailyGoalProgress = { done, goal ->
        if (done >= goal) "Heute: $done / $goal Wörter (Ziel erreicht! 🎉)" else "Heute: $done / $goal Wörter (noch ${goal - done})"
    },
    sessionCardProgress = { current, total, todayDone ->
        "Karte $current von $total • Heute $todayDone Wörter"
    },
    sessionMasteryProgress = { mastered, total, remaining ->
        if (remaining <= 0) "Gemeistert: $mastered / $total Wörter • Abgeschlossen! 🎉"
        else "Gemeistert: $mastered / $total Wörter (noch $remaining)"
    },
    wordsInDeck = { count -> "$count Wörter im Stapel" },
    settingsAndLeaderboard = "Einstellungen & Statistiken",
    tabGoalAndStats = "⚙️ Einstellungen",
    tabLeaderboardAndFriends = "📊 Statistiken",
    appearanceTitle = "🌗 Erscheinungsbild (Hell / Dunkel)",
    lightModeLabel = "☀️ Hell",
    darkModeLabel = "🌙 Dunkel",
    dailyGoalTitle = "Tägliches Wortziel",
    wordsCountBadge = { n -> "$n Wörter" },
    dailyGoalSliderHint = "Bewege den Regler (5 bis 200 Wörter) für dein Tagesziel:",
    englishLevelTitle = "🇬🇧 Englischniveau (A1 – C2)",
    languageLevelTitle = { lang -> "${lang.flagEmoji} Sprachniveau ${lang.titleEn} (A1 – C2)" },
    allLevelsButton = "Alle Niveaus (A1 – C2)",
    allStatsTitle = "Lernstatistiken",
    statStreakDays = { d -> "$d Tage" },
    statStreakLabel = "Tages-Streak",
    statAccuracyLabel = "Genauigkeit",
    statSpokenLabel = "Per Stimme gesprochen",
    statTodayGoalLabel = "Heutiges Ziel",
    statTotalLearnedLabel = "Gesamt gelernt",
    statCompletedLessonsLabel = "Abgeschlossene Lektionen",
    leaderboardTitle = "🏆 Tägliche Rangliste",
    leaderboardSubtitle = "Echte Nutzer mit Google-Konto verbunden",
    leaderboardEmptyHint = "Noch keine Konten verbunden. Melde dich unten mit deinem Google-Konto an, um in der Rangliste zu erscheinen.",
    leaderboardRowSub = { streak, spoken -> "🔥 $streak Tage • 🎙️ $spoken gesprochen" },
    leaderboardWordsToday = { n -> "$n Wörter heute" },
    inviteFriendTitle = "Freund einladen",
    inviteFriendHint = { code -> "Dein Code ist $code. Gib den Code oder die Gmail eines Freundes ein, um euch in der Rangliste zu sehen:" },
    inviteInputPlaceholder = "Code SLOV-... oder Gmail",
    shareInviteButton = "Einladung an einen Freund teilen",
    shareChooserTitle = "Freund zum gemeinsamen Lernen einladen",
    shareMessage = { code -> "Lerne mit mir Vokabeln in LingoStacks und miss dich in der täglichen Rangliste! Gib in den Einstellungen meinen Code ein: $code" },
    googleSectionTitle = "Google-Konto & Ranglisten-Profil",
    googleSignOutHint = { _ -> "Um an der täglichen Rangliste teilzunehmen, verbinde bitte dein Google-Konto:" },
    googleSignInButton = "Mit Google anmelden",
    googleManualPrompt = "Gib deine Google-Kontodaten ein:",
    googleNameLabel = "Name in der Rangliste",
    googleEmailLabel = "Gmail-Adresse",
    googleConfirmButton = "Bestätigen & Synchronisieren",
    googleYourInviteCode = { code -> "Dein Code: $code" },
    googleSignOutButton = "Abmelden",
    googleEditNameButton = "Namen bearbeiten",
    googleSaveNameButton = "Speichern",
    googleCancelButton = "Abbrechen",
    cardPromptSpeakEnglish = "SPRICH AUF ENGLISCH",
    cardPromptSpeakCzech = "SPRICH AUF TSCHECHISCH",
    cardPromptSpeakLanguage = { lang -> "SPRICH AUF ${lang.titleEn.uppercase()}" },
    showHintButton = "Hinweis anzeigen",
    badgeDontKnow = "Nicht gewusst ✗",
    badgeTryAgain = "Nochmal versuchen ✗",
    badgeKnow = "Gewusst ✓",
    badgeSpoken = "Gesprochen ✓",
    bannerSpokenCorrect = { heard -> "Richtig gesprochen: „$heard“" },
    bannerSpokenWrong = { heard -> "✗ Gehört: „$heard“ – versuche es nochmal" },
    bannerMarkedKnow = "Als Gewusst markiert",
    bannerMarkedDontKnow = "Als Nicht gewusst markiert",
    bannerMicOff = "Mikrofon aus – nutze Tasten oder wische die Karte",
    bannerHeard = { heard -> "Gehört: „$heard“" },
    bannerListening = "Höre zu – sprich das Wort jetzt",
    bannerListeningCzech = "Höre zu – sprich die Übersetzung auf Tschechisch",
    bannerListeningForLanguage = { lang -> "Höre zu – sprich das Wort auf ${lang.titleEn} jetzt" },
    bannerMicReady = "Mikrofon bereit – sprich oder wische die Karte",
    summaryCompleteTitle = "Lektion abgeschlossen!",
    summaryAccuracy = "Genauigkeit",
    summarySpoken = "Gesprochen 🎙️",
    summaryMissed = "Zu wiederholen",
    summaryWordsOverview = "Wortübersicht der Lektion",
    summaryRetryMissed = { n -> "Fehlerhafte Wörter üben ($n)" },
    summaryRestartAll = "Alles neu starten",
    summaryBackToDecks = "Zurück zu Stapeln",
    methodSpoken = "Mit Stimme gesprochen 🎙️",
    methodSwipedKnow = "Markiert: Gewusst 👉",
    methodSwipedDontKnow = "Markiert: Nicht gewusst 👈",
    appVersionInfo = { v -> "LingoStacks v$v" },
    initialLanguageWelcomeTitle = "Willkommen bei LingoStacks!",
    initialLanguageWelcomeSubtitle = "Welche Sprache möchtest du lernen?",
    initialLanguageChooseHint = "Du kannst die Zielsprache jederzeit in den Einstellungen ändern.",
    initialLanguageConfirmButton = "Jetzt lernen"
)

val frenchStrings = UiStrings(
    isCzech = false,
    appHeaderSubtitle = "LINGOSTACKS",
    appHeaderTitle = "LingoStacks",
    learningLanguageTitle = "🌍 Langue à apprendre",
    allLevelsLabel = "Tous les niveaux (A1–C2)",
    remainingOfGoal = { rem, total -> "Reste $rem sur $total" },
    remainingWordsOfGoal = { rem, total -> "Reste $rem sur $total mots" },
    dailyGoalProgress = { done, goal ->
        if (done >= goal) "Aujourd'hui: $done / $goal mots (Objectif atteint! 🎉)" else "Aujourd'hui: $done / $goal mots (reste ${goal - done})"
    },
    sessionCardProgress = { current, total, todayDone ->
        "Carte $current sur $total • Aujourd'hui $todayDone mots"
    },
    sessionMasteryProgress = { mastered, total, remaining ->
        if (remaining <= 0) "Maîtrisés: $mastered / $total mots • Terminé! 🎉"
        else "Maîtrisés: $mastered / $total mots (reste $remaining)"
    },
    wordsInDeck = { count -> "$count mots dans le paquet" },
    settingsAndLeaderboard = "Paramètres & Statistiques",
    tabGoalAndStats = "⚙️ Paramètres",
    tabLeaderboardAndFriends = "📊 Statistiques",
    appearanceTitle = "🌗 Apparence (Clair / Sombre)",
    lightModeLabel = "☀️ Clair",
    darkModeLabel = "🌙 Sombre",
    dailyGoalTitle = "Objectif quotidien de mots",
    wordsCountBadge = { n -> "$n mots" },
    dailyGoalSliderHint = "Faites glisser la barre (5 à 200 mots) pour définir votre objectif:",
    englishLevelTitle = "🇬🇧 Niveau d'anglais (A1 – C2)",
    languageLevelTitle = { lang -> "${lang.flagEmoji} Niveau de ${lang.titleEn} (A1 – C2)" },
    allLevelsButton = "Tous les niveaux (A1 – C2)",
    allStatsTitle = "Statistiques d'apprentissage",
    statStreakDays = { d -> "$d jours" },
    statStreakLabel = "Série quotidienne",
    statAccuracyLabel = "Précision",
    statSpokenLabel = "Prononcés à la voix",
    statTodayGoalLabel = "Objectif du jour",
    statTotalLearnedLabel = "Total appris",
    statCompletedLessonsLabel = "Leçons terminées",
    leaderboardTitle = "🏆 Classement quotidien",
    leaderboardSubtitle = "Vrais utilisateurs connectés avec Google",
    leaderboardEmptyHint = "Aucun compte connecté. Connectez-vous ci-dessous avec Google pour apparaître dans le classement.",
    leaderboardRowSub = { streak, spoken -> "🔥 $streak jours • 🎙️ $spoken prononcés" },
    leaderboardWordsToday = { n -> "$n mots aujourd'hui" },
    inviteFriendTitle = "Inviter un ami",
    inviteFriendHint = { code -> "Votre code est $code. Entrez le code ou le Gmail d'un ami pour vous voir dans le classement:" },
    inviteInputPlaceholder = "Code SLOV-... ou Gmail",
    shareInviteButton = "Partager l'invitation avec un ami",
    shareChooserTitle = "Inviter un ami à partager sa progression",
    shareMessage = { code -> "Apprends du vocabulaire avec moi sur LingoStacks et défie-moi au classement quotidien! Entre mon code dans les Paramètres: $code" },
    googleSectionTitle = "Compte Google et profil",
    googleSignOutHint = { _ -> "Pour rejoindre le classement quotidien, veuillez vous connecter avec Google:" },
    googleSignInButton = "Se connecter avec Google",
    googleManualPrompt = "Entrez les identifiants de votre compte Google:",
    googleNameLabel = "Nom dans le classement",
    googleEmailLabel = "Adresse Gmail",
    googleConfirmButton = "Confirmer et synchroniser",
    googleYourInviteCode = { code -> "Votre code: $code" },
    googleSignOutButton = "Se déconnecter",
    googleEditNameButton = "Modifier le nom",
    googleSaveNameButton = "Enregistrer",
    googleCancelButton = "Annuler",
    cardPromptSpeakEnglish = "DIS EN ANGLAIS",
    cardPromptSpeakCzech = "DIS EN TCHÈQUE",
    cardPromptSpeakLanguage = { lang -> "DIS EN ${lang.titleEn.uppercase()}" },
    showHintButton = "Afficher l'indice",
    badgeDontKnow = "Je ne sais pas ✗",
    badgeTryAgain = "Réessaie ✗",
    badgeKnow = "Je sais ✓",
    badgeSpoken = "Prononcé ✓",
    bannerSpokenCorrect = { heard -> "Correctement prononcé: «$heard»" },
    bannerSpokenWrong = { heard -> "✗ Entendu: «$heard» – réessaie" },
    bannerMarkedKnow = "Marqué comme Je sais",
    bannerMarkedDontKnow = "Marqué comme Je ne sais pas",
    bannerMicOff = "Micro désactivé – utilise les boutons ou glisse la carte",
    bannerHeard = { heard -> "Entendu: «$heard»" },
    bannerListening = "À l'écoute – dis le mot maintenant",
    bannerListeningCzech = "À l'écoute – dis la traduction en tchèque",
    bannerListeningForLanguage = { lang -> "À l'écoute – dis le mot en ${lang.titleEn} maintenant" },
    bannerMicReady = "Micro prêt – parle ou glisse la carte",
    summaryCompleteTitle = "Leçon terminée!",
    summaryAccuracy = "Précision",
    summarySpoken = "Prononcés 🎙️",
    summaryMissed = "À revoir",
    summaryWordsOverview = "Aperçu des mots de la leçon",
    summaryRetryMissed = { n -> "Pratiquer les mots manqués ($n)" },
    summaryRestartAll = "Tout recommencer",
    summaryBackToDecks = "Retour aux paquets",
    methodSpoken = "Prononcé à la voix 🎙️",
    methodSwipedKnow = "Marqué: Je sais 👉",
    methodSwipedDontKnow = "Marqué: Je ne sais pas 👈",
    appVersionInfo = { v -> "LingoStacks v$v" },
    initialLanguageWelcomeTitle = "Bienvenue sur LingoStacks!",
    initialLanguageWelcomeSubtitle = "Quelle langue voulez-vous apprendre?",
    initialLanguageChooseHint = "Vous pouvez changer de langue cible à tout moment dans les Paramètres.",
    initialLanguageConfirmButton = "Commencer à apprendre"
)

val italianStrings = UiStrings(
    isCzech = false,
    appHeaderSubtitle = "LINGOSTACKS",
    appHeaderTitle = "LingoStacks",
    learningLanguageTitle = "🌍 Lingua da imparare",
    allLevelsLabel = "Tutti i livelli (A1–C2)",
    remainingOfGoal = { rem, total -> "Mancano $rem di $total" },
    remainingWordsOfGoal = { rem, total -> "Mancano $rem di $total parole" },
    dailyGoalProgress = { done, goal ->
        if (done >= goal) "Oggi: $done / $goal parole (Obiettivo raggiunto! 🎉)" else "Oggi: $done / $goal parole (mancano ${goal - done})"
    },
    sessionCardProgress = { current, total, todayDone ->
        "Carta $current di $total • Oggi $todayDone parole"
    },
    sessionMasteryProgress = { mastered, total, remaining ->
        if (remaining <= 0) "Padroneggiate: $mastered / $total parole • Completato! 🎉"
        else "Padroneggiate: $mastered / $total parole (mancano $remaining)"
    },
    wordsInDeck = { count -> "$count parole nel mazzo" },
    settingsAndLeaderboard = "Impostazioni e statistiche",
    tabGoalAndStats = "⚙️ Impostazioni",
    tabLeaderboardAndFriends = "📊 Statistiche",
    appearanceTitle = "🌗 Aspetto (Chiaro / Scuro)",
    lightModeLabel = "☀️ Chiaro",
    darkModeLabel = "🌙 Scuro",
    dailyGoalTitle = "Obiettivo giornaliero",
    wordsCountBadge = { n -> "$n parole" },
    dailyGoalSliderHint = "Fai scorrere la barra (da 5 a 200 parole) per impostare il tuo obiettivo:",
    englishLevelTitle = "🇬🇧 Livello di inglese (A1 – C2)",
    languageLevelTitle = { lang -> "${lang.flagEmoji} Livello di ${lang.titleEn} (A1 – C2)" },
    allLevelsButton = "Tutti i livelli (A1 – C2)",
    allStatsTitle = "Statistiche di apprendimento",
    statStreakDays = { d -> "$d giorni" },
    statStreakLabel = "Serie giornaliera",
    statAccuracyLabel = "Precisione",
    statSpokenLabel = "Pronunciate a voce",
    statTodayGoalLabel = "Obiettivo di oggi",
    statTotalLearnedLabel = "Totale imparate",
    statCompletedLessonsLabel = "Lezioni completate",
    leaderboardTitle = "🏆 Classifica giornaliera",
    leaderboardSubtitle = "Utenti reali collegati tramite account Google",
    leaderboardEmptyHint = "Nessun account ancora collegato. Accedi sotto con Google per apparire nella classifica.",
    leaderboardRowSub = { streak, spoken -> "🔥 $streak giorni • 🎙️ $spoken pronunciate" },
    leaderboardWordsToday = { n -> "$n parole oggi" },
    inviteFriendTitle = "Invita un amico",
    inviteFriendHint = { code -> "Il tuo codice è $code. Inserisci il codice o l'email Gmail di un amico per vedervi nella classifica:" },
    inviteInputPlaceholder = "Codice SLOV-... o Gmail",
    shareInviteButton = "Condividi invito con un amico",
    shareChooserTitle = "Invita un amico a condividere i progressi",
    shareMessage = { code -> "Vieni a imparare i vocaboli con me su LingoStacks e sfidami nella classifica giornaliera! Inserisci il mio codice nelle Impostazioni: $code" },
    googleSectionTitle = "Account Google e profilo",
    googleSignOutHint = { _ -> "Per entrare nella classifica giornaliera, collega il tuo account Google:" },
    googleSignInButton = "Accedi con Google",
    googleManualPrompt = "Inserisci i dati del tuo account Google:",
    googleNameLabel = "Nome nella classifica",
    googleEmailLabel = "Indirizzo Gmail",
    googleConfirmButton = "Conferma e sincronizza",
    googleYourInviteCode = { code -> "Il tuo codice: $code" },
    googleSignOutButton = "Esci",
    googleEditNameButton = "Modifica nome",
    googleSaveNameButton = "Salva",
    googleCancelButton = "Annulla",
    cardPromptSpeakEnglish = "DICI IN INGLESE",
    cardPromptSpeakCzech = "DICI IN CECO",
    cardPromptSpeakLanguage = { lang -> "DICI IN ${lang.titleEn.uppercase()}" },
    showHintButton = "Mostra suggerimento",
    badgeDontKnow = "Non so ✗",
    badgeTryAgain = "Riprova ✗",
    badgeKnow = "Lo so ✓",
    badgeSpoken = "Pronunciato ✓",
    bannerSpokenCorrect = { heard -> "Pronunciato correttamente: «$heard»" },
    bannerSpokenWrong = { heard -> "✗ Ascoltato: «$heard» – riprova" },
    bannerMarkedKnow = "Contrassegnato come Lo so",
    bannerMarkedDontKnow = "Contrassegnato come Non so",
    bannerMicOff = "Microfono disattivato – usa i pulsanti o fai scorrere la carta",
    bannerHeard = { heard -> "Ascoltato: «$heard»" },
    bannerListening = "In ascolto – pronuncia la parola ora",
    bannerListeningCzech = "In ascolto – pronuncia la traduzione in ceco",
    bannerListeningForLanguage = { lang -> "In ascolto – pronuncia la parola in ${lang.titleEn} ora" },
    bannerMicReady = "Microfono pronto – parla o fai scorrere la carta",
    summaryCompleteTitle = "Lezione completata!",
    summaryAccuracy = "Precisione",
    summarySpoken = "Pronunciate 🎙️",
    summaryMissed = "Da ripassare",
    summaryWordsOverview = "Panoramica delle parole della lezione",
    summaryRetryMissed = { n -> "Esercitati sulle parole sbagliate ($n)" },
    summaryRestartAll = "Ricomincia tutto",
    summaryBackToDecks = "Torna ai mazzi",
    methodSpoken = "Pronunciato a voce 🎙️",
    methodSwipedKnow = "Contrassegnato: Lo so 👉",
    methodSwipedDontKnow = "Contrassegnato: Non so 👈",
    appVersionInfo = { v -> "LingoStacks v$v" },
    initialLanguageWelcomeTitle = "Benvenuto su LingoStacks!",
    initialLanguageWelcomeSubtitle = "Quale lingua vuoi imparare?",
    initialLanguageChooseHint = "Puoi cambiare la lingua scelta in qualsiasi momento nelle Impostazioni.",
    initialLanguageConfirmButton = "Inizia a imparare"
)

val portugueseStrings = UiStrings(
    isCzech = false,
    appHeaderSubtitle = "Pratique a pronúncia falando em voz alta",
    appHeaderTitle = "LingoStacks",
    learningLanguageTitle = "Idioma que estou aprendendo",
    allLevelsLabel = "Todos os níveis (A1 – C2)",
    remainingOfGoal = { rem, total -> "Faltam $rem de $total palavras hoje" },
    remainingWordsOfGoal = { rem, total -> "Faltam $rem de $total palavras" },
    dailyGoalProgress = { done, goal ->
        if (done >= goal) "Hoje: $done / $goal palavras (Meta alcançada! 🎉)" else "Hoje: $done / $goal palavras (faltam ${goal - done})"
    },
    sessionCardProgress = { current, total, todayDone ->
        "Cartão $current de $total • Hoje $todayDone palavras"
    },
    sessionMasteryProgress = { mastered, total, remaining ->
        if (remaining <= 0) "Dominadas: $mastered / $total palavras • Concluído! 🎉"
        else "Dominadas: $mastered / $total palavras (faltam $remaining)"
    },
    wordsInDeck = { count -> "$count palavras no baralho" },
    settingsAndLeaderboard = "Configurações e estatísticas",
    tabGoalAndStats = "⚙️ Configurações",
    tabLeaderboardAndFriends = "📊 Estatísticas",
    appearanceTitle = "🌗 Tema (Claro / Escuro)",
    lightModeLabel = "☀️ Claro",
    darkModeLabel = "🌙 Escuro",
    dailyGoalTitle = "Meta diária",
    wordsCountBadge = { n -> "$n palavras" },
    dailyGoalSliderHint = "Arraste a barra (de 5 a 200 palavras) para definir sua meta:",
    englishLevelTitle = "🇬🇧 Nível de inglês (A1 – C2)",
    languageLevelTitle = { lang -> "${lang.flagEmoji} Nível de ${lang.titleEn} (A1 – C2)" },
    allLevelsButton = "Todos os níveis (A1 – C2)",
    allStatsTitle = "Estatísticas de aprendizagem",
    statStreakDays = { d -> "$d dias" },
    statStreakLabel = "Sequência diária",
    statAccuracyLabel = "Precisão",
    statSpokenLabel = "Faladas por voz",
    statTodayGoalLabel = "Meta de hoje",
    statTotalLearnedLabel = "Total aprendidas",
    statCompletedLessonsLabel = "Lições concluídas",
    leaderboardTitle = "🏆 Classificação diária",
    leaderboardSubtitle = "Usuários reais conectados com conta Google",
    leaderboardEmptyHint = "Nenhuma conta conectada ainda. Conecte sua conta Google abaixo para aparecer na classificação.",
    leaderboardRowSub = { streak, spoken -> "🔥 $streak dias • 🎙️ $spoken faladas" },
    leaderboardWordsToday = { n -> "$n palavras hoje" },
    inviteFriendTitle = "Convidar um amigo",
    inviteFriendHint = { code -> "Seu código é $code. Insira o código ou e-mail de um amigo para se verem na classificação:" },
    inviteInputPlaceholder = "Código SLOV-... ou Gmail",
    shareInviteButton = "Compartilhar convite",
    shareChooserTitle = "Convidar amigo para aprender juntos",
    shareMessage = { code -> "Venha aprender vocabulário comigo no LingoStacks e dispute comigo na classificação diária! Insira meu código nas Configurações: $code" },
    googleSectionTitle = "Conta Google e perfil",
    googleSignOutHint = { _ -> "Para entrar na classificação diária, conecte sua conta Google:" },
    googleSignInButton = "Entrar com o Google",
    googleManualPrompt = "Insira os dados da sua conta Google:",
    googleNameLabel = "Nome na classificação",
    googleEmailLabel = "Endereço Gmail",
    googleConfirmButton = "Confirmar e sincronizar",
    googleYourInviteCode = { code -> "Seu código: $code" },
    googleSignOutButton = "Sair",
    googleEditNameButton = "Editar nome",
    googleSaveNameButton = "Salvar",
    googleCancelButton = "Cancelar",
    cardPromptSpeakEnglish = "DIGA EM INGLÊS",
    cardPromptSpeakCzech = "DIGA EM TCHECO",
    cardPromptSpeakLanguage = { lang -> "DIGA EM ${lang.titleEn.uppercase()}" },
    showHintButton = "Mostrar dica",
    badgeDontKnow = "Não sei ✗",
    badgeTryAgain = "Tentar de novo ✗",
    badgeKnow = "Eu sei ✓",
    badgeSpoken = "Falado ✓",
    bannerSpokenCorrect = { heard -> "Pronunciado corretamente: «$heard»" },
    bannerSpokenWrong = { heard -> "✗ Ouvido: «$heard» – tente novamente" },
    bannerMarkedKnow = "Marcado como Eu sei",
    bannerMarkedDontKnow = "Marcado como Não sei",
    bannerMicOff = "Microfone desativado – use os botões ou deslize o cartão",
    bannerHeard = { heard -> "Ouvido: «$heard»" },
    bannerListening = "Ouvindo – fale a palavra agora",
    bannerListeningCzech = "Ouvindo – fale a tradução em tcheco",
    bannerListeningForLanguage = { lang -> "Ouvindo – fale a palavra em ${lang.titleEn} agora" },
    bannerMicReady = "Microfone pronto – fale ou deslize o cartão",
    summaryCompleteTitle = "Lição concluída!",
    summaryAccuracy = "Precisão",
    summarySpoken = "Faladas 🎙️",
    summaryMissed = "Para revisar",
    summaryWordsOverview = "Resumo das palavras da lição",
    summaryRetryMissed = { n -> "Praticar palavras erradas ($n)" },
    summaryRestartAll = "Recomeçar tudo",
    summaryBackToDecks = "Voltar aos baralhos",
    methodSpoken = "Falado por voz 🎙️",
    methodSwipedKnow = "Marcado: Eu sei 👉",
    methodSwipedDontKnow = "Marcado: Não sei 👈",
    appVersionInfo = { v -> "LingoStacks v$v" },
    initialLanguageWelcomeTitle = "Bem-vindo ao LingoStacks!",
    initialLanguageWelcomeSubtitle = "Qual idioma você quer aprender?",
    initialLanguageChooseHint = "Você pode alterar o idioma a qualquer momento nas Configurações.",
    initialLanguageConfirmButton = "Começar a aprender"
)

fun getUiStrings(isCs: Boolean): UiStrings = if (isCs) czechStrings else englishStrings

fun getUiStrings(lang: LearningLanguage): UiStrings = when (lang) {
    LearningLanguage.CZECH -> czechStrings
    LearningLanguage.SPANISH -> spanishStrings
    LearningLanguage.GERMAN -> germanStrings
    LearningLanguage.FRENCH -> frenchStrings
    LearningLanguage.ITALIAN -> italianStrings
    LearningLanguage.PORTUGUESE -> portugueseStrings
    LearningLanguage.ENGLISH -> englishStrings
}

@Composable
fun rememberAppStrings(nativeLanguage: LearningLanguage? = null): UiStrings {
    val config = LocalConfiguration.current
    val lang = if (nativeLanguage != null) {
        nativeLanguage
    } else {
        val sysLang = if (config.locales.isEmpty) {
            Locale.getDefault().language
        } else {
            config.locales[0].language
        }
        when {
            sysLang.startsWith("cs") || sysLang.startsWith("sk") -> LearningLanguage.CZECH
            sysLang.startsWith("es") -> LearningLanguage.SPANISH
            sysLang.startsWith("de") -> LearningLanguage.GERMAN
            sysLang.startsWith("fr") -> LearningLanguage.FRENCH
            sysLang.startsWith("it") -> LearningLanguage.ITALIAN
            sysLang.startsWith("pt") -> LearningLanguage.PORTUGUESE
            else -> LearningLanguage.ENGLISH
        }
    }
    return getUiStrings(lang)
}
