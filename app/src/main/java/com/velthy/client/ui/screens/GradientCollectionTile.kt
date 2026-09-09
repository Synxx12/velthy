package com.velthy.client.ui.screens

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage

/**
 * A BitChord-style collection card: a colour gradient the title's own hash
 * picks, with a tilted square cover leaning out of the far corner. Wherever
 * there is no cover, the tilted tile is still there — just as frosted glass —
 * so the cards read as one family rather than some of them carrying a shape of
 * their own.
 *
 * Shared by the Explore tab and the Library's Playlists "Show all" page so the
 * two full-list views keep the same look. A long press is optional (the grid
 * opens it into a collection menu; Explore doesn't use one).
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun GradientCollectionTile(
    title: String,
    coverUrl: String?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    onLongClick: (() -> Unit)? = null,
) {
    val from = collectionTileColor(title)
    val tap = if (onLongClick != null) {
        Modifier.combinedClickable(onClick = onClick, onLongClick = onLongClick)
    } else {
        Modifier.clickable(onClick = onClick)
    }
    Box(
        modifier = modifier
            .height(100.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(
                Brush.linearGradient(
                    listOf(
                        from,
                        Color(
                            red = (from.red * 0.68f).coerceIn(0f, 1f),
                            green = (from.green * 0.68f).coerceIn(0f, 1f),
                            blue = (from.blue * 0.68f).coerceIn(0f, 1f),
                        ),
                    ),
                ),
            )
            .then(tap)
            .padding(12.dp),
    ) {
        Box(
            Modifier
                .align(Alignment.BottomEnd)
                .offset(x = 10.dp, y = 12.dp)
                .size(82.dp)
                .graphicsLayer { rotationZ = 16f }
                .clip(RoundedCornerShape(7.dp))
                .background(Color.White.copy(alpha = 0.22f)),
            contentAlignment = Alignment.Center,
        ) {
            if (coverUrl != null) {
                AsyncImage(
                    model = coverUrl,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize(),
                )
            } else {
                // Moods don't ship cover art, so the tilted tile carries a note
                // instead of sitting as an empty pane of glass.
                Icon(
                    imageVector = Icons.Rounded.MusicNote,
                    contentDescription = null,
                    tint = Color.White.copy(alpha = 0.6f),
                    modifier = Modifier.size(34.dp),
                )
            }
        }
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = Color.White,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier
                .align(Alignment.TopStart)
                .padding(end = 48.dp),
        )
    }
}

/** A stable colour per title, so a card keeps its hue across refreshes. */
internal fun collectionTileColor(title: String): Color =
    when ((title.hashCode() and Int.MAX_VALUE) % 8) {
        0 -> Color(0xFFE64A19)
        1 -> Color(0xFFEC0B65)
        2 -> Color(0xFF8664AC)
        3 -> Color(0xFF6B4EFF)
        4 -> Color(0xFFBE6100)
        5 -> Color(0xFF233C78)
        6 -> Color(0xFF4D97E5)
        else -> Color(0xFFAA267E)
    }
