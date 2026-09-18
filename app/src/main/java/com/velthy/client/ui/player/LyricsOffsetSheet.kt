package com.velthy.client.ui.player

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.RestartAlt
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.velthy.client.data.settings.AppSettings
import com.velthy.client.ui.haptics.Haptic
import com.velthy.client.ui.haptics.rememberHaptics
import java.util.Locale
import kotlin.math.roundToInt

private val CONTROL_SHAPE = RoundedCornerShape(16.dp)
private val BUTTON_SHAPE = RoundedCornerShape(14.dp)
private const val StepMs = 100

/**
 * A player sheet for shifting all synced lyrics against the playback clock.
 *
 * Shaped as [AudioOutputSheet] is, because they are the same kind of control:
 * one value over the whole track, adjusted on a sheet that slides up from the
 * bottom and drags down to dismiss. The whole page moves at once because that
 * is what the fix is — a lyric file is stamped once by whoever wrote it, and if
 * it is off it is off by a constant for the whole song. Nudging line by line
 * would be an editor, not a control.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun LyricsOffsetSheet(
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val offsetMs by AppSettings.lyricsOffsetMs.collectAsStateWithLifecycle()
    val haptics = rememberHaptics()
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        modifier = modifier,
        containerColor = Color(0xFF16161A),
        contentColor = Color.White,
        shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp),
        dragHandle = {
            Box(
                modifier = Modifier
                    .padding(top = 10.dp, bottom = 6.dp)
                    .size(width = 32.dp, height = 4.dp)
                    .background(Color.White.copy(alpha = 0.25f), CircleShape),
            )
        },
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .navigationBarsPadding()
                .padding(bottom = 20.dp),
        ) {
            Text(
                text = "Lyrics offset",
                style = MaterialTheme.typography.titleMedium.copy(
                    fontWeight = FontWeight.Bold,
                    letterSpacing = (-0.2).sp,
                ),
                color = Color.White,
                modifier = Modifier.fillMaxWidth(),
            )
            Text(
                text = "Shifts every synced line against the track, for lyrics that are " +
                    "stamped a little early or late.",
                style = MaterialTheme.typography.bodyMedium,
                color = Color.White.copy(alpha = 0.55f),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 3.dp, bottom = 14.dp),
            )

            // The value reads as a clock rather than a caption — it is the one
            // number someone who opened this sheet came for — with the slider
            // and the fine steps sharing the row under it.
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(CONTROL_SHAPE)
                    .background(Color.White.copy(alpha = 0.05f))
                    .padding(horizontal = 16.dp, vertical = 16.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(
                    text = formatOffset(offsetMs),
                    style = MaterialTheme.typography.headlineSmall.copy(
                        fontWeight = FontWeight.Bold,
                        letterSpacing = (-0.5).sp,
                    ),
                    color = Color.White,
                )
                Spacer(Modifier.size(14.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    StepButton(
                        label = "−",
                        contentDescription = "Decrease lyrics offset",
                        enabled = offsetMs > AppSettings.MIN_LYRICS_OFFSET_MS,
                    ) {
                        haptics.play(Haptic.Select)
                        AppSettings.setLyricsOffsetMs(offsetMs - StepMs)
                    }
                    ThinSlider(
                        value = offsetToFraction(offsetMs),
                        onValueChange = { AppSettings.setLyricsOffsetMs(fractionToOffset(it)) },
                        idleHeight = 6.dp,
                        activeHeight = 10.dp,
                        modifier = Modifier.weight(1f),
                    )
                    StepButton(
                        label = "+",
                        contentDescription = "Increase lyrics offset",
                        enabled = offsetMs < AppSettings.MAX_LYRICS_OFFSET_MS,
                    ) {
                        haptics.play(Haptic.Select)
                        AppSettings.setLyricsOffsetMs(offsetMs + StepMs)
                    }
                }
            }

            Spacer(Modifier.size(10.dp))

            // A row of the same inset weight as the output rows above it, so
            // resetting reads as one of the sheet's actions rather than a stray
            // word under the slider.
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(CONTROL_SHAPE)
                    .background(Color.White.copy(alpha = if (offsetMs == 0) 0.03f else 0.05f))
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        enabled = offsetMs != 0,
                    ) {
                        haptics.play(Haptic.Tap)
                        AppSettings.setLyricsOffsetMs(0)
                    }
                    .padding(horizontal = 16.dp, vertical = 14.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    imageVector = Icons.Rounded.RestartAlt,
                    contentDescription = null,
                    tint = Color.White.copy(alpha = if (offsetMs == 0) 0.35f else 0.8f),
                    modifier = Modifier.size(20.dp),
                )
                Spacer(Modifier.width(12.dp))
                Text(
                    text = "Reset to zero",
                    style = MaterialTheme.typography.bodyLarge,
                    color = Color.White.copy(alpha = if (offsetMs == 0) 0.4f else 0.9f),
                )
            }
        }
    }
}

@Composable
private fun StepButton(
    label: String,
    contentDescription: String,
    enabled: Boolean,
    onClick: () -> Unit,
) {
    Box(
        modifier = Modifier
            .size(44.dp)
            .clip(BUTTON_SHAPE)
            .background(
                Color.White.copy(alpha = if (enabled) 0.08f else 0.03f),
            )
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                enabled = enabled,
                onClick = onClick,
            ),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Medium),
            color = Color.White.copy(alpha = if (enabled) 0.9f else 0.25f),
        )
    }
}

private fun offsetToFraction(offsetMs: Int): Float =
    (offsetMs - AppSettings.MIN_LYRICS_OFFSET_MS).toFloat() /
        (AppSettings.MAX_LYRICS_OFFSET_MS - AppSettings.MIN_LYRICS_OFFSET_MS)

private fun fractionToOffset(fraction: Float): Int {
    val raw = AppSettings.MIN_LYRICS_OFFSET_MS +
        fraction.coerceIn(0f, 1f) *
        (AppSettings.MAX_LYRICS_OFFSET_MS - AppSettings.MIN_LYRICS_OFFSET_MS)
    return (raw / StepMs).roundToInt() * StepMs
}

private fun formatOffset(offsetMs: Int): String = String.format(
    Locale.getDefault(),
    "%+.1f s",
    offsetMs / 1_000f,
)
