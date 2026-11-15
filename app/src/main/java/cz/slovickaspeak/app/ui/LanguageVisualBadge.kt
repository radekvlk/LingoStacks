package cz.slovickaspeak.app.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import cz.slovickaspeak.app.model.LearningLanguage
import cz.slovickaspeak.app.ui.theme.AppleBlue
import cz.slovickaspeak.app.ui.theme.CardBorderGray

enum class FlagDesignMode {
    ROUNDED_BADGE,  // Option 1: Apple-style rounded rectangle flag badge with hairline border
    CIRCULAR_BADGE, // Option 1b: Circular avatar flag
    VECTOR_FLAT,    // Option 2: Pure geometric flat vector flag
    ISO_CODE        // Option 3: Modern typography ISO chip
}

// Current visual mode to showcase to the user
var activeFlagDesignMode: FlagDesignMode = FlagDesignMode.VECTOR_FLAT

@Composable
fun LanguageVisualBadge(
    language: LearningLanguage,
    mode: FlagDesignMode = activeFlagDesignMode,
    modifier: Modifier = Modifier
) {
    when (mode) {
        FlagDesignMode.ROUNDED_BADGE -> {
            Box(
                modifier = modifier
                    .size(width = 20.dp, height = 14.dp)
                    .clip(RoundedCornerShape(3.5.dp))
                    .background(Color.White)
                    .border(0.5.dp, CardBorderGray.copy(alpha = 0.85f), RoundedCornerShape(3.5.dp)),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = language.flagEmoji,
                    fontSize = 11.5.sp,
                    lineHeight = 11.5.sp,
                    textAlign = TextAlign.Center
                )
            }
        }
        FlagDesignMode.CIRCULAR_BADGE -> {
            Box(
                modifier = modifier
                    .size(17.dp)
                    .clip(CircleShape)
                    .background(Color.White)
                    .border(0.5.dp, CardBorderGray.copy(alpha = 0.85f), CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = language.flagEmoji,
                    fontSize = 12.sp,
                    lineHeight = 12.sp,
                    textAlign = TextAlign.Center
                )
            }
        }
        FlagDesignMode.VECTOR_FLAT -> {
            Canvas(
                modifier = modifier
                    .size(width = 20.dp, height = 14.dp)
                    .clip(RoundedCornerShape(3.dp))
                    .border(0.5.dp, CardBorderGray.copy(alpha = 0.7f), RoundedCornerShape(3.dp))
            ) {
                val w = size.width
                val h = size.height
                when (language) {
                    LearningLanguage.CZECH -> {
                        drawRect(Color.White, topLeft = Offset.Zero, size = Size(w, h / 2f))
                        drawRect(Color(0xFFD7141A), topLeft = Offset(0f, h / 2f), size = Size(w, h / 2f))
                        val triangle = Path().apply {
                            moveTo(0f, 0f)
                            lineTo(w * 0.5f, h * 0.5f)
                            lineTo(0f, h)
                            close()
                        }
                        drawPath(triangle, Color(0xFF11457E))
                    }
                    LearningLanguage.GERMAN -> {
                        val h3 = h / 3f
                        drawRect(Color(0xFF262626), topLeft = Offset(0f, 0f), size = Size(w, h3))
                        drawRect(Color(0xFFDD0000), topLeft = Offset(0f, h3), size = Size(w, h3))
                        drawRect(Color(0xFFFFCE00), topLeft = Offset(0f, h3 * 2f), size = Size(w, h3))
                    }
                    LearningLanguage.FRENCH -> {
                        val w3 = w / 3f
                        drawRect(Color(0xFF0055A4), topLeft = Offset(0f, 0f), size = Size(w3, h))
                        drawRect(Color.White, topLeft = Offset(w3, 0f), size = Size(w3, h))
                        drawRect(Color(0xFFEF4135), topLeft = Offset(w3 * 2f, 0f), size = Size(w3, h))
                    }
                    LearningLanguage.ITALIAN -> {
                        val w3 = w / 3f
                        drawRect(Color(0xFF009246), topLeft = Offset(0f, 0f), size = Size(w3, h))
                        drawRect(Color.White, topLeft = Offset(w3, 0f), size = Size(w3, h))
                        drawRect(Color(0xFFCE2B37), topLeft = Offset(w3 * 2f, 0f), size = Size(w3, h))
                    }
                    LearningLanguage.SPANISH -> {
                        val h4 = h / 4f
                        drawRect(Color(0xFFAA151B), topLeft = Offset(0f, 0f), size = Size(w, h4))
                        drawRect(Color(0xFFF1BF00), topLeft = Offset(0f, h4), size = Size(w, h4 * 2f))
                        drawRect(Color(0xFFAA151B), topLeft = Offset(0f, h4 * 3f), size = Size(w, h4))
                    }
                    LearningLanguage.ENGLISH -> {
                        drawRect(Color(0xFF012169), topLeft = Offset.Zero, size = size)
                        drawLine(Color.White, Offset(0f, 0f), Offset(w, h), strokeWidth = w * 0.18f)
                        drawLine(Color.White, Offset(0f, h), Offset(w, 0f), strokeWidth = w * 0.18f)
                        drawLine(Color(0xFFC8102E), Offset(0f, 0f), Offset(w, h), strokeWidth = w * 0.08f)
                        drawLine(Color(0xFFC8102E), Offset(0f, h), Offset(w, 0f), strokeWidth = w * 0.08f)
                        drawRect(Color.White, topLeft = Offset(w * 0.38f, 0f), size = Size(w * 0.24f, h))
                        drawRect(Color.White, topLeft = Offset(0f, h * 0.32f), size = Size(w, h * 0.36f))
                        drawRect(Color(0xFFC8102E), topLeft = Offset(w * 0.43f, 0f), size = Size(w * 0.14f, h))
                        drawRect(Color(0xFFC8102E), topLeft = Offset(0f, h * 0.40f), size = Size(w, h * 0.20f))
                    }
                    LearningLanguage.PORTUGUESE -> {
                        val wSplit = w * 0.40f
                        drawRect(Color(0xFF046A38), topLeft = Offset.Zero, size = Size(wSplit, h))
                        drawRect(Color(0xFFDA291C), topLeft = Offset(wSplit, 0f), size = Size(w - wSplit, h))
                        drawCircle(Color(0xFFFFDF00), radius = h * 0.26f, center = Offset(wSplit, h * 0.5f))
                    }
                }
            }
        }
        FlagDesignMode.ISO_CODE -> {
            Surface(
                shape = RoundedCornerShape(4.dp),
                color = AppleBlue.copy(alpha = 0.12f),
                modifier = modifier
            ) {
                Text(
                    text = language.code.uppercase(),
                    fontSize = 9.sp,
                    fontWeight = FontWeight.Bold,
                    color = AppleBlue,
                    modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)
                )
            }
        }
    }
}
