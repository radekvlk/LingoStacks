package cz.slovickaspeak.app.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import cz.slovickaspeak.app.model.AnswerMethod
import cz.slovickaspeak.app.model.CardAttemptResult
import cz.slovickaspeak.app.model.VocabDeck
import cz.slovickaspeak.app.model.VocabWord
import cz.slovickaspeak.app.ui.theme.*
import kotlin.math.roundToInt

@Composable
fun SessionSummaryScreen(
    deck: VocabDeck,
    results: List<CardAttemptResult>,
    onRetryMissed: (List<VocabWord>) -> Unit,
    onRestartAll: () -> Unit,
    onBackToDecks: () -> Unit
) {
    val strings = rememberAppStrings()
    val total = results.size.coerceAtLeast(1)
    val correctCount = results.count { it.isCorrect }
    val spokenCount = results.count { it.method == AnswerMethod.SPOKEN_CORRECT }
    val missedWords = results.filter { !it.isCorrect }.map { it.word }
    val percentage = ((correctCount.toFloat() / total) * 100).roundToInt()

    Scaffold(
        containerColor = SurfaceBg
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 20.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Top Trophy Header
            Card(
                shape = RoundedCornerShape(24.dp),
                colors = CardDefaults.cardColors(containerColor = CardSurface),
                border = BorderStroke(2.dp, CardBorderGray),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(20.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = if (percentage >= 80) "🏆" else if (percentage >= 50) "👏" else "💪",
                        fontSize = 54.sp
                    )
                    Text(
                        text = strings.summaryCompleteTitle,
                        fontSize = 24.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = TextDark
                    )
                    Text(
                        text = "${deck.localizedTitle(strings.isCzech)} (${deck.level.label})",
                        fontSize = 15.sp,
                        color = TextMuted,
                        fontWeight = FontWeight.SemiBold
                    )

                    Spacer(modifier = Modifier.height(4.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceEvenly
                    ) {
                        StatBadge(
                            value = "$percentage %",
                            label = strings.summaryAccuracy,
                            color = DuoGreen
                        )
                        StatBadge(
                            value = "$spokenCount",
                            label = strings.summarySpoken,
                            color = DuoBlue
                        )
                        StatBadge(
                            value = "${missedWords.size}",
                            label = strings.summaryMissed,
                            color = if (missedWords.isEmpty()) DuoGreen else DuoRed
                        )
                    }
                }
            }

            Text(
                text = strings.summaryWordsOverview,
                fontSize = 17.sp,
                fontWeight = FontWeight.ExtraBold,
                color = TextDark
            )

            LazyColumn(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                items(results) { item ->
                    ResultWordRow(strings = strings, item = item)
                }
            }

            // Bottom Action Buttons
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                if (missedWords.isNotEmpty()) {
                    Button(
                        onClick = { onRetryMissed(missedWords) },
                        shape = RoundedCornerShape(16.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = DuoOrange,
                            contentColor = Color.White
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(54.dp)
                    ) {
                        Icon(imageVector = Icons.Default.Refresh, contentDescription = null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = strings.summaryRetryMissed(missedWords.size),
                            fontWeight = FontWeight.ExtraBold,
                            fontSize = 15.sp
                        )
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    OutlinedButton(
                        onClick = onRestartAll,
                        shape = RoundedCornerShape(16.dp),
                        border = BorderStroke(2.dp, DuoBlue),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = DuoBlueDark),
                        modifier = Modifier
                            .weight(1f)
                            .height(52.dp)
                    ) {
                        Icon(imageVector = Icons.Default.Refresh, contentDescription = null)
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = strings.summaryRestartAll,
                            fontWeight = FontWeight.ExtraBold
                        )
                    }

                    Button(
                        onClick = onBackToDecks,
                        shape = RoundedCornerShape(16.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = DuoGreen,
                            contentColor = Color.White
                        ),
                        modifier = Modifier
                            .weight(1f)
                            .height(52.dp)
                    ) {
                        Icon(imageVector = Icons.Default.Home, contentDescription = null)
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = strings.summaryBackToDecks,
                            fontWeight = FontWeight.ExtraBold
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun StatBadge(
    value: String,
    label: String,
    color: Color
) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            text = value,
            fontSize = 22.sp,
            fontWeight = FontWeight.ExtraBold,
            color = color
        )
        Text(
            text = label,
            fontSize = 12.sp,
            fontWeight = FontWeight.SemiBold,
            color = TextMuted
        )
    }
}

@Composable
private fun ResultWordRow(
    strings: UiStrings,
    item: CardAttemptResult
) {
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = CardSurface,
        border = BorderStroke(
            width = 1.5.dp,
            color = if (item.isCorrect) DuoGreen.copy(alpha = 0.45f) else DuoRed.copy(alpha = 0.45f)
        ),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(text = item.word.emoji, fontSize = 28.sp)

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "${item.word.czech} → ${item.word.english}",
                    fontWeight = FontWeight.ExtraBold,
                    fontSize = 15.sp,
                    color = TextDark
                )
                val methodLabel = when (item.method) {
                    AnswerMethod.SPOKEN_CORRECT -> strings.methodSpoken
                    AnswerMethod.SWIPED_KNOW -> strings.methodSwipedKnow
                    AnswerMethod.SWIPED_DONT_KNOW -> strings.methodSwipedDontKnow
                }
                Text(
                    text = methodLabel,
                    fontSize = 12.sp,
                    color = TextMuted
                )
            }

            Box(
                modifier = Modifier
                    .size(32.dp)
                    .clip(CircleShape)
                    .background(if (item.isCorrect) DuoGreen else DuoRed),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = when (item.method) {
                        AnswerMethod.SPOKEN_CORRECT -> Icons.Default.Mic
                        AnswerMethod.SWIPED_KNOW -> Icons.Default.Check
                        AnswerMethod.SWIPED_DONT_KNOW -> Icons.Default.Close
                    },
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(18.dp)
                )
            }
        }
    }
}

@androidx.compose.ui.tooling.preview.Preview(showBackground = true, widthDp = 390, heightDp = 800)
@Composable
fun SessionSummaryScreenPreview() {
    val sampleDeck = cz.slovickaspeak.app.model.BuiltInVocabulary.decks.first()
    val sampleResults = listOf(
        CardAttemptResult(sampleDeck.words[0], true, AnswerMethod.SPOKEN_CORRECT, "apple"),
        CardAttemptResult(sampleDeck.words[1], true, AnswerMethod.SWIPED_KNOW),
        CardAttemptResult(sampleDeck.words[2], false, AnswerMethod.SWIPED_DONT_KNOW)
    )
    SlovickaSpeakTheme {
        SessionSummaryScreen(
            deck = sampleDeck,
            results = sampleResults,
            onRetryMissed = {},
            onRestartAll = {},
            onBackToDecks = {}
        )
    }
}
