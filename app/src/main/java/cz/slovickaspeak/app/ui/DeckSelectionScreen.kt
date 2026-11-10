package cz.slovickaspeak.app.ui

import android.content.res.Configuration
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import cz.slovickaspeak.app.model.CefrLevel
import cz.slovickaspeak.app.model.LearningLanguage
import cz.slovickaspeak.app.model.VocabDeck
import cz.slovickaspeak.app.ui.theme.*

val GoalOptions = listOf(5, 10, 15, 20, 30)

@Composable
fun DeckSelectionScreen(
    decks: List<VocabDeck>,
    progressState: cz.slovickaspeak.app.data.ProgressStateHolder,
    onSelectDeck: (VocabDeck) -> Unit
) {
    val strings = rememberAppStrings()
    val selectedLanguage = progressState.selectedLanguage
    val selectedLevel = progressState.selectedCefrLevel
    var showSettingsDialog by remember { mutableStateOf(false) }
    var settingsInitialTab by remember { mutableIntStateOf(0) }
    val dailyGoalWords = progressState.dailyGoalWords
    val completedTodayWords = progressState.todayCompletedWords

    val filteredDecks = remember(decks, selectedLanguage, selectedLevel) {
        val byLang = decks
            .filter { it.language == selectedLanguage }
            .sortedBy { it.level.ordinal }
        if (selectedLevel == null) byLang else byLang.filter { it.level == selectedLevel }
    }

    val remainingWords = (dailyGoalWords - completedTodayWords).coerceAtLeast(0)
    val isGoalReached = completedTodayWords >= dailyGoalWords
    val goalProgressFraction by animateFloatAsState(
        targetValue = (completedTodayWords.toFloat() / dailyGoalWords.coerceAtLeast(1).toFloat()).coerceIn(0f, 1f),
        animationSpec = tween(durationMillis = 350, easing = FastOutSlowInEasing),
        label = "homeGoalProgressBar"
    )

    if (showSettingsDialog) {
        AppleFullSettingsDialog(
            progressState = progressState,
            initialTab = settingsInitialTab,
            onDismiss = { showSettingsDialog = false }
        )
    }

    if (!progressState.hasSelectedInitialLanguage) {
        InitialLanguageDialog(
            strings = strings,
            onSelectLanguage = { lang ->
                progressState.selectInitialLanguage(lang)
            }
        )
    }

    val configuration = LocalConfiguration.current
    val isLandscape = configuration.orientation == Configuration.ORIENTATION_LANDSCAPE ||
            (configuration.screenWidthDp > configuration.screenHeightDp)

    Scaffold(
        containerColor = SurfaceBg
    ) { innerPadding ->
        if (isLandscape) {
            // Fold / Tablet Landscape (na sirku): 2 sloupce vedle sebe (Dashboard vlevo, Balicky vpravo)
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .padding(horizontal = 24.dp, vertical = 14.dp),
                horizontalArrangement = Arrangement.spacedBy(20.dp)
            ) {
                // Levy sloupec: Hlavicka a Karta denniho cile
                LazyColumn(
                    modifier = Modifier
                        .weight(0.9f)
                        .fillMaxHeight(),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    item {
                        DeckHeaderRow(
                            strings = strings,
                            versionName = progressState.appVersionName,
                            onOpenLeaderboard = {
                                settingsInitialTab = 1
                                showSettingsDialog = true
                            },
                            onOpenSettings = {
                                settingsInitialTab = 0
                                showSettingsDialog = true
                            }
                        )
                    }

                    val pendingOta = progressState.availableOtaRelease
                    if (pendingOta != null && !progressState.otaBannerDismissed) {
                        item {
                            OtaUpdateBannerCard(
                                strings = strings,
                                newVersionName = pendingOta.versionName,
                                onUpdateClick = { progressState.openOtaModal() },
                                onDismissClick = { progressState.dismissOtaBanner() }
                            )
                        }
                    }

                    item {
                        DeckGoalIndicatorCard(
                            strings = strings,
                            selectedLanguage = selectedLanguage,
                            selectedLevel = selectedLevel,
                            completedTodayWords = completedTodayWords,
                            dailyGoalWords = dailyGoalWords,
                            isGoalReached = isGoalReached,
                            goalProgressFraction = goalProgressFraction,
                            onOpenSettings = {
                                settingsInitialTab = 0
                                showSettingsDialog = true
                            },
                            onSelectLevel = { level ->
                                progressState.updateSelectedCefrLevel(level)
                            }
                        )
                    }
                }

                // Pravy sloupec: Seznam tematickych balicku
                LazyColumn(
                    modifier = Modifier
                        .weight(1.1f)
                        .fillMaxHeight(),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    item {
                        Text(
                            text = if (strings.isCzech) "Tematické balíčky" else "Topic Decks",
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold,
                            color = TextDark,
                            modifier = Modifier.padding(bottom = 2.dp)
                        )
                    }
 items(filteredDecks, key = { it.id }) { deck ->
 val masteredCount = progressState.getDeckMasteredCount(deck)
 val needsReviewCount = progressState.getDeckNeedsReviewCount(deck)
 DeckCard(
 deck = deck,
 strings = strings,
 masteredCount = masteredCount,
 needsReviewCount = needsReviewCount,
 onClick = { onSelectDeck(deck) }
 )
 }
 }
 }
 } else {
 // Portrait (na vysku): Nechano plne roztazene pres celou sirku podle zadani
 LazyColumn(
 modifier = Modifier
 .fillMaxSize()
 .padding(innerPadding),
 contentPadding = PaddingValues(horizontal = 20.dp, vertical = 18.dp),
 verticalArrangement = Arrangement.spacedBy(16.dp)
 ) {
 item {
 DeckHeaderRow(
 strings = strings,
 versionName = progressState.appVersionName,
 onOpenLeaderboard = {
 settingsInitialTab = 1
 showSettingsDialog = true
 },
 onOpenSettings = {
 settingsInitialTab = 0
 showSettingsDialog = true
 }
 )
 }

 val pendingOta = progressState.availableOtaRelease
 if (pendingOta != null && !progressState.otaBannerDismissed) {
 item {
 OtaUpdateBannerCard(
 strings = strings,
 newVersionName = pendingOta.versionName,
 onUpdateClick = { progressState.openOtaModal() },
 onDismissClick = { progressState.dismissOtaBanner() }
 )
 }
 }

 item {
 DeckGoalIndicatorCard(
 strings = strings,
 selectedLanguage = selectedLanguage,
 selectedLevel = selectedLevel,
 completedTodayWords = completedTodayWords,
 dailyGoalWords = dailyGoalWords,
 isGoalReached = isGoalReached,
 goalProgressFraction = goalProgressFraction,
 onOpenSettings = {
 settingsInitialTab = 0
 showSettingsDialog = true
 },
 onSelectLevel = { level ->
 progressState.updateSelectedCefrLevel(level)
 }
 )
 }

 items(filteredDecks, key = { it.id }) { deck ->
 val masteredCount = progressState.getDeckMasteredCount(deck)
 val needsReviewCount = progressState.getDeckNeedsReviewCount(deck)
 DeckCard(
 deck = deck,
 strings = strings,
 masteredCount = masteredCount,
 needsReviewCount = needsReviewCount,
 onClick = { onSelectDeck(deck) }
 )
 }
 }
 }
 }
}

@Composable
private fun OtaUpdateBannerCard(
    strings: UiStrings,
    newVersionName: String,
    onUpdateClick: () -> Unit,
    onDismissClick: () -> Unit
) {
    Surface(
        shape = RoundedCornerShape(18.dp),
        color = AppleGreenTint,
        border = BorderStroke(1.dp, AppleGreen.copy(alpha = 0.55f)),
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .clickable(onClick = onUpdateClick)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.weight(1f)
            ) {
                Text(text = "🚀", fontSize = 18.sp)
                Column {
                    Text(
                        text = if (strings.isCzech) "Nová verze v$newVersionName" else "New version v$newVersionName",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        color = TextDark
                    )
                    Text(
                        text = if (strings.isCzech) "Klepni pro stažení OTA aktualizace" else "Tap to download OTA update",
                        fontSize = 11.sp,
                        color = AppleGreenDark,
                        fontWeight = FontWeight.Medium
                    )
                }
            }
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Surface(
                    shape = RoundedCornerShape(50),
                    color = AppleGreen
                ) {
                    Text(
                        text = if (strings.isCzech) "Aktualizovat" else "Update",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp)
                    )
                }
                IconButton(
                    onClick = onDismissClick,
                    modifier = Modifier.size(24.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = null,
                        tint = TextMuted,
                        modifier = Modifier.size(14.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun DeckHeaderRow(
    strings: UiStrings,
    versionName: String,
    onOpenLeaderboard: () -> Unit,
    onOpenSettings: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Text(
                    text = strings.appHeaderSubtitle,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    color = AppleBlue,
                    letterSpacing = 1.1.sp
                )
                Surface(
                    shape = RoundedCornerShape(6.dp),
                    color = AppleBlue.copy(alpha = 0.12f)
                ) {
                    Text(
                        text = "v$versionName",
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        color = AppleBlue,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                    )
                }
            }
            Text(
                text = strings.appHeaderTitle,
                fontSize = 32.sp,
                fontWeight = FontWeight.Bold,
                color = TextDark,
                letterSpacing = (-0.5).sp
            )
        }

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            // Leaderboard shortcut button (Denní žebříček)
            Surface(
                shape = CircleShape,
                color = CardSurface,
                border = BorderStroke(0.5.dp, CardBorderGray),
                modifier = Modifier
                    .size(42.dp)
                    .shadow(4.dp, CircleShape, spotColor = Color.Black.copy(alpha = 0.08f))
                    .clip(CircleShape)
                    .clickable { onOpenLeaderboard() }
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Text(text = "🏆", fontSize = 18.sp)
                }
            }

            // Settings gear button (Nastavení)
            Surface(
                shape = CircleShape,
                color = CardSurface,
                border = BorderStroke(0.5.dp, CardBorderGray),
                modifier = Modifier
                    .size(42.dp)
                    .shadow(4.dp, CircleShape, spotColor = Color.Black.copy(alpha = 0.08f))
                    .clip(CircleShape)
                    .clickable { onOpenSettings() }
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = Icons.Default.Settings,
                        contentDescription = strings.settingsAndLeaderboard,
                        tint = TextDark,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun DeckGoalIndicatorCard(
    strings: UiStrings,
    selectedLanguage: LearningLanguage,
    selectedLevel: CefrLevel?,
    completedTodayWords: Int,
    dailyGoalWords: Int,
    isGoalReached: Boolean,
    goalProgressFraction: Float,
    onOpenSettings: () -> Unit,
    onSelectLevel: (CefrLevel?) -> Unit
) {
    var levelDropdownExpanded by remember { mutableStateOf(false) }

    Surface(
        shape = RoundedCornerShape(22.dp),
        color = CardSurface,
        border = BorderStroke(0.5.dp, CardBorderGray.copy(alpha = 0.6f)),
        modifier = Modifier
            .fillMaxWidth()
            .shadow(6.dp, RoundedCornerShape(22.dp), spotColor = Color.Black.copy(alpha = 0.05f))
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 18.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    // Current Language Pill (Tap opens settings if user wants to switch)
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = SurfaceBg,
                        border = BorderStroke(0.5.dp, CardBorderGray),
                        modifier = Modifier
                            .clip(RoundedCornerShape(12.dp))
                            .clickable { onOpenSettings() }
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Text(
                                text = "${selectedLanguage.flagEmoji} ${selectedLanguage.localizedTitle(strings.isCzech)}",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                color = TextDark
                            )
                        }
                    }

                    // Top-left Dropdown ("výběr vlevo nahoře v rozevírací liště")
                    Box {
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = SurfaceBg,
                            border = BorderStroke(0.5.dp, CardBorderGray),
                            modifier = Modifier
                                .clip(RoundedCornerShape(12.dp))
                                .clickable { levelDropdownExpanded = true }
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Text(
                                    text = selectedLevel?.let { it.label } ?: strings.allLevelsLabel,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = TextDark
                                )
                                Text(
                                    text = "▾",
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = AppleBlue
                                )
                            }
                        }

                        DropdownMenu(
                            expanded = levelDropdownExpanded,
                            onDismissRequest = { levelDropdownExpanded = false }
                        ) {
                            DropdownMenuItem(
                                text = {
                                    Text(
                                        text = strings.allLevelsLabel,
                                        fontWeight = if (selectedLevel == null) FontWeight.Bold else FontWeight.Medium
                                    )
                                },
                                onClick = {
                                    onSelectLevel(null)
                                    levelDropdownExpanded = false
                                }
                            )
                            CefrLevel.entries.forEach { level ->
                                DropdownMenuItem(
                                    text = {
                                        Text(
                                            text = "${level.label} • ${level.localizedDescription(strings.isCzech)}",
                                            fontWeight = if (selectedLevel == level) FontWeight.Bold else FontWeight.Medium
                                        )
                                    },
                                    onClick = {
                                        onSelectLevel(level)
                                        levelDropdownExpanded = false
                                    }
                                )
                            }
                        }
                    }
                }

                Text(
                    text = strings.dailyGoalProgress(completedTodayWords, dailyGoalWords),
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    color = if (isGoalReached) AppleGreen else TextDark
                )
            }

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(12.dp)
                    .clip(RoundedCornerShape(50))
                    .background(SurfaceBg)
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxHeight()
                        .fillMaxWidth(goalProgressFraction.coerceIn(0f, 1f))
                        .clip(RoundedCornerShape(50))
                        .background(
                            Brush.horizontalGradient(
                                colors = if (isGoalReached) {
                                    listOf(AppleGreen, Color(0xFF34C759))
                                } else {
                                    listOf(AppleBlue, AppleIndigo)
                                }
                            )
                        )
                )
            }
        }
    }
}

@Composable
fun AppleGoalSettingsDialog(
    currentGoal: Int,
    onSelectGoal: (Int) -> Unit,
    onDismiss: () -> Unit
) {
    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = RoundedCornerShape(26.dp),
            color = Color.White,
            border = BorderStroke(0.5.dp, CardBorderGray),
            modifier = Modifier
                .fillMaxWidth()
                .shadow(20.dp, RoundedCornerShape(26.dp), spotColor = Color.Black.copy(alpha = 0.15f))
        ) {
            Column(
                modifier = Modifier.padding(22.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Nastavení",
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold,
                        color = TextDark
                    )
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
                            contentDescription = "Zavřít",
                            tint = TextMuted,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }

                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = "Počet slovíček (denní cíl)",
                        fontSize = 15.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = TextDark
                    )
                    Text(
                        text = "Zvolte, kolik slovíček chcete v rámci cíle procvičit:",
                        fontSize = 13.sp,
                        color = TextMuted
                    )
                }

                // iOS Segmented Control for Word Count
                Surface(
                    shape = RoundedCornerShape(14.dp),
                    color = SurfaceBg,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(4.dp),
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        GoalOptions.forEach { option ->
                            val isSelected = currentGoal == option
                            Surface(
                                shape = RoundedCornerShape(10.dp),
                                color = if (isSelected) Color.White else Color.Transparent,
                                shadowElevation = if (isSelected) 2.dp else 0.dp,
                                modifier = Modifier
                                    .weight(1f)
                                    .clip(RoundedCornerShape(10.dp))
                                    .clickable { onSelectGoal(option) }
                            ) {
                                Text(
                                    text = "$option",
                                    color = if (isSelected) AppleBlue else TextDark,
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                    fontSize = 15.sp,
                                    modifier = Modifier
                                        .padding(vertical = 10.dp)
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
private fun LevelFilterChip(
    label: String,
    selected: Boolean,
    onClick: () -> Unit
) {
    Surface(
        shape = RoundedCornerShape(50),
        color = if (selected) TextDark else Color.White,
        border = BorderStroke(0.5.dp, if (selected) TextDark else CardBorderGray),
        modifier = Modifier
            .clip(RoundedCornerShape(50))
            .clickable(onClick = onClick)
    ) {
        Text(
            text = label,
            color = if (selected) Color.White else TextDark,
            fontWeight = FontWeight.SemiBold,
            fontSize = 13.sp,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
        )
    }
}

@Composable
private fun DeckCard(
    deck: VocabDeck,
    strings: UiStrings,
    masteredCount: Int = 0,
    needsReviewCount: Int = 0,
    onClick: () -> Unit
) {
    val levelColor = Color(deck.level.colorHex)

    Surface(
        shape = RoundedCornerShape(22.dp),
        color = CardSurface,
        border = BorderStroke(0.5.dp, CardBorderGray.copy(alpha = 0.6f)),
        modifier = Modifier
            .fillMaxWidth()
            .shadow(6.dp, RoundedCornerShape(22.dp), spotColor = Color.Black.copy(alpha = 0.05f))
            .clip(RoundedCornerShape(22.dp))
            .clickable(onClick = onClick)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(58.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .background(SurfaceBg),
                contentAlignment = Alignment.Center
            ) {
                Text(text = deck.emoji, fontSize = 32.sp)
            }

            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(3.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Surface(
                        shape = RoundedCornerShape(6.dp),
                        color = levelColor.copy(alpha = 0.14f)
                    ) {
                        Text(
                            text = deck.level.label,
                            color = levelColor,
                            fontWeight = FontWeight.Bold,
                            fontSize = 11.sp,
                            modifier = Modifier.padding(horizontal = 7.dp, vertical = 2.dp)
                        )
                    }
                    Text(
                        text = if (masteredCount > 0) {
                            if (strings.isCzech) "Naučeno $masteredCount / ${deck.words.size}" else "Learned $masteredCount / ${deck.words.size}"
                        } else {
                            strings.wordsInDeck(deck.words.size)
                        },
                        fontSize = 12.sp,
                        color = if (masteredCount > 0) AppleGreen else TextMuted,
                        fontWeight = FontWeight.Medium
                    )
                    if (needsReviewCount > 0) {
                        Surface(
                            shape = RoundedCornerShape(6.dp),
                            color = AppleRedTint
                        ) {
                            Text(
                                text = if (strings.isCzech) "K opakování: $needsReviewCount" else "Review: $needsReviewCount",
                                color = AppleRed,
                                fontWeight = FontWeight.SemiBold,
                                fontSize = 10.sp,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                            )
                        }
                    }
                }

                Text(
                    text = deck.localizedTitle(strings.isCzech),
                    fontSize = 17.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = TextDark
                )

                Text(
                    text = deck.localizedSubtitle(strings.isCzech),
                    fontSize = 13.sp,
                    color = TextMuted
                )
            }

            Box(
                modifier = Modifier
                    .size(34.dp)
                    .clip(CircleShape)
                    .background(SurfaceBg),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.ChevronRight,
                    contentDescription = "Spustit lekci",
                    tint = TextMuted
                )
            }
        }
    }
}

@Composable
fun InitialLanguageDialog(
    strings: UiStrings,
    onSelectLanguage: (LearningLanguage) -> Unit
) {
    Dialog(
        onDismissRequest = {},
        properties = androidx.compose.ui.window.DialogProperties(
            dismissOnBackPress = false,
            dismissOnClickOutside = false
        )
    ) {
        Surface(
            shape = RoundedCornerShape(28.dp),
            color = CardSurface,
            border = BorderStroke(0.5.dp, CardBorderGray),
            modifier = Modifier
                .fillMaxWidth()
                .shadow(24.dp, RoundedCornerShape(28.dp), spotColor = Color.Black.copy(alpha = 0.2f))
        ) {
            Column(
                modifier = Modifier.padding(26.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Surface(
                    shape = CircleShape,
                    color = AppleBlueTint,
                    modifier = Modifier.size(68.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Text(text = "🌍", fontSize = 34.sp)
                    }
                }

                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Text(
                        text = strings.initialLanguageWelcomeTitle,
                        fontSize = 22.sp,
                        fontWeight = FontWeight.Bold,
                        color = TextDark,
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center
                    )
                    Text(
                        text = strings.initialLanguageWelcomeSubtitle,
                        fontSize = 14.sp,
                        color = TextMuted,
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center
                    )
                }

                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    // English Option Card
                    Surface(
                        shape = RoundedCornerShape(18.dp),
                        color = SurfaceBg,
                        border = BorderStroke(1.dp, CardBorderGray.copy(alpha = 0.8f)),
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(18.dp))
                            .clickable { onSelectLanguage(LearningLanguage.ENGLISH) }
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 18.dp, vertical = 14.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(14.dp)
                        ) {
                            Text(text = "🇬🇧", fontSize = 32.sp)
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = LearningLanguage.ENGLISH.localizedTitle(strings.isCzech),
                                    fontSize = 17.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = TextDark
                                )
                                val enWordCount = remember {
                                    cz.slovickaspeak.app.model.BuiltInVocabulary.englishDecks.sumOf { it.words.size }
                                }
                                val enDeckCount = remember {
                                    cz.slovickaspeak.app.model.BuiltInVocabulary.englishDecks.size
                                }
                                Text(
                                    text = if (strings.isCzech) {
                                        "$enWordCount slov a frází • $enDeckCount okruhů (A1–C2)"
                                    } else {
                                        "$enWordCount words & phrases • $enDeckCount decks (A1–C2)"
                                    },
                                    fontSize = 12.sp,
                                    color = TextMuted
                                )
                            }
                            Icon(
                                imageVector = Icons.Default.ChevronRight,
                                contentDescription = null,
                                tint = AppleBlue
                            )
                        }
                    }

                    // Spanish Option Card
                    Surface(
                        shape = RoundedCornerShape(18.dp),
                        color = SurfaceBg,
                        border = BorderStroke(1.dp, CardBorderGray.copy(alpha = 0.8f)),
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(18.dp))
                            .clickable { onSelectLanguage(LearningLanguage.SPANISH) }
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 18.dp, vertical = 14.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(14.dp)
                        ) {
                            Text(text = "🇪🇸", fontSize = 32.sp)
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = LearningLanguage.SPANISH.localizedTitle(strings.isCzech),
                                    fontSize = 17.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = TextDark
                                )
                                val esWordCount = remember {
                                    cz.slovickaspeak.app.model.BuiltInVocabulary.spanishDecks.sumOf { it.words.size }
                                }
                                val esDeckCount = remember {
                                    cz.slovickaspeak.app.model.BuiltInVocabulary.spanishDecks.size
                                }
                                Text(
                                    text = if (strings.isCzech) {
                                        "$esWordCount slov a frází • $esDeckCount okruhů (A1–C2)"
                                    } else {
                                        "$esWordCount words & phrases • $esDeckCount decks (A1–C2)"
                                    },
                                    fontSize = 12.sp,
                                    color = TextMuted
                                )
                            }
                            Icon(
                                imageVector = Icons.Default.ChevronRight,
                                contentDescription = null,
                                tint = AppleBlue
                            )
                        }
                    }
                }

                Text(
                    text = strings.initialLanguageChooseHint,
                    fontSize = 12.sp,
                    color = TextMuted,
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center
                )
            }
        }
    }
}

@androidx.compose.ui.tooling.preview.Preview(showBackground = true, widthDp = 390, heightDp = 800)
@Composable
fun DeckSelectionScreenPreview() {
    val context = androidx.compose.ui.platform.LocalContext.current
    SlovickaSpeakTheme {
        DeckSelectionScreen(
            decks = cz.slovickaspeak.app.model.BuiltInVocabulary.decks,
            progressState = remember { cz.slovickaspeak.app.data.ProgressStateHolder(context) },
            onSelectDeck = {}
        )
    }
}

