package com.velthy.client.ui.components

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.QueueMusic
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Mic
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import coil3.compose.AsyncImage
import com.velthy.client.data.model.ROW_ART_PX
import com.velthy.client.data.model.Song
import com.velthy.client.data.model.artworkAt
import com.velthy.client.data.recognition.MusicRecognitionEngine
import com.velthy.client.data.recognition.MusicRecognitionEngine.RecognitionState
import com.velthy.client.ui.haptics.Haptic
import com.velthy.client.ui.haptics.Haptics
import com.velthy.client.ui.haptics.rememberHaptics
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

/** How many bars the live level strip is built from. Odd, so one sits dead centre. */
private const val BAR_COUNT = 21

/**
 * The stages the sheet animates between.
 *
 * Deliberately coarser than [RecognitionState]: the listening state re-emits a
 * new amplitude thirty times a second, and a transition keyed on the state
 * itself would restart on every one of those. This is the shape of what is
 * happening; the numbers ride along inside it.
 */
private enum class RecognitionStage { Listening, Identifying, Success, NotFound, Error }

/**
 * "What is this song?" — the microphone sheet.
 *
 * Built from the same parts as every other sheet in the app: the dark card, the
 * small drag handle, rows of icon-plus-label, and the pill for the one action
 * that deserves weight. Nothing here is a stock Material button, because a
 * platform control dropped into a surface where every other control is bespoke
 * reads as the one thing that was not finished.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MusicRecognitionSheet(
    onDismiss: () -> Unit,
    onPlaySong: (Song) -> Unit,
    onAddToQueue: (Song) -> Unit,
    onSearchSong: (String) -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val haptics = rememberHaptics()
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    var hasPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.RECORD_AUDIO,
            ) == PackageManager.PERMISSION_GRANTED,
        )
    }

    var recognitionState by remember { mutableStateOf<RecognitionState>(RecognitionState.Idle) }
    var currentJob by remember { mutableStateOf<Job?>(null) }

    val startRecognition: () -> Unit = {
        currentJob?.cancel()
        currentJob = scope.launch {
            MusicRecognitionEngine.recognizeSong().collectLatest { state ->
                recognitionState = state
            }
        }
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission(),
    ) { granted ->
        hasPermission = granted
        if (granted) {
            startRecognition()
        } else {
            recognitionState = RecognitionState.Error("The microphone permission is needed to listen for music")
        }
    }

    LaunchedEffect(hasPermission) {
        if (hasPermission) {
            startRecognition()
        } else {
            permissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
        }
    }

    val stage = when (recognitionState) {
        is RecognitionState.Idle, is RecognitionState.Listening -> RecognitionStage.Listening
        is RecognitionState.Identifying -> RecognitionStage.Identifying
        is RecognitionState.Success -> RecognitionStage.Success
        is RecognitionState.NotFound -> RecognitionStage.NotFound
        is RecognitionState.Error -> RecognitionStage.Error
    }

    // Opening the mic is a deliberate act, so it gets a press of its own; the
    // answer that comes back gets a second one, different when it is a refusal.
    LaunchedEffect(Unit) { haptics.play(Haptic.Tap) }
    LaunchedEffect(stage) {
        when (stage) {
            RecognitionStage.Success -> haptics.play(Haptic.Select)
            RecognitionStage.NotFound, RecognitionStage.Error -> haptics.play(Haptic.Tick)
            else -> Unit
        }
    }

    ModalBottomSheet(
        onDismissRequest = {
            currentJob?.cancel()
            onDismiss()
        },
        sheetState = sheetState,
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
                .padding(horizontal = 20.dp, vertical = 6.dp)
                .navigationBarsPadding(),
        ) {
            // Header
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "Music Recognition",
                    style = MaterialTheme.typography.titleMedium.copy(
                        fontWeight = FontWeight.Bold,
                        letterSpacing = (-0.2).sp,
                    ),
                    color = Color.White,
                    modifier = Modifier.weight(1f),
                )
                val (status, live) = when (stage) {
                    RecognitionStage.Listening -> "Listening" to true
                    RecognitionStage.Identifying -> "Identifying" to true
                    RecognitionStage.Success -> "Found" to false
                    RecognitionStage.NotFound -> "Not found" to false
                    RecognitionStage.Error -> "Failed" to false
                }
                Text(
                    text = status,
                    style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold),
                    color = if (live || stage == RecognitionStage.Success) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        Color.White.copy(alpha = 0.5f)
                    },
                )
            }

            // One animated swap for the whole body, so the sheet settles into
            // the next state — including its new height — instead of the content
            // blinking over from one layout to another.
            AnimatedContent(
                targetState = stage,
                transitionSpec = {
                    (
                        fadeIn(tween(260, easing = FastOutSlowInEasing)) togetherWith
                            fadeOut(tween(140))
                        ).using(SizeTransform(clip = false))
                },
                label = "recognitionStage",
                modifier = Modifier.fillMaxWidth(),
            ) { current ->
                when (current) {
                    RecognitionStage.Listening -> {
                        val listening = recognitionState as? RecognitionState.Listening
                        ListeningView(
                            amplitude = listening?.amplitude ?: 0f,
                            progress = listening?.progress ?: 0f,
                        )
                    }

                    RecognitionStage.Identifying -> IdentifyingView()

                    RecognitionStage.Success -> {
                        val success = recognitionState as? RecognitionState.Success
                        if (success != null) {
                            RecognizedSongView(
                                recognizedTitle = success.recognizedTitle,
                                recognizedArtist = success.recognizedArtist,
                                matchedSong = success.matchedSong,
                                haptics = haptics,
                                onPlay = {
                                    success.matchedSong?.let(onPlaySong)
                                    onDismiss()
                                },
                                onQueue = {
                                    success.matchedSong?.let(onAddToQueue)
                                    onDismiss()
                                },
                                onSearch = {
                                    onSearchSong("${success.recognizedTitle} ${success.recognizedArtist}")
                                    onDismiss()
                                },
                            )
                        }
                    }

                    RecognitionStage.NotFound -> MessageView(
                        icon = Icons.Rounded.Search,
                        tint = Color.White.copy(alpha = 0.6f),
                        message = (recognitionState as? RecognitionState.NotFound)?.message
                            ?: "Nothing was recognised",
                        onRetry = startRecognition,
                    )

                    RecognitionStage.Error -> MessageView(
                        icon = Icons.Rounded.Close,
                        tint = MaterialTheme.colorScheme.error,
                        message = (recognitionState as? RecognitionState.Error)?.error
                            ?: "Recognition failed",
                        onRetry = {
                            if (!hasPermission) {
                                permissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                            } else {
                                startRecognition()
                            }
                        },
                    )
                }
            }

            Spacer(Modifier.height(8.dp))
        }
    }
}

/**
 * Waiting for something to listen to: the microphone with two rings breathing
 * with it, and below them the live level strip.
 *
 * Every moving part of this is read while the frame is drawn. The bars are a
 * fixed height and only their layer is scaled, so a bouncing strip is a repaint
 * and never a relayout of the twenty-one boxes inside it — which is the whole
 * difference between this and a row of views being re-measured thirty times a
 * second.
 */
@Composable
private fun ListeningView(amplitude: Float, progress: Float) {
    val infiniteTransition = rememberInfiniteTransition(label = "listening")
    val phase by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = (2 * Math.PI).toFloat(),
        animationSpec = infiniteRepeatable(
            animation = tween(1400, easing = LinearEasing),
            repeatMode = RepeatMode.Restart,
        ),
        label = "phase",
    )

    val accent = MaterialTheme.colorScheme.primary
    val accentSoft = MaterialTheme.colorScheme.tertiary
    val orbBrush = remember(accent, accentSoft) { Brush.linearGradient(listOf(accent, accentSoft)) }
    val barBrush = remember(accent, accentSoft) { Brush.verticalGradient(listOf(accent, accentSoft)) }
    val loud = amplitude > 0.12f

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 10.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            modifier = Modifier.size(132.dp),
            contentAlignment = Alignment.Center,
        ) {
            // Outer ring: breathes with the room, and dims back to nothing when
            // the room goes quiet.
            Box(
                modifier = Modifier
                    .size(132.dp)
                    .graphicsLayer {
                        val spread = 1f + amplitude * 0.30f
                        scaleX = spread
                        scaleY = spread
                        alpha = (0.10f + amplitude * 0.24f).coerceIn(0.10f, 0.34f)
                    }
                    .clip(CircleShape)
                    .background(accent),
            )
            Box(
                modifier = Modifier
                    .size(102.dp)
                    .graphicsLayer {
                        val spread = 0.94f + amplitude * 0.10f
                        scaleX = spread
                        scaleY = spread
                    }
                    .clip(CircleShape)
                    .background(accent.copy(alpha = 0.22f)),
            )
            Box(
                modifier = Modifier
                    .size(72.dp)
                    .clip(CircleShape)
                    .background(orbBrush),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = Icons.Rounded.Mic,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(32.dp),
                )
            }
        }

        Spacer(Modifier.height(18.dp))

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(46.dp)
                .clip(RoundedCornerShape(14.dp))
                .background(Color.White.copy(alpha = 0.06f))
                .padding(horizontal = 14.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.Bottom,
        ) {
            for (i in 0 until BAR_COUNT) {
                // A bell over the strip: the middle bars carry the most, so a
                // voice lands as a shape rather than as a block of equal spikes.
                val distance = Math.abs(i - (BAR_COUNT - 1) / 2f) / ((BAR_COUNT - 1) / 2f)
                val weight = (1f - distance * 0.55f).coerceIn(0.35f, 1f)
                Box(
                    modifier = Modifier
                        .width(3.dp)
                        .height(30.dp)
                        .graphicsLayer {
                            val ripple = Math.sin(phase.toDouble() + i * 0.45).toFloat()
                            val level = (amplitude * weight + ripple * 0.14f).coerceIn(0.06f, 1f)
                            transformOrigin = TransformOrigin(0.5f, 1f)
                            scaleY = level
                        }
                        .clip(RoundedCornerShape(2.dp))
                        .background(barBrush),
                )
            }
        }

        Spacer(Modifier.height(18.dp))

        Text(
            text = "Listening…",
            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
            color = Color.White,
        )

        Spacer(Modifier.height(4.dp))

        Text(
            text = if (loud) "Hearing it — hold still" else "Hold your phone near the music",
            style = MaterialTheme.typography.bodyMedium,
            color = if (loud) accent else Color.White.copy(alpha = 0.55f),
        )

        Spacer(Modifier.height(16.dp))

        Box(
            modifier = Modifier
                .fillMaxWidth(0.7f)
                .height(5.dp)
                .clip(RoundedCornerShape(3.dp))
                .background(Color.White.copy(alpha = 0.12f)),
        ) {
            Box(
                modifier = Modifier
                    .matchParentSize()
                    .graphicsLayer {
                        transformOrigin = TransformOrigin(0f, 0.5f)
                        scaleX = progress.coerceIn(0f, 1f)
                    }
                    .background(accent, RoundedCornerShape(3.dp)),
            )
        }
    }
}

@Composable
private fun IdentifyingView() {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 34.dp, bottom = 26.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        CircularProgressIndicator(
            modifier = Modifier.size(52.dp),
            strokeWidth = 3.5.dp,
            color = MaterialTheme.colorScheme.primary,
            trackColor = Color.White.copy(alpha = 0.10f),
        )
        Spacer(Modifier.height(20.dp))
        Text(
            text = "Identifying the song…",
            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
            color = Color.White,
        )
        Spacer(Modifier.height(6.dp))
        Text(
            text = "Matching what it heard",
            style = MaterialTheme.typography.bodyMedium,
            color = Color.White.copy(alpha = 0.55f),
        )
    }
}

@Composable
private fun RecognizedSongView(
    recognizedTitle: String,
    recognizedArtist: String,
    matchedSong: Song?,
    haptics: Haptics,
    onPlay: () -> Unit,
    onQueue: () -> Unit,
    onSearch: () -> Unit,
) {
    val title = matchedSong?.title ?: recognizedTitle
    val artist = matchedSong?.artist ?: recognizedArtist
    val artwork = matchedSong?.thumbnailUrl

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 6.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            modifier = Modifier
                .size(148.dp)
                .clip(RoundedCornerShape(18.dp))
                .background(Color.White.copy(alpha = 0.06f)),
            contentAlignment = Alignment.Center,
        ) {
            if (artwork != null) {
                AsyncImage(
                    model = artwork.artworkAt(ROW_ART_PX),
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize(),
                )
            } else {
                Icon(
                    imageVector = Icons.Rounded.MusicNote,
                    contentDescription = null,
                    tint = Color.White.copy(alpha = 0.4f),
                    modifier = Modifier.size(48.dp),
                )
            }
        }

        Spacer(Modifier.height(16.dp))

        Text(
            text = title,
            style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
            color = Color.White,
            textAlign = TextAlign.Center,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )

        Spacer(Modifier.height(4.dp))

        Text(
            text = artist,
            style = MaterialTheme.typography.bodyLarge,
            color = Color.White.copy(alpha = 0.6f),
            textAlign = TextAlign.Center,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )

        Spacer(Modifier.height(18.dp))

        // The three things to do with it, as the same inset row the rest of the
        // app uses for actions.
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(18.dp))
                .background(Color.White.copy(alpha = 0.08f)),
        ) {
            RecognitionActionRow(
                icon = Icons.Rounded.PlayArrow,
                label = "Play",
                onClick = { haptics.play(Haptic.Tap); onPlay() },
            )
            RowDivider()
            RecognitionActionRow(
                icon = Icons.AutoMirrored.Rounded.QueueMusic,
                label = "Add to queue",
                onClick = { haptics.play(Haptic.Tap); onQueue() },
            )
            RowDivider()
            RecognitionActionRow(
                icon = Icons.Rounded.Search,
                label = "Search on YouTube Music",
                onClick = { haptics.play(Haptic.Tap); onSearch() },
            )
        }
    }
}

@Composable
private fun MessageView(
    icon: ImageVector,
    tint: Color,
    message: String,
    onRetry: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            modifier = Modifier
                .size(64.dp)
                .clip(CircleShape)
                .background(tint.copy(alpha = 0.14f)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = tint,
                modifier = Modifier.size(28.dp),
            )
        }

        Spacer(Modifier.height(14.dp))

        Text(
            text = message,
            style = MaterialTheme.typography.bodyLarge,
            color = Color.White.copy(alpha = 0.8f),
            textAlign = TextAlign.Center,
        )

        Spacer(Modifier.height(18.dp))

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(14.dp))
                .background(Color.White.copy(alpha = 0.12f))
                .clickable(onClick = onRetry)
                .padding(vertical = 12.dp),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = Icons.Rounded.Refresh,
                contentDescription = null,
                tint = Color.White,
                modifier = Modifier.size(18.dp),
            )
            Spacer(Modifier.width(6.dp))
            Text(
                text = "Try Again",
                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                color = Color.White,
            )
        }
    }
}

@Composable
private fun RecognitionActionRow(
    icon: ImageVector,
    label: String,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 11.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(32.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(Color.White.copy(alpha = 0.08f)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = Color.White.copy(alpha = 0.85f),
                modifier = Modifier.size(17.dp),
            )
        }
        Spacer(Modifier.width(14.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.bodyLarge,
            color = Color.White,
        )
    }
}

@Composable
private fun RowDivider() {
    HorizontalDivider(
        modifier = Modifier.padding(start = 16.dp),
        thickness = 0.5.dp,
        color = Color.White.copy(alpha = 0.08f),
    )
}
