package cz.slovickaspeak.app.ui

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.core.content.ContextCompat
import androidx.activity.result.contract.ActivityResultContracts
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.android.gms.common.api.ApiException
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.SystemUpdate
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import cz.slovickaspeak.app.data.FriendProgress
import cz.slovickaspeak.app.data.LanguageCompleteStats
import cz.slovickaspeak.app.data.LeaderboardPeriod
import cz.slovickaspeak.app.data.ProgressStateHolder
import cz.slovickaspeak.app.model.LearningLanguage
import cz.slovickaspeak.app.model.StudyDirection
import cz.slovickaspeak.app.ui.theme.*
import kotlinx.coroutines.delay

@Composable
fun AppleFullSettingsDialog(
    progressState: ProgressStateHolder,
    initialTab: Int = 0,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val strings = rememberAppStrings(progressState.nativeLanguage)
    var selectedTab by remember { mutableIntStateOf(initialTab) } // 0 = Goal & Stats, 1 = Leaderboard
    var showCompleteStatsInModal by remember { mutableStateOf(false) }
    var showGoogleEmailPrompt by remember { mutableStateOf(false) }
    var manualGoogleEmail by remember { mutableStateOf("") }
    var manualGoogleName by remember { mutableStateOf("") }

    // Sync cloud leaderboard ONLY while Leaderboard tab is visible, in foreground (RESUMED), and user is signed in
    val lifecycleOwner = LocalLifecycleOwner.current
    LaunchedEffect(Unit) {
        progressState.pushMyProgressToCloud(force = true)
    }
    LaunchedEffect(progressState.googleUser) {
        showGoogleEmailPrompt = false
    }
    LaunchedEffect(selectedTab, progressState.googleUser, showCompleteStatsInModal, lifecycleOwner) {
        if (selectedTab == 1 && progressState.googleUser != null && !showCompleteStatsInModal) {
            lifecycleOwner.lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) {
                while (true) {
                    progressState.refreshFromCloud()
                    delay(30_000L)
                }
            }
        }
    }

    val googleSignInClient = remember(context) {
        val gso = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
            .requestIdToken(context.getString(cz.slovickaspeak.app.R.string.default_web_client_id))
            .requestEmail()
            .build()
        GoogleSignIn.getClient(context, gso)
    }
    val googleAccountLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        try {
            val task = GoogleSignIn.getSignedInAccountFromIntent(result.data)
            val account = task.getResult(ApiException::class.java)
            val idToken = account?.idToken
            val email = account?.email
            if (!idToken.isNullOrBlank() && !email.isNullOrBlank()) {
                val derivedName = account.displayName?.takeIf { it.isNotBlank() }
                    ?: email.substringBefore("@")
                        .split(".", "_", "-")
                        .joinToString(" ") { part -> part.replaceFirstChar { it.uppercase() } }
                progressState.signInWithGoogleFirebase(idToken, derivedName, email)
                showGoogleEmailPrompt = false
            } else if (progressState.googleUser == null) {
                showGoogleEmailPrompt = true
            }
        } catch (_: ApiException) {
            if (progressState.googleUser == null) showGoogleEmailPrompt = true
        } catch (_: Exception) {
            if (progressState.googleUser == null) showGoogleEmailPrompt = true
        }
    }

    if (showCompleteStatsInModal) {
        AppleCompleteStatsDialog(
            progressState = progressState,
            onOpenLeaderboard = {
                showCompleteStatsInModal = false
                selectedTab = 1
            },
            onDismiss = { showCompleteStatsInModal = false }
        )
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            shape = RoundedCornerShape(28.dp),
            color = SurfaceBg,
            border = BorderStroke(0.5.dp, CardBorderGray),
            modifier = Modifier
                .fillMaxWidth(0.94f)
                .fillMaxHeight(0.90f)
                .shadow(24.dp, RoundedCornerShape(28.dp), spotColor = Color.Black.copy(alpha = 0.18f))
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                // Top Modal Header
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            text = strings.settingsAndLeaderboard,
                            fontSize = 22.sp,
                            fontWeight = FontWeight.Bold,
                            color = TextDark
                        )
                        Text(
                            text = progressState.lastSyncStatus,
                            fontSize = 11.sp,
                            color = if (progressState.lastSyncOnlineOk) {
                                if (isDarkThemeActive) AppleGreen else AppleGreenDark
                            } else {
                                AppleOrange
                            },
                            fontWeight = FontWeight.Medium
                        )
                    }
                    Box(
                        modifier = Modifier
                            .size(32.dp)
                            .clip(CircleShape)
                            .background(CardSurface)
                            .clickable(onClick = onDismiss),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = null,
                            tint = TextMuted,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }

                // iOS Segmented Tab Switcher
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = AppleSecondaryFill,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(3.dp),
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        listOf(strings.tabGoalAndStats, strings.tabLeaderboardAndFriends).forEachIndexed { idx, title ->
                            val selected = selectedTab == idx
                            Surface(
                                shape = RoundedCornerShape(9.dp),
                                color = if (selected) CardElevatedSurface else Color.Transparent,
                                border = if (selected) BorderStroke(1.dp, if (cz.slovickaspeak.app.ui.theme.isDarkThemeActive) AppleBlue.copy(alpha = 0.6f) else Color.Black.copy(alpha = 0.08f)) else null,
                                shadowElevation = if (selected) 2.dp else 0.dp,
                                modifier = Modifier
                                    .weight(1f)
                                    .clip(RoundedCornerShape(9.dp))
                                    .clickable { selectedTab = idx }
                            ) {
                                Text(
                                    text = title,
                                    color = if (selected) (if (cz.slovickaspeak.app.ui.theme.isDarkThemeActive) Color.White else TextDark) else TextMuted,
                                    fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                                    fontSize = 13.sp,
                                    modifier = Modifier
                                        .padding(vertical = 8.dp)
                                        .wrapContentWidth(Alignment.CenterHorizontally)
                                )
                            }
                        }
                    }
                }

                // Scrollable Content Area
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    if (selectedTab == 0) {
                        DailyGoalSectionCard(
                            strings = strings,
                            currentGoal = progressState.dailyGoalWords,
                            onSelectGoal = { progressState.updateDailyGoal(it) }
                        )

                        NativeLanguageSectionCard(
                            strings = strings,
                            nativeLanguage = progressState.nativeLanguage,
                            onSelectNativeLanguage = { progressState.updateNativeLanguage(it) }
                        )

                        AppearanceSectionCard(
                            strings = strings,
                            isDarkMode = progressState.isDarkMode,
                            onSelectDarkMode = { progressState.updateDarkMode(it) }
                        )

                        CardExitSpeedSectionCard(
                            strings = strings,
                            pauseWhileSpeaking = progressState.pauseCardWhileSpeaking,
                            onSelectPauseWhileSpeaking = { progressState.updatePauseCardWhileSpeaking(it) }
                        )

                        AutoPronounceSectionCard(
                            strings = strings,
                            autoPronounceEnabled = progressState.autoPronounceEnabled,
                            onToggleAutoPronounce = { progressState.setAutoPronounce(it) }
                        )

                        StudyReminderSectionCard(
                            strings = strings,
                            reminderEnabled = progressState.studyReminderEnabled,
                            reminderHour = progressState.studyReminderHour,
                            reminderMinute = progressState.studyReminderMinute,
                            onUpdateReminder = { enabled, hour, minute ->
                                progressState.updateStudyReminder(enabled, hour, minute)
                            }
                        )

                        AppVersionSectionCard(
                            strings = strings,
                            progressState = progressState
                        )
                    } else {
                        // 1. Overall Personal Statistics
                        AllStatisticsSectionCard(
                            strings = strings,
                            progressState = progressState,
                            onTileClick = { showCompleteStatsInModal = true }
                        )

                        // 2. Fair Daily & Weekly XP Leaderboard
                        DailyLeaderboardSectionCard(
                            strings = strings,
                            leaderboard = progressState.dailyLeaderboard,
                            weeklyLeaderboard = progressState.weeklyLeaderboard,
                            isSignedIn = progressState.googleUser != null,
                            isSyncing = progressState.isSyncingCloud,
                            onRefresh = { progressState.refreshFromCloud() }
                        )

                        // 2. Google Account Profile
                        GoogleAccountSectionCard(
                            strings = strings,
                            progressState = progressState,
                            showEmailPrompt = showGoogleEmailPrompt,
                            manualName = manualGoogleName,
                            manualEmail = manualGoogleEmail,
                            onManualNameChange = { manualGoogleName = it },
                            onManualEmailChange = { manualGoogleEmail = it },
                            onConfirmManualSignIn = {
                                progressState.signInWithGoogle(manualGoogleName, manualGoogleEmail)
                                showGoogleEmailPrompt = false
                            },
                            onStartGoogleSignIn = {
                                try {
                                    googleAccountLauncher.launch(googleSignInClient.signInIntent)
                                } catch (_: Exception) {
                                    showGoogleEmailPrompt = true
                                }
                            }
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun DailyLeaderboardSectionCard(
    strings: UiStrings = rememberAppStrings(),
    leaderboard: List<FriendProgress>,
    weeklyLeaderboard: List<FriendProgress> = leaderboard,
    isSignedIn: Boolean = true,
    isSyncing: Boolean,
    onRefresh: () -> Unit
) {
    var selectedPeriod by remember { mutableStateOf(LeaderboardPeriod.TODAY) }
    val activeEntries = if (selectedPeriod == LeaderboardPeriod.TODAY) leaderboard else weeklyLeaderboard
    val maxPeriodXp = activeEntries.maxOfOrNull {
        if (selectedPeriod == LeaderboardPeriod.TODAY) it.todayXp else it.weeklyXp
    }?.coerceAtLeast(10) ?: 10
    var expandedEntryId by remember { mutableStateOf<String?>(null) }

    Surface(
        shape = RoundedCornerShape(20.dp),
        color = CardSurface,
        border = BorderStroke(0.5.dp, CardBorderGray.copy(alpha = 0.6f)),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = if (strings.isCzech) "🏆 Žebříček" else "🏆 Leaderboard",
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                        color = TextDark
                    )
                    Text(
                        text = when {
                            !isSignedIn -> if (strings.isCzech) {
                                "Viditelné až po přihlášení do aplikace"
                            } else {
                                "Available after signing in"
                            }
                            strings.isCzech -> "🎙️ +2 XP hlasem  •  👉 +1 XP swipe  •  🎯 +5 XP cíl"
                            else -> "🎙️ +2 XP spoken  •  👉 +1 XP swipe  •  🎯 +5 XP goal"
                        },
                        fontSize = 10.5.sp,
                        color = TextMuted
                    )
                }

                if (isSignedIn) {
                    IconButton(
                        onClick = onRefresh,
                        modifier = Modifier.size(32.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Refresh,
                            contentDescription = null,
                            tint = if (isSyncing) AppleOrange else AppleBlue
                        )
                    }
                }
            }

            if (isSignedIn) {
                AppleSegmentedControl(
                    options = listOf(
                        LeaderboardPeriod.TODAY to (if (strings.isCzech) "📅 Dnes" else "📅 Today"),
                        LeaderboardPeriod.WEEK to (if (strings.isCzech) "🗓️ Tento týden" else "🗓️ This Week")
                    ),
                    selectedValue = selectedPeriod,
                    onSelect = { selectedPeriod = it },
                    fontSize = 12.5.sp
                )
            }

            if (!isSignedIn) {
                Surface(
                    shape = RoundedCornerShape(14.dp),
                    color = AppleBlueTint.copy(alpha = 0.6f),
                    border = BorderStroke(0.8.dp, AppleBlue.copy(alpha = 0.3f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Surface(
                            shape = CircleShape,
                            color = CardSurface,
                            modifier = Modifier.size(36.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Text(text = "🔒", fontSize = 16.sp)
                            }
                        }
                        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                            Text(
                                text = if (strings.isCzech) "Žebříček uvidíš až po přihlášení" else "Sign in to view the leaderboard",
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold,
                                color = TextDark
                            )
                            Text(
                                text = if (strings.isCzech) {
                                    "Ostatní účastníky a férové denní i týdenní porovnání zobrazíme, jakmile se níže přihlásíš svým Google účtem."
                                } else {
                                    "Other learners and fair daily/weekly rankings will appear once you sign in with your Google account below."
                                },
                                fontSize = 12.sp,
                                color = TextMuted,
                                lineHeight = 16.sp
                            )
                        }
                    }
                }
                return@Column
            }

            if (activeEntries.isEmpty()) {
                Surface(
                    shape = RoundedCornerShape(14.dp),
                    color = SurfaceBg,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = strings.leaderboardEmptyHint,
                        fontSize = 12.sp,
                        color = TextMuted,
                        modifier = Modifier.padding(14.dp)
                    )
                }
            }

            activeEntries.forEachIndexed { index, entry ->
                val isExpanded = expandedEntryId == entry.id
                val chevronRotation by animateFloatAsState(
                    targetValue = if (isExpanded) 180f else 0f,
                    animationSpec = spring(dampingRatio = 0.55f, stiffness = Spring.StiffnessMediumLow),
                    label = "leaderboardChevron"
                )
                val rankMedal = when (index) {
                    0 -> "🥇"
                    1 -> "🥈"
                    2 -> "🥉"
                    else -> "${index + 1}."
                }
                val langFlag = if (entry.languageCode == "es") "🇪🇸" else "🇬🇧"
                val dailyGoal = entry.dailyGoal.coerceAtLeast(1)
                val dailyGoalMet = entry.todayCompleted >= dailyGoal
                val periodXp = if (selectedPeriod == LeaderboardPeriod.TODAY) entry.todayXp else entry.weeklyXp
                val periodWords = if (selectedPeriod == LeaderboardPeriod.TODAY) entry.todayCompleted else entry.weeklyCompleted
                val barFraction = (periodXp.toFloat() / maxPeriodXp.toFloat()).coerceIn(0.03f, 1f)
                val isWeeklyPeriod = selectedPeriod == LeaderboardPeriod.WEEK
                val isHighlightGreen = if (isWeeklyPeriod) index == 0 && periodXp > 0 else dailyGoalMet
                val catalogTotal = entry.catalogWordCount.coerceAtLeast(1)
                val overallPercent = ((entry.masteredCount.toFloat() / catalogTotal.toFloat()) * 100f)
                    .toInt()
                    .coerceIn(0, 100)
                val overallBarFraction = (entry.masteredCount.toFloat() / catalogTotal.toFloat()).coerceIn(0.02f, 1f)
                val todayIso = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.US).format(java.util.Date())
                val activeLabel = when {
                    entry.todayCompleted > 0 || entry.lastActiveDate == todayIso ->
                        if (strings.isCzech) "Aktivní dnes" else "Active today"
                    entry.lastActiveDate.isNotBlank() ->
                        if (strings.isCzech) "Naposledy ${entry.lastActiveDate}" else "Last ${entry.lastActiveDate}"
                    else ->
                        if (strings.isCzech) "Připojen" else "Connected"
                }

                Surface(
                    shape = RoundedCornerShape(14.dp),
                    color = if (entry.isCurrentUser) AppleBlueTint else SurfaceBg,
                    border = if (entry.isCurrentUser) BorderStroke(1.dp, AppleBlue.copy(alpha = 0.45f)) else null,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(14.dp))
                        .clickable {
                            expandedEntryId = if (isExpanded) null else entry.id
                        }
                ) {
                    Column(
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
                        verticalArrangement = Arrangement.spacedBy(5.dp)
                    ) {
                        // Condensed Single-Line Main Row
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(7.dp),
                                modifier = Modifier.weight(1f)
                            ) {
                                Text(
                                    text = rankMedal,
                                    fontSize = 15.sp,
                                    fontWeight = FontWeight.Bold
                                )
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = "$langFlag ${entry.displayName}",
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 13.sp,
                                        color = if (entry.isCurrentUser) AppleBlueDark else TextDark,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    val subLine = if (isWeeklyPeriod) {
                                        "🔥 ${entry.streakDays}d  •  🎯 ${entry.accuracyPercent}%  •  $periodWords ${if (strings.isCzech) "slov/týd." else "words/wk"}"
                                    } else {
                                        "🔥 ${entry.streakDays}d  •  🎯 ${entry.accuracyPercent}%  •  $periodWords/$dailyGoal ${if (strings.isCzech) "slov" else "words"}"
                                    }
                                    Text(
                                        text = subLine,
                                        fontSize = 10.sp,
                                        color = TextMuted,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                            }

                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Surface(
                                    shape = RoundedCornerShape(50),
                                    color = when {
                                        isHighlightGreen -> AppleGreen
                                        periodXp > 0 -> AppleBlue
                                        else -> CardSurface
                                    }
                                ) {
                                    Text(
                                        text = "$periodXp XP",
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = if (periodXp > 0) Color.White else TextMuted,
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                                    )
                                }

                                Icon(
                                    imageVector = Icons.Default.KeyboardArrowDown,
                                    contentDescription = null,
                                    tint = TextMuted,
                                    modifier = Modifier
                                        .size(18.dp)
                                        .rotate(chevronRotation)
                                )
                            }
                        }

                        // Thin relative XP progress bar
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(3.5.dp)
                                .clip(RoundedCornerShape(50))
                                .background(CardSurface)
                        ) {
                            Box(
                                modifier = Modifier
                                    .fillMaxHeight()
                                    .fillMaxWidth(barFraction)
                                    .clip(RoundedCornerShape(50))
                                    .background(if (isHighlightGreen) AppleGreen else AppleBlue)
                            )
                        }

                        // Bouncy Expandable Full Progress Details
                        AnimatedVisibility(
                            visible = isExpanded,
                            enter = expandVertically(
                                animationSpec = spring(
                                    dampingRatio = 0.52f,
                                    stiffness = Spring.StiffnessMediumLow
                                )
                            ) + fadeIn(tween(180)),
                            exit = shrinkVertically(
                                animationSpec = tween<IntSize>(160)
                            ) + fadeOut(tween(120))
                        ) {
                            Surface(
                                shape = RoundedCornerShape(11.dp),
                                color = CardSurface.copy(alpha = 0.92f),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(top = 3.dp)
                            ) {
                                Column(
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
                                    verticalArrangement = Arrangement.spacedBy(5.dp)
                                ) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(
                                            text = if (strings.isCzech) {
                                                "📚 Celkový progres: ${entry.masteredCount} / $catalogTotal slov"
                                            } else {
                                                "📚 Overall: ${entry.masteredCount} / $catalogTotal words"
                                            },
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.SemiBold,
                                            color = TextDark
                                        )
                                        Text(
                                            text = "$overallPercent %",
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = AppleIndigo
                                        )
                                    }

                                    Box(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .height(4.dp)
                                            .clip(RoundedCornerShape(50))
                                            .background(SurfaceBg)
                                    ) {
                                        Box(
                                            modifier = Modifier
                                                .fillMaxHeight()
                                                .fillMaxWidth(overallBarFraction)
                                                .clip(RoundedCornerShape(50))
                                                .background(AppleIndigo)
                                        )
                                    }

                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(
                                            text = if (strings.isCzech) "🎙️ ${entry.totalSpoken} hlasem" else "🎙️ ${entry.totalSpoken} spoken",
                                            fontSize = 10.sp,
                                            color = TextMuted
                                        )
                                        Text(
                                            text = if (strings.isCzech) "🏆 ${entry.completedSessions} lekcí" else "🏆 ${entry.completedSessions} lessons",
                                            fontSize = 10.sp,
                                            color = TextMuted
                                        )
                                        Text(
                                            text = if (entry.accuracyPercent > 0) {
                                                if (strings.isCzech) "🎯 ${entry.accuracyPercent}% přesnost" else "🎯 ${entry.accuracyPercent}% acc."
                                            } else {
                                                "🕒 $activeLabel"
                                            },
                                            fontSize = 10.sp,
                                            color = TextMuted
                                        )
                                    }

                                    if (entry.accuracyPercent > 0) {
                                        Text(
                                            text = "🕒 $activeLabel  •  ✓ ${entry.totalCorrectCount} ${if (strings.isCzech) "správně celkem" else "total correct"}",
                                            fontSize = 10.sp,
                                            color = TextMuted
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
}

@Composable
private fun AppVersionSectionCard(
    strings: UiStrings,
    progressState: ProgressStateHolder
) {
    val context = LocalContext.current
    val versionName = progressState.appVersionName
    val available = progressState.availableOtaRelease
    val isChecking = progressState.isCheckingOta
    val uriHandler = LocalUriHandler.current
    val cleanSlug = cz.slovickaspeak.app.data.OtaUpdater.normalizeRepoSlug(progressState.otaRepoSlug)
    val githubReleasesUrl = "https://github.com/$cleanSlug/releases/latest"
    val projectWebsiteUrl = "https://radekvlk.github.io/LingoStacks/"
    val targetShareVersion = available?.versionName ?: versionName
    val directApkDownloadUrl = available?.apkDownloadUrl?.takeIf { it.isNotBlank() }
        ?: "https://github.com/$cleanSlug/releases/latest/download/LingoStacks.apk"

    val shareDirectApkLink: () -> Unit = {
        val shareBody = if (strings.isCzech) {
            "Stáhni si LingoStacks v$targetShareVersion (APK): $directApkDownloadUrl"
        } else {
            "Download LingoStacks v$targetShareVersion (APK): $directApkDownloadUrl"
        }
        val sendIntent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_SUBJECT, "LingoStacks v$targetShareVersion")
            putExtra(Intent.EXTRA_TEXT, shareBody)
        }
        val chooser = Intent.createChooser(
            sendIntent,
            if (strings.isCzech) "Sdílet odkaz ke stažení" else "Share download link"
        )
        try {
            context.startActivity(chooser)
        } catch (_: Exception) {
        }
    }

    Surface(
        shape = RoundedCornerShape(20.dp),
        color = CardSurface,
        border = BorderStroke(0.5.dp, CardBorderGray.copy(alpha = 0.6f)),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Apple Settings Style Header: clickable to open project website + AppleBlue version pill
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .clickable {
                        try {
                            uriHandler.openUri(projectWebsiteUrl)
                        } catch (_: Exception) {
                        }
                    }
                    .padding(vertical = 2.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    Box(
                        modifier = Modifier
                            .size(38.dp)
                            .clip(RoundedCornerShape(11.dp))
                            .background(Brush.linearGradient(listOf(AppleBlue, AppleIndigo))),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.SystemUpdate,
                            contentDescription = null,
                            tint = Color.White,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                    Column(verticalArrangement = Arrangement.spacedBy(1.dp)) {
                        Text(
                            text = if (strings.isCzech) "Verze aplikace" else "App Version",
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold,
                            color = TextDark,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = if (cz.slovickaspeak.app.BuildConfig.IS_PLAY_STORE) {
                                "LingoStacks • Google Play"
                            } else {
                                "LingoStacks • GitHub OTA"
                            },
                            fontSize = 12.sp,
                            color = TextMuted,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }

                Spacer(modifier = Modifier.width(8.dp))

                Surface(
                    shape = RoundedCornerShape(9.dp),
                    color = AppleBlueTint,
                    border = BorderStroke(0.5.dp, AppleBlue.copy(alpha = 0.28f))
                ) {
                    Text(
                        text = "v$versionName",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = AppleBlue,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp)
                    )
                }
            }

            // Grouped iOS Settings Action List
            Surface(
                shape = RoundedCornerShape(14.dp),
                color = SurfaceBg,
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.fillMaxWidth()) {
                    if (cz.slovickaspeak.app.BuildConfig.IS_PLAY_STORE) {
                        // Google Play compliant actions (Web + Privacy Policy + Share)
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    try {
                                        uriHandler.openUri(projectWebsiteUrl)
                                    } catch (_: Exception) {
                                    }
                                }
                                .padding(horizontal = 13.dp, vertical = 11.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(26.dp)
                                    .clip(RoundedCornerShape(7.dp))
                                    .background(AppleBlueTint),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.AutoMirrored.Filled.OpenInNew,
                                    contentDescription = null,
                                    tint = AppleBlue,
                                    modifier = Modifier.size(14.dp)
                                )
                            }
                            Text(
                                text = if (strings.isCzech) "Webové stránky projektu" else "Official Website",
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Medium,
                                color = TextDark,
                                modifier = Modifier.weight(1f)
                            )
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                                contentDescription = null,
                                tint = TextMuted,
                                modifier = Modifier.size(18.dp)
                            )
                        }

                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(start = 49.dp)
                                .height(0.5.dp)
                                .background(CardBorderGray.copy(alpha = 0.45f))
                        )

                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    try {
                                        uriHandler.openUri("https://radekvlk.github.io/LingoStacks/privacy.html")
                                    } catch (_: Exception) {
                                    }
                                }
                                .padding(horizontal = 13.dp, vertical = 11.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(26.dp)
                                    .clip(RoundedCornerShape(7.dp))
                                    .background(AppleGreenTint),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Info,
                                    contentDescription = null,
                                    tint = AppleGreenDark,
                                    modifier = Modifier.size(14.dp)
                                )
                            }
                            Text(
                                text = if (strings.isCzech) "Zásady ochrany soukromí" else "Privacy Policy",
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Medium,
                                color = TextDark,
                                modifier = Modifier.weight(1f)
                            )
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                                contentDescription = null,
                                tint = TextMuted,
                                modifier = Modifier.size(18.dp)
                            )
                        }

                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(start = 49.dp)
                                .height(0.5.dp)
                                .background(CardBorderGray.copy(alpha = 0.45f))
                        )

                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable(onClick = shareDirectApkLink)
                                .padding(horizontal = 13.dp, vertical = 11.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(26.dp)
                                    .clip(RoundedCornerShape(7.dp))
                                    .background(AppleBlueTint),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Share,
                                    contentDescription = null,
                                    tint = AppleBlue,
                                    modifier = Modifier.size(14.dp)
                                )
                            }
                            Text(
                                text = if (strings.isCzech) "Doporučit aplikaci" else "Share App",
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Medium,
                                color = TextDark,
                                modifier = Modifier.weight(1f)
                            )
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                                contentDescription = null,
                                tint = TextMuted,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    } else {
                        // GitHub direct release actions (Releases + Direct APK + Ko-fi donate)
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    try {
                                        uriHandler.openUri(githubReleasesUrl)
                                    } catch (_: Exception) {
                                    }
                                }
                                .padding(horizontal = 13.dp, vertical = 11.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(26.dp)
                                    .clip(RoundedCornerShape(7.dp))
                                    .background(AppleBlueTint),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.AutoMirrored.Filled.OpenInNew,
                                    contentDescription = null,
                                    tint = AppleBlue,
                                    modifier = Modifier.size(14.dp)
                                )
                            }
                            Text(
                                text = if (strings.isCzech) {
                                    "Nejnovější vydání na GitHubu"
                                } else {
                                    "Download latest on GitHub"
                                },
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Medium,
                                color = TextDark,
                                modifier = Modifier.weight(1f)
                            )
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                                contentDescription = null,
                                tint = TextMuted,
                                modifier = Modifier.size(18.dp)
                            )
                        }

                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(start = 49.dp)
                                .height(0.5.dp)
                                .background(CardBorderGray.copy(alpha = 0.45f))
                        )

                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable(onClick = shareDirectApkLink)
                                .padding(horizontal = 13.dp, vertical = 11.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(26.dp)
                                    .clip(RoundedCornerShape(7.dp))
                                    .background(AppleBlueTint),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Share,
                                    contentDescription = null,
                                    tint = AppleBlue,
                                    modifier = Modifier.size(14.dp)
                                )
                            }
                            Text(
                                text = if (strings.isCzech) {
                                    "Sdílet odkaz ke stažení (APK)"
                                } else {
                                    "Share direct APK link"
                                },
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Medium,
                                color = TextDark,
                                modifier = Modifier.weight(1f)
                            )
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                                contentDescription = null,
                                tint = TextMuted,
                                modifier = Modifier.size(18.dp)
                            )
                        }

                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(start = 49.dp)
                                .height(0.5.dp)
                                .background(CardBorderGray.copy(alpha = 0.45f))
                        )

                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    try {
                                        uriHandler.openUri("https://ko-fi.com/radekvlk")
                                    } catch (_: Exception) {
                                    }
                                }
                                .padding(horizontal = 13.dp, vertical = 11.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(26.dp)
                                    .clip(RoundedCornerShape(7.dp))
                                    .background(Color(0xFFFF5E5B).copy(alpha = 0.14f)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Favorite,
                                    contentDescription = null,
                                    tint = Color(0xFFFF5E5B),
                                    modifier = Modifier.size(14.dp)
                                )
                            }
                            Text(
                                text = if (strings.isCzech) {
                                    "Podpořit vývoj (Ko-fi)"
                                } else {
                                    "Support development (Ko-fi)"
                                },
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Medium,
                                color = TextDark,
                                modifier = Modifier.weight(1f)
                            )
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                                contentDescription = null,
                                tint = TextMuted,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }
                }
            }

            if (cz.slovickaspeak.app.BuildConfig.ENABLE_OTA_UPDATES) {
                if (progressState.otaStatusMessage != null) {
                    Text(
                        text = progressState.otaStatusMessage ?: "",
                        fontSize = 11.5.sp,
                        color = if (available != null) AppleGreenDark else TextMuted
                    )
                }

                // Full-width clean Apple tonal CTA button (no harsh wireframe outline)
                if (available != null) {
                    Button(
                        onClick = { progressState.openOtaModal() },
                        shape = RoundedCornerShape(13.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = AppleGreen),
                        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 0.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(44.dp)
                    ) {
                        Text(
                            text = if (strings.isCzech) {
                                "Aktualizovat na v${available.versionName}"
                            } else {
                                "Update to v${available.versionName}"
                            },
                            fontWeight = FontWeight.Bold,
                            fontSize = 13.5.sp,
                            color = Color.White,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                } else {
                    Surface(
                        shape = RoundedCornerShape(13.dp),
                        color = AppleBlueTint,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(44.dp)
                            .clip(RoundedCornerShape(13.dp))
                            .clickable(enabled = !isChecking) {
                                progressState.checkForOtaUpdate(manual = true)
                            }
                    ) {
                        Row(
                            modifier = Modifier.fillMaxSize(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.Center
                        ) {
                            if (isChecking) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(15.dp),
                                    strokeWidth = 2.dp,
                                    color = AppleBlue
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                            } else {
                                Icon(
                                    imageVector = Icons.Default.Refresh,
                                    contentDescription = null,
                                    tint = AppleBlue,
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(7.dp))
                            }
                            Text(
                                text = if (strings.isCzech) "Zkontrolovat aktualizace" else "Check for Updates",
                                color = AppleBlue,
                                fontWeight = FontWeight.SemiBold,
                                fontSize = 13.5.sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }
            }

            Text(
                text = if (strings.isCzech) {
                    "© 2026 Radek Vlk • Všechna práva vyhrazena"
                } else {
                    "© 2026 Radek Vlk • All Rights Reserved"
                },
                fontSize = 11.sp,
                color = TextMuted,
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 2.dp)
            )
        }
    }
}

@Composable
private fun <T> AppleSegmentedControl(
    options: List<Pair<T, String>>,
    selectedValue: T,
    onSelect: (T) -> Unit,
    fontSize: TextUnit = 13.sp
) {
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = AppleSecondaryFill,
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(3.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            options.forEach { (value, label) ->
                val selected = selectedValue == value
                Surface(
                    shape = RoundedCornerShape(9.dp),
                    color = if (selected) CardElevatedSurface else Color.Transparent,
                    border = if (selected) {
                        BorderStroke(
                            1.dp,
                            if (isDarkThemeActive) AppleBlue.copy(alpha = 0.65f) else Color.Black.copy(alpha = 0.08f)
                        )
                    } else null,
                    shadowElevation = if (selected) 3.dp else 0.dp,
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(9.dp))
                        .clickable { onSelect(value) }
                ) {
                    Text(
                        text = label,
                        color = if (selected) AppleBlue else TextMuted,
                        fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                        fontSize = fontSize,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier
                            .padding(vertical = 9.dp, horizontal = 6.dp)
                            .wrapContentWidth(Alignment.CenterHorizontally)
                    )
                }
            }
        }
    }
}

@Composable
private fun SettingsSwitchCard(
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Surface(
        shape = RoundedCornerShape(20.dp),
        color = CardSurface,
        border = BorderStroke(0.5.dp, CardBorderGray.copy(alpha = 0.6f)),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(end = 12.dp),
                verticalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                Text(
                    text = title,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold,
                    color = TextDark
                )
                Text(
                    text = subtitle,
                    fontSize = 12.sp,
                    color = TextMuted,
                    lineHeight = 16.sp
                )
            }
            Switch(
                checked = checked,
                onCheckedChange = onCheckedChange,
                colors = SwitchDefaults.colors(
                    checkedThumbColor = Color.White,
                    checkedTrackColor = AppleBlue,
                    uncheckedThumbColor = Color.White,
                    uncheckedTrackColor = CardBorderGray
                )
            )
        }
    }
}

@Composable
private fun AppearanceSectionCard(
    strings: UiStrings,
    isDarkMode: Boolean,
    onSelectDarkMode: (Boolean) -> Unit
) {
    SettingsSwitchCard(
        title = if (strings.isCzech) "🌙 Tmavý režim" else "🌙 Dark Mode",
        subtitle = if (strings.isCzech) {
            "Přepne vzhled aplikace mezi světlým a tmavým motivem."
        } else {
            "Switches app appearance between Light and Dark theme."
        },
        checked = isDarkMode,
        onCheckedChange = onSelectDarkMode
    )
}

@Composable
private fun NativeLanguageSectionCard(
    strings: UiStrings,
    nativeLanguage: LearningLanguage,
    onSelectNativeLanguage: (LearningLanguage) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }

    Surface(
        shape = RoundedCornerShape(16.dp),
        color = CardSurface,
        border = BorderStroke(0.5.dp, CardBorderGray),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { expanded = !expanded },
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(34.dp)
                        .clip(RoundedCornerShape(9.dp))
                        .background(AppleBlueTint),
                    contentAlignment = Alignment.Center
                ) {
                    Text(text = nativeLanguage.flagEmoji, fontSize = 20.sp)
                }
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = if (strings.isCzech) "Můj rodný jazyk" else "My Native Language",
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold,
                        color = TextDark
                    )
                    Text(
                        text = if (strings.isCzech) {
                            "Mluvím: ${nativeLanguage.flagEmoji} ${nativeLanguage.localizedTitle(true)}"
                        } else {
                            "I speak: ${nativeLanguage.flagEmoji} ${nativeLanguage.localizedTitle(false)}"
                        },
                        fontSize = 12.sp,
                        color = TextMuted
                    )
                }
                Icon(
                    imageVector = if (expanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                    contentDescription = null,
                    tint = TextMuted,
                    modifier = Modifier.size(20.dp)
                )
            }

            if (expanded) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 4.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    LearningLanguage.entries.forEach { lang ->
                        val isSelected = lang == nativeLanguage
                        Surface(
                            shape = RoundedCornerShape(10.dp),
                            color = if (isSelected) AppleBlueTint else SurfaceBg,
                            border = BorderStroke(
                                if (isSelected) 1.dp else 0.5.dp,
                                if (isSelected) AppleBlue else CardBorderGray.copy(alpha = 0.5f)
                            ),
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(10.dp))
                                .clickable {
                                    onSelectNativeLanguage(lang)
                                    expanded = false
                                }
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 9.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                Text(text = lang.flagEmoji, fontSize = 20.sp)
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = lang.localizedTitle(strings.isCzech),
                                        fontSize = 14.sp,
                                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                        color = if (isSelected) AppleBlue else TextDark
                                    )
                                    val nativeName = when (lang) {
                                        LearningLanguage.CZECH -> "Čeština"
                                        LearningLanguage.ENGLISH -> "English"
                                        LearningLanguage.SPANISH -> "Español"
                                        LearningLanguage.GERMAN -> "Deutsch"
                                        LearningLanguage.FRENCH -> "Français"
                                        LearningLanguage.ITALIAN -> "Italiano"
                                        LearningLanguage.PORTUGUESE -> "Português"
                                    }
                                    Text(
                                        text = nativeName,
                                        fontSize = 11.sp,
                                        color = TextMuted
                                    )
                                }
                                if (isSelected) {
                                    Icon(
                                        imageVector = Icons.Default.Check,
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
    }
}

@Composable
private fun AutoPronounceSectionCard(
    strings: UiStrings,
    autoPronounceEnabled: Boolean,
    onToggleAutoPronounce: (Boolean) -> Unit
) {
    SettingsSwitchCard(
        title = if (strings.isCzech) "🔊 Zopakovat výslovnost" else "🔊 Repeat Pronunciation",
        subtitle = if (strings.isCzech) {
            "Po vyřešení karty nahlas zopakuje správnou výslovnost (i při volbě Nevím)."
        } else {
            "Repeats correct pronunciation out loud after finishing each card (and on Unknown)."
        },
        checked = autoPronounceEnabled,
        onCheckedChange = onToggleAutoPronounce
    )
}

@Composable
private fun CardExitSpeedSectionCard(
    strings: UiStrings,
    pauseWhileSpeaking: Boolean,
    onSelectPauseWhileSpeaking: (Boolean) -> Unit
) {
    SettingsSwitchCard(
        title = if (strings.isCzech) "📖 Zobrazení slova po odpovědi" else "📖 Word Display After Answer",
        subtitle = if (strings.isCzech) {
            "Po odjezdu karty ukáže bounce okno se slovem; vypnuté přejde ihned na další kartu."
        } else {
            "Shows a bounce bubble with the word between cards; turn off for an instant transition."
        },
        checked = pauseWhileSpeaking,
        onCheckedChange = onSelectPauseWhileSpeaking
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun StudyReminderSectionCard(
    strings: UiStrings,
    reminderEnabled: Boolean,
    reminderHour: Int,
    reminderMinute: Int,
    onUpdateReminder: (Boolean, Int, Int) -> Unit
) {
    val context = LocalContext.current
    var showTimePickerDialog by remember { mutableStateOf(false) }

    val notificationPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            onUpdateReminder(true, reminderHour, reminderMinute)
        }
    }

    val formattedTime = String.format(java.util.Locale.US, "%02d:%02d", reminderHour, reminderMinute)

    if (showTimePickerDialog) {
        val pickerState = rememberTimePickerState(
            initialHour = reminderHour,
            initialMinute = reminderMinute,
            is24Hour = true
        )
        Dialog(onDismissRequest = { showTimePickerDialog = false }) {
            Surface(
                shape = RoundedCornerShape(24.dp),
                color = CardSurface,
                border = BorderStroke(0.5.dp, CardBorderGray),
                modifier = Modifier
                    .fillMaxWidth()
                    .shadow(20.dp, RoundedCornerShape(24.dp))
            ) {
                Column(
                    modifier = Modifier.padding(20.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    Text(
                        text = if (strings.isCzech) "Vybrat čas připomenutí" else "Choose reminder time",
                        fontSize = 17.sp,
                        fontWeight = FontWeight.Bold,
                        color = TextDark
                    )

                    TimePicker(
                        state = pickerState,
                        colors = TimePickerDefaults.colors(
                            clockDialColor = SurfaceBg,
                            selectorColor = AppleBlue,
                            timeSelectorSelectedContainerColor = AppleBlueTint,
                            timeSelectorUnselectedContainerColor = SurfaceBg,
                            timeSelectorSelectedContentColor = AppleBlue,
                            timeSelectorUnselectedContentColor = TextDark
                        )
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        OutlinedButton(
                            onClick = { showTimePickerDialog = false },
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.weight(1f)
                        ) {
                            Text(
                                text = if (strings.isCzech) "Zrušit" else "Cancel",
                                color = TextMuted,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                        Button(
                            onClick = {
                                onUpdateReminder(true, pickerState.hour, pickerState.minute)
                                showTimePickerDialog = false
                            },
                            shape = RoundedCornerShape(12.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = AppleBlue),
                            modifier = Modifier.weight(1f)
                        ) {
                            Text(
                                text = if (strings.isCzech) "Uložit" else "Save",
                                color = Color.White,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            }
        }
    }

    Surface(
        shape = RoundedCornerShape(20.dp),
        color = CardSurface,
        border = BorderStroke(0.5.dp, CardBorderGray.copy(alpha = 0.6f)),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .padding(end = 12.dp)
                ) {
                    Text(
                        text = if (strings.isCzech) "🔔 Denní připomenutí učení" else "🔔 Daily Study Reminder",
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold,
                        color = TextDark
                    )
                    Text(
                        text = if (strings.isCzech) {
                            "Upozorní tě ve zvolený čas, pokud ještě nemáš splněný denní cíl."
                        } else {
                            "Notifies you at your chosen time if today's daily goal isn't done yet."
                        },
                        fontSize = 12.sp,
                        color = TextMuted,
                        lineHeight = 16.sp
                    )
                }

                Switch(
                    checked = reminderEnabled,
                    onCheckedChange = { wantEnabled ->
                        if (!wantEnabled) {
                            onUpdateReminder(false, reminderHour, reminderMinute)
                        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                            ContextCompat.checkSelfPermission(
                                context,
                                Manifest.permission.POST_NOTIFICATIONS
                            ) != PackageManager.PERMISSION_GRANTED
                        ) {
                            notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                        } else {
                            onUpdateReminder(true, reminderHour, reminderMinute)
                        }
                    },
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = Color.White,
                        checkedTrackColor = AppleBlue,
                        uncheckedThumbColor = Color.White,
                        uncheckedTrackColor = CardBorderGray
                    )
                )
            }

            AnimatedVisibility(
                visible = reminderEnabled,
                enter = expandVertically(
                    animationSpec = spring(dampingRatio = 0.6f, stiffness = Spring.StiffnessMediumLow)
                ) + fadeIn(tween(180)),
                exit = shrinkVertically(animationSpec = tween(150)) + fadeOut(tween(120))
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Surface(
                        shape = RoundedCornerShape(14.dp),
                        color = SurfaceBg,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(14.dp))
                            .clickable { showTimePickerDialog = true }
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = if (strings.isCzech) "⏰ Čas připomenutí" else "⏰ Reminder time",
                                fontSize = 13.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = TextDark
                            )
                            Surface(
                                shape = RoundedCornerShape(10.dp),
                                color = AppleBlueTint,
                                border = BorderStroke(0.8.dp, AppleBlue.copy(alpha = 0.45f))
                            ) {
                                Text(
                                    text = "$formattedTime  ▾",
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = AppleBlue,
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                                )
                            }
                        }
                    }

                    // Quick 1-tap time presets
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        val presets = listOf(8 to 30, 12 to 30, 18 to 0, 20 to 30)
                        presets.forEach { (h, m) ->
                            val isSelected = reminderHour == h && reminderMinute == m
                            val label = String.format(java.util.Locale.US, "%02d:%02d", h, m)
                            Surface(
                                shape = RoundedCornerShape(10.dp),
                                color = if (isSelected) AppleBlue else SurfaceBg,
                                modifier = Modifier
                                    .weight(1f)
                                    .clip(RoundedCornerShape(10.dp))
                                    .clickable { onUpdateReminder(true, h, m) }
                            ) {
                                Text(
                                    text = label,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = if (isSelected) Color.White else TextMuted,
                                    modifier = Modifier
                                        .padding(vertical = 7.dp)
                                        .wrapContentWidth(Alignment.CenterHorizontally)
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
private fun DailyGoalSectionCard(
    strings: UiStrings,
    currentGoal: Int,
    onSelectGoal: (Int) -> Unit
) {
    var sliderValue by remember(currentGoal) { mutableFloatStateOf(currentGoal.toFloat().coerceIn(5f, 200f)) }

    Surface(
        shape = RoundedCornerShape(20.dp),
        color = CardSurface,
        border = BorderStroke(0.5.dp, CardBorderGray.copy(alpha = 0.6f)),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = strings.dailyGoalTitle,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold,
                    color = TextDark
                )
                Surface(
                    shape = RoundedCornerShape(50),
                    color = AppleBlueTint
                ) {
                    Text(
                        text = strings.wordsCountBadge(sliderValue.toInt()),
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        color = AppleBlue,
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp)
                    )
                }
            }
            Text(
                text = strings.dailyGoalSliderHint,
                fontSize = 12.sp,
                color = TextMuted
            )

            Slider(
                value = sliderValue,
                onValueChange = { raw ->
                    val stepped = ((raw / 5f).toInt() * 5).coerceIn(5, 200)
                    sliderValue = stepped.toFloat()
                    onSelectGoal(stepped)
                },
                valueRange = 5f..200f,
                colors = SliderDefaults.colors(
                    thumbColor = AppleBlue,
                    activeTrackColor = AppleBlue,
                    inactiveTrackColor = SurfaceBg
                ),
                modifier = Modifier.fillMaxWidth()
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                listOf(5, 50, 100, 150, 200).forEach { mark ->
                    Text(
                        text = "$mark",
                        fontSize = 11.sp,
                        fontWeight = if (sliderValue.toInt() == mark) FontWeight.Bold else FontWeight.Medium,
                        color = if (sliderValue.toInt() == mark) AppleBlue else TextMuted,
                        modifier = Modifier.clickable {
                            sliderValue = mark.toFloat()
                            onSelectGoal(mark)
                        }
                    )
                }
            }
        }
    }
}

@Composable
private fun AllStatisticsSectionCard(
    strings: UiStrings,
    progressState: ProgressStateHolder,
    onTileClick: (() -> Unit)? = null
) {
    Surface(
        shape = RoundedCornerShape(20.dp),
        color = CardSurface,
        border = BorderStroke(0.5.dp, CardBorderGray.copy(alpha = 0.6f)),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                text = strings.allStatsTitle,
                fontSize = 15.sp,
                fontWeight = FontWeight.Bold,
                color = TextDark
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                AppleStatTile(
                    emoji = "🔥",
                    value = strings.statStreakDays(progressState.streakDays),
                    label = strings.statStreakLabel,
                    accentColor = AppleOrange,
                    modifier = Modifier.weight(1f),
                    onClick = onTileClick
                )
                AppleStatTile(
                    emoji = "🎯",
                    value = "${progressState.accuracyPercent} %",
                    label = strings.statAccuracyLabel,
                    accentColor = AppleGreen,
                    modifier = Modifier.weight(1f),
                    onClick = onTileClick
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                AppleStatTile(
                    emoji = "🎙️",
                    value = "${progressState.totalSpokenCorrect}",
                    label = strings.statSpokenLabel,
                    accentColor = AppleBlue,
                    modifier = Modifier.weight(1f),
                    onClick = onTileClick
                )
                AppleStatTile(
                    emoji = "✅",
                    value = "${progressState.todayCompletedWords}/${progressState.dailyGoalWords}",
                    label = strings.statTodayGoalLabel,
                    accentColor = AppleIndigo,
                    modifier = Modifier.weight(1f),
                    onClick = onTileClick
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                AppleStatTile(
                    emoji = "📚",
                    value = "${progressState.totalCorrect}",
                    label = strings.statTotalLearnedLabel,
                    accentColor = AppleGreenDark,
                    modifier = Modifier.weight(1f),
                    onClick = onTileClick
                )
                AppleStatTile(
                    emoji = "🏆",
                    value = "${progressState.completedSessions}",
                    label = strings.statCompletedLessonsLabel,
                    accentColor = ApplePurple,
                    modifier = Modifier.weight(1f),
                    onClick = onTileClick
                )
            }
        }
    }
}

@Composable
private fun AppleStatTile(
    emoji: String,
    value: String,
    label: String,
    accentColor: Color,
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null
) {
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = SurfaceBg,
        modifier = modifier
            .clip(RoundedCornerShape(16.dp))
            .let { base -> if (onClick != null) base.clickable(onClick = onClick) else base }
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Text(text = emoji, fontSize = 16.sp)
                Text(
                    text = value,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    color = accentColor
                )
            }
            Text(
                text = label,
                fontSize = 12.sp,
                color = TextMuted,
                fontWeight = FontWeight.Medium
            )
        }
    }
}

@Composable
private fun GoogleAccountSectionCard(
    strings: UiStrings,
    progressState: ProgressStateHolder,
    showEmailPrompt: Boolean,
    manualName: String,
    manualEmail: String,
    onManualNameChange: (String) -> Unit,
    onManualEmailChange: (String) -> Unit,
    onConfirmManualSignIn: () -> Unit,
    onStartGoogleSignIn: () -> Unit
) {
    val user = progressState.googleUser

    Surface(
        shape = RoundedCornerShape(20.dp),
        color = CardSurface,
        border = BorderStroke(0.5.dp, CardBorderGray.copy(alpha = 0.6f)),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                text = strings.googleSectionTitle,
                fontSize = 15.sp,
                fontWeight = FontWeight.Bold,
                color = TextDark
            )

            if (user == null) {
                Text(
                    text = strings.googleSignOutHint(""),
                    fontSize = 12.sp,
                    color = TextMuted
                )

                Button(
                    onClick = onStartGoogleSignIn,
                    shape = RoundedCornerShape(14.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = AppleBlue,
                        contentColor = Color.White
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(46.dp)
                ) {
                    Icon(imageVector = Icons.Default.AccountCircle, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = strings.googleSignInButton,
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp
                    )
                }

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp)
                ) {
                    Text(text = "☁️", fontSize = 11.sp)
                    Text(
                        text = if (strings.isCzech) {
                            "Tato záloha nezabírá žádné místo na vašem Google účtu ani Disku."
                        } else {
                            "This backup does not take up any space on your Google account or Google Drive."
                        },
                        fontSize = 11.sp,
                        color = TextMuted,
                        lineHeight = 14.sp
                    )
                }

                if (showEmailPrompt) {
                    Surface(
                        shape = RoundedCornerShape(14.dp),
                        color = SurfaceBg,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier.padding(12.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Text(
                                text = strings.googleManualPrompt,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = TextDark
                            )
                            OutlinedTextField(
                                value = manualName,
                                onValueChange = onManualNameChange,
                                label = { Text(strings.googleNameLabel) },
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth()
                            )
                            OutlinedTextField(
                                value = manualEmail,
                                onValueChange = onManualEmailChange,
                                label = { Text(strings.googleEmailLabel) },
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth()
                            )
                            Button(
                                onClick = onConfirmManualSignIn,
                                shape = RoundedCornerShape(12.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = AppleGreen),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text(strings.googleConfirmButton, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            } else {
                var isEditingName by remember { mutableStateOf(false) }
                var editedName by remember(user.displayName) { mutableStateOf(user.displayName) }

                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = SurfaceBg,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(14.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(44.dp)
                                    .clip(CircleShape)
                                    .background(Brush.linearGradient(listOf(AppleBlue, AppleIndigo))),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = user.displayName.take(1).uppercase(),
                                    color = Color.White,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 18.sp
                                )
                            }
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = user.displayName,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 15.sp,
                                    color = TextDark
                                )
                                Text(
                                    text = user.email,
                                    fontSize = 12.sp,
                                    color = TextMuted
                                )
                            }
                            val cardContext = LocalContext.current
                            TextButton(
                                onClick = {
                                    try {
                                        GoogleSignIn.getClient(
                                            cardContext,
                                            GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN).build()
                                        ).signOut()
                                    } catch (_: Exception) {
                                    }
                                    progressState.signOutGoogle()
                                }
                            ) {
                                Text(strings.googleSignOutButton, color = AppleRed, fontSize = 12.sp)
                            }
                        }

                        val fullBackupActive = progressState.isFullCloudBackupActive
                        val syncingBackup = progressState.isSyncingBackup
                        Surface(
                            shape = RoundedCornerShape(10.dp),
                            color = if (fullBackupActive) {
                                AppleGreen.copy(alpha = 0.12f)
                            } else {
                                AppleOrange.copy(alpha = 0.12f)
                            },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 9.dp),
                                verticalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Text(
                                    text = when {
                                        fullBackupActive && syncingBackup -> if (strings.isCzech) {
                                            "☁️ Synchronizuji zálohu účtu..."
                                        } else {
                                            "☁️ Syncing full cloud backup..."
                                        }
                                        fullBackupActive -> if (strings.isCzech) {
                                            "☁️ Plná záloha účtu aktivní"
                                        } else {
                                            "☁️ Full cloud backup active"
                                        }
                                        else -> if (strings.isCzech) {
                                            "⚠️ Jen lokální profil / žebříček — připoj Google účet pro obnovu slovíček na jiném telefonu."
                                        } else {
                                            "⚠️ Local profile / leaderboard only — connect Google for full cross-device word backup."
                                        }
                                    },
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = if (fullBackupActive) AppleGreenDark else AppleOrange
                                )
                                if (!fullBackupActive) {
                                    Text(
                                        text = if (strings.isCzech) {
                                            "ℹ️ Tato záloha nezabírá žádné místo na vašem Google účtu ani Disku."
                                        } else {
                                            "ℹ️ This backup does not take up any space on your Google account or Google Drive."
                                        },
                                        fontSize = 11.sp,
                                        color = TextMuted,
                                        lineHeight = 14.sp
                                    )
                                    OutlinedButton(
                                        onClick = onStartGoogleSignIn,
                                        modifier = Modifier.fillMaxWidth(),
                                        shape = RoundedCornerShape(10.dp),
                                        border = BorderStroke(1.dp, AppleBlue.copy(alpha = 0.45f))
                                    ) {
                                        Text(
                                            text = if (strings.isCzech) {
                                                "🔐 Propojit Google pro plnou zálohu"
                                            } else {
                                                "🔐 Connect Google for full backup"
                                            },
                                            fontSize = 12.sp,
                                            fontWeight = FontWeight.SemiBold,
                                            color = AppleBlue
                                        )
                                    }
                                } else {
                                    OutlinedButton(
                                        onClick = { progressState.pullAndMergeCloudBackup(pushMergedBack = true) },
                                        enabled = !syncingBackup,
                                        modifier = Modifier.fillMaxWidth(),
                                        shape = RoundedCornerShape(10.dp),
                                        border = BorderStroke(1.dp, AppleGreen.copy(alpha = 0.45f))
                                    ) {
                                        Text(
                                            text = if (strings.isCzech) {
                                                if (syncingBackup) "⏳ Stahuji zálohu z cloudu..." else "🔄 Obnovit data ze zálohy cloudu"
                                            } else {
                                                if (syncingBackup) "⏳ Downloading backup..." else "🔄 Restore from cloud backup"
                                            },
                                            fontSize = 12.sp,
                                            fontWeight = FontWeight.SemiBold,
                                            color = if (syncingBackup) TextMuted else AppleGreenDark
                                        )
                                    }
                                }
                            }
                        }

                        if (isEditingName) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                OutlinedTextField(
                                    value = editedName,
                                    onValueChange = { editedName = it },
                                    label = { Text(strings.googleNameLabel, fontSize = 12.sp) },
                                    singleLine = true,
                                    shape = RoundedCornerShape(10.dp),
                                    modifier = Modifier.weight(1f)
                                )
                                Button(
                                    onClick = {
                                        if (editedName.isNotBlank()) {
                                            progressState.updateDisplayName(editedName)
                                            isEditingName = false
                                        }
                                    },
                                    shape = RoundedCornerShape(10.dp),
                                    colors = ButtonDefaults.buttonColors(containerColor = AppleGreen),
                                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 10.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Check,
                                        contentDescription = null,
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text(
                                        text = strings.googleSaveNameButton,
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                                TextButton(
                                    onClick = {
                                        editedName = user.displayName
                                        isEditingName = false
                                    }
                                ) {
                                    Text(strings.googleCancelButton, color = TextMuted, fontSize = 12.sp)
                                }
                            }
                        } else {
                            OutlinedButton(
                                onClick = {
                                    editedName = user.displayName
                                    isEditingName = true
                                },
                                shape = RoundedCornerShape(10.dp),
                                border = BorderStroke(1.dp, AppleBlue.copy(alpha = 0.4f)),
                                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Edit,
                                    contentDescription = null,
                                    tint = AppleBlue,
                                    modifier = Modifier.size(14.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = strings.googleEditNameButton,
                                    color = AppleBlue,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.SemiBold
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
fun OtaUpdateModalDialog(
    progressState: ProgressStateHolder,
    onDismiss: () -> Unit
) {
    val strings = rememberAppStrings(progressState.nativeLanguage)
    val release = progressState.availableOtaRelease ?: return
    val isDownloading = progressState.isDownloadingOta
    val progress = progressState.otaDownloadFraction
    val downloadedApk = progressState.downloadedOtaApkFile
    val needsPerm = progressState.otaNeedsInstallPermission
    val uriHandler = LocalUriHandler.current
    val sizeMb = if (release.apkSizeBytes > 0) {
        String.format(java.util.Locale.US, "%.1f MB", release.apkSizeBytes / (1024f * 1024f))
    } else {
        "APK"
    }

    Dialog(onDismissRequest = { if (!isDownloading) onDismiss() }) {
        Surface(
            shape = RoundedCornerShape(26.dp),
            color = CardSurface,
            border = BorderStroke(0.8.dp, AppleBlue.copy(alpha = 0.35f)),
            modifier = Modifier
                .fillMaxWidth()
                .shadow(24.dp, RoundedCornerShape(26.dp), spotColor = Color.Black.copy(alpha = 0.22f))
        ) {
            Column(
                modifier = Modifier.padding(22.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Surface(
                            shape = CircleShape,
                            color = AppleGreenTint,
                            modifier = Modifier.size(40.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Text(text = "🚀", fontSize = 20.sp)
                            }
                        }
                        Column {
                            Text(
                                text = if (strings.isCzech) "Nová verze k dispozici" else "Update Available",
                                fontSize = 17.sp,
                                fontWeight = FontWeight.ExtraBold,
                                color = TextDark
                            )
                            Text(
                                text = "v${progressState.appVersionName} → v${release.versionName} • $sizeMb",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = AppleBlue
                            )
                        }
                    }

                    if (!isDownloading) {
                        Box(
                            modifier = Modifier
                                .size(30.dp)
                                .clip(CircleShape)
                                .background(SurfaceBg)
                                .clickable(onClick = onDismiss),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Close,
                                contentDescription = null,
                                tint = TextMuted,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }
                }

                val localizedChangelog = remember(release.changelog, progressState.nativeLanguage) {
                    cz.slovickaspeak.app.data.OtaUpdater.extractLocalizedChangelog(release.changelog, progressState.nativeLanguage)
                }
                if (localizedChangelog.isNotBlank()) {
                    Surface(
                        shape = RoundedCornerShape(14.dp),
                        color = SurfaceBg,
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 180.dp)
                    ) {
                        Column(
                            modifier = Modifier
                                .padding(12.dp)
                                .verticalScroll(rememberScrollState()),
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Text(
                                text = if (strings.isCzech) "Co je nového:" else "What's new:",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                color = TextDark
                            )
                            Text(
                                text = localizedChangelog,
                                fontSize = 12.sp,
                                color = TextMuted,
                                lineHeight = 17.sp
                            )
                        }
                    }
                }

                if (isDownloading) {
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = if (strings.isCzech) "Stahuji aktualizaci..." else "Downloading update...",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = AppleBlue
                            )
                            Text(
                                text = "${(progress * 100).toInt()}% (${progressState.otaDownloadedMbText})",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                color = TextDark
                            )
                        }
                        LinearProgressIndicator(
                            progress = { progress },
                            color = AppleBlue,
                            trackColor = SurfaceBg,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(8.dp)
                                .clip(RoundedCornerShape(50))
                        )
                    }
                } else if (needsPerm) {
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = AppleOrange.copy(alpha = 0.14f),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = if (strings.isCzech) {
                                "Pro přímou instalaci OTA je potřeba jednorázově povolit instalaci aplikací z LingoStacks v nastavení Androidu."
                            } else {
                                "Please allow LingoStacks to install app updates once in Android Settings."
                            },
                            fontSize = 12.sp,
                            color = TextDark,
                            modifier = Modifier.padding(10.dp)
                        )
                    }
                    Button(
                        onClick = { progressState.openOtaInstallPermissionSettings() },
                        shape = RoundedCornerShape(14.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = AppleOrange),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(46.dp)
                    ) {
                        Text(
                            text = if (strings.isCzech) "Povolit v Nastavení Androidu" else "Allow in Android Settings",
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                    }
                } else {
                    val statusMsg = progressState.otaStatusMessage
                    if (statusMsg != null && !statusMsg.contains("připravena") && !statusMsg.contains("is ready")) {
                        Text(
                            text = statusMsg,
                            fontSize = 11.sp,
                            color = TextMuted
                        )
                    }

                    Button(
                        onClick = { progressState.downloadAndInstallOta() },
                        shape = RoundedCornerShape(14.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = AppleGreen),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp)
                    ) {
                        Text(
                            text = if (downloadedApk != null && downloadedApk.exists()) {
                                if (strings.isCzech) "Nainstalovat v${release.versionName}" else "Install v${release.versionName}"
                            } else {
                                if (strings.isCzech) "Stáhnout a aktualizovat ($sizeMb)" else "Download & Update ($sizeMb)"
                            },
                            fontWeight = FontWeight.Bold,
                            fontSize = 14.sp,
                            color = Color.White
                        )
                    }

                    Text(
                        text = if (strings.isCzech) {
                            "Tip: Pokud Play Protect zobrazí varování (aplikace mimo Obchod Play), klepni na Více informací → Přesto nainstalovat."
                        } else {
                            "Tip: If Play Protect warns about an app outside Play Store, tap More details → Install anyway."
                        },
                        fontSize = 11.sp,
                        color = TextMuted,
                        lineHeight = 15.sp,
                        modifier = Modifier.padding(horizontal = 4.dp)
                    )

                    Text(
                        text = if (strings.isCzech) {
                            "Otevřít stažení přímo na GitHubu (${release.tagName}) ↗"
                        } else {
                            "Download directly on GitHub (${release.tagName}) ↗"
                        },
                        fontSize = 11.5.sp,
                        fontWeight = FontWeight.Medium,
                        color = AppleBlue,
                        modifier = Modifier
                            .align(Alignment.CenterHorizontally)
                            .clip(RoundedCornerShape(6.dp))
                            .clickable {
                                try {
                                    uriHandler.openUri(release.apkDownloadUrl)
                                } catch (_: Exception) {
                                }
                            }
                            .padding(horizontal = 6.dp, vertical = 2.dp)
                    )
                }
            }
        }
    }
}

@Composable
fun AppleCompleteStatsDialog(
    progressState: ProgressStateHolder,
    onOpenLeaderboard: () -> Unit,
    onDismiss: () -> Unit
) {
    val strings = rememberAppStrings(progressState.nativeLanguage)
    val allLanguageStats = remember(progressState.srsRevision, progressState.studyDirection, progressState.nativeLanguage) {
        LearningLanguage.entries.filter { it != progressState.nativeLanguage }.map { progressState.getLanguageCompleteStats(it) }
    }
    val activeStats = allLanguageStats.firstOrNull { it.language == progressState.selectedLanguage }
        ?: allLanguageStats.firstOrNull()
        ?: progressState.getLanguageCompleteStats(progressState.selectedLanguage)

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            shape = RoundedCornerShape(28.dp),
            color = SurfaceBg,
            border = BorderStroke(0.5.dp, CardBorderGray),
            modifier = Modifier
                .fillMaxWidth(0.94f)
                .fillMaxHeight(0.88f)
                .shadow(24.dp, RoundedCornerShape(28.dp), spotColor = Color.Black.copy(alpha = 0.18f))
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Text(
                            text = if (strings.isCzech) "📊 Kompletní statistika" else "📊 Complete Statistics",
                            fontSize = 21.sp,
                            fontWeight = FontWeight.Bold,
                            color = TextDark
                        )
                        Text(
                            text = if (strings.isCzech) {
                                "Celkem ${progressState.totalAttempts} pokusů • Přesnost ${progressState.accuracyPercent} %"
                            } else {
                                "${progressState.totalAttempts} total attempts • ${progressState.accuracyPercent}% accuracy"
                            },
                            fontSize = 12.sp,
                            color = TextMuted,
                            fontWeight = FontWeight.Medium
                        )
                    }
                    Box(
                        modifier = Modifier
                            .size(32.dp)
                            .clip(CircleShape)
                            .background(CardSurface)
                            .clickable(onClick = onDismiss),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = null,
                            tint = TextMuted,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }

                Column(
                    modifier = Modifier
                        .weight(1f)
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    AllStatisticsSectionCard(
                        strings = strings,
                        progressState = progressState
                    )

                    allLanguageStats.forEach { langStats ->
                        CompleteLanguageMasteryCard(
                            strings = strings,
                            stats = langStats
                        )
                    }

                    CompleteCefrBreakdownCard(
                        strings = strings,
                        stats = activeStats
                    )

                    Surface(
                        shape = RoundedCornerShape(16.dp),
                        color = CardSurface,
                        border = BorderStroke(0.5.dp, CardBorderGray.copy(alpha = 0.7f)),
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(16.dp))
                            .clickable {
                                onDismiss()
                                onOpenLeaderboard()
                            }
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 12.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = if (strings.isCzech) "🏆 Otevřít denní žebříček" else "🏆 Open Daily Leaderboard",
                                fontSize = 14.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = AppleBlue
                            )
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                                contentDescription = null,
                                tint = AppleBlue,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun CompleteLanguageMasteryCard(
    strings: UiStrings,
    stats: LanguageCompleteStats
) {
    Surface(
        shape = RoundedCornerShape(20.dp),
        color = CardSurface,
        border = BorderStroke(0.5.dp, CardBorderGray.copy(alpha = 0.6f)),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "${stats.language.flagEmoji} ${stats.language.localizedTitle(strings.isCzech)}",
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold,
                    color = TextDark
                )
                Text(
                    text = if (strings.isCzech) "${stats.totalWords} slovíček" else "${stats.totalWords} words",
                    fontSize = 12.sp,
                    color = TextMuted,
                    fontWeight = FontWeight.Medium
                )
            }

            MasteryProgressBarRow(
                label = StudyDirection.NATIVE_TO_FOREIGN.shortLabel(stats.language),
                current = stats.activeMasteredCount,
                total = stats.totalWords,
                colors = listOf(AppleBlue, AppleIndigo)
            )

            MasteryProgressBarRow(
                label = StudyDirection.FOREIGN_TO_NATIVE.shortLabel(stats.language),
                current = stats.passiveMasteredCount,
                total = stats.totalWords,
                colors = listOf(AppleGreen, Color(0xFF34C759))
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = SurfaceBg,
                    modifier = Modifier.weight(1f)
                ) {
                    Text(
                        text = if (strings.isCzech) "⏰ K zopakování: ${stats.dueForReviewCount}" else "⏰ Due review: ${stats.dueForReviewCount}",
                        fontSize = 11.5.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = AppleBlue,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 7.dp)
                    )
                }
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = SurfaceBg,
                    modifier = Modifier.weight(1f)
                ) {
                    Text(
                        text = if (strings.isCzech) "🔁 Procvičit: ${stats.needsPracticeCount}" else "🔁 Needs review: ${stats.needsPracticeCount}",
                        fontSize = 11.5.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = if (stats.needsPracticeCount > 0) AppleOrange else TextMuted,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 7.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun CompleteCefrBreakdownCard(
    strings: UiStrings,
    stats: LanguageCompleteStats
) {
    Surface(
        shape = RoundedCornerShape(20.dp),
        color = CardSurface,
        border = BorderStroke(0.5.dp, CardBorderGray.copy(alpha = 0.6f)),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Text(
                text = if (strings.isCzech) {
                    "🎓 Úrovně A1–C2 (${stats.language.localizedTitle(true)})"
                } else {
                    "🎓 CEFR Levels A1–C2 (${stats.language.localizedTitle(false)})"
                },
                fontSize = 15.sp,
                fontWeight = FontWeight.Bold,
                color = TextDark
            )

            stats.levelStats.forEach { levelStat ->
                MasteryProgressBarRow(
                    label = "${levelStat.level.label} • ${levelStat.level.localizedDescription(strings.isCzech)}",
                    current = levelStat.masteredCount,
                    total = levelStat.totalCount,
                    colors = listOf(AppleIndigo, ApplePurple)
                )
            }
        }
    }
}

@Composable
private fun MasteryProgressBarRow(
    label: String,
    current: Int,
    total: Int,
    colors: List<Color>
) {
    val safeTotal = total.coerceAtLeast(1)
    val fraction = (current.toFloat() / safeTotal.toFloat()).coerceIn(0f, 1f)
    val pct = (fraction * 100f).toInt()
    Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = label,
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold,
                color = TextDark,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f)
            )
            Text(
                text = "$current / $total ($pct %)",
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                color = TextMuted
            )
        }
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(8.dp)
                .clip(RoundedCornerShape(50))
                .background(SurfaceBg)
        ) {
            if (current > 0) {
                Box(
                    modifier = Modifier
                        .fillMaxHeight()
                        .fillMaxWidth(fraction.coerceAtLeast(0.02f))
                        .clip(RoundedCornerShape(50))
                        .background(Brush.horizontalGradient(colors = colors))
                )
            }
        }
    }
}
