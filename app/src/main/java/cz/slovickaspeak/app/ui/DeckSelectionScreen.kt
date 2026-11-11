package cz.slovickaspeak.app.ui

import android.content.res.Configuration
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import kotlin.math.PI
import kotlin.math.sin
import cz.slovickaspeak.app.data.DeckMemorySummary
import cz.slovickaspeak.app.model.CefrLevel
import cz.slovickaspeak.app.model.LearningLanguage
import cz.slovickaspeak.app.model.StudyDirection
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
    val studyDirection = progressState.studyDirection
    var showSettingsDialog by remember { mutableStateOf(false) }
    var settingsInitialTab by remember { mutableIntStateOf(0) }
    var showCompleteStatsDialog by remember { mutableStateOf(false) }
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

    if (showCompleteStatsDialog) {
        AppleCompleteStatsDialog(
            progressState = progressState,
            onOpenLeaderboard = {
                settingsInitialTab = 1
                showSettingsDialog = true
            },
            onDismiss = { showCompleteStatsDialog = false }
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
                            selectedLanguage = selectedLanguage,
                            strings = strings,
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
                            studyDirection = studyDirection,
                            completedTodayWords = completedTodayWords,
                            dailyGoalWords = dailyGoalWords,
                            isGoalReached = isGoalReached,
                            goalProgressFraction = goalProgressFraction,
                            onSelectLanguage = { lang ->
                                progressState.updateSelectedLanguage(lang)
                            },
                            onSelectLevel = { level ->
                                progressState.updateSelectedCefrLevel(level)
                            },
                            onSelectDirection = { direction ->
                                progressState.updateStudyDirection(direction)
                            },
                            onOpenStats = { showCompleteStatsDialog = true }
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
                        val memorySummary = remember(deck.id, progressState.srsRevision, studyDirection) {
                            progressState.getDeckMemorySummary(deck)
                        }
                        DeckCard(
                            deck = deck,
                            strings = strings,
                            memorySummary = memorySummary,
                            onClick = { onSelectDeck(deck) }
                        )
                    }
                }
            }
        } else {
            // Portrait (na vysku): Nechano plne roztazene pres celou sirku podle zadani
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(
                    start = 20.dp,
                    end = 20.dp,
                    top = innerPadding.calculateTopPadding() + 18.dp,
                    bottom = innerPadding.calculateBottomPadding() + 18.dp
                ),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                item {
                    DeckHeaderRow(
                        selectedLanguage = selectedLanguage,
                        strings = strings,
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
                        studyDirection = studyDirection,
                        completedTodayWords = completedTodayWords,
                        dailyGoalWords = dailyGoalWords,
                        isGoalReached = isGoalReached,
                        goalProgressFraction = goalProgressFraction,
                        onSelectLanguage = { lang ->
                            progressState.updateSelectedLanguage(lang)
                        },
                        onSelectLevel = { level ->
                            progressState.updateSelectedCefrLevel(level)
                        },
                        onSelectDirection = { direction ->
                            progressState.updateStudyDirection(direction)
                        },
                        onOpenStats = { showCompleteStatsDialog = true }
                    )
                }

                items(filteredDecks, key = { it.id }) { deck ->
                    val memorySummary = remember(deck.id, progressState.srsRevision, studyDirection) {
                        progressState.getDeckMemorySummary(deck)
                    }
                    DeckCard(
                        deck = deck,
                        strings = strings,
                        memorySummary = memorySummary,
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
private fun LingoWordsHeaderMark(
    modifier: Modifier = Modifier
) {
    Canvas(modifier = modifier.size(36.dp)) {
        val w = size.width
        val h = size.height
        val corner = CornerRadius(w * 0.14f, w * 0.14f)
        val cardW = w * 0.52f
        val cardH = h * 0.72f

        // Back frosted glass flashcard (tilted -13 deg, without circular wrapper)
        rotate(degrees = -13f, pivot = Offset(w * 0.42f, h * 0.50f)) {
            drawRoundRect(
                brush = Brush.linearGradient(
                    colors = listOf(
                        AppleBlue.copy(alpha = 0.28f),
                        AppleIndigo.copy(alpha = 0.38f)
                    )
                ),
                topLeft = Offset(w * 0.15f, h * 0.12f),
                size = Size(cardW, cardH),
                cornerRadius = corner
            )
            drawRoundRect(
                color = AppleBlue.copy(alpha = 0.45f),
                topLeft = Offset(w * 0.15f, h * 0.12f),
                size = Size(cardW, cardH),
                cornerRadius = corner,
                style = Stroke(width = 1.dp.toPx())
            )
        }

        // Front white flashcard + 4-point star emblem (tilted +4 deg)
        rotate(degrees = 4f, pivot = Offset(w * 0.56f, h * 0.54f)) {
            drawRoundRect(
                color = Color(0x22002B66),
                topLeft = Offset(w * 0.31f, h * 0.20f),
                size = Size(cardW, cardH),
                cornerRadius = corner
            )
            drawRoundRect(
                color = Color.White,
                topLeft = Offset(w * 0.30f, h * 0.16f),
                size = Size(cardW, cardH),
                cornerRadius = corner
            )
            drawRoundRect(
                color = CardBorderGray.copy(alpha = 0.75f),
                topLeft = Offset(w * 0.30f, h * 0.16f),
                size = Size(cardW, cardH),
                cornerRadius = corner,
                style = Stroke(width = 0.75.dp.toPx())
            )

            val cx = w * 0.56f
            val cy = h * 0.52f
            val r = w * 0.21f
            val ri = w * 0.052f
            val starPath = Path().apply {
                moveTo(cx, cy - r)
                lineTo(cx + ri, cy - ri)
                lineTo(cx + r, cy)
                lineTo(cx + ri, cy + ri)
                lineTo(cx, cy + r)
                lineTo(cx - ri, cy + ri)
                lineTo(cx - r, cy)
                lineTo(cx - ri, cy - ri)
                close()
            }
            drawPath(
                path = starPath,
                brush = Brush.linearGradient(
                    colors = listOf(Color(0xFF007AFF), Color(0xFF5856D6)),
                    start = Offset(cx - r, cy - r),
                    end = Offset(cx + r, cy + r)
                )
            )
        }
    }
}

@Composable
private fun WavingLanguageFlagBackdrop(
    selectedLanguage: LearningLanguage,
    modifier: Modifier = Modifier
) {
    val infiniteTransition = rememberInfiniteTransition(label = "headerFlagWave")
    val wavePhase by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = (2.0 * PI).toFloat(),
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 5600, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "headerFlagWavePhase"
    )

    Crossfade(
        targetState = selectedLanguage,
        animationSpec = tween(durationMillis = 420),
        modifier = modifier,
        label = "headerFlagCrossfade"
    ) { language ->
        Canvas(
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer(compositingStrategy = CompositingStrategy.Offscreen)
        ) {
            val w = size.width
            val h = size.height
            if (w <= 0f || h <= 0f) return@Canvas

            val dark = isDarkThemeActive
            val whiteBeamAlpha = if (dark) 0.16f else 0.44f
            val whiteCenterAlpha = if (dark) 0.15f else 0.54f
            val ribbonGain = if (dark) 0.14f else 0.10f
            val amp = (h * 0.068f).coerceAtLeast(3.dp.toPx())
            val steps = 28

            fun waveY(xFrac: Float, baseY: Float, phaseShift: Float = 0f): Float {
                return baseY + sin(xFrac * 2f * PI.toFloat() * 1.45f - wavePhase + phaseShift) * amp
            }

            fun horizontalWaveRibbon(
                topFrac: Float,
                bottomFrac: Float,
                phaseShift: Float = 0f
            ): Path = Path().apply {
                for (i in 0..steps) {
                    val f = i / steps.toFloat()
                    val x = w * f
                    val y = waveY(f, h * topFrac, phaseShift)
                    if (i == 0) moveTo(x, y) else lineTo(x, y)
                }
                for (i in steps downTo 0) {
                    val f = i / steps.toFloat()
                    val x = w * f
                    val y = waveY(f, h * bottomFrac, phaseShift + 0.35f)
                    lineTo(x, y)
                }
                close()
            }

            fun verticalWaveRibbon(
                leftFrac: Float,
                rightFrac: Float,
                tiltFrac: Float = 0.055f,
                phaseShift: Float = 0f
            ): Path = Path().apply {
                for (i in 0..steps) {
                    val yf = i / steps.toFloat()
                    val ripple = sin(yf * PI.toFloat() * 1.4f - wavePhase + phaseShift) * (w * 0.022f)
                    val x = w * (leftFrac + (yf - 0.5f) * tiltFrac) + ripple
                    val y = h * yf
                    if (i == 0) moveTo(x, y) else lineTo(x, y)
                }
                for (i in steps downTo 0) {
                    val yf = i / steps.toFloat()
                    val ripple = sin(yf * PI.toFloat() * 1.4f - wavePhase + phaseShift + 0.5f) * (w * 0.022f)
                    val x = w * (rightFrac + (yf - 0.5f) * tiltFrac) + ripple
                    val y = h * yf
                    lineTo(x, y)
                }
                close()
            }

            when (language) {
                LearningLanguage.ENGLISH -> {
                    val ukBlue = Color(0xFF012169)
                    val ukRed = Color(0xFFC8102E)

                    // Soft Royal Blue sea wash
                    drawRect(
                        brush = Brush.linearGradient(
                            colors = listOf(
                                ukBlue.copy(alpha = 0.16f),
                                ukBlue.copy(alpha = 0.22f),
                                ukBlue.copy(alpha = 0.14f)
                            ),
                            start = Offset(0f, waveY(0f, h * 0.15f)),
                            end = Offset(w, waveY(1f, h * 0.85f))
                        )
                    )
                    // Soft diagonal St. Andrew (white) & St. Patrick (red) glow beams
                    val diagShift = sin(wavePhase) * (h * 0.07f)
                    drawLine(
                        color = Color.White.copy(alpha = whiteBeamAlpha),
                        start = Offset(0f, -h * 0.08f + diagShift),
                        end = Offset(w, h * 1.08f - diagShift),
                        strokeWidth = h * 0.30f
                    )
                    drawLine(
                        color = Color.White.copy(alpha = whiteBeamAlpha),
                        start = Offset(0f, h * 1.08f - diagShift),
                        end = Offset(w, -h * 0.08f + diagShift),
                        strokeWidth = h * 0.30f
                    )
                    drawLine(
                        color = ukRed.copy(alpha = if (dark) 0.24f else 0.17f),
                        start = Offset(0f, -h * 0.08f + diagShift),
                        end = Offset(w, h * 1.08f - diagShift),
                        strokeWidth = h * 0.12f
                    )
                    drawLine(
                        color = ukRed.copy(alpha = if (dark) 0.24f else 0.17f),
                        start = Offset(0f, h * 1.08f - diagShift),
                        end = Offset(w, -h * 0.08f + diagShift),
                        strokeWidth = h * 0.12f
                    )
                    // Soft waving St. George cross halo & crimson ribbon
                    drawPath(
                        path = horizontalWaveRibbon(0.32f, 0.68f, phaseShift = 0.2f),
                        color = Color.White.copy(alpha = whiteBeamAlpha)
                    )
                    drawPath(
                        path = verticalWaveRibbon(0.37f, 0.49f, tiltFrac = 0.03f, phaseShift = 0.4f),
                        color = Color.White.copy(alpha = whiteBeamAlpha * 0.9f)
                    )
                    drawPath(
                        path = horizontalWaveRibbon(0.41f, 0.59f, phaseShift = 0.25f),
                        color = ukRed.copy(alpha = if (dark) 0.30f else 0.22f)
                    )
                    drawPath(
                        path = verticalWaveRibbon(0.405f, 0.455f, tiltFrac = 0.03f, phaseShift = 0.45f),
                        color = ukRed.copy(alpha = if (dark) 0.28f else 0.20f)
                    )
                }

                LearningLanguage.SPANISH -> {
                    val esRed = Color(0xFFC60B1E)
                    val esGold = Color(0xFFFFC400)
                    val shift = sin(wavePhase) * 0.04f

                    drawRect(
                        brush = Brush.verticalGradient(
                            colorStops = arrayOf(
                                0.0f to esRed.copy(alpha = if (dark) 0.28f else 0.22f),
                                (0.26f + shift) to esRed.copy(alpha = if (dark) 0.24f else 0.18f),
                                (0.36f + shift) to esGold.copy(alpha = if (dark) 0.26f else 0.30f),
                                (0.64f - shift) to esGold.copy(alpha = if (dark) 0.28f else 0.32f),
                                (0.74f - shift) to esRed.copy(alpha = if (dark) 0.24f else 0.18f),
                                1.0f to esRed.copy(alpha = if (dark) 0.28f else 0.22f)
                            )
                        )
                    )
                    drawPath(
                        path = horizontalWaveRibbon(-0.08f, 0.29f, phaseShift = 0f),
                        color = esRed.copy(alpha = ribbonGain + 0.04f)
                    )
                    drawPath(
                        path = horizontalWaveRibbon(0.28f, 0.72f, phaseShift = 0.3f),
                        color = esGold.copy(alpha = ribbonGain + 0.08f)
                    )
                    drawPath(
                        path = horizontalWaveRibbon(0.71f, 1.08f, phaseShift = 0.6f),
                        color = esRed.copy(alpha = ribbonGain + 0.04f)
                    )
                }

                LearningLanguage.GERMAN -> {
                    val deBlack = if (dark) Color(0xFF4A4A55) else Color(0xFF1C1C22)
                    val deRed = Color(0xFFDD0000)
                    val deGold = Color(0xFFFFCE00)
                    val shift = sin(wavePhase) * 0.04f

                    drawRect(
                        brush = Brush.verticalGradient(
                            colorStops = arrayOf(
                                0.0f to deBlack.copy(alpha = if (dark) 0.24f else 0.13f),
                                (0.31f + shift) to deBlack.copy(alpha = if (dark) 0.20f else 0.12f),
                                (0.40f + shift) to deRed.copy(alpha = if (dark) 0.28f else 0.22f),
                                (0.63f - shift) to deRed.copy(alpha = if (dark) 0.28f else 0.22f),
                                (0.72f - shift) to deGold.copy(alpha = if (dark) 0.26f else 0.32f),
                                1.0f to deGold.copy(alpha = if (dark) 0.24f else 0.28f)
                            )
                        )
                    )
                    drawPath(
                        path = horizontalWaveRibbon(-0.08f, 0.34f, phaseShift = 0f),
                        color = deBlack.copy(alpha = if (dark) 0.14f else 0.08f)
                    )
                    drawPath(
                        path = horizontalWaveRibbon(0.33f, 0.67f, phaseShift = 0.35f),
                        color = deRed.copy(alpha = ribbonGain + 0.04f)
                    )
                    drawPath(
                        path = horizontalWaveRibbon(0.66f, 1.08f, phaseShift = 0.7f),
                        color = deGold.copy(alpha = ribbonGain + 0.08f)
                    )
                }

                LearningLanguage.FRENCH -> {
                    val frBlue = Color(0xFF0055A4)
                    val frRed = Color(0xFFEF4135)
                    val shift = sin(wavePhase) * 0.03f

                    drawRect(
                        brush = Brush.linearGradient(
                            colorStops = arrayOf(
                                0.0f to frBlue.copy(alpha = if (dark) 0.32f else 0.24f),
                                (0.31f + shift) to frBlue.copy(alpha = if (dark) 0.26f else 0.19f),
                                (0.43f + shift) to Color.White.copy(alpha = whiteCenterAlpha),
                                (0.57f - shift) to Color.White.copy(alpha = whiteCenterAlpha),
                                (0.69f - shift) to frRed.copy(alpha = if (dark) 0.26f else 0.19f),
                                1.0f to frRed.copy(alpha = if (dark) 0.32f else 0.24f)
                            ),
                            start = Offset(0f, waveY(0f, h * 0.30f)),
                            end = Offset(w, waveY(1f, h * 0.70f))
                        )
                    )
                    drawPath(
                        path = verticalWaveRibbon(-0.04f, 0.35f, tiltFrac = 0.06f, phaseShift = 0f),
                        color = frBlue.copy(alpha = 0.08f)
                    )
                    drawPath(
                        path = verticalWaveRibbon(0.33f, 0.67f, tiltFrac = 0.06f, phaseShift = 0.4f),
                        color = Color.White.copy(alpha = if (dark) 0.08f else 0.16f)
                    )
                    drawPath(
                        path = verticalWaveRibbon(0.65f, 1.04f, tiltFrac = 0.06f, phaseShift = 0.8f),
                        color = frRed.copy(alpha = 0.08f)
                    )
                }

                LearningLanguage.ITALIAN -> {
                    val itGreen = Color(0xFF009246)
                    val itRed = Color(0xFFCE2B37)
                    val shift = sin(wavePhase) * 0.03f

                    drawRect(
                        brush = Brush.linearGradient(
                            colorStops = arrayOf(
                                0.0f to itGreen.copy(alpha = if (dark) 0.30f else 0.23f),
                                (0.31f + shift) to itGreen.copy(alpha = if (dark) 0.24f else 0.18f),
                                (0.43f + shift) to Color.White.copy(alpha = whiteCenterAlpha),
                                (0.57f - shift) to Color.White.copy(alpha = whiteCenterAlpha),
                                (0.69f - shift) to itRed.copy(alpha = if (dark) 0.26f else 0.19f),
                                1.0f to itRed.copy(alpha = if (dark) 0.32f else 0.24f)
                            ),
                            start = Offset(0f, waveY(0f, h * 0.30f)),
                            end = Offset(w, waveY(1f, h * 0.70f))
                        )
                    )
                    drawPath(
                        path = verticalWaveRibbon(-0.04f, 0.35f, tiltFrac = 0.06f, phaseShift = 0f),
                        color = itGreen.copy(alpha = 0.08f)
                    )
                    drawPath(
                        path = verticalWaveRibbon(0.33f, 0.67f, tiltFrac = 0.06f, phaseShift = 0.4f),
                        color = Color.White.copy(alpha = if (dark) 0.08f else 0.16f)
                    )
                    drawPath(
                        path = verticalWaveRibbon(0.65f, 1.04f, tiltFrac = 0.06f, phaseShift = 0.8f),
                        color = itRed.copy(alpha = 0.08f)
                    )
                }
            }

            // Moving diagonal silk-fold highlights (creates realistic 3D cloth wave shimmer)
            val sheenMax = if (dark) 0.10f else 0.24f
            val sheenStops = Array(8) { idx ->
                val f = idx / 7f
                val crest = ((sin(f * 2f * PI.toFloat() * 1.45f - wavePhase) + 1f) * 0.5f)
                f to Color.White.copy(alpha = 0.01f + crest * sheenMax)
            }
            drawRect(
                brush = Brush.linearGradient(
                    colorStops = sheenStops,
                    start = Offset(0f, 0f),
                    end = Offset(w, h * 0.45f)
                )
            )

            // Soft feathered fade on top/bottom & left/right edges so flag shines broadly above & below header
            drawRect(
                brush = Brush.verticalGradient(
                    colorStops = arrayOf(
                        0.0f to Color.Transparent,
                        0.22f to Color.White.copy(alpha = 0.85f),
                        0.40f to Color.White,
                        0.58f to Color.White,
                        0.80f to Color.White.copy(alpha = 0.45f),
                        1.0f to Color.Transparent
                    )
                ),
                blendMode = BlendMode.DstIn
            )
            drawRect(
                brush = Brush.horizontalGradient(
                    colorStops = arrayOf(
                        0.0f to Color.Transparent,
                        0.04f to Color.White.copy(alpha = 0.72f),
                        0.18f to Color.White,
                        0.86f to Color.White.copy(alpha = 0.88f),
                        1.0f to Color.Transparent
                    )
                ),
                blendMode = BlendMode.DstIn
            )
        }
    }
}

@Composable
private fun DeckHeaderRow(
    selectedLanguage: LearningLanguage,
    strings: UiStrings,
    onOpenLeaderboard: () -> Unit,
    onOpenSettings: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 56.dp),
        contentAlignment = Alignment.Center
    ) {
        WavingLanguageFlagBackdrop(
            selectedLanguage = selectedLanguage,
            modifier = Modifier
                .matchParentSize()
                .layout { measurable, constraints ->
                    val extraW = 24.dp.roundToPx()
                    val extraTop = 44.dp.roundToPx()
                    val extraBottom = 46.dp.roundToPx()
                    val expandedW = (constraints.maxWidth + extraW * 2).coerceAtLeast(1)
                    val expandedH = (constraints.maxHeight + extraTop + extraBottom).coerceAtLeast(1)
                    val placeable = measurable.measure(Constraints.fixed(expandedW, expandedH))
                    layout(constraints.maxWidth, constraints.maxHeight) {
                        placeable.place(-extraW, -extraTop)
                    }
                }
        )

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 4.dp, vertical = 6.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                LingoWordsHeaderMark()
                Text(
                    text = strings.appHeaderTitle,
                    fontSize = 30.sp,
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
}

@Composable
private fun FilterDropdownPill(
    label: String,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    val screenWidthDp = LocalConfiguration.current.screenWidthDp
    var fontSizeSp by remember(label, screenWidthDp) { mutableFloatStateOf(12f) }

    Surface(
        shape = RoundedCornerShape(12.dp),
        color = SurfaceBg,
        border = BorderStroke(0.75.dp, CardBorderGray.copy(alpha = 0.85f)),
        modifier = modifier
            .height(36.dp)
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
    ) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(3.dp, Alignment.CenterHorizontally)
        ) {
            Text(
                text = label,
                fontSize = fontSizeSp.sp,
                fontWeight = FontWeight.SemiBold,
                color = TextDark,
                maxLines = 1,
                softWrap = false,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f, fill = false),
                onTextLayout = { result ->
                    if (result.hasVisualOverflow && fontSizeSp > 8.5f) {
                        fontSizeSp = (fontSizeSp - 0.5f).coerceAtLeast(8.5f)
                    }
                }
            )
            Text(
                text = "▾",
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                color = AppleBlue
            )
        }
    }
}

@Composable
private fun DeckGoalIndicatorCard(
    strings: UiStrings,
    selectedLanguage: LearningLanguage,
    selectedLevel: CefrLevel?,
    studyDirection: StudyDirection,
    completedTodayWords: Int,
    dailyGoalWords: Int,
    isGoalReached: Boolean,
    goalProgressFraction: Float,
    onSelectLanguage: (LearningLanguage) -> Unit,
    onSelectLevel: (CefrLevel?) -> Unit,
    onSelectDirection: (StudyDirection) -> Unit,
    onOpenStats: () -> Unit
) {
    var languageDropdownExpanded by remember { mutableStateOf(false) }
    var levelDropdownExpanded by remember { mutableStateOf(false) }
    var directionDropdownExpanded by remember { mutableStateOf(false) }

    Surface(
        shape = RoundedCornerShape(22.dp),
        color = CardSurface,
        border = BorderStroke(0.5.dp, CardBorderGray.copy(alpha = 0.6f)),
        modifier = Modifier
            .fillMaxWidth()
            .shadow(6.dp, RoundedCornerShape(22.dp), spotColor = Color.Black.copy(alpha = 0.05f))
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 15.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Learning Language Dropdown (equal 1/3 pill width)
                Box(modifier = Modifier.weight(1f)) {
                    FilterDropdownPill(
                        label = "${selectedLanguage.flagEmoji} ${selectedLanguage.localizedTitle(strings.isCzech)}",
                        modifier = Modifier.fillMaxWidth(),
                        onClick = { languageDropdownExpanded = true }
                    )

                    DropdownMenu(
                        expanded = languageDropdownExpanded,
                        onDismissRequest = { languageDropdownExpanded = false }
                    ) {
                        LearningLanguage.entries.forEach { lang ->
                            DropdownMenuItem(
                                text = {
                                    Text(
                                        text = "${lang.flagEmoji} ${lang.localizedTitle(strings.isCzech)}",
                                        fontWeight = if (selectedLanguage == lang) FontWeight.Bold else FontWeight.Medium
                                    )
                                },
                                onClick = {
                                    onSelectLanguage(lang)
                                    languageDropdownExpanded = false
                                }
                            )
                        }
                    }
                }

                // Study Direction Dropdown (CZ -> EN / EN -> CZ / Mix)
                Box(modifier = Modifier.weight(1f)) {
                    FilterDropdownPill(
                        label = studyDirection.shortLabel(selectedLanguage),
                        modifier = Modifier.fillMaxWidth(),
                        onClick = { directionDropdownExpanded = true }
                    )

                    DropdownMenu(
                        expanded = directionDropdownExpanded,
                        onDismissRequest = { directionDropdownExpanded = false }
                    ) {
                        StudyDirection.entries.forEach { dir ->
                            DropdownMenuItem(
                                text = {
                                    Text(
                                        text = dir.localizedDescription(strings.isCzech, selectedLanguage),
                                        fontWeight = if (studyDirection == dir) FontWeight.Bold else FontWeight.Medium,
                                        textAlign = TextAlign.Center,
                                        modifier = Modifier.fillMaxWidth()
                                    )
                                },
                                onClick = {
                                    onSelectDirection(dir)
                                    directionDropdownExpanded = false
                                }
                            )
                        }
                    }
                }

                // CEFR Level Dropdown (equal 1/3 pill width, compact A1-C2 label on pill)
                Box(modifier = Modifier.weight(1f)) {
                    FilterDropdownPill(
                        label = selectedLevel?.label ?: "A1–C2",
                        modifier = Modifier.fillMaxWidth(),
                        onClick = { levelDropdownExpanded = true }
                    )

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

            // Clickable progress strip + bar opening Complete Statistics without altering visual design
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = onOpenStats
                    ),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // Clean single-line row for Today Words progress & remaining count
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = if (strings.isCzech) {
                            "Dnes: $completedTodayWords / $dailyGoalWords slov"
                        } else {
                            "Today: $completedTodayWords / $dailyGoalWords words"
                        },
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        color = if (isGoalReached) AppleGreen else TextDark,
                        maxLines = 1
                    )

                    val remaining = (dailyGoalWords - completedTodayWords).coerceAtLeast(0)
                    Text(
                        text = when {
                            isGoalReached -> if (strings.isCzech) "Cíl splněn! 🎉" else "Goal reached! 🎉"
                            strings.isCzech -> "zbývá $remaining"
                            else -> "$remaining left"
                        },
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = if (isGoalReached) AppleGreen else AppleBlue,
                        maxLines = 1
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
    memorySummary: DeckMemorySummary,
    onClick: () -> Unit
) {
    val levelColor = Color(deck.level.colorHex)
    val learnedAnyCount = memorySummary.learnedAnyCount
    val needsReviewCount = memorySummary.needsReviewCount

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
                        text = if (learnedAnyCount > 0) {
                            if (strings.isCzech) "Naučeno $learnedAnyCount / ${deck.words.size}" else "Learned $learnedAnyCount / ${deck.words.size}"
                        } else {
                            strings.wordsInDeck(deck.words.size)
                        },
                        fontSize = 12.sp,
                        color = if (learnedAnyCount > 0) AppleGreen else TextMuted,
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

                Spacer(modifier = Modifier.height(4.dp))
                DeckMemoryProgressRow(summary = memorySummary)
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
private fun DeckMemoryProgressRow(summary: DeckMemorySummary) {
    val pct = summary.progressPercent
    val pctColor = when {
        summary.needsReviewCount > 0 && summary.learnedAnyCount == 0 -> AppleRed
        pct >= 80 -> AppleGreen
        summary.learnedAnyCount > 0 -> AppleBlue
        else -> TextMuted
    }
    val minSegmentWeight = maxOf(2f, summary.totalCount * 0.035f)
    val segments = buildList {
        if (summary.masteredCount > 0) {
            add(AppleGreen to summary.masteredCount.toFloat().coerceAtLeast(minSegmentWeight))
        }
        if (summary.learningCount > 0) {
            add(AppleBlue to summary.learningCount.toFloat().coerceAtLeast(minSegmentWeight))
        }
        if (summary.needsReviewCount > 0) {
            add(AppleRed to summary.needsReviewCount.toFloat().coerceAtLeast(minSegmentWeight))
        }
        if (summary.unseenCount > 0 || isEmpty()) {
            add(SurfaceBg to summary.unseenCount.toFloat().coerceAtLeast(1f))
        }
    }

    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Row(
            modifier = Modifier
                .weight(1f)
                .height(6.dp)
                .clip(RoundedCornerShape(50)),
            horizontalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            segments.forEach { (color, segWeight) ->
                Box(
                    modifier = Modifier
                        .weight(segWeight)
                        .fillMaxHeight()
                        .clip(RoundedCornerShape(50))
                        .background(color)
                )
            }
        }
        Text(
            text = "$pct %",
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold,
            color = pctColor
        )
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
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 420.dp)
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    LearningLanguage.entries.forEach { lang ->
                        Surface(
                            shape = RoundedCornerShape(18.dp),
                            color = SurfaceBg,
                            border = BorderStroke(1.dp, CardBorderGray.copy(alpha = 0.8f)),
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(18.dp))
                                .clickable { onSelectLanguage(lang) }
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 18.dp, vertical = 14.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(14.dp)
                            ) {
                                Text(text = lang.flagEmoji, fontSize = 32.sp)
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = lang.localizedTitle(strings.isCzech),
                                        fontSize = 17.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = TextDark
                                    )
                                    val (wordCount, deckCount) = remember(lang) {
                                        val decks = cz.slovickaspeak.app.model.BuiltInVocabulary.decksForLanguage(lang)
                                        decks.sumOf { it.words.size } to decks.size
                                    }
                                    Text(
                                        text = if (strings.isCzech) {
                                            "$wordCount slov a frází • $deckCount okruhů (A1–C2)"
                                        } else {
                                            "$wordCount words & phrases • $deckCount decks (A1–C2)"
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

