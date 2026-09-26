package com.velthy.client.ui.player

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * The widest the two columns are allowed to get between them, centred in
 * whatever is left over.
 *
 * Without a cap the lyric column simply takes every pixel past the artwork, and
 * on a wide window that is a column of text with a hand's width of empty
 * backdrop trailing off the right of every line. Lyrics are read down, not
 * across: past a certain measure the extra width is further for the eye to
 * travel back rather than more room for the words.
 */
private val LANDSCAPE_PLAYER_MAX_WIDTH = 1100.dp

/**
 * Below this height the landscape player tightens its right column — smaller
 * transport, shorter gaps — so a phone on its side fits the credits, the
 * scrubber, the transport and the volume bar between its status and navigation
 * bars without any of them falling off the bottom.
 */
private val LANDSCAPE_COMPACT_HEIGHT = 440.dp

/**
 * Side margin of the landscape player's sleeve column and credits on a
 * phone-height window, in place of the player's ordinary gutter.
 */
private val LANDSCAPE_GUTTER_COMPACT = 20.dp

/**
 * How the right column hands over between the player, the lyrics and the queue.
 * The left column never moves, so a crossfade is the whole transition — the
 * same page being turned, not a panel being opened.
 */
private const val LANDSCAPE_PANE_FADE_IN_MS = 220
private const val LANDSCAPE_PANE_FADE_IN_DELAY_MS = 90
private const val LANDSCAPE_PANE_FADE_OUT_MS = 140

/** Which of its three things the player's content column is showing. */
internal enum class PlayerPane { Main, Lyrics, Queue }

/**
 * How much room the dismiss strip is given at the top of the landscape layout.
 *
 * The portrait player reserves the same height for its own strip, and the
 * gesture there is what closes the player — Velthy's player is drawn by the
 * host rather than being a bottom sheet, so there is nothing else to inherit a
 * downward drag from. Landscape therefore keeps the strip, and it is the one
 * piece of chrome that is *not* decoration.
 */
internal val LANDSCAPE_DISMISS_STRIP = 44.dp

/**
 * The player in a landscape window — a tablet, or a phone on its side. See
 * [landscapePlayerAvailable].
 *
 * Two columns of equal width. The left one is the same in every state: the
 * sleeve, and under it the lyrics / output / queue row with the output name —
 * the controls for *which* of the three the right column shows. The right one
 * is that one thing: the credits and transport, the lyric sheet or the queue.
 * Nothing else is drawn twice and nothing moves between columns, so opening the
 * lyrics or the queue is only ever the right column's page being turned.
 *
 * Purely the arrangement. Every slot is built by [NowPlayingScreen], which is
 * where the state behind all of them lives — the same state the portrait player
 * reads, so rotating mid-song carries the open panel, the scrub and the
 * translation mode straight across. That is also why this file can stay as
 * small as it is: the player's own composables and constants are private to the
 * file that owns the state, and none of them has to be opened up to be arranged
 * differently.
 *
 * Held to [LANDSCAPE_PLAYER_MAX_WIDTH] and centred, inside the safe-drawing
 * insets: a phone on its side has its status bar, navigation bar and camera
 * cutout down the short edges, and the player's content keeps clear of all
 * three while its backdrop still runs underneath them.
 */
@Composable
internal fun LandscapePlayerLayout(
    pane: PlayerPane,
    /** The player's own side margin, so both columns agree with portrait. */
    gutter: Dp,
    background: @Composable (Modifier) -> Unit,
    /** The way out of the player — the only downward drag in this layout. */
    dismissStrip: @Composable () -> Unit,
    /** The sleeve; handed a modifier that already fixes its square size. */
    artwork: @Composable (Modifier) -> Unit,
    /** The row under the sleeve, emitted into its column. */
    actions: @Composable () -> Unit,
    /** Credits and transport. [compact] is a phone-height window. */
    mainPane: @Composable (compact: Boolean) -> Unit,
    lyricsPane: @Composable () -> Unit,
    queuePane: @Composable () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(modifier = modifier.fillMaxSize()) {
        background(Modifier.fillMaxSize())

        BoxWithConstraints(
            modifier = Modifier
                .fillMaxSize()
                .windowInsetsPadding(WindowInsets.safeDrawing),
            contentAlignment = Alignment.TopCenter,
        ) {
            val compact = maxHeight < LANDSCAPE_COMPACT_HEIGHT
            val sideGutter = if (compact) LANDSCAPE_GUTTER_COMPACT else gutter

            Column(
                modifier = Modifier
                    .widthIn(max = LANDSCAPE_PLAYER_MAX_WIDTH)
                    .fillMaxSize(),
            ) {
                // The strip that hands a downward drag back to the host, which
                // is what closes the player. Nothing else in this layout
                // consumes one, so a drag anywhere else on the sheet still
                // reaches it — this is only the part that says so.
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(LANDSCAPE_DISMISS_STRIP),
                    contentAlignment = Alignment.Center,
                ) {
                    dismissStrip()
                }

                Row(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .padding(bottom = if (compact) 8.dp else 20.dp),
                ) {
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight()
                            .padding(horizontal = sideGutter),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        // The sleeve is square and takes whichever of the two
                        // axes runs out first once the row below has had its
                        // height — the column's width on a big tablet, the
                        // height on anything shorter. Measured here rather than
                        // estimated, so the row under it can never be pushed
                        // off the bottom.
                        BoxWithConstraints(
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxWidth(),
                            contentAlignment = Alignment.Center,
                        ) {
                            artwork(Modifier.size(minOf(maxWidth, maxHeight)))
                        }
                        Spacer(Modifier.height(if (compact) 12.dp else 24.dp))
                        actions()
                    }

                    AnimatedContent(
                        targetState = pane,
                        transitionSpec = {
                            fadeIn(
                                tween(
                                    LANDSCAPE_PANE_FADE_IN_MS,
                                    delayMillis = LANDSCAPE_PANE_FADE_IN_DELAY_MS,
                                ),
                            ) togetherWith fadeOut(tween(LANDSCAPE_PANE_FADE_OUT_MS))
                        },
                        contentAlignment = Alignment.Center,
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight(),
                        label = "landscapePane",
                    ) { shown ->
                        when (shown) {
                            PlayerPane.Main -> Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .padding(horizontal = sideGutter),
                                contentAlignment = Alignment.Center,
                            ) {
                                mainPane(compact)
                            }
                            // The lyric sheet and the queue both reach back
                            // across the gutter on their own — see
                            // [bleedHorizontally] — so that is the padding they
                            // get: their scroll areas then run exactly to the
                            // column's edges.
                            PlayerPane.Lyrics -> Box(
                                Modifier
                                    .fillMaxSize()
                                    .padding(horizontal = gutter),
                            ) {
                                lyricsPane()
                            }
                            PlayerPane.Queue -> Box(
                                Modifier
                                    .fillMaxSize()
                                    .padding(horizontal = gutter),
                            ) {
                                queuePane()
                            }
                        }
                    }
                }
            }
        }
    }
}
