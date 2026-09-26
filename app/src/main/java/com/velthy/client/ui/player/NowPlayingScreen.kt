package com.velthy.client.ui.player

import android.content.Intent
import android.database.ContentObserver
import android.graphics.Bitmap
import android.media.AudioManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.content.pm.PackageManager
import android.view.View
import android.widget.Toast
import android.window.OnBackInvokedCallback
import android.window.OnBackInvokedDispatcher
import androidx.activity.compose.BackHandler
import androidx.annotation.RequiresApi
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalView
import com.velthy.client.ui.haptics.Haptic
import com.velthy.client.ui.haptics.rememberHaptics
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.ui.draw.BlurredEdgeTreatment
import androidx.compose.ui.draw.blur
import androidx.compose.runtime.produceState
import kotlinx.coroutines.delay
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.interaction.DragInteraction
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.VolumeDown
import androidx.compose.material.icons.automirrored.rounded.VolumeUp
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Bluetooth
import androidx.compose.material.icons.rounded.Cast
import androidx.compose.material.icons.rounded.Tv
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.DragHandle
import androidx.compose.material.icons.rounded.FastForward
import androidx.compose.material.icons.rounded.FastRewind
import androidx.compose.material.icons.rounded.GraphicEq
import androidx.compose.material.icons.rounded.Headphones
import androidx.compose.material.icons.rounded.MoreHoriz
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.Translate
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.Videocam
import androidx.compose.material.icons.rounded.PlayArrow
import com.velthy.client.playback.AudioDeviceHelper
import com.velthy.client.playback.SleepTimer
import com.velthy.client.playback.rememberActiveAudioDevice
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import com.velthy.client.ui.theme.rememberArtworkPalette
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.MutableLongState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.withFrameMillis
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.State
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import kotlinx.coroutines.flow.first
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.drawscope.ContentDrawScope
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.layout
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.lerp
import androidx.compose.ui.unit.Velocity
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import androidx.media3.common.Player
import coil3.compose.AsyncImage
import coil3.compose.AsyncImagePainter
import coil3.request.ImageRequest
import com.velthy.client.ui.components.MarqueeText
import com.velthy.client.ui.components.thumbnailBorder
import com.velthy.client.ui.icons.VelthyIcons
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.velthy.client.data.NerdStats
import com.velthy.client.data.settings.TrackAnalysisState
import com.velthy.client.data.canvas.CanvasArtwork
import com.velthy.client.data.canvas.CanvasRepository
import com.velthy.client.data.lyrics.CharGrowth
import com.velthy.client.data.lyrics.Genius
import com.velthy.client.data.lyrics.GrowingWord
import com.velthy.client.data.lyrics.LyricAlignment
import com.velthy.client.data.lyrics.LyricLine
import com.velthy.client.data.lyrics.LyricsSource
import com.velthy.client.data.listentogether.ListenTogether
import com.velthy.client.data.lyrics.LyricsTranslation
import com.velthy.client.data.lyrics.translationLanguageName
import com.velthy.client.data.settings.AppSettings
import com.velthy.client.data.settings.AudioQuality
import com.velthy.client.data.model.LikeStatus
import com.velthy.client.data.model.Song
import com.velthy.client.data.model.PlaybackSourceType
import com.velthy.client.data.model.artworkAt
import com.velthy.client.playback.BACK_RESTARTS_AFTER_MS
import com.velthy.client.playback.autoplaySectionStart
import com.velthy.client.ui.rememberIsForeground
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import java.util.concurrent.TimeUnit
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.random.Random

/** Collapsed-header geometry, shared by the layout and its animation. */
/** Comfortably over the sleeve's drawn size on a phone, without wasting bytes. */
private const val ART_PX = 1200

/**
 * How long a canvas lookup waits for the track's album name before giving up
 * on it. Long enough to cover the album lookup on a normal connection, short
 * enough not to be noticed on a track that has no album to find.
 */
private const val ALBUM_SETTLE_MS = 700L

/**
 * How close the player's reported position has to get to a released scrub
 * handle before the handle stops being drawn where it was dropped. Wide enough
 * to swallow a coarse progress tick, tight enough that the handle doesn't hand
 * over while it is still visibly wrong.
 */
private const val SEEK_SETTLE_TOLERANCE_MS = 1_500L

/**
 * How long that handle is held at the drop point regardless. A backstop, not a
 * schedule: a seek normally settles in a tick or two, and this only decides how
 * long a seek that never settles can freeze the bar for. Generous enough that a
 * slow buffer still hands over smoothly rather than snapping back.
 */
private const val SEEK_SETTLE_TIMEOUT_MS = 4_000L

private val THUMB_SIZE = 54.dp
private val HEADER_HEIGHT = 60.dp
private val ART_TITLE_GAP = 20.dp
/** Only drags starting in this top strip reach the sheet and close the player. */
private val DISMISS_STRIP_HEIGHT = 44.dp
/** The breathing room above the sleeve, needed twice: once to apply, once to measure past. */
private val ART_BOX_TOP_PAD = 14.dp
/**
 * Share of the motion-artwork banner's height given over to its dissolve.
 *
 * Generous on purpose: the banner has no card edge to stop at, so anything
 * short enough to still be reading as artwork where it ends reads as a picture
 * that was cut off rather than one that ran out.
 */
private const val HERO_FADE_FRACTION = 0.42f
/**
 * How often a frame is read back off a playing clip to re-tint the backdrop
 * behind it — see [CanvasArtworkPlayer]'s `refreshFrameEveryMs`.
 *
 * A clip pans or cuts as it loops, so its dominant colours move; a backdrop
 * that only read the opening frame would drift out of step with the picture on
 * top of it within a bar or two. Often enough to follow that, and affordable
 * because the read is a small one rather than full-bleed.
 */
private const val MESH_REFRESH_MS = 500L

/**
 * Where the lyrics panel is with respect to a translation of the track.
 *
 * [Idle] is "not asked for yet" — the panel is showing the original words. A
 * [Ready] set is kept around rather than re-requested so toggling back and forth
 * between the original and the translation is instant, and [SameLanguage] is
 * its own state rather than a failure: the lyrics are already in the language
 * asked for, which is a different thing to say than "could not translate".
 */
private sealed interface LyricsTranslationUiState {
    data object Idle : LyricsTranslationUiState
    data object Loading : LyricsTranslationUiState
    data class Ready(val lines: List<LyricLine>) : LyricsTranslationUiState
    data object SameLanguage : LyricsTranslationUiState
}

/** The player's side margin. Scrollable panels reach back across it. */
private val PLAYER_GUTTER = 30.dp

/** The height of a control in the player's bottom row, circle or capsule segment alike. */
private val BOTTOM_ACTION_SIZE = 44.dp

/** How wide one segment of a capsule comes out. */
private val PILL_SEGMENT_WIDTH = 54.dp

/** How wide a capsule of [segments] comes out, dividers included. */
private fun pillWidth(segments: Int): Dp =
    PILL_SEGMENT_WIDTH * segments + 1.dp * (segments - 1)

/**
 * Optical sizes, not equal ones.
 *
 * Headphones is a tall, narrow glyph and Person a taller, narrower one, so
 * drawn at the same nominal size the second reads as the bigger of the two.
 * These are the numbers at which they look like a matched pair.
 */
private val PILL_HEADPHONES_SIZE = 23.dp
private val PILL_PARTY_SIZE = 22.dp
/**
 * How wide the player's content is ever allowed to get. A sleeve and a volume
 * slider stretched right across a tablet aren't a bigger player, just a coarser
 * one; past this the column stops growing and centres itself instead. Phones
 * are narrower than this, so for them it does nothing.
 */
private val PLAYER_MAX_WIDTH = 560.dp

/**
 * Whether this screen is narrow enough for the player to run artwork edge to
 * edge on it — the gate on both the motion-artwork banner and
 * [AppSettings.fullBleedArtwork]. Public so the settings sheet can leave the
 * switch out entirely where it would do nothing.
 */
@Composable
fun fullBleedArtworkAvailable(): Boolean =
    LocalConfiguration.current.screenWidthDp.dp <= PLAYER_MAX_WIDTH + PLAYER_GUTTER * 2

/** Share of a lyric line's own length spent fading out, and its bounds. */
private const val LYRIC_FADE_FRACTION = 0.28f
private const val LYRIC_FADE_MIN_MS = 160f
private const val LYRIC_FADE_MAX_MS = 700f

/**
 * How far back the part of the playing line that hasn't been sung yet is held.
 *
 * The strip above the scrubber gets less of a gap than the full panel: it is
 * one line of small type with nothing around it to compare against, and taking
 * it as far down as the panel does left the words ahead of the highlight hard
 * to read at a glance.
 */
private const val UNSUNG_ALPHA = 0.45f
private const val UNSUNG_ALPHA_STRIP = 0.55f

/**
 * How a lyric line fades and blurs by its distance from the one being sung.
 *
 * A table rather than a gradient, and symmetric either side of the playing line:
 * the two rows around it stay comfortably readable — so a listener can follow
 * back over what was just sung as well as ahead to what is coming — and
 * everything past that settles on the same floor instead of continuing to fade
 * to nothing. A formula that kept scaling with distance made a long song's
 * early lines vanish entirely, which reads as the panel losing text rather than
 * as depth.
 */
private val LINE_FALLOFF_ALPHA = floatArrayOf(1f, 0.8f, 0.7f, 0.58f, 0.46f)
private val LINE_FALLOFF_BLUR = arrayOf(0.dp, 1.dp, 1.dp, 1.7.dp, 2.4.dp)

/**
 * What every line flattens to while a finger is reading the panel by hand.
 *
 * All one brightness, because browsing is not following along — there is no
 * line being pointed at, and keeping the gradient would have the panel claiming
 * to know where the song is while somebody scrolls away from it.
 */
private const val BROWSING_ALPHA = 0.8f

/** How an interruption is drawn: three dots counting the break out. */
private val GAP_ROW_HEIGHT = 40.dp
private val GAP_ROW_SPACING = 16.dp
private val GAP_DOT_SIZE = 13.dp
private val GAP_DOT_GAP = 5.dp
private const val GAP_DOTS = 3
private const val GAP_DOT_REST = 0.25f
private const val GAP_REST_SCALE = 0.76f

/** The curve and duration every lyric transition settles on. */
private val LYRIC_EASING = CubicBezierEasing(0.41f, 0f, 0.12f, 0.99f)
private const val LYRIC_SETTLE_MS = 400

/**
 * The bloom behind the line being sung, at its very strongest.
 *
 * Kept well under half strength: the halo is drawn from the same white as the
 * text, so at full alpha it stops reading as light and starts reading as a
 * second, badly printed copy of the words. What is actually drawn is this
 * scaled by how long the word is being held, so only a properly carried note
 * ever sees the whole of it.
 */
private const val GLOW_ALPHA = 0.62f
private val GLOW_RADIUS = 9.dp

/**
 * How far the lines around the one being sung sit back, and how far a row dips
 * under a finger.
 *
 * The move is small on purpose. The playing line comes *forward* to meet the
 * reader rather than swelling past its neighbours, so a handover does not shove
 * the type around — and a half of one percent is enough for the eye to read as
 * depth without the list looking like it is breathing.
 */
private const val INACTIVE_LYRIC_SCALE = 0.98f
private const val PRESSED_LYRIC_SCALE = 0.96f

/**
 * Room reserved inside each copy of a line for the halo to spread into.
 *
 * A blur is computed on its layer's own bitmap, so a halo with nowhere to go
 * inside those bounds is a halo with a hard edge — which is what cropped the
 * bloom to the line's box. Every copy carries the same inset so they still lay
 * out identically, and the list gives the width back by taking it off its own
 * padding and row spacing.
 */
private val GLOW_ROOM = 10.dp

/** Stands in for an instrumental stretch on the single-line strip. */
private const val INSTRUMENTAL_MARK = "Instrumental"

/**
 * Shown on the strip during the intro, before the first sung line — one picked
 * at random per track, so the wait for the vocals has some character to it.
 */
private val INTRO_LINES = listOf(
    "Beat's landing",
    "Song's starting",
    "Intro's cooking",
    "Warming up",
    "Here we go",
    "Setting the mood",
    "Drums are in",
    "Bass first, words later",
    "Turn it up",
    "Vibe check",
    "Wait for it",
    "Feel that build",
    "Let it ride",
    "Just the groove for now",
    "Speakers breathing",
    "Rolling in",
    "Hold tight",
    "Riff o'clock",
    "Strings first",
    "Hook's on the way",
    "Eyes closed",
    "Loading the vibe",
    "Almost words",
    "Pure heat, no words",
    "Tuning in",
    "Buckle up",
    "Let it breathe",
    "That opening though",
    "Bass is talking",
    "Lyrics loading",
    "Give it a sec",
    "Building something",
    "Cue the vocals",
    "Slow burn",
    "First notes in",
    "Nod along",
    "Groove's on deck",
    "Melody first",
    "Ease into it",
    "Big things coming",
    "Stage is set",
    "The calm before",
    "Sit with it",
    "Any second now",
    "Volume up, phone down",
    "Drums doing the talking",
    "Locked in",
    "Something's brewing",
    "Finding its feet",
    "Deep breath",
)

/**
 * Shown on the strip while a lyrics lookup is still in flight — one picked
 * at random per track, in the same spirit as [INTRO_LINES].
 */
private val LYRICS_LOADING_LINES = listOf(
    "Getting lyrics",
    "Chasing the words",
    "Digging up the lyrics",
    "Words incoming",
    "On the hunt for lyrics",
    "Fetching the verses",
    "Tracking down the words",
    "Lyrics loading",
    "Reading between the lines",
    "Scanning for lyrics",
    "Words on the way",
    "Looking this one up",
    "Checking the lyric sheet",
    "Pulling up the words",
    "Searching the songbook",
    "Lining up the lyrics",
    "One sec, finding the words",
    "Combing through for lyrics",
    "Lyrics inbound",
    "Sourcing the verses",
    "Cross-checking the words",
    "Rounding up the lyrics",
    "Text hunt in progress",
    "Syncing up the words",
    "Peeking at the lyric sheet",
    "Almost got the words",
    "Fishing for lyrics",
    "Grabbing the transcript",
    "Lyrics, one moment",
    "Tuning in the words",
    "Locating the verses",
    "Words are en route",
    "Checking the archives",
    "Piecing the lyrics together",
    "Loading up the words",
    "Lyric search underway",
    "Finding the right words",
    "Tracking the lyric sheet",
    "Verses incoming",
    "Getting the words lined up",
    "Hang tight, fetching lyrics",
    "Looking for the hook",
    "Words are loading",
    "Lyrics on their way",
    "Checking what's sung here",
    "Reading the room for lyrics",
    "Lyric lookup in progress",
    "Bringing up the words",
    "Just a sec, finding words",
    "Lyrics coming together",
)

private const val LYRICS_UNAVAILABLE_HOLD_MS = 5_000L
private const val LYRICS_UNAVAILABLE_FADE_MS = 900
// How long the transport block stays hidden once a queue/lyrics scroll rests,
// so a short pause mid-browse doesn't flash the controls in and out.
private const val PANEL_REST_MS = 900L

private val BACKING_FONT_SIZE = 23.sp
private val BACKING_LINE_HEIGHT = 29.sp
private const val BACKING_ALPHA = 0.72f

/**
 * How far the sweep's leading edge fades out instead of ending on a cut.
 *
 * A hard boundary is legible as a boundary: the eye reads a bar travelling
 * across the words rather than the words themselves lighting up as they are
 * sung. Feathering it over roughly a character and a half is what turns the
 * cut back into a wavefront.
 */
private val WIPE_FEATHER = 30.dp

/**
 * How far the word being sung lifts off the line.
 *
 * Two pixels, and it has to be about two: enough that the eye catches the
 * words moving under the sweep, little enough that nothing appears to come
 * loose from the line it belongs to.
 */
private val WORD_RISE = 2.dp

/**
 * How much further up a row is opened when it holds a word being animated
 * letter by letter, in multiples of [WORD_RISE].
 *
 * A letter that swells has to be given the room above the line it grew out of
 * or the top of it is shaved off by the band it is drawn in. Covers the lift
 * and the swell together, which is why it is well over the one rise an
 * ordinary word needs.
 */
private const val GROW_HEADROOM = 3f

/**
 * The lane kept clear on the far side of a duet line.
 *
 * Only ever applied to a song that actually has two voices laid out. Without
 * it a long right-hand line reaches all the way back across the panel and the
 * split stops reading as a split at all; with it, each voice keeps its own
 * column even when only one of them is singing.
 */
private val DUET_LANE = 44.dp

/** How long the panel takes to settle on a new line, and how far ahead it starts. */
private const val SCROLL_LEAD_MIN_MS = 350L
private const val SCROLL_LEAD_MAX_MS = 500L

/**
 * How the rows fan out as the panel moves between lines.
 *
 * They do not travel as a block. Each row after the one being scrolled to sets
 * off slightly later than the row before it, up to a few rows back, so the
 * spacing opens as the panel leaves and closes as it arrives. A block of text
 * sliding rigidly is a list being scrolled; the same lines arriving one behind
 * another is the panel handing over.
 *
 * Deliberately under half of what the renderer this came from uses. Its lines
 * carry the whole scroll themselves, so a long delay only means arriving late;
 * here the list has already moved underneath them, and the same delay reads as
 * the rows being dragged rather than following.
 */
private const val STAGGER_STEPS = 3
private const val STAGGER_FRACTION = 0.06f

/** One handover: how far the panel is going, and how long it is taking. */
private class ScrollRun(val id: Int, val delta: Float, val durationMs: Int) {
    /** The last row to arrive does so this long after the panel sets off. */
    val spanMs: Float get() = durationMs * (1f + STAGGER_FRACTION * STAGGER_STEPS)
}

/**
 * How long before a line lands the panel starts moving to it — and how long
 * the move then takes, which is the same number.
 *
 * It is the run-up: the silence between the last word of the line being sung
 * and the first of the next. Bounded either side, because that silence is a
 * held breath in one song and half a verse in another, and neither the snap
 * nor the drift is what you want to be reading against.
 */
private fun scrollLead(lines: List<LyricLine>, positionMs: Long): Long {
    val current = lines.indexOfLast { it.timeMs <= positionMs }
    // Before the first line's own timestamp there is no current line to
    // measure a run-up from. [indexOfLast] answers -1 there, and the guard
    // below does not catch it: `current + 1` is 0, which is a perfectly real
    // line, so the elvis never fires and `lines[current]` indexes at -1.
    //
    // Only reachable while the playhead is genuinely before the first lyric —
    // a track paused at 0:00 whose words start a few seconds in, which is
    // every track that opens on an intro.
    if (current < 0) return SCROLL_LEAD_MIN_MS
    val next = lines.getOrNull(current + 1) ?: return SCROLL_LEAD_MIN_MS
    val gap = next.timeMs - lines[current].endMs
    return gap.coerceIn(SCROLL_LEAD_MIN_MS, SCROLL_LEAD_MAX_MS)
}

/**
 * Stands in for the lyrics while the lookup is still out.
 *
 * Without it the panel had one empty state doing two jobs: a lookup that had
 * come back with nothing and a lookup that had not come back yet both said "No
 * lyrics for this track", so every track was declared to have none for as long
 * as it took to find out that it did.
 */
private val SKELETON_BLOCKS = listOf(
    floatArrayOf(0.97f, 0.54f),
    floatArrayOf(0.92f, 0.99f, 0.41f),
    floatArrayOf(0.68f),
    floatArrayOf(0.95f, 0.73f),
    floatArrayOf(0.89f, 0.96f, 0.37f),
)

/**
 * Set to the panel's own metrics: a bar stands the cap height of the 34sp the
 * lines are drawn in, rows of one line sit a line-height apart, and lines are a
 * row's own padding further apart again than that.
 */
private val SKELETON_BAR = 26.dp
private val SKELETON_LEADING = 15.dp
private val SKELETON_BLOCK_GAP = 35.dp
private const val SKELETON_PERIOD_MS = 1_400

/** Every unfinished vocal kept visible; see [activeLyricRows]. */
private fun activeLyricRows(lines: List<LyricLine>, positionMs: Long): List<Int> {
    val latest = lines.indexOfLast { it.timeMs <= positionMs }
    if (latest < 0) return emptyList()
    return (0..latest).filter { index ->
        val line = lines[index]
        index == latest || (!line.isGap &&
            (line.hasKnownEnd || line.background?.hasKnownEnd == true) &&
            line.timeMs <= positionMs && positionMs < line.endMs)
    }
}

/**
 * How visible the player's own sleeve/banner should be while the host's cover
 * morph is running. Reads the morph's [State] inside the artwork layers' draw
 * lambdas, so it updates every frame without recomposing the whole screen.
 */
private fun artRevealOf(morph: State<Float>?): Float =
    coverArtReveal(morph?.value ?: 1f)

/**
 * The finger's vertical speed, converted into the units the morph moves in.
 *
 * Pixels per second is not a number the host can use: what it has is a progress
 * value, and how many pixels make a whole point of that progress is
 * [travelPx] — the same distance the drag itself is measured against, so a
 * velocity of one means "the finger was covering a full open or close every
 * second". Positive is towards open, because progress grows as the finger rises.
 *
 * Clamped, because a flick off the edge of a screen can report several thousand
 * pixels a second and a spring handed that would overshoot into a bounce nobody
 * asked for. Four progress-units a second is already far past "this was
 * deliberate".
 */
private fun VelocityTracker.progressVelocity(travelPx: Float): Float {
    if (travelPx <= 0f) return 0f
    val raw = calculateVelocity().y / travelPx
    return (-raw).coerceIn(-MAX_PULL_VELOCITY, MAX_PULL_VELOCITY)
}

/** Ceiling on how much of a snap the finger's own speed is allowed to decide. */
private const val MAX_PULL_VELOCITY = 4f

/**
 * Apple Music's Now Playing, closely: artwork that shrinks when paused, a
 * hairline scrubber with elapsed / remaining either side, oversized transport
 * glyphs, a volume capsule flanked by speaker icons, and lyrics / AirPlay /
 * queue along the bottom.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NowPlayingScreen(
    song: Song,
    isPlaying: Boolean,
    isLoading: Boolean,
    positionMs: Long,
    durationMs: Long,
    queue: List<Song>,
    queueIndex: Int,
    hasPrevious: Boolean,
    hasNext: Boolean,
    repeatMode: Int,
    shuffleEnabled: Boolean,
    autoplayEnabled: Boolean,
    signedIn: Boolean,
    likeStatus: LikeStatus,
    onToggleLike: () -> Unit,
    onPlayPause: () -> Unit,
    onNext: () -> Unit,
    onPrevious: () -> Unit,
    onSeek: (Long) -> Unit,
    /**
     * Seek to a fraction of the track, for the scrubber.
     *
     * Separate from [onSeek] because the scrubber is the one caller that knows
     * *where along the bar* it wants to go rather than a time. Converting that
     * here would use this screen's cached duration, which lags a track change by
     * however long the session takes to report the new one — long enough to drop
     * the handle on a bar still scaled to the previous song and seek to the
     * wrong fraction of the current one. The conversion belongs wherever the
     * freshest duration is.
     */
    onSeekFraction: (Float) -> Unit,
    onToggleShuffle: () -> Unit,
    onCycleRepeat: () -> Unit,
    onToggleAutoplay: () -> Unit,
    onJumpTo: (Int) -> Unit,
    onRemoveFromQueue: (Int) -> Unit,
    onMoveInQueue: (Int, Int) -> Unit,
    onClearQueue: () -> Unit,
    onOpenMenu: () -> Unit,
    onOpenAlbum: (String) -> Unit,
    onOpenArtist: (String) -> Unit,
    lyrics: List<LyricLine>?,
    lyricsSource: LyricsSource?,
    lyricsUnavailable: Boolean,
    /** Opens the Listen Together screen, from the right half of the output capsule. */
    onOpenParty: () -> Unit = {},
    /**
     * Where tapping the origin caption goes: back to the album, playlist or
     * artist page the queue was started from, or the tab it came in on. Only
     * called for an origin this app can resolve — see [PlayingFromCaption].
     */
    onOpenPlaybackSource: () -> Unit = {},
    /**
     * Swaps a music video for the catalogue audio version, or back.
     *
     * The button only appears while one of those two is true — a video upload,
     * or a catalogue track already swapped *away* from its video — because for
     * every other track there is no second version to offer. Null hides it
     * outright, which is what a track with no video anywhere is.
     */
    onToggleAudioVersion: (() -> Unit)? = null,
    /** True while this track is the catalogue audio version of a video upload. */
    isAudioVersion: Boolean = false,
    /** True while that swap is in flight, so the button can show it. */
    audioVersionSwitching: Boolean = false,
    /** Fired as the player is pulled down from the top strip, with the current
     *  expanded fraction (1 = fully open, 0 = collapsed). */
    onPull: (Float) -> Unit = {},
    /**
     * Fired when the pull gesture ends, carrying how fast the finger was moving
     * in progress-per-second — positive towards open, negative towards closed.
     *
     * The host decides whether to snap, and this is the half of the answer a
     * position cannot give it: a short fast flick and a long slow drag that end
     * at the same place are different intentions, and a gesture that ignores
     * the difference feels like it is arguing with the hand.
     */
    onPullEnd: (Float) -> Unit = {},
    /**
     * The open/close progress (1 = fully open) driving the mini → full cover
     * morph. While < 1 the player's own artwork stays hidden behind the moving
     * cover drawn by the host (see [coverArtReveal]); the mesh, credits and
     * controls fade in on their own gentler curve.
     */
    morph: State<Float>? = null,
    /** Reports where this player's artwork will sit once fully open, in window
     *  pixels, so the host can aim the morph's travelling cover at it. */
    onArtTargetChanged: ((CoverTarget?) -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val density = LocalDensity.current
    val configuration = LocalConfiguration.current
    val isCompactScreen = configuration.screenHeightDp < 740
    var sleeveRootRect by remember { mutableStateOf<Rect?>(null) }

    val syncedLyricsEnabled by AppSettings.syncedLyrics.collectAsStateWithLifecycle()
    val hideVolumeBar by AppSettings.hideVolumeBar.collectAsStateWithLifecycle()
    // Read here rather than only inside the panel: the offset control now lives
    // in the title row, which is composed whether or not the lyrics panel is.
    val lyricsOffsetMs by AppSettings.lyricsOffsetMs.collectAsStateWithLifecycle()

    // Animated cover art: the looping video some labels publish alongside a
    // release, laid over the sleeve. A miss is the normal answer — see
    // CanvasRepository, which is also where the "is this actually the right
    // track" check lives.
    val canvasEnabled by AppSettings.animatedCanvas.collectAsStateWithLifecycle()
    val canvasOverCellular by AppSettings.canvasOverCellular.collectAsStateWithLifecycle()
    val meteredConnection by AppSettings.meteredConnection.collectAsStateWithLifecycle()
    val canvasAllowedNow = canvasEnabled && (meteredConnection != true || canvasOverCellular)
    var canvas by remember(song.videoId) { mutableStateOf<CanvasArtwork?>(null) }
    // Whether the clip actually has a frame on screen right now, and one of
    // them — used to blow the sleeve out to the full-bleed hero treatment and
    // to re-tint the backdrop off the clip's own colours rather than the
    // still sleeve's.
    var canvasRendered by remember(song.videoId) { mutableStateOf(false) }
    var canvasFrame by remember(song.videoId) { mutableStateOf<Bitmap?>(null) }
    // How far along the clip's fade is, reported by CanvasArtworkPlayer
    // itself. Read from a draw scope rather than in composition: it moves every
    // frame of the fade, and the still art it governs is an AsyncImage whose
    // request is rebuilt on each pass and so would not be skipped.
    val canvasCover = remember(song.videoId) { mutableFloatStateOf(0f) }
    // The one thing about it worth recomposing for: whether the clip is opaque
    // enough that the still frame under it can go entirely. Derived, so this
    // flips twice across a fade instead of once per frame of it.
    val stillCovered by remember(song.videoId) {
        derivedStateOf { canvasCover.floatValue > 0.999f }
    }
    // v1.5's backdrop, kept behind a switch — see [AppSettings.legacyMeshGradient].
    val legacyMesh by AppSettings.legacyMeshGradient.collectAsStateWithLifecycle()
    // The two backdrops answer different questions and are never both on screen,
    // so whichever is not showing is pure cost: the artwork mesh does a pixel
    // readback of its own on every track change, and the legacy path pays
    // [rememberArtworkColors] instead.
    val meshColors = if (legacyMesh) rememberArtworkColors(song.thumbnailUrl, canvasFrame) else null
    val artMesh = if (legacyMesh) null else rememberArtworkMesh(song.thumbnailUrl, canvasFrame, ART_PX)
    // Asked of every clip, Spotify's Canvas and every other source alike — see
    // CanvasArtworkPlayer's refreshFrameEveryMs. A clip's own colours move as it
    // plays regardless of who published it, and the backdrop should follow.
    //
    // Often enough that the backdrop moves with the clip rather than catching up
    // with it every few seconds. What keeps that affordable is the size of each
    // read, not the number of them: the frame comes back at `frameCapturePx`
    // rather than full-bleed, and is averaged on a stride off the main thread.
    val meshRefreshMs = MESH_REFRESH_MS
    LaunchedEffect(song.videoId, song.albumName, canvasAllowedNow) {
        if (!canvasAllowedNow) {
            canvas = null
            return@LaunchedEffect
        }
        // Anything already settled for this track paints immediately: a
        // reopened player, or a track coming round again in the queue.
        canvas = CanvasRepository.cached(song) ?: canvas

        // The album name is looked up separately and lands a moment after the
        // player opens, and it is the field that makes the catalogue searches
        // match. Give it that moment: if it arrives, this effect restarts and
        // all that was spent waiting is the wait. If it never does — a track
        // with no album, or a lookup that failed — the search still goes out,
        // just a beat later, which is imperceptible for decoration.
        if (canvas == null && song.albumName == null) delay(ALBUM_SETTLE_MS)
        // Keep what an earlier pass found if this one comes back empty, rather
        // than pulling a playing clip out from under itself.
        canvas = CanvasRepository.canvasFor(song) ?: canvas
    }

    val haptics = rememberHaptics()
    var scrubbing by remember { mutableStateOf(false) }
    var scrubValue by remember { mutableFloatStateOf(0f) }
    // The queue lives inside the player, Apple-style, rather than in a sheet.
    var queueOpen by remember { mutableStateOf(false) }
    var lyricsOpen by remember { mutableStateOf(false) }
    var showSleepTimerSheet by remember { mutableStateOf(false) }
    var showAudioOutputSheet by remember { mutableStateOf(false) }
    var showLyricsOffset by remember { mutableStateOf(false) }
    // Apple-style: while the queue or lyrics list is being scrolled the whole
    // transport block ducks out of the way so the content gets the screen,
    // then slides back on its own once the list rests. Raised by the lists
    // themselves (only a real finger-drag counts), never by their auto-scroll.
    var panelScrollHidden by remember { mutableStateOf(false) }
    LaunchedEffect(queueOpen, lyricsOpen) {
        // Never leave the controls stranded behind artwork: closing either
        // panel means the content no longer needs the borrowed height.
        if (!queueOpen && !lyricsOpen) panelScrollHidden = false
    }
    val activeDevice by rememberActiveAudioDevice()
    val palette = rememberArtworkPalette(song.thumbnailUrl)
    val sleepDeadline by SleepTimer.deadline.collectAsStateWithLifecycle()
    // Only ticks while a timer is actually armed. The countdown is read by the
    // badge and the sheet, both of which are showing a number that changes once
    // a second — but with no deadline there is no number to change, and the loop
    // used to run anyway: one wakeup per second, forever, on a screen somebody
    // may leave open all night.
    val sleepRemaining by produceState<Long?>(
        initialValue = SleepTimer.remainingMs(),
        sleepDeadline,
    ) {
        val deadline = sleepDeadline
        if (deadline == null) {
            value = null
            return@produceState
        }
        while (true) {
            value = SleepTimer.remainingMs()
            delay(1000)
        }
    }
    LaunchedEffect(song.videoId) { lyricsOpen = false }

    // ---- Lyrics translation -------------------------------------------
    //
    // The language the translate button renders into. Settings wins where it
    // has been set; blank means follow the app, which is only narrowed to a
    // base language on that path — a code chosen in Settings is already
    // exactly what the endpoint wants, and narrowing it would throw away the
    // script half of zh-TW.
    val preferredTranslation by AppSettings.translationLanguage.collectAsStateWithLifecycle()
    val appLanguageTag = configuration.locales.get(0)?.toLanguageTag().orEmpty()
    val translationLanguage = remember(appLanguageTag, preferredTranslation) {
        preferredTranslation.ifBlank {
            java.util.Locale.forLanguageTag(appLanguageTag.ifBlank { "en" }).language.ifBlank { "en" }
        }
    }
    val translationLanguageLabel = remember(appLanguageTag, translationLanguage) {
        translationLanguageName(
            translationLanguage,
            java.util.Locale.forLanguageTag(appLanguageTag.ifBlank { "en" }),
        )
    }
    var translationState by remember(song.videoId, translationLanguage, lyrics) {
        mutableStateOf<LyricsTranslationUiState>(LyricsTranslationUiState.Idle)
    }
    var showingTranslation by remember(song.videoId, translationLanguage, lyrics) {
        mutableStateOf(false)
    }
    var translationJob by remember(song.videoId, translationLanguage, lyrics) {
        mutableStateOf<Job?>(null)
    }
    DisposableEffect(song.videoId, translationLanguage, lyrics) {
        onDispose { translationJob?.cancel() }
    }
    // Bumped every time the panel swaps between the original words and their
    // translation. Read as a *transition trigger* by [LyricsTranslationMotion],
    // which is how the particle pass knows to replay: the lines are replaced
    // wholesale, and a value read off them would fire on every recomposition.
    val reduceAnimation by AppSettings.reduceAnimation.collectAsStateWithLifecycle()
    var translationTransition by remember(song.videoId) { mutableIntStateOf(0) }
    LaunchedEffect(showingTranslation, translationState) {
        if (translationState is LyricsTranslationUiState.Ready) translationTransition++
    }
    // What the panel actually draws: the translation when one is being shown,
    // the original words otherwise.
    val displayedLyrics = if (showingTranslation) {
        (translationState as? LyricsTranslationUiState.Ready)?.lines ?: lyrics.orEmpty()
    } else {
        lyrics.orEmpty()
    }
    // Whether this device is in a party, for the capsule's right half. Collected
    // here rather than deeper so the lit half lights for the whole player.
    val party by ListenTogether.state.collectAsStateWithLifecycle()
    // A lookup still in flight, as opposed to one that has come back empty.
    // The two used to share "No lyrics for this track", so every track was
    // declared to have none for as long as it took to find out that it did.
    // [lyricsUnavailable] is the "came back empty" flag; empty without it is
    // still out.
    val lyricsLooking = !lyricsUnavailable && lyrics.isNullOrEmpty()

    val translationScope = rememberCoroutineScope()
    val toggleTranslation: () -> Unit = toggleTranslation@{
        when (val state = translationState) {
            is LyricsTranslationUiState.Ready -> {
                showingTranslation = !showingTranslation
                haptics.play(Haptic.Select)
            }
            LyricsTranslationUiState.Loading -> Unit
            LyricsTranslationUiState.SameLanguage -> {
                haptics.play(Haptic.Tap)
                Toast.makeText(
                    context,
                    "Lyrics are already in $translationLanguageLabel",
                    Toast.LENGTH_SHORT,
                ).show()
            }
            LyricsTranslationUiState.Idle -> {
                val source = lyrics.orEmpty()
                if (source.isEmpty()) return@toggleTranslation
                haptics.play(Haptic.Tap)
                translationState = LyricsTranslationUiState.Loading
                translationJob?.cancel()
                translationJob = translationScope.launch {
                    when (
                        val result = LyricsTranslation.translate(
                            context = context.applicationContext,
                            trackId = song.videoId,
                            lines = source,
                            targetLanguageTag = translationLanguage,
                        )
                    ) {
                        is LyricsTranslation.Result.Translated -> {
                            translationState = LyricsTranslationUiState.Ready(result.lines)
                            showingTranslation = true
                            haptics.play(Haptic.ToggleOn)
                        }
                        is LyricsTranslation.Result.SameLanguage -> {
                            translationState = LyricsTranslationUiState.SameLanguage
                            Toast.makeText(
                                context,
                                "Lyrics are already in $translationLanguageLabel",
                                Toast.LENGTH_SHORT,
                            ).show()
                        }
                        LyricsTranslation.Result.Unavailable -> {
                            translationState = LyricsTranslationUiState.Idle
                            Toast.makeText(
                                context,
                                "Couldn't translate these lyrics",
                                Toast.LENGTH_SHORT,
                            ).show()
                        }
                    }
                }
            }
        }
    }

    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        val view = LocalView.current
        val hasOverlay = lyricsOpen || queueOpen || showSleepTimerSheet || showAudioOutputSheet || showLyricsOffset
        DisposableEffect(view, hasOverlay) {
            val callback = if (hasOverlay) {
                OverlayBack.register(view) {
                    if (showLyricsOffset) {
                        showLyricsOffset = false
                    } else if (showSleepTimerSheet) {
                        showSleepTimerSheet = false
                    } else if (showAudioOutputSheet) {
                        showAudioOutputSheet = false
                    } else {
                        lyricsOpen = false
                        queueOpen = false
                    }
                }
            } else {
                null
            }
            onDispose { OverlayBack.unregister(view, callback) }
        }
    }

    BackHandler(enabled = lyricsOpen || queueOpen || showSleepTimerSheet || showAudioOutputSheet || showLyricsOffset) {
        if (showLyricsOffset) {
            showLyricsOffset = false
        } else if (showSleepTimerSheet) {
            showSleepTimerSheet = false
        } else if (showAudioOutputSheet) {
            showAudioOutputSheet = false
        } else {
            lyricsOpen = false
            queueOpen = false
        }
    }

    // 0 = full sleeve, 1 = queue. Everything that moves reads off this.
    val queueProgress by animateFloatAsState(
        targetValue = if (queueOpen) 1f else 0f,
        animationSpec = tween(durationMillis = 420, easing = FastOutSlowInEasing),
        label = "queueProgress",
    )

    // Horizontal fling anywhere on the player skips tracks; the artwork
    // follows the finger so the gesture has something to hold on to.
    val swipeThreshold = with(density) { 72.dp.toPx() }
    var swipeOffset by remember { mutableFloatStateOf(0f) }
    val swipeSettle by animateFloatAsState(
        targetValue = swipeOffset,
        animationSpec = spring(stiffness = Spring.StiffnessMediumLow),
        label = "swipeOffset",
    )

    // After releasing the scrubber the player needs to buffer before it
    // reports the new position. Keep showing where the user dropped it so the
    // handle doesn't snap back and then jump forward once loading finishes.
    var pendingSeek by remember { mutableStateOf<Float?>(null) }

    val fraction = if (durationMs > 0) positionMs.toFloat() / durationMs else 0f
    val shown = when {
        scrubbing -> scrubValue
        pendingSeek != null -> pendingSeek!!
        else -> fraction.coerceIn(0f, 1f)
    }

    // Released as soon as the player's own position agrees with where the handle
    // was dropped — and unconditionally a few seconds later whether it agrees or
    // not.
    //
    // The agreement test alone is not enough, because it is the only thing that
    // ever cleared the override: if the position never passes close to the
    // target — a track ending early, a decoder that dropped a frame — the
    // override held for the rest of the play and the scrubber stayed frozen.
    // The timeout below guarantees the bar returns to live tracking; the
    // agreement test is the fast path that clears it as soon as the seek lands.
    //
    // Kept across a track change so the new track can't inherit a seek from the
    // old one: if the previous song had a seek pending when the queue moved on,
    // the new song was held to that fraction until the timeout fired. A real
    // failure looks like a stuck seek bar on a track that is playing fine.
    //
    // Tolerance is absolute rather than a share of the duration: two percent is
    // a quarter-second on a jingle and twelve seconds on a long mix, and it is
    // the wall-clock gap that decides whether the handle appears to jump.
    LaunchedEffect(positionMs, durationMs, pendingSeek) {
        val target = pendingSeek ?: return@LaunchedEffect
        if (durationMs > 0 && abs(positionMs - (target * durationMs).toLong()) < SEEK_SETTLE_TOLERANCE_MS) {
            pendingSeek = null
        }
    }
    LaunchedEffect(pendingSeek) {
        if (pendingSeek == null) return@LaunchedEffect
        delay(SEEK_SETTLE_TIMEOUT_MS)
        pendingSeek = null
    }
    LaunchedEffect(song.videoId) { pendingSeek = null }

    // Signature Apple Music touch: the sleeve shrinks back while paused.
    val artScale by animateFloatAsState(
        targetValue = if (isPlaying) 1f else 0.86f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioLowBouncy,
            stiffness = Spring.StiffnessLow,
        ),
        label = "artScale",
    )

    val audioManager = remember(context) {
        context.getSystemService(AudioManager::class.java)
    }
    val maxVolume = remember(audioManager) {
        audioManager?.getStreamMaxVolume(AudioManager.STREAM_MUSIC)?.coerceAtLeast(1) ?: 15
    }
    val scope = rememberCoroutineScope()
    // Animatable rather than plain state: a hardware volume step is a jump of
    // 1/15th of the bar, which reads as a stutter unless it's tweened.
    val volume = remember {
        Animatable(
            (audioManager?.getStreamVolume(AudioManager.STREAM_MUSIC) ?: 0).toFloat() / maxVolume,
        )
    }
    var volumeDragging by remember { mutableStateOf(false) }
    var systemVolume by remember { mutableFloatStateOf(volume.value) }

    // Glide to the level the system reports, but never fight the finger — a
    // drag writes the stream, which calls straight back through here.
    LaunchedEffect(systemVolume) {
        if (!volumeDragging) {
            volume.animateTo(systemVolume, tween(durationMillis = 220, easing = FastOutSlowInEasing))
        }
    }

    // Hardware volume keys and the system panel change the stream behind our
    // back — watch Settings for changes so the bar tracks them live.
    DisposableEffect(audioManager) {
        val observer = object : ContentObserver(Handler(Looper.getMainLooper())) {
            override fun onChange(selfChange: Boolean) {
                val current = audioManager?.getStreamVolume(AudioManager.STREAM_MUSIC) ?: return
                systemVolume = current.toFloat() / maxVolume
            }
        }
        context.contentResolver.registerContentObserver(
            Settings.System.CONTENT_URI,
            true,
            observer,
        )
        onDispose { context.contentResolver.unregisterContentObserver(observer) }
    }

    // 0 = the ordinary square sleeve, 1 = the artwork as a full-bleed banner.
    // Both states collapse the header, but the banner only ever shows over a
    // settled player: opening the queue or the lyrics hands the sleeve back its
    // card first.
    // One animation for both surfaces. This used to read
    // `if (lyricsOpen) 1f else queueProgress`, which gave the queue a 420ms ease
    // and the lyrics nothing at all: opening them snapped the sleeve to a
    // thumbnail in a single frame while [heroT] — reading off this same value —
    // went on fading the banner out over the full 420. One half of the artwork
    // jumped, the other half glided after it, and the pair read as a stutter
    // rather than as either.
    val p by animateFloatAsState(
        targetValue = if (lyricsOpen || queueOpen) 1f else 0f,
        animationSpec = tween(durationMillis = 420, easing = FastOutSlowInEasing),
        label = "sleeveCollapse",
    )
    val fullBleedArt by AppSettings.fullBleedArtwork.collectAsStateWithLifecycle()
    // Full-bleed is a phone idiom. Past the width the player is willing to grow
    // to, edge to edge stops meaning "the artwork *is* the screen" and starts
    // meaning "a picture, and separately some controls" — the banner would be
    // running a foot wider than the column of controls under it. Tablets keep
    // the sleeve, and a clip plays inside it as before.
    //
    // One question for the still cover and the clip both, rather than two that
    // could disagree — and they did, twice over. Dissolving a TextureView's
    // bottom edge needs a RenderEffect, so below API 31 the clip was held in its
    // sleeve while the cover behind it went edge to edge, and the artwork
    // changed shape the moment a clip arrived. In the other direction the clip
    // ignored [fullBleedArt] entirely, so turning the setting off still left a
    // clip running the full screen. CanvasArtworkPlayer masks itself on every
    // API level now, and both layers answer to this.
    val heroMode = fullBleedArt && fullBleedArtworkAvailable()
    // Whether there's a still image to blow out — a placeholder tile is a card
    // or it is nothing, and going full-bleed with one would just tint the top
    // third of the screen.
    var artLoaded by remember(song.videoId) { mutableStateOf(false) }
    // Sticky, unlike [artLoaded]: the banner is the shape of the player rather
    // than a property of the track in it. Waiting on each new cover would
    // collapse the banner into a card and blow it back out on every skip —
    // twice the length of the whole screen's worth of movement for a change the
    // artwork itself already announces. The frame stays; the cover arrives in
    // it, fading in as Coil fades in everywhere else.
    var heroSettled by remember { mutableStateOf(false) }
    LaunchedEffect(artLoaded) { if (artLoaded) heroSettled = true }
    // The clip that gets the banner, if any. Hoisted because the still frame
    // underneath keys its handover on exactly what is mounted here: both are
    // decided in the same composition pass, so opening the queue or the lyrics —
    // which takes the clip away — brings the still frame back in the very frame
    // the clip goes, instead of a frame later with the sleeve behind it still
    // transparent and no artwork anywhere.
    val heroClip = canvas?.takeIf { heroMode && p < 0.5f }
    val heroT by animateFloatAsState(
        targetValue = if (
            heroMode && p < 0.5f && (canvasRendered || artLoaded || heroSettled)
        ) 1f else 0f,
        animationSpec = tween(durationMillis = 420, easing = FastOutSlowInEasing),
        label = "heroCanvas",
    )
    // How tall that banner is, worked out down in the layout where the sleeve's
    // own geometry is known. Zero until the first measure, which is fine: there
    // is nothing to show that early either.
    var heroHeight by remember { mutableStateOf(0.dp) }
    val statusBarTop = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()

    // Where the artwork a morph should aim at finally sits: the square sleeve,
    // which on a phone sits inside the full-bleed banner's region. Reported
    // whenever it moves so the host's travelling cover can follow it.
    val coverTarget: CoverTarget? = sleeveRootRect?.let { CoverTarget(isHero = false, rect = it) }
    LaunchedEffect(coverTarget) { onArtTargetChanged?.invoke(coverTarget) }

    Box(modifier = modifier.fillMaxSize()) {
        if (meshColors != null) {
            // Keyed on the track: the backdrop drifts when the player opens and
            // on every skip, then rests. Position ticks recompose this screen
            // twice a second and must not drag a full-screen blur along with
            // them, which is why the palette is passed as one immutable value.
            // No seam: the blobs are not anchored to anything on screen — they
            // fill the player and the artwork simply sits on top of them.
            MeshGradientBackground(palette = meshColors, trackKey = song.videoId)
        } else {
            // The sleeve's own colours, hung from where the artwork stops — so
            // the colour immediately under it is the colour it ended on, and
            // there is no join to hide.
            ArtworkMeshBackdrop(
                mesh = artMesh,
                seam = if (heroMode) heroHeight else 0.dp,
            )
        }

        // The artwork, edge to edge and running up behind the status bar,
        // dissolving into the backdrop where the sleeve's bottom edge would
        // have been. It lives out here rather than in the sleeve because that
        // is the only way to escape the player's side gutter and its status-bar
        // inset — a banner that stops short of either reads as a misplaced card
        // rather than as the artwork the screen is made of.
        if (heroHeight > 0.dp) {
            // The still sleeve first, so a clip fading in on top of it never
            // shows the backdrop through the gap between them — and only until
            // that fade has run. Both layers carry the same bottom gradient, so
            // a still frame left lit under a settled clip is not hidden by it:
            // down in the fade the clip is only part-opaque, and what shows
            // through it there is the cover art rather than the backdrop. That
            // is the artwork and the clip on screen at once.
            //
            // So it is dropped outright once the clip is opaque, rather than
            // held at alpha 0: nothing under a full-bleed clip is ever visible,
            // and a full-screen AsyncImage kept mounted for no one is a bitmap
            // and a layer the compositor still has to carry.
            //
            // Kept mounted through the handover in either direction rather than
            // dropped the moment [p] crosses the collapse threshold: the sleeve
            // behind it is still transparent at that point, so pulling the
            // banner straight out leaves a frame or two with no artwork anywhere
            // on screen before the card catches up.
            if (heroMode && !(stillCovered && heroClip != null) &&
                (p < 0.5f || heroT > 0.001f)
            ) {
                AsyncImage(
                    // Decoded at the same size the sleeve asks for, so the two
                    // share one entry in Coil's cache and one bitmap: the pair
                    // cross-fade into each other, and asking twice at two sizes
                    // would decode the same art twice and let the banner fade in
                    // before its own copy had arrived.
                    model = ImageRequest.Builder(context)
                        .data(song.artworkAt(ART_PX))
                        .size(ART_PX)
                        .build(),
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .fillMaxWidth()
                        .height(heroHeight)
                        .graphicsLayer {
                            // Hands its opacity to the clip as the clip takes
                            // over, and takes it straight back if there is no
                            // clip mounted to hand it to.
                            alpha = heroT *
                                (1f - if (heroClip != null) canvasCover.floatValue else 0f) *
                                artRevealOf(morph)
                            // The mask below erases part of what this layer
                            // drew, which it can only do in a buffer of its own.
                            compositingStrategy = CompositingStrategy.Offscreen
                        }
                        .drawWithContent {
                            drawContent()
                            drawRect(
                                brush = Brush.verticalGradient(
                                    colors = listOf(Color.Black, Color.Transparent),
                                    startY = size.height * (1f - HERO_FADE_FRACTION),
                                    endY = size.height,
                                ),
                                blendMode = BlendMode.DstIn,
                            )
                        },
                )
            }

            // Motion artwork over it, in the same frame.
            //
            // Always composed while there's a clip to play, never gated on
            // [heroT]: the clip has to be mounted and decoding *before* it can
            // report the first frame that raises heroT in the first place.
            if (heroMode) {
                heroClip?.let { clip ->
                    CanvasArtworkPlayer(
                        canvas = clip,
                        isPlaying = isPlaying,
                        onRenderedChanged = { canvasRendered = it },
                        onFrameCaptured = { canvasFrame = it },
                        onCoverChanged = { canvasCover.floatValue = it },
                        refreshFrameEveryMs = meshRefreshMs,
                        bottomFade = HERO_FADE_FRACTION,
                        modifier = Modifier
                            .align(Alignment.TopStart)
                            .fillMaxWidth()
                            .height(heroHeight)
                            .graphicsLayer { alpha = artRevealOf(morph) },
                    )
                }
            }
            // The clock, the signal bars and the drag handle are all white, and
            // the banner puts whatever the artwork happens to have up there
            // directly behind them — a bright frame or a pale sleeve leaves the
            // top of the screen unreadable. Faded in with the banner and gone
            // with it.
            if (heroT > 0.01f) {
                Box(
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .fillMaxWidth()
                        .height(statusBarTop + DISMISS_STRIP_HEIGHT)
                        .background(
                            Brush.verticalGradient(
                                listOf(
                                    Color.Black.copy(alpha = 0.38f * heroT),
                                    Color.Transparent,
                                ),
                            ),
                        ),
                )
            }
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
                .pointerInput(Unit) {
                    var total = 0f
                    detectHorizontalDragGestures(
                        onDragStart = { total = 0f },
                        onDragCancel = { swipeOffset = 0f },
                        onDragEnd = {
                            when {
                                total <= -swipeThreshold && hasNext -> {
                                    haptics.play(Haptic.SkipNext)
                                    onNext()
                                }
                                total >= swipeThreshold && hasPrevious -> {
                                    haptics.play(Haptic.SkipPrevious)
                                    onPrevious()
                                }
                            }
                            swipeOffset = 0f
                        },
                        onHorizontalDrag = { _, delta ->
                            total += delta
                            // Damped: it's a hint, not a drag-to-position.
                            swipeOffset = total * 0.35f
                        },
                    )
                },
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            // The only strip that passes drags through to the sheet, so the
            // player closes from the handle and the space around it — not from
            // a stray downward swipe on the artwork or the controls.
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(DISMISS_STRIP_HEIGHT)
                    .pointerInput(Unit) {
                        var down = 0f
                        // Use a short fixed travel distance so a quick flick of
                        // ~200 dp is enough to close the player — the old code
                        // divided by the full screen height which made it feel
                        // impossibly stiff.
                        val dismissTravelPx = with(density) { 200.dp.toPx() }
                        // How fast the finger was moving when it left, in
                        // progress-per-second. A flick that covers 40dp in 80ms
                        // and a slow drag that covers the same 40dp in 600ms are
                        // the same *position* and completely different
                        // intentions, and only the velocity tells them apart —
                        // which is why every platform's own dismiss gesture
                        // weighs it. Without it, a flick had to be dragged all
                        // the way past the halfway point before it counted, and
                        // a decisive flick that stopped short snapped back.
                        val velocity = VelocityTracker()
                        detectVerticalDragGestures(
                            onDragStart = {
                                down = 0f
                                velocity.resetTracking()
                            },
                            onVerticalDrag = { change, dragAmount ->
                                change.consume()
                                velocity.addPosition(change.uptimeMillis, change.position)
                                // Signed: pulling down grows `down` (closing),
                                // pulling back up shrinks it again (cancel).
                                down = (down + dragAmount).coerceAtLeast(0f)
                                onPull((1f - down / dismissTravelPx).coerceIn(0f, 1f))
                            },
                            // Signed so a flick *up* carries the player back
                            // open: the progress grows as the finger rises, so
                            // its own velocity is already in that direction.
                            onDragEnd = { onPullEnd(velocity.progressVelocity(dismissTravelPx)) },
                            onDragCancel = { onPullEnd(0f) },
                        )
                    },
                contentAlignment = Alignment.Center,
            ) {
                // The handle sits at the top of the strip, and the origin caption
                // takes its foot — the strip is the only place above the artwork
                // that is not the picture, which is exactly where this belongs: a
                // caption *about* the track must not be read as part of the
                // credits under it.
                Box(
                    Modifier
                        .align(Alignment.TopCenter)
                        .offset(y = 6.dp)
                        .width(38.dp)
                        .height(5.dp)
                        .clip(RoundedCornerShape(3.dp))
                        .background(Color.White.copy(alpha = 0.32f)),
                )

                // Kept in composition until the panel handover's last frame so
                // it fades rather than blinks, but stripped of its click target
                // once the lyrics or the queue owns the player.
                if (p < 0.999f) {
                    PlayingFromCaption(
                        song = song,
                        playedBy = party.playback
                            .takeIf {
                                party.inParty && it.track?.videoId == song.videoId
                            }
                            ?.let { playback ->
                                playback.startedByName?.takeIf(String::isNotBlank)
                                    ?: party.members
                                        .firstOrNull { it.memberId == playback.startedBy }
                                        ?.displayName
                                        ?.takeIf(String::isNotBlank)
                            }
                            ?.trim()
                            ?.split(Regex("\\s+"))
                            ?.firstOrNull(),
                        // Party caption opens the party; a queue source opens the
                        // queue; anything else opens the page it names.
                        onParty = onOpenParty,
                        onOpenQueue = {
                            queueOpen = true
                            lyricsOpen = false
                        },
                        onOpenSource = onOpenPlaybackSource,
                        alpha = 1f - p,
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .padding(bottom = 1.dp),
                    )
                }
            }

            Column(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    // Swallow vertical drags before the sheet can read them as
                    // "dismiss me". Children that scroll consume first, so the
                    // lists are unaffected. This sits outside the side padding
                    // on purpose: inside it, the two gutters were left as bare
                    // sheet, and a swipe that strayed into one closed the whole
                    // player instead of scrolling the lyrics or the queue.
                    .pointerInput(Unit) {
                        detectVerticalDragGestures { change, _ -> change.consume() }
                    }
                    .padding(horizontal = PLAYER_GUTTER),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
            // ---- Top and centre: artwork, then the credits ----
            // Everything that changes between the artwork and the queue lives
            // in this one weighted box, so the controls below it never move.
            // Read up here rather than down by the scrubber: the stats-for-nerds
            // line now lives inside the sleeve itself, so the art Box below
            // needs these before the seek bar does.
            val showNerdStats by AppSettings.showNerdStats.collectAsStateWithLifecycle()
            val nerdStats by NerdStats.current.collectAsStateWithLifecycle()
            // Hoisted alongside the other two rather than read where it is drawn:
            // the stats block is inside a condition that flips as the sleeve
            // collapses, and re-subscribing to a flow on every frame of that
            // collapse is a waste of a subscription.
            val smartFadeOn by AppSettings.smartFadeEnabled.collectAsStateWithLifecycle()
            val smartAnalysis by AppSettings.smartAnalysis.collectAsStateWithLifecycle()
            BoxWithConstraints(
                modifier = Modifier
                    .weight(1f)
                    .widthIn(max = PLAYER_MAX_WIDTH)
                    .fillMaxWidth()
                    .padding(top = ART_BOX_TOP_PAD, bottom = 18.dp),
            ) {
                // The sleeve is square, so it is bounded by whichever of the
                // two axes runs out first: the player's width on a phone, or —
                // on a tablet, where there is width to spare — the height left
                // over once the credits row and the gap above it have had
                // theirs. Sizing it off the width alone is what pushed the
                // credits down across the scrubber on anything but a phone.
                val fullArt = minOf(maxWidth, maxHeight - ART_TITLE_GAP - HEADER_HEIGHT)
                    .coerceAtLeast(THUMB_SIZE)
                // Artwork and the title row travel together as one block, so
                // the pair sits centred while the queue is closed.
                val groupTop = ((maxHeight - fullArt - ART_TITLE_GAP - HEADER_HEIGHT) / 2)
                    .coerceAtLeast(0.dp)
                val artSize = lerp(fullArt, THUMB_SIZE, p)
                val artTop = lerp(groupTop, 0.dp, p)
                // Expanded and height-bound, the sleeve is narrower than the
                // player and has to be centred in it; collapsed, it belongs
                // hard against the left edge with the credits beside it.
                val artStart = lerp((maxWidth - fullArt) / 2, 0.dp, p)
                val titleTop = lerp(groupTop + fullArt + ART_TITLE_GAP, 0.dp, p)
                val titleStart = lerp(0.dp, THUMB_SIZE + 12.dp, p)

                // How far down the *screen* the sleeve's bottom edge sits, which
                // is where the full-bleed banner has to stop for the credits
                // below it not to move when it appears. Everything between the
                // screen's top and this box's own top is fixed padding, so it
                // can simply be added back up rather than measured.
                val bannerBottom = statusBarTop + DISMISS_STRIP_HEIGHT + ART_BOX_TOP_PAD +
                    groupTop + fullArt + ART_TITLE_GAP / 2
                SideEffect { heroHeight = bannerBottom }

                // Empty state lives on this Box, not the AsyncImage: a
                // background *and* a painter both trying to fill the same
                // clipped shape is what read as two overlapping squares
                // whenever there was nothing to paint. One layer, one square.
                // [artLoaded] is hoisted to the screen, where the banner needs
                // it too.
                Box(
                    modifier = Modifier
                        .offset(x = artStart, y = artTop)
                        .size(artSize)
                        .graphicsLayer {
                            // The paused shrink and the swipe nudge only make
                            // sense on the full sleeve.
                            val idle = artScale + (1f - artScale) * p
                            scaleX = idle
                            scaleY = idle
                            translationX = swipeSettle * (1f - p)
                        }
                        // Collapsed, the sleeve is the way back: tapping the
                        // thumbnail puts the queue or the lyrics away again.
                        .then(
                            if (queueOpen || lyricsOpen) {
                                Modifier.clickable {
                                    queueOpen = false
                                    lyricsOpen = false
                                }
                            } else {
                                Modifier
                            },
                        )
                        .onGloballyPositioned { sleeveRootRect = it.boundsInRoot() },
                    contentAlignment = Alignment.Center,
                ) {
                    // The sleeve proper. Separated from the box around it so
                    // the banner can dissolve the card — shadow, corners, tile
                    // and all — without taking the stats line with it.
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .graphicsLayer { alpha = (1f - heroT) * artRevealOf(morph) }
                            // A drop shadow grounds a photo; on the flat
                            // placeholder tile it has nothing to sit behind, so
                            // it just reads as a second, darker square ringing
                            // the first. Only cast it once there's actually art.
                            .shadow(
                                if (artLoaded) lerp(14.dp, 6.dp, p) else 0.dp,
                                RoundedCornerShape(lerp(10.dp, 7.dp, p)),
                            )
                            .clip(RoundedCornerShape(lerp(10.dp, 7.dp, p)))
                            .background(Color.Black.copy(alpha = 0.18f)),
                        contentAlignment = Alignment.Center,
                    ) {
                        if (!artLoaded) {
                            Icon(
                                imageVector = VelthyIcons.MusicNote,
                                contentDescription = null,
                                tint = Color.White.copy(alpha = 0.35f),
                                modifier = Modifier.size(lerp(40.dp, 20.dp, p)),
                            )
                        }
                        AsyncImage(
                            // Decode at the sleeve's *expanded* size, always.
                            // Coil otherwise sizes the decode to however large
                            // this is when the request goes out — and changing
                            // that moment.
                            //
                            // Asked for at the source's own size rather than the
                            // sleeve's: it is the same request the full-bleed
                            // banner makes, and the banner is taller than the
                            // sleeve is wide. One ask, one decode, one bitmap for
                            // both — and nothing to upscale when the two swap.
                            model = ImageRequest.Builder(LocalContext.current)
                                .data(song.artworkAt(ART_PX))
                                .size(ART_PX)
                                .build(),
                            contentDescription = null,
                            // Video thumbnails are 16:9; letterboxing them inside
                            // the square sleeve looks like a broken frame.
                            contentScale = ContentScale.Crop,
                            onState = { artLoaded = it is AsyncImagePainter.State.Success },
                            modifier = Modifier.fillMaxSize(),
                        )

                        // Where the clip plays when it can't have the banner:
                        // inside the same clip as the still art, taking the
                        // sleeve's corners, shadow and paused shrink for free.
                        if (!heroMode) {
                            canvas?.takeIf { p < 0.5f }?.let { clip ->
                                CanvasArtworkPlayer(
                                    canvas = clip,
                                    isPlaying = isPlaying,
                                    onRenderedChanged = { canvasRendered = it },
                                    onFrameCaptured = { canvasFrame = it },
                                    refreshFrameEveryMs = meshRefreshMs,
                                    modifier = Modifier.fillMaxSize(),
                                )
                            }
                        }
                    }

                    // Measured stats, pinned to the sleeve's own bottom-centre
                    // rather than squeezed under the seek bar with the
                    // "Lossless" badge — the badge is a claim, this is the
                    // evidence, and the two no longer swap for each other on a
                    // tap. Fades out with the sleeve as it collapses to a
                    // thumbnail, where there's no room to read it anyway.
                    if (showNerdStats && p < 0.5f) {
                        // A plain white line reads fine over the usual dark
                        // tile, but a light stretch of an animated cover — sky,
                        // snow, a pale sleeve — washes it out entirely. The
                        // shadow costs nothing on a dark background and is what
                        // keeps it legible on a bright one.
                        val nerdStyle = MaterialTheme.typography.labelSmall.copy(
                            shadow = Shadow(
                                color = Color.Black.copy(alpha = 0.55f),
                                offset = Offset(0f, 1f),
                                blurRadius = 4f,
                            ),
                        )
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            modifier = Modifier
                                .align(Alignment.BottomCenter)
                                .padding(horizontal = 10.dp, vertical = 8.dp)
                                .graphicsLayer { alpha = (1f - p * 2f) * artRevealOf(morph) },
                        ) {
                            nerdStats?.describe()?.let { stats ->
                                Text(
                                    text = stats,
                                    style = nerdStyle,
                                    color = Color.White.copy(alpha = 0.65f),
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    textAlign = TextAlign.Center,
                                )
                            }
                            // Only when Automix is actually switched on:
                            // otherwise this would report on analysis nothing is
                            // going to use, which is noise rather than a stat.
                            if (smartFadeOn) {
                                Text(
                                    // Both sides always named, even when they
                                    // agree, so the line reads the same way every
                                    // time and the eye can find the half it wants
                                    // without re-parsing the sentence.
                                    text = "Automix · this song " +
                                        smartAnalysis.current.label() +
                                        " · next " + smartAnalysis.next.label(),
                                    style = nerdStyle,
                                    // Dimmer than the measured line above it: that
                                    // one describes the audio, this one describes
                                    // the app, and the ranking should show.
                                    color = Color.White.copy(alpha = 0.5f),
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    textAlign = TextAlign.Center,
                                )
                            }
                        }
                    }
                }

                // Video or music: the one explicit opt-in to a catalogue match,
                // offered on a video upload. Rides just under the sleeve's top
                // edge rather than straddling it — everything above the sleeve
                // is spoken for by the dismiss strip and the origin caption.
                // Hidden once the lyrics or queue owns the player: both of those
                // are about the track, and a control that reloads it would be
                // the one thing on screen able to throw away what is being read.
                if (onToggleAudioVersion != null && (song.isVideo || isAudioVersion) &&
                    !lyricsOpen && !queueOpen && p < 0.5f
                ) {
                    VideoAudioVersionButton(
                        audioVersion = isAudioVersion,
                        loading = audioVersionSwitching,
                        onClick = onToggleAudioVersion,
                        modifier = Modifier
                            .align(Alignment.TopCenter)
                            .offset(y = artTop + ART_BOX_TOP_PAD),
                    )
                }

                // Sits in the gap under the sleeve, clear of its rounded
                // corners and shadow - no box, no clip, nothing for the art
                // itself to be cropped by. Just a glyph that fades in with
                // the drag to hint which way a release would skip.
                //
                // Shown under the banner as well as under the card, and it is
                // the only feedback the drag has there: a card can slide with
                // the finger, but a full-bleed image sliding would open a strip
                // of bare backdrop down one edge of the screen. It lands where
                // the banner has all but dissolved, so it reads against the
                // backdrop rather than against the artwork.
                val swipeHintProgress = (abs(swipeSettle) / swipeThreshold)
                    .coerceIn(0f, 1f) * (1f - p)
                if (swipeHintProgress > 0.01f) {
                    val showNext = swipeSettle < 0f
                    val enabled = if (showNext) hasNext else hasPrevious
                    Icon(
                        imageVector = if (showNext) Icons.Rounded.FastForward else Icons.Rounded.FastRewind,
                        contentDescription = null,
                        tint = Color.White.copy(
                            alpha = swipeHintProgress * if (enabled) 0.85f else 0.3f,
                        ),
                        modifier = Modifier
                            .align(Alignment.TopCenter)
                            .offset(y = artTop + artSize + (ART_TITLE_GAP - 16.dp) / 2)
                            .size(16.dp),
                    )
                }

                // ---- Title + menu ----
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .offset(y = titleTop)
                        .padding(start = titleStart)
                        .height(HEADER_HEIGHT)
                        // The credits rise the last stretch into place behind
                        // the backdrop, on their own run. Sibling of the
                        // artwork, never its ancestor: a transform over the
                        // sleeve's own box would move the bounds the morph aims
                        // its cover at, and every frame of the flight would
                        // then push a new target back up to the host.
                        .graphicsLayer {
                            translationY = (1f - playerCreditsReveal(morph?.value ?: 1f)) *
                                18.dp.toPx()
                        },
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(Modifier.weight(1f)) {
                        // Shrinks as the header collapses, so the queue's
                        // heading doesn't have to compete with it.
                        val titleSize = lerp(20.sp, 16.sp, p)
                        // Crawls when it is too long for the row, like the bar's
                        // own title does — the credits here have the whole width
                        // and still regularly need more of it than there is.
                        MarqueeText(
                            text = song.title,
                            style = MaterialTheme.typography.titleLarge.copy(
                                fontSize = titleSize,
                            ),
                            color = Color.White,
                            // Only the tracks YouTube hands us a browse id for
                            // lead anywhere; the rest stay plain text.
                            modifier = Modifier
                                .fillMaxWidth()
                                .opensPage(song.albumId, onOpenAlbum),
                        )
                        Text(
                            text = song.artist,
                            style = MaterialTheme.typography.titleLarge.copy(
                                fontWeight = FontWeight.W500,
                                fontSize = titleSize,
                            ),
                            color = Color.White.copy(alpha = 0.55f),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.opensPage(song.artistId, onOpenArtist),
                        )
                    }
                    Spacer(Modifier.width(10.dp))
                    // Lyrics controls sit in the title row, just before the
                    // like and overflow buttons, but only while the lyrics
                    // panel is open: they act on the words on screen, and a
                    // translate/offset button over the artwork would invite a
                    // tap with nothing to apply it to. The offset control also
                    // needs synced lines to shift.
                    if (lyricsOpen && syncedLyricsEnabled && !lyrics.isNullOrEmpty()) {
                        CircleGlyph(
                            icon = Icons.Rounded.Tune,
                            contentDescription = "Lyrics offset",
                            onClick = {
                                haptics.play(Haptic.Tap)
                                showLyricsOffset = true
                            },
                            active = lyricsOffsetMs != 0,
                        )
                        Spacer(Modifier.width(8.dp))
                        TranslationToggleButton(
                            state = translationState,
                            showingTranslation = showingTranslation,
                            onClick = toggleTranslation,
                        )
                        Spacer(Modifier.width(8.dp))
                    }
                    // Beside the credits rather than down in the toggle row:
                    // liking is about *this song*, and the row below is about
                    // how the queue plays. Guests get nothing to tap, since
                    // there's no account to record it against — and neither
                    // does a local file or a finished download, which carries
                    // no YouTube identity to rate.
                    if (signedIn && song.localUri == null) {
                        val liked = likeStatus == LikeStatus.LIKE
                        CircleGlyph(
                            icon = if (liked) VelthyIcons.HeartFilled else VelthyIcons.Heart,
                            contentDescription = if (liked) "Remove from Liked Music" else "Like",
                            onClick = onToggleLike,
                            active = liked,
                        )
                        Spacer(Modifier.width(8.dp))
                    }
                    CircleGlyph(
                        icon = Icons.Rounded.MoreHoriz,
                        contentDescription = "More",
                        onClick = onOpenMenu,
                    )
                }

                if (lyricsOpen) {
                    Box(
                        // A tap on the lower half brings the transport back once
                        // a scroll has ducked it away. The detector is only
                        // installed while it is actually hidden, so with the
                        // controls on screen the list scrolls off its own slop
                        // as it always has — see [revealLyricsControlsOnTap].
                        modifier = Modifier
                            .fillMaxSize()
                            .revealLyricsControlsOnTap(
                                enabled = panelScrollHidden,
                                onReveal = { panelScrollHidden = false },
                            ),
                    ) {
                        // Swapping between the original words and the
                        // translation is a short particle dissolve rather than
                        // a hard cut, with the panel itself staying put — no
                        // second scroll, no blank frame, and the playback clock
                        // untouched. Collapses to an instant swap under Reduce
                        // animation.
                        LyricsTranslationMotion(
                            trigger = translationTransition,
                            reduceMotion = reduceAnimation,
                            modifier = Modifier.fillMaxSize(),
                        ) { particleProgress ->
                            LyricsPanel(
                                lines = displayedLyrics,
                                trackKey = song.videoId,
                                positionMs = positionMs,
                                looking = lyricsLooking,
                                isPlaying = isPlaying,
                                onSeekToLine = onSeek,
                                onUserScroll = { panelScrollHidden = it },
                                translationProgress = particleProgress,
                                modifier = Modifier
                                    .fillMaxSize()
                                    .padding(top = HEADER_HEIGHT + 10.dp),
                            )
                        }
                    }
                }

                // Toggles and the queue arrive after the sleeve has finished
                // travelling, and leave before it starts coming back.
                if (!lyricsOpen && queueProgress > 0.01f) {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(top = HEADER_HEIGHT + 10.dp)
                            .graphicsLayer {
                                alpha = ((queueProgress - 0.45f) / 0.55f).coerceIn(0f, 1f)
                                translationY = (1f - queueProgress) * 26.dp.toPx()
                            },
                    ) {
                        InlineQueue(
                            queue = queue,
                            currentIndex = queueIndex,
                            autoplayEnabled = autoplayEnabled,
                            onJumpTo = onJumpTo,
                            onRemove = onRemoveFromQueue,
                            onMove = onMoveInQueue,
                            onClear = onClearQueue,
                            onUserScroll = { panelScrollHidden = it },
                            modifier = Modifier.weight(1f),
                        )
                    }
                }
            }

            // ---- Bottom: lyric strip, scrubber, transport, volume, toggles ----
            // One block, measured at its natural height and pinned to the foot
            // of the player. Whatever is left over above it is the artwork's,
            // which is what keeps this row of controls in the same place on
            // every screen instead of being shoved off the bottom of a tall one.
            //
            // While a list (queue or lyrics) is being scrolled the block ducks
            // down out of the way and hands the content the height — resizing
            // is what lets the weighted artwork area above reclaim the room, so
            // the content genuinely gets bigger instead of just sitting behind
            // a translucent bar. It slides back on its own once the list rests.
            AnimatedVisibility(
                visible = !(panelScrollHidden && (queueOpen || lyricsOpen)),
                enter = expandVertically(
                    expandFrom = Alignment.Bottom,
                    animationSpec = tween(durationMillis = 340, easing = FastOutSlowInEasing),
                ) + fadeIn(animationSpec = tween(durationMillis = 220)),
                exit = shrinkVertically(
                    shrinkTowards = Alignment.Bottom,
                    animationSpec = tween(durationMillis = 300, easing = FastOutSlowInEasing),
                ) + fadeOut(animationSpec = tween(durationMillis = 200)),
                modifier = Modifier
                    .widthIn(max = PLAYER_MAX_WIDTH)
                    .fillMaxWidth(),
            ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    // The transport rises on the longest run of the three, so
                    // it is still settling as the credits land and the
                    // backdrop finishes — the last thing to arrive, a beat
                    // after the title it belongs to. Drawn, not composed: this
                    // moves on every frame of the morph.
                    .graphicsLayer {
                        translationY = (1f - playerControlsReveal(morph?.value ?: 1f)) *
                            28.dp.toPx()
                    },
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
            // Current lyric, one line, directly above the scrubber. It stays in
            // the layout while the queue is open and only fades — dropping it
            // would shorten this block, and the controls under it would jump
            // the moment the queue started sliding in.
            //
            // Switched off in Settings it goes entirely, rather than sitting
            // there saying no lyrics were found: none were looked for. It is
            // also the only way into the full lyrics panel, so with it gone
            // the feature is properly gone.
            if (!lyricsOpen && syncedLyricsEnabled) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        // The slider's touch target reaches ~13dp above the
                        // drawn bar, so the strip reads as further off it than
                        // it is. Nudged down into that dead space, the same way
                        // the timestamps below are pulled back up into it.
                        .offset(y = 6.dp)
                        .graphicsLayer { alpha = 1f - queueProgress },
                ) {
                    if (!lyrics.isNullOrEmpty()) {
                        CurrentLyricLine(
                            lines = lyrics,
                            trackKey = song.videoId,
                            positionMs = positionMs,
                            isPlaying = isPlaying,
                            durationMs = durationMs,
                            // Faded out behind the queue, so it must not still
                            // be a target for a tap meant for the list.
                            onClick = { if (!queueOpen) lyricsOpen = true },
                            modifier = Modifier.fillMaxWidth(),
                        )
                    } else if (lyricsUnavailable) {
                        LyricsUnavailableLine(
                            trackKey = song.videoId,
                            modifier = Modifier.fillMaxWidth(),
                        )
                    } else {
                        LyricsLoadingLine(
                            trackKey = song.videoId,
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                }
            }
            val mixing by AppSettings.smartMixInProgress.collectAsStateWithLifecycle()
            val transitionWindow by AppSettings.smartTransitionWindow.collectAsStateWithLifecycle()
            ThinSlider(
                value = shown,
                onValueChange = {
                    scrubbing = true
                    scrubValue = it
                },
                onValueChangeFinished = {
                    haptics.play(Haptic.Select)
                    pendingSeek = scrubValue
                    onSeekFraction(scrubValue)
                    scrubbing = false
                },
                // Suppressed under the finger: the bar is already thickening and
                // tracking a drag, and a sheen sweeping through that reads as a
                // rendering glitch rather than as a signal.
                mixing = mixing && !scrubbing,
                // Hidden while scrubbing for the same reason as the sheen: the
                // planner is still describing where the transition *would* be,
                // and a marker sitting under a finger that is moving the
                // playhead invites reading it as a drag target.
                transitionWindow = transitionWindow
                    ?.takeIf { !scrubbing && it.end > it.start }
                    ?.let { it.start..it.end },
                isLoading = isLoading && !scrubbing,
            )
            val losslessOn by AppSettings.losslessAudio.collectAsStateWithLifecycle()
            val wifiQuality by AppSettings.audioQualityWifi.collectAsStateWithLifecycle()
            val cellularQuality by AppSettings.audioQualityCellular.collectAsStateWithLifecycle()
            val metered by AppSettings.meteredConnection.collectAsStateWithLifecycle()
            // Whether this playback session is even asking for a lossless
            // stream — the same computation SourceResolver.requestForNow()
            // makes, mirrored here so "Loading lossless" only appears when a
            // lossless fetch is actually in flight, not on every buffering
            // YouTube track.
            val losslessRequested = losslessOn &&
                (if (metered == true) cellularQuality else wifiQuality) == AudioQuality.LOSSLESS
            // Whether a module is still racing YouTube for this exact track —
            // see [NerdStats.racingLossless]. YouTube can win that race and
            // already be playing while the module lookup is still running
            // detached in the background, and the badge should keep saying
            // "loading" through that stretch rather than going blank only to
            // possibly say "loading" again a moment later.
            val racingLossless by NerdStats.racingLossless.collectAsStateWithLifecycle()
            val stillRacing = song.videoId in racingLossless
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    // The slider's touch target extends well past the drawn
                    // bar, so pull the labels back up under it.
                    .offset(y = (-9).dp),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Text(
                        text = formatTime((shown * durationMs).toLong()),
                        style = MaterialTheme.typography.labelMedium,
                        color = Color.White.copy(alpha = 0.55f),
                    )
                    Text(
                        text = "-" + formatTime(durationMs - (shown * durationMs).toLong()),
                        style = MaterialTheme.typography.labelMedium,
                        color = Color.White.copy(alpha = 0.55f),
                    )
                }
                // Pinned to the box's own center rather than squeezed into the
                // gap between the two timestamps: that gap's width changes by
                // a digit's worth every time a minute rolls over, which was
                // dragging this along with it every tick. The screen's center
                // doesn't move.
                LosslessOrStats(
                    isLoading = isLoading,
                    stillRacing = stillRacing,
                    losslessRequested = losslessRequested,
                    nerdStats = nerdStats,
                    modifier = Modifier
                        .align(Alignment.Center)
                        .padding(horizontal = 8.dp),
                )
            }

            Spacer(Modifier.height(14.dp))

            // ---- Transport ----
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    TransportGlyph(
                        icon = Icons.Rounded.FastRewind,
                        contentDescription = "Previous",
                        size = 46.dp,
                        onClick = {
                            haptics.play(Haptic.SkipPrevious)
                            onPrevious()
                        },
                        // Lit whenever back has something to do — either a track to
                        // step to, or enough elapsed for it to restart this one.
                        enabled = hasPrevious || positionMs > BACK_RESTARTS_AFTER_MS,
                    )
                    TransportGlyph(
                        icon = if (isPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                        contentDescription = if (isPlaying) "Pause" else "Play",
                        size = 62.dp,
                        onClick = {
                            haptics.play(if (isPlaying) Haptic.Pause else Haptic.Resume)
                            onPlayPause()
                        },
                    )
                    TransportGlyph(
                        icon = Icons.Rounded.FastForward,
                        contentDescription = "Next",
                        size = 46.dp,
                        onClick = {
                            haptics.play(Haptic.SkipNext)
                            onNext()
                        },
                        enabled = hasNext,
                    )
                }

                // Hide volume bar on setting or when queue is open on compact screens only
                if (hideVolumeBar || (queueOpen && isCompactScreen)) {
                    Spacer(Modifier.height(20.dp))
                } else {
                    Spacer(Modifier.height(16.dp))

                    // ---- Volume ----
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(
                            Icons.AutoMirrored.Rounded.VolumeDown,
                            contentDescription = null,
                            tint = Color.White.copy(alpha = 0.5f),
                            modifier = Modifier.size(20.dp),
                        )
                        Spacer(Modifier.width(10.dp))
                        ThinSlider(
                            value = volume.value,
                            onValueChange = {
                                volumeDragging = true
                                // Follow the finger exactly; only external changes tween.
                                scope.launch { volume.snapTo(it) }
                                audioManager?.setStreamVolume(
                                    AudioManager.STREAM_MUSIC,
                                    (it * maxVolume).roundToInt(),
                                    0,
                                )
                            },
                            onValueChangeFinished = { volumeDragging = false },
                            idleHeight = 6.dp,
                            activeHeight = 10.dp,
                            modifier = Modifier.weight(1f),
                        )
                        Spacer(Modifier.width(10.dp))
                        Icon(
                            Icons.AutoMirrored.Rounded.VolumeUp,
                            contentDescription = null,
                            tint = Color.White.copy(alpha = 0.5f),
                            modifier = Modifier.size(20.dp),
                        )
                    }

                    Spacer(Modifier.height(20.dp))
                }

                // ---- Bottom: Lyrics · Sleep/Audio/Party capsule · Queue ----
                val sleepAfterTrack by SleepTimer.afterTrack.collectAsStateWithLifecycle()
                // The capsule's sleep half lights from the badge it draws, which
                // is non-null exactly while a timer is running — asked of the
                // deadline rather than of a ticked preset, because adding minutes
                // to a running timer clears the preset, and a lit moon that went
                // out while the timer it stands for was still counting down would
                // be the loudest kind of wrong.

                // Three controls — lyrics, the capsule, the queue — laid out so
                // the gaps between them are equal.
                //
                // The width is summed from the controls themselves rather than
                // left to SpaceEvenly: the capsule is three segments wide and the
                // three glyphs are one square each, so even spacing would divide
                // the free space into equal *slots* and leave visibly different
                // gaps beside the wide one. The row is then centred by an equal
                // inset at each end, and SpaceBetween distributes what is left
                // evenly between the four — which is what makes the spacing
                // read as deliberate at every screen width. The sum is fixed, so
                // nothing shifts when the party half lights up.
                BoxWithConstraints(Modifier.fillMaxWidth()) {
                val rowWidth = BOTTOM_ACTION_SIZE * 2 + pillWidth(3)
                val edgeInset = ((maxWidth - rowWidth) / 4).coerceAtLeast(0.dp)
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = edgeInset),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    // 1. Lyrics Button
                    BottomGlyph(
                        icon = VelthyIcons.LyricsQuote,
                        contentDescription = "Lyrics",
                        onClick = {
                            haptics.play(if (!lyricsOpen) Haptic.ToggleOn else Haptic.ToggleOff)
                            queueOpen = false
                            lyricsOpen = !lyricsOpen
                        },
                        highlighted = lyricsOpen,
                    )

                    // 2. Sleep timer + audio output + party, one capsule.
                    val sleepBadge = remember(sleepRemaining, sleepAfterTrack, durationMs, positionMs) {
                        val rem = sleepRemaining
                        when {
                            rem != null -> {
                                if (rem >= 60_000L) {
                                    "${(rem + 59999L) / 60000L}m"
                                } else {
                                    "${(rem / 1000L).coerceAtLeast(1)}s"
                                }
                            }
                            sleepAfterTrack -> {
                                val songRemainingMs = (durationMs - positionMs).coerceAtLeast(0L)
                                when {
                                    songRemainingMs >= 60_000L -> "${(songRemainingMs + 59999L) / 60000L}m"
                                    songRemainingMs > 0L -> "${(songRemainingMs / 1000L).coerceAtLeast(1)}s"
                                    else -> "End"
                                }
                            }
                            else -> null
                        }
                    }
                    // The middle control is a capsule either way, and only its
                    // contents change: the things that surround the *track*
                    // while the artwork is showing, and the things that govern
                    // the *queue* once it is. One control rather than two,
                    // because the row has exactly one slot of this width and
                    // swapping what it holds is how both states get to have it.
                    //
                    // The capsule is sized for the wider of the two in both
                    // states — three equal segments either way — so it does not
                    // resize under the finger when the panel opens, and the two
                    // glyphs beside it never move.
                    AnimatedContent(
                        targetState = queueOpen,
                        transitionSpec = {
                            (fadeIn(tween(180, delayMillis = 140)) togetherWith fadeOut(tween(140)))
                                // Unclipped: the capsule's own rounded ends are
                                // what the eye follows through the change, and
                                // the default clip cuts them square while it
                                // happens.
                                .using(SizeTransform(clip = false) { _, _ -> tween(220) })
                        },
                        label = "playerBottomPill",
                    ) { showQueueModes ->
                        if (showQueueModes) {
                            QueueModesPill(
                                shuffleEnabled = shuffleEnabled,
                                repeatMode = repeatMode,
                                autoplayEnabled = autoplayEnabled,
                                onShuffle = {
                                    haptics.play(if (shuffleEnabled) Haptic.ToggleOff else Haptic.ToggleOn)
                                    onToggleShuffle()
                                },
                                onRepeat = {
                                    haptics.play(Haptic.Select)
                                    onCycleRepeat()
                                },
                                onAutoplay = {
                                    haptics.play(if (autoplayEnabled) Haptic.ToggleOff else Haptic.ToggleOn)
                                    onToggleAutoplay()
                                },
                            )
                        } else {
                            OutputPartyPill(
                                onSleep = {
                                    haptics.play(Haptic.Tap)
                                    showSleepTimerSheet = true
                                },
                                onOutput = {
                                    haptics.play(Haptic.Tap)
                                    showAudioOutputSheet = true
                                },
                                onParty = {
                                    haptics.play(Haptic.Tap)
                                    onOpenParty()
                                },
                                inParty = party.inParty,
                                sleepBadge = sleepBadge,
                            )
                        }
                    }

                    // 3. Queue Button
                    BottomGlyph(
                        icon = VelthyIcons.Queue,
                        contentDescription = "Queue",
                        onClick = {
                            haptics.play(if (!queueOpen) Haptic.ToggleOn else Haptic.ToggleOff)
                            lyricsOpen = false
                            queueOpen = !queueOpen
                        },
                        highlighted = queueOpen,
                    )
                }
                }
            }
            }

            // Where the sound is going, under the controls and kept there in
            // both player and queue modes — it is a fact about the track, not
            // about the panel that happens to be open. In a party the jam takes
            // the line over, because once four devices are playing the same song
            // the room is no longer what the listener is checking. The tap
            // follows the label: whichever is on screen is what it opens.
            Spacer(Modifier.height(18.dp))
            Box(
                modifier = Modifier.fillMaxWidth().height(20.dp),
                contentAlignment = Alignment.TopCenter,
            ) {
                OutputCaption(
                    // A connected device names itself; the phone's own speaker
                    // does not — it reports the *phone's* model name, which is
                    // why the line used to read like a serial number — so it is
                    // named here instead.
                    outputName = if (activeDevice.isExternal) {
                        activeDevice.name
                    } else {
                        "This phone"
                    },
                    hostFirstName = party.members.firstOrNull { it.isHost }
                        ?.displayName
                        ?.trim()
                        ?.split(Regex("\\s+"))
                        ?.firstOrNull(),
                    inParty = party.inParty,
                    onOpenOutput = {
                        haptics.play(Haptic.Tap)
                        showAudioOutputSheet = true
                    },
                    onOpenParty = {
                        haptics.play(Haptic.Tap)
                        onOpenParty()
                    },
                )
            }
            Spacer(Modifier.height(18.dp))
        }
    }

    // The lyrics offset drawer, raised from the lyrics panel.
    if (showLyricsOffset) {
        LyricsOffsetSheet(onDismiss = { showLyricsOffset = false })
    }

    // ---- Compact Sleep Timer Modal Sheet ----
        if (showSleepTimerSheet) {
            val afterTrack by SleepTimer.afterTrack.collectAsStateWithLifecycle()

            ModalBottomSheet(
                onDismissRequest = { showSleepTimerSheet = false },
                sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
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
                    // Header (Compact)
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = "Sleep Timer",
                            style = MaterialTheme.typography.titleMedium.copy(
                                fontWeight = FontWeight.Bold,
                                letterSpacing = (-0.2).sp,
                            ),
                            color = Color.White,
                            modifier = Modifier.weight(1f),
                        )
                    }

                    // Two faces, never both. Before anything is picked this is
                    // the menu; once a timer is armed it is that timer — what is
                    // left, five more minutes, and the way out. The rungs leave
                    // with the menu, because changing the duration now means
                    // turning this one off first: one screen for choosing, one
                    // for what was chosen.
                    if (sleepRemaining == null && !afterTrack) {
                        // Compact Grouped Inset
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(16.dp))
                                .background(Color.White.copy(alpha = 0.08f)),
                        ) {
                            CompactOptionRow(
                                label = "After this song",
                                onClick = {
                                    SleepTimer.startAfterTrack()
                                    showSleepTimerSheet = false
                                },
                            )

                            HorizontalDivider(
                                modifier = Modifier.padding(start = 16.dp),
                                thickness = 0.5.dp,
                                color = Color.White.copy(alpha = 0.08f),
                            )

                            val presets = listOf(
                                15 to "15 minutes",
                                30 to "30 minutes",
                                45 to "45 minutes",
                                60 to "1 hour",
                            )

                            presets.forEachIndexed { index, (minutes, label) ->
                                CompactOptionRow(
                                    label = label,
                                    onClick = {
                                        SleepTimer.start(minutes)
                                        showSleepTimerSheet = false
                                    },
                                )
                                if (index < presets.lastIndex) {
                                    HorizontalDivider(
                                        modifier = Modifier.padding(start = 16.dp),
                                        thickness = 0.5.dp,
                                        color = Color.White.copy(alpha = 0.08f),
                                    )
                                }
                            }
                        }
                    } else {
                        val rem = sleepRemaining
                        if (rem != null) {
                            // What is left, at the size of a clock rather than a
                            // caption — the one number someone who opened this
                            // sheet half asleep is looking for, and the reason
                            // they opened it at all.
                            Text(
                                text = SleepTimer.clock(rem),
                                style = MaterialTheme.typography.displaySmall.copy(
                                    fontWeight = FontWeight.Bold,
                                    letterSpacing = (-1).sp,
                                ),
                                color = Color.White,
                                textAlign = TextAlign.Center,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(top = 6.dp, bottom = 16.dp),
                            )
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(bottom = 12.dp)
                                    .clip(RoundedCornerShape(14.dp))
                                    .background(Color.White.copy(alpha = 0.12f))
                                    .clickable { SleepTimer.extend(5) }
                                    .padding(vertical = 12.dp),
                                horizontalArrangement = Arrangement.Center,
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.Add,
                                    contentDescription = null,
                                    tint = Color.White,
                                    modifier = Modifier.size(18.dp),
                                )
                                Spacer(Modifier.width(6.dp))
                                Text(
                                    text = "Add 5 minutes",
                                    style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                                    color = Color.White,
                                )
                            }
                        } else {
                            // No number to add to: an end-of-track timer is an
                            // event, not a duration, so it gets the state and
                            // the way out and nothing else.
                            Text(
                                text = "End of current track",
                                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
                                color = Color.White,
                                textAlign = TextAlign.Center,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(top = 10.dp, bottom = 22.dp),
                            )
                        }

                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(14.dp))
                                .background(MaterialTheme.colorScheme.error.copy(alpha = 0.12f))
                                .clickable {
                                    SleepTimer.cancel()
                                    showSleepTimerSheet = false
                                }
                                .padding(vertical = 12.dp, horizontal = 16.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.Center,
                        ) {
                            Text(
                                text = "Turn Off Timer",
                                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                                color = MaterialTheme.colorScheme.error,
                            )
                        }
                    }

                    Spacer(Modifier.height(8.dp))
                }
            }
        }

        // Where the music comes out, and how loud â€” the drawer BitChord uses,
        // down to the arrangement: the outputs, then volume.
        if (showAudioOutputSheet) {
            AudioOutputSheet(onDismiss = { showAudioOutputSheet = false })
        }
    }
}

@Composable
private fun CompactOptionRow(
    label: String,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 13.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyLarge,
            color = Color.White.copy(alpha = 0.85f),
        )
    }
}


/**
 * The song position, ticking every frame.
 *
 * The player reports where it is about twice a second, which is fine for a
 * scrubber and far too coarse for a highlight that has to keep up with a
 * singer. This carries that report forward on the frame clock between
 * reports, and resets to the real value whenever a fresh one lands — so it
 * never drifts, it just fills in.
 *
 * Returned as state rather than a plain value on purpose: read inside a draw
 * lambda, only the draw phase re-runs each frame. Read in composition, the
 * whole line would recompose sixty times a second.
 */
@Composable
private fun rememberLyricClock(positionMs: Long, isPlaying: Boolean): MutableLongState {
    val clock = remember { mutableLongStateOf(positionMs) }
    LaunchedEffect(positionMs, isPlaying) {
        clock.longValue = positionMs
        if (!isPlaying) return@LaunchedEffect
        var previousFrame = withFrameMillis { it }
        while (true) {
            withFrameMillis { frame ->
                clock.longValue += frame - previousFrame
                previousFrame = frame
            }
        }
    }
    return clock
}

/**
 * A two-tab pill for swapping a music video for the catalogue audio version.
 *
 * One control rather than two buttons because the two states are the *same*
 * choice seen from either side — which version of this song — and the selected
 * tab says where you are without a label. The frosted fill matches the other
 * floating controls over the artwork rather than introducing a new surface.
 */
@Composable
private fun VideoAudioVersionButton(
    audioVersion: Boolean,
    loading: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val haptics = rememberHaptics()
    val shape = RoundedCornerShape(percent = 50)
    Box(
        modifier = modifier
            .height(44.dp)
            .clip(shape)
            .background(Color.Black.copy(alpha = 0.28f))
            .border(0.5.dp, Color.White.copy(alpha = 0.12f), shape),
        contentAlignment = Alignment.Center,
    ) {
        Row(
            modifier = Modifier.padding(3.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            VideoAudioTab(
                icon = Icons.Rounded.Videocam,
                contentDescription = "Play the video version",
                selected = !audioVersion,
                enabled = audioVersion && !loading,
                onClick = {
                    haptics.play(Haptic.Tap)
                    onClick()
                },
            )
            VideoAudioTab(
                icon = VelthyIcons.MusicNote,
                contentDescription = "Play the audio version",
                selected = audioVersion,
                enabled = !audioVersion && !loading,
                loading = loading,
                onClick = {
                    haptics.play(Haptic.Tap)
                    onClick()
                },
            )
        }
    }
}

@Composable
private fun VideoAudioTab(
    icon: ImageVector,
    contentDescription: String,
    selected: Boolean,
    enabled: Boolean,
    onClick: () -> Unit,
    loading: Boolean = false,
) {
    Box(
        modifier = Modifier
            .size(38.dp)
            .clip(CircleShape)
            .background(Color.White.copy(alpha = if (selected) 0.20f else 0f))
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                enabled = enabled,
                onClick = onClick,
            ),
        contentAlignment = Alignment.Center,
    ) {
        if (loading) {
            CircularProgressIndicator(
                modifier = Modifier.size(17.dp),
                color = Color.White,
                strokeWidth = 2.dp,
            )
        } else {
            Icon(
                imageVector = icon,
                contentDescription = contentDescription,
                tint = Color.White.copy(alpha = if (selected || enabled) 1f else 0.55f),
                modifier = Modifier.size(20.dp),
            )
        }
    }
}

/**
 * The line above the credits: where this track is playing *from*.
 *
 * Four answers, in the order they matter:
 *
 *  - the party, because listening with other people is the fact most worth
 *    stating and the one a reader would otherwise be surprised by;
 *  - a station's own name, which explains a queue nobody built by hand;
 *  - the album or page the song was started out of;
 *  - nothing at all, in which case no line is drawn — an empty caption would be
 *    a gap in the layout with no content in it.
 *
 * The prefix is fixed text and the rest is a name, so the line reads the same
 * way every time and the eye can find the half it wants without re-parsing.
 */
/**
 * The line under the controls: which speaker, or which jam.
 *
 * A party overrides the output rather than sitting beside it, because the two
 * are not the same kind of fact. "This phone" answers which speaker in this
 * room; once four devices are playing the same song, the room is no longer what
 * the listener is checking. The tap follows the label, so whichever one is on
 * screen is the thing it opens.
 *
 * A party with no host name yet — a snapshot before anyone's joined in earnest —
 * still says it is listening together rather than falling back to the device,
 * which would read as though the party had ended.
 */
@Composable
private fun OutputCaption(
    outputName: String,
    hostFirstName: String?,
    inParty: Boolean,
    onOpenOutput: () -> Unit,
    onOpenParty: () -> Unit,
) {
    val label = if (inParty) {
        hostFirstName?.let { "$it's Jam" } ?: "Listening together"
    } else {
        outputName
    }
    Text(
        text = label,
        style = MaterialTheme.typography.labelSmall.copy(fontSize = 11.sp),
        color = Color.White.copy(alpha = 0.55f),
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        textAlign = TextAlign.Center,
        modifier = Modifier
            .fillMaxWidth(0.65f)
            .clickable { if (inParty) onOpenParty() else onOpenOutput() },
    )
}

@Composable
private fun PlayingFromCaption(
    song: Song,
    playedBy: String?,
    onParty: () -> Unit,
    onOpenQueue: () -> Unit,
    onOpenSource: () -> Unit,
    alpha: Float,
    modifier: Modifier = Modifier,
) {
    val isParty = !playedBy.isNullOrBlank()
    val isQueue = song.playbackSourceType == PlaybackSourceType.QUEUE
    // The last fallback is the queue itself, so the line is never blank while a
    // track is on — "Playing from Queue" is a real answer about where this came
    // from even when nothing else names a page.
    val source = song.playbackSource ?: song.albumName ?: "Queue"
    val label = when {
        isParty -> "Played by $playedBy"
        !song.radioName.isNullOrBlank() -> "Playing ${song.radioName} Radio"
        else -> "Playing from $source"
    }

    // A party, a queue, and any source this app can navigate back to are
    // tappable. A source named but not openable — a plain "History", say —
    // stays plain text rather than offering a tap that would do nothing.
    val hasSource = song.playbackSource != null || song.albumName != null
    val onTap: (() -> Unit)? = when {
        isParty -> onParty
        isQueue -> onOpenQueue
        hasSource -> onOpenSource
        else -> null
    }

    Text(
        text = label,
        style = MaterialTheme.typography.labelSmall,
        color = Color.White.copy(alpha = 0.62f),
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        textAlign = TextAlign.Center,
        modifier = modifier
            .fillMaxWidth()
            .graphicsLayer { this.alpha = alpha }
            .then(if (onTap != null) Modifier.clickable(onClick = onTap) else Modifier)
            .padding(start = PLAYER_GUTTER, end = PLAYER_GUTTER, bottom = 1.dp),
    )
}

/**
 * A lyric line with the sung part of it lit, the rest dimmed, and the boundary
 * travelling across the words in time with the vocal.
 *
 * Two copies of the same text stacked: a dim one and a bright one clipped to
 * whatever has been sung. Same string, same style, same constraints, so the
 * two lay out identically and the bright copy lands exactly on top of the dim
 * one. The alternative — colouring an AnnotatedString word by word — can only
 * change a whole word at a time, which turns the sweep into a flicker.
 *
 * The clip is recomputed in the draw phase, so a frame costs one clip and one
 * redraw of already-measured text.
 *
 * [glowAlpha] adds Apple's bloom: a third copy, blurred, behind the other two
 * and clipped to the same boundary. Blurring *after* the clip rather than
 * before is what makes the halo bleed a little way past the sweep's leading
 * edge, which is the part that reads as light coming off the word being sung
 * rather than a drop shadow sitting under the line.
 */
@Composable
private fun SweptLyricLine(
    line: LyricLine,
    clock: MutableLongState,
    style: TextStyle,
    dimAlpha: Float,
    modifier: Modifier = Modifier,
    maxLines: Int = Int.MAX_VALUE,
    overflow: TextOverflow = TextOverflow.Clip,
    glowAlpha: Float = 0f,
    glowRadius: Dp = GLOW_RADIUS,
    glowRoom: Dp = 0.dp,
    feather: Boolean = false,
    rise: Boolean = true,
    alignEnd: Boolean = false,
    translationProgress: State<Float>? = null,
) {
    var layout by remember(line) { mutableStateOf<TextLayoutResult?>(null) }

    // Filled in and read back a letter at a time inside the draw lambdas, and
    // shared by all three copies of the line — they draw one after another on
    // the same thread, so there is only ever one letter in hand. Held here
    // rather than allocated per frame: a held word is seven letters at the
    // outside, but this runs on every frame of every line that has one.
    val growth = remember { CharGrowth() }

    // Carried by every copy: identical insets keep them laying out identically,
    // and the inset is what gives the blurred copy's layer somewhere to put the
    // halo. Sits inside the blur and outside the draw lambdas, so text-layout
    // coordinates and draw coordinates still agree.
    //
    // Off unless asked for. Only the full panel can afford it — it takes the
    // space back off its own row spacing and content padding. Handed to the
    // one-line strip above the scrubber, where there is no glow to make room
    // for and nothing paying the space back, it just left the line sitting in
    // a pocket of air with the chevron pushed off it.
    val room = if (glowRoom > 0.dp) Modifier.padding(glowRoom) else Modifier

    // Sits outside [room] and outside the sweep, so what it moves is the
    // finished picture of the word — dim tail, lit head and all — rather than
    // one copy sliding out from under another. Carried by both copies from the
    // same arithmetic, which is what keeps them on top of each other.
    //
    // Off for the one-line strip above the scrubber ([rise] = false). The lift
    // belongs to a page of lyrics, where a word rising out of the line it sits
    // in is the thing being read; on a single line pinned between the credits
    // and the slider it has nothing to rise away from and reads as the strip
    // itself twitching.
    val riseAgainst: (Modifier) -> Modifier = { inner ->
        if (!rise) {
            inner
        } else {
            Modifier
                .drawWithContent {
                    val measured = layout
                    if (measured == null || line.words.isEmpty()) {
                        drawContent()
                    } else {
                        riseWith(
                            layout = measured,
                            line = line,
                            positionMs = clock.longValue,
                            inset = glowRoom.toPx(),
                            peak = WORD_RISE.toPx(),
                            growth = growth,
                        )
                    }
                }
                .then(inner)
        }
    }

    val sweep = Modifier.drawWithContent {
        val position = clock.longValue
        when {
            // Sung and done with: all of it is lit. Checked first so the lines
            // above and below the playing one — which are in this same state
            // for minutes at a time — cost a comparison per frame rather than
            // a walk of their words.
            position >= line.endMs -> drawContent()
            // Not started: nothing lit, the dim copy is the whole of it.
            position <= line.timeMs -> Unit
            else -> layout?.let { sweepTo(it, line.revealedChars(position), feather) }
        }
    }

    // A right-hand duet line right-aligns twice over: the block within the row,
    // for the case where it is one short line in a wide panel, and the lines
    // within the block, for the case where it has wrapped. Neither alone is
    // enough, and the three copies all take both, so they still land on top of
    // each other.
    Box(
        modifier.lyricParticles(layout, translationProgress, glowRoom),
        contentAlignment = if (alignEnd) Alignment.TopEnd else Alignment.TopStart,
    ) {
        Text(
            text = line.text,
            style = style,
            color = Color.White.copy(alpha = dimAlpha),
            maxLines = maxLines,
            overflow = overflow,
            onTextLayout = { layout = it },
            modifier = riseAgainst(room),
        )
        if (glowAlpha > 0.01f) {
            Text(
                text = line.text,
                style = style,
                color = Color.White,
                maxLines = maxLines,
                overflow = overflow,
                modifier = Modifier
                    .graphicsLayer { alpha = glowAlpha }
                    .blur(glowRadius, BlurredEdgeTreatment.Unbounded)
                    .then(room)
                    // Each letter is masked to its own brightness with DstIn,
                    // which needs a layer of its own to erase into — against the
                    // backdrop it would take the artwork with it.
                    .graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
                    .drawWithContent {
                        // Deliberately not the shared sweep: that lights
                        // everything sung so far, and this lights only the words
                        // being held. Most lines draw nothing here at all, which
                        // is the whole difference between this and a halo
                        // travelling under the highlight.
                        val measured = layout ?: return@drawWithContent
                        glowGrown(
                            layout = measured,
                            line = line,
                            positionMs = clock.longValue,
                            inset = glowRoom.toPx(),
                            peak = WORD_RISE.toPx(),
                            growth = growth,
                        )
                    },
            )
        }
        Text(
            text = line.text,
            style = style,
            color = Color.White,
            maxLines = maxLines,
            overflow = overflow,
            // The feather erases into this layer, so the layer has to exist —
            // and only while it is being drawn. Every line carrying one would
            // put the whole panel through an offscreen buffer to soften an edge
            // that at most two of them have.
            modifier = riseAgainst(
                Modifier
                    .graphicsLayer {
                        compositingStrategy = if (feather) {
                            CompositingStrategy.Offscreen
                        } else {
                            CompositingStrategy.Auto
                        }
                    }
                    .then(room)
                    .then(sweep),
            ),
        )
    }
}

/**
 * Draws this text clipped to the letters of the words being held, each at its
 * own brightness — the light the singing is actually giving off, rather than a
 * band of it dragged along behind the highlight.
 *
 * Nothing at all on a line of ordinary syllables: the words that light up are
 * the ones held long enough to have earned it, so a verse of patter is simply
 * dark and costs one comparison to establish. That selectiveness is the point.
 * A glow present on every word is a property of the highlight; a glow that
 * arrives only when a note is carried is a property of the voice.
 *
 * Each letter is masked to its own bloom rather than drawn at it, because the
 * caller's layer is what this erases into — see [SweptLyricLine]. The mask
 * lands before the blur, so what spreads is already the right brightness.
 */
private fun ContentDrawScope.glowGrown(
    layout: TextLayoutResult,
    line: LyricLine,
    positionMs: Long,
    inset: Float,
    peak: Float,
    growth: CharGrowth,
) {
    if (!line.isGrowing(positionMs)) return
    val em = layout.layoutInput.style.fontSize.toPx()
    val length = layout.layoutInput.text.length
    for (word in line.growingWords) {
        if (positionMs < word.startMs || positionMs > word.restsAtMs) continue
        val span = line.wordSpans[word.index]
        val fall = line.wordFall(word.index, positionMs)
        for (char in span.first..minOf(span.last, length - 1)) {
            word.sampleInto(char - span.first, positionMs, growth)
            if (growth.bloom <= 0.01f) continue
            val visualLine = layout.getLineForOffset(char)
            // Row-aware, for the same reason the sweep is; see [xOn].
            val from = layout.xOn(char, visualLine, inset)
            val to = layout.xOn(char + 1, visualLine, inset)
            if (to <= from) continue
            val dx = growth.shift * em
            val dy = -growth.rise * peak * fall
            val rowTop = layout.getLineTop(visualLine) + inset
            val bottom = layout.getLineBottom(visualLine) + inset
            val overhang = (to - from) * (growth.scale - 1f) / 2f
            clipRect(
                left = from - overhang + dx,
                top = rowTop - peak * GROW_HEADROOM,
                right = to + overhang + dx,
                bottom = bottom,
            ) {
                translate(left = dx, top = dy) {
                    scale(
                        growth.scale,
                        growth.scale,
                        Offset((from + to) / 2f, (rowTop + bottom) / 2f),
                    ) {
                        this@glowGrown.drawContent()
                    }
                }
                // Scoped to this letter's own clip, so it takes this letter's
                // brightness down and leaves its neighbours — which have their
                // own, a beat behind — where they are.
                drawRect(
                    color = Color.White.copy(alpha = growth.bloom),
                    blendMode = BlendMode.DstIn,
                )
            }
        }
    }
}

/**
 * Redraws this row with the word being sung lifted off the line, and the ones
 * behind it settling back down.
 *
 * The line is cut at word boundaries and each piece replayed at its own
 * height, which is what CSS gets for free by making every syllable its own
 * box. Cutting between words rather than inside one means no glyph is ever
 * sliced, and the pieces that are on the floor — which is most of them, most
 * of the time — are one replay between them rather than one each.
 *
 * Costs nothing at all until something is off the floor: a line with no lift
 * on it draws exactly once, the same as it did before any of this.
 */
private fun ContentDrawScope.riseWith(
    layout: TextLayoutResult,
    line: LyricLine,
    positionMs: Long,
    inset: Float,
    peak: Float,
    growth: CharGrowth,
) {
    if (!line.isLifted(positionMs)) {
        drawContent()
        return
    }
    val em = layout.layoutInput.style.fontSize.toPx()
    for (visualLine in 0 until layout.lineCount) {
        val lineStart = layout.getLineStart(visualLine)
        val lineEnd = layout.getLineEnd(visualLine, visibleEnd = true)
        // The row's own box. Anything standing still is clipped to exactly
        // this: a band opened upwards would take in the bottom of the row
        // above and draw it a second time, and two passes of a half-transparent
        // line do not add up to the same line. That doubled sliver along every
        // row is what read as the lines overlapping.
        val top = layout.getLineTop(visualLine) + inset
        val bottom = layout.getLineBottom(visualLine) + inset
        var at = lineStart
        var edge = layout.getLineLeft(visualLine) + inset
        for (index in line.words.indices) {
            val span = line.wordSpans[index]
            val start = maxOf(span.first, lineStart)
            val end = minOf(span.last + 1, lineEnd)
            if (start >= end) continue
            // Only while it is actually moving. Once the last letter has come to
            // rest the word is back to being an ordinary sung word settling
            // down, and the two agree exactly at the handover — a letter rests
            // at precisely the lift [LyricLine.wordLift] would give it — so the
            // cheaper single slice takes over without a step.
            val held = line.growingAt(index)?.takeIf { positionMs in it.startMs..it.restsAtMs }
            val lift = line.wordLift(index, positionMs)
            // A word with nothing happening to it is left to the flat run,
            // which is the whole of the line for all but a syllable of it.
            if (held == null && lift <= 0.01f) continue
            val from = layout.xOn(start, visualLine, inset)
            val to = layout.xOn(end, visualLine, inset)
            // Nothing to cut. Left where it is rather than stepped over, so the
            // flat run still has it and the row keeps its words.
            if (to <= from) continue
            // Everything between the last risen word and this one is flat, and
            // goes down in a single piece however many words that spans.
            if (start > at) sliceRisen(edge, top, from, bottom, 0f)
            if (held != null) {
                growEach(
                    layout, held, line, positionMs, visualLine,
                    start, end, top, bottom, inset, peak, em, growth,
                )
            } else {
                // Only what is off the floor gets room above the row to be off
                // it in; see [top].
                sliceRisen(from, top - peak, to, bottom, -lift * peak)
            }
            at = end
            edge = to
        }
        if (at < lineEnd) {
            sliceRisen(edge, top, layout.getLineRight(visualLine) + inset, bottom, 0f)
        }
    }
}

/**
 * Redraws one held word a letter at a time, each at its own swell and height.
 *
 * The word is cut between characters rather than between words, so a letter can
 * be scaled about its own centre without the ones either side of it coming
 * along. Each piece is clipped to where its letter is *going* rather than where
 * it sits: a glyph grown about its middle reaches past the box it was laid out
 * in, and clipping to that box would shave both sides off it as it swells.
 *
 * The overlap that buys — a letter's clip reaching a pixel or so into its
 * neighbour's — is why this is only ever run on a word that has earned it. Two
 * copies of a glyph edge a pixel apart is nothing on a letter mid-swell and
 * would be an obvious double image across a whole line.
 */
@Suppress("LongParameterList")
private fun ContentDrawScope.growEach(
    layout: TextLayoutResult,
    word: GrowingWord,
    line: LyricLine,
    positionMs: Long,
    visualLine: Int,
    start: Int,
    end: Int,
    top: Float,
    bottom: Float,
    inset: Float,
    peak: Float,
    em: Float,
    growth: CharGrowth,
) {
    // The settle is shared with every other word: a letter comes to rest at the
    // same small lift, and then goes down with the rest of the line.
    val fall = line.wordFall(word.index, positionMs)
    val first = line.wordSpans[word.index].first
    // Room to swell into, above the row rather than inside it. The pivot stays
    // on the row's own middle: scaling about the middle of the *band* would
    // walk every letter downwards as it grew.
    val ceiling = top - peak * GROW_HEADROOM
    val middle = (top + bottom) / 2f
    for (char in start until end) {
        word.sampleInto(char - first, positionMs, growth)
        val from = layout.xOn(char, visualLine, inset)
        val to = layout.xOn(char + 1, visualLine, inset)
        if (to <= from) continue
        val dx = growth.shift * em
        val dy = -growth.rise * peak * fall
        val overhang = (to - from) * (growth.scale - 1f) / 2f
        clipRect(
            left = from - overhang + dx,
            top = ceiling,
            right = to + overhang + dx,
            bottom = bottom,
        ) {
            translate(left = dx, top = dy) {
                scale(growth.scale, growth.scale, Offset((from + to) / 2f, middle)) {
                    this@growEach.drawContent()
                }
            }
        }
    }
}

/**
 * Where an offset sits horizontally *on the row it was cut out of*.
 *
 * [TextLayoutResult.getHorizontalPosition] answers for the row the offset
 * itself belongs to — and the offset one past the last character of a wrapped
 * row belongs to the next row, so asking where a word that runs up to a wrap
 * *ends* gives a position at the far left, one row down. A slice cut between
 * there and the word's start is empty, and the walk then treats the row as
 * finished: everything from that word to the end of the row is never drawn.
 *
 * Whole rows disappeared that way, and Japanese lines disappeared most, because
 * Apple's word spans there are whole phrases and reach a wrap on their own where
 * an English word rarely does.
 *
 * So both ends of a row are answered with the row's own edges, and anything in
 * between is held inside them.
 */
private fun TextLayoutResult.xOn(offset: Int, visualLine: Int, inset: Float): Float {
    val left = getLineLeft(visualLine) + inset
    val right = getLineRight(visualLine) + inset
    return when {
        offset <= getLineStart(visualLine) -> left
        offset >= getLineEnd(visualLine, visibleEnd = true) -> right
        else -> (getHorizontalPosition(offset, usePrimaryDirection = true) + inset)
            .coerceIn(left, right)
    }
}

/** One piece of a line, clipped to its own width and drawn at its own height. */
private fun ContentDrawScope.sliceRisen(
    from: Float,
    top: Float,
    to: Float,
    bottom: Float,
    dy: Float,
) {
    if (to <= from) return
    clipRect(left = from, top = top, right = to, bottom = bottom) {
        translate(top = dy) { this@sliceRisen.drawContent() }
    }
}

/** Where a fractional character index sits across a visual line, in pixels. */
private fun horizontalAt(
    layout: TextLayoutResult,
    chars: Float,
    visualLine: Int,
): Float {
    val lineStart = layout.getLineStart(visualLine)
    val lineEnd = layout.getLineEnd(visualLine, visibleEnd = true)
    val index = chars.toInt().coerceIn(lineStart, lineEnd)
    // Row-aware at both ends: on the last character of a wrapped row the next
    // position belongs to the row below, and read straight it puts the edge
    // back at the left margin — the highlight jumped backwards a letter before
    // every wrap.
    val here = layout.xOn(index, visualLine, 0f)
    val next = layout.xOn((index + 1).coerceAtMost(lineEnd), visualLine, 0f)
    return here + (next - here) * (chars - index)
}

/**
 * Draws this text clipped to its first [revealedChars] characters.
 *
 * Wrapped lines are handled a visual line at a time: the ones already passed
 * are drawn whole, the one holding the boundary is cut at it, and the rest are
 * left to the dim copy. Within a word the cut sits between two character
 * positions, so the edge advances smoothly rather than jumping a letter at a
 * time.
 *
 * The boundary itself is then feathered over [WIPE_FEATHER] rather than left
 * as the cut, which needs the caller to give this an offscreen layer to erase
 * into — see [SweptLyricLine]. Only the line actually being sung carries one;
 * everywhere else the boundary is at one end of the text or the other and
 * there is nothing to soften.
 */
private fun ContentDrawScope.sweepTo(
    layout: TextLayoutResult,
    revealedChars: Float,
    feather: Boolean,
) {
    if (revealedChars <= 0f) return
    if (revealedChars >= layout.layoutInput.text.length) {
        drawContent()
        return
    }
    for (visualLine in 0 until layout.lineCount) {
        val start = layout.getLineStart(visualLine)
        // Lines beyond the boundary have nothing lit on them, and neither has
        // anything after them.
        if (revealedChars <= start) return
        val end = layout.getLineEnd(visualLine, visibleEnd = true)
        val cut = revealedChars < end
        val right = if (cut) {
            horizontalAt(layout, revealedChars, visualLine)
        } else {
            layout.getLineRight(visualLine)
        }
        val top = layout.getLineTop(visualLine)
        val bottom = layout.getLineBottom(visualLine)
        clipRect(
            left = layout.getLineLeft(visualLine),
            top = top,
            right = right,
            bottom = bottom,
        ) {
            this@sweepTo.drawContent()
        }
        // Only the visual line holding the boundary has an edge to soften; a
        // line revealed to its end runs into the wrap, which is not an edge.
        if (!feather || !cut) continue
        // Scoped to this line's band so the mask cannot reach the lines above
        // and below it: DstIn erases whatever the source does not cover, and
        // outside the clip there is no source at all, so they are left alone.
        // Within it the brush clamps — opaque behind the feather, gone past it.
        clipRect(top = top, bottom = bottom) {
            drawRect(
                brush = Brush.horizontalGradient(
                    0f to Color.White,
                    1f to Color.Transparent,
                    startX = (right - WIPE_FEATHER.toPx())
                        .coerceAtLeast(layout.getLineLeft(visualLine)),
                    endX = right,
                ),
                blendMode = BlendMode.DstIn,
            )
        }
    }
}

/**
 * A short text-material transition: the list and its playback clock stay in
 * place while a field of tiny glyph-like particles resolves into the new text.
 * Only the dedicated Canvas drawing moves, so changing language never causes a
 * second scroll, a blank frame, or a new lyrics timeline. The app's Reduce
 * animation preference collapses the whole response to an immediate swap.
 */
@Composable
private fun LyricsTranslationMotion(
    trigger: Int,
    reduceMotion: Boolean,
    modifier: Modifier = Modifier,
    content: @Composable (State<Float>?) -> Unit,
) {
    val progress = remember { Animatable(1f) }
    val foreground = rememberIsForeground()
    // Reopening the panel or returning from the background must not replay a
    // previous toggle. A new toggle cancels the previous effect automatically.
    var consumedTrigger by remember { mutableIntStateOf(trigger) }
    LaunchedEffect(trigger, reduceMotion, foreground) {
        val changed = trigger != consumedTrigger
        consumedTrigger = trigger
        if (!changed || trigger <= 0 || reduceMotion || !foreground) {
            progress.snapTo(1f)
        } else {
            progress.snapTo(0f)
            progress.animateTo(
                targetValue = 1f,
                animationSpec = tween(durationMillis = TRANSLATION_MOTION_MS, easing = LinearEasing),
            )
        }
    }

    Box(modifier = modifier) {
        // Keep the lyrics subtree completely outside the animation clock. In
        // particular, do not read progress in composition or apply a clipping
        // layer here: the panel's active line deliberately scales beyond its
        // measured bounds and its glow uses unbounded blur.
        content(progress.asState().takeIf { !reduceMotion && foreground && trigger > 0 })
    }
}

/** Glyph positions are cached at layout time; the shared clock is draw-only. */
private fun Modifier.lyricParticles(
    layout: TextLayoutResult?,
    progress: State<Float>?,
    room: Dp,
): Modifier {
    if (layout == null || progress == null) return this
    return drawWithCache {
        val text = layout.layoutInput.text.text
        val candidates = text.indices.filter { text[it].isLetterOrDigit() }
        val random = Random(text.hashCode())
        val inset = room.toPx()
        val particles = candidates.shuffled(random).take(PARTICLES_PER_VOICE).map { index ->
            val glyph = layout.getBoundingBox(index)
            TranslationParticle(
                anchor = glyph.center + Offset(inset, inset),
                drift = Offset(
                    (random.nextFloat() - 0.5f) * 12.dp.toPx(),
                    -(5f + random.nextFloat() * 11f).dp.toPx(),
                ),
                radius = (0.65f + random.nextFloat() * 0.65f).dp.toPx(),
                delay = 0.16f * index / text.length.coerceAtLeast(1),
            )
        }
        onDrawWithContent {
            drawContent()
            val value = progress.value
            if (value > 0f && value < 1f) {
                particles.forEach { particle ->
                    val t = ((value - particle.delay) / 0.84f).coerceIn(0f, 1f)
                    val envelope = sin(PI * t).toFloat()
                    val ease = 1f - (1f - t) * (1f - t)
                    val center = particle.anchor + Offset(
                        particle.drift.x * ease,
                        particle.drift.y * ease + 3.dp.toPx() * t * t,
                    )
                    // Two inexpensive circles give a soft halo without another
                    // blur layer; opacity rises and falls without a flash.
                    drawCircle(Color.White, particle.radius * 2.7f, center, alpha = envelope * 0.07f)
                    drawCircle(Color.White, particle.radius, center, alpha = envelope * 0.58f)
                }
            }
        }
    }
}

/** A particle's resting place and where it drifts as the new text resolves. */
private data class TranslationParticle(
    val anchor: Offset,
    val drift: Offset,
    val radius: Float,
    val delay: Float,
)

private const val PARTICLES_PER_VOICE = 18
private const val TRANSLATION_MOTION_MS = 540

/**
 * Stands in for the lyrics while the lookup is still out.
 *
 * Without it the panel had one empty state doing two jobs: a lookup that had
 * come back with nothing and a lookup that had not come back yet both said "No
 * lyrics for this track", so every track was declared to have none for as long
 * as it took to find out that it did.
 */
@Composable
private fun LyricsSkeleton(modifier: Modifier = Modifier) {
    val sweep by rememberInfiniteTransition(label = "lyricsSkeleton").animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            tween(SKELETON_PERIOD_MS, easing = LinearEasing),
        ),
        label = "sweep",
    )
    BoxWithConstraints(
        // No gutter of its own: the list this stands in for bleeds out to the
        // panel's full width and puts the gutter back as content padding, so
        // the words land level with the panel's own edge and so does this.
        modifier.padding(top = 40.dp),
    ) {
        // Every bar sweeps against the width of the column rather than its own,
        // so one band crosses the whole page. Measured per bar, a short row
        // lights end to end in the time a long one takes to get halfway, and
        // the block reads as a row of separate things loading separately.
        val column = maxWidth
        Column(verticalArrangement = Arrangement.spacedBy(SKELETON_BLOCK_GAP)) {
            SKELETON_BLOCKS.forEach { rows ->
                Column(verticalArrangement = Arrangement.spacedBy(SKELETON_LEADING)) {
                    rows.forEach { fraction ->
                        Box(
                            Modifier
                                .fillMaxWidth(fraction)
                                .height(SKELETON_BAR)
                                .clip(RoundedCornerShape(4.dp))
                                // Read in the draw block, not the body: a
                                // pageful of these would otherwise recompose on
                                // every frame, and all any of them needs per
                                // frame is a fresh gradient.
                                .drawWithCache {
                                    val full = column.toPx()
                                    val band = full * 0.45f
                                    val startX = -band + sweep * (full + band * 2)
                                    val brush = Brush.horizontalGradient(
                                        colors = listOf(
                                            Color.White.copy(alpha = 0.10f),
                                            Color.White.copy(alpha = 0.26f),
                                            Color.White.copy(alpha = 0.10f),
                                        ),
                                        startX = startX,
                                        endX = startX + band,
                                    )
                                    onDrawBehind { drawRect(brush) }
                                },
                        )
                    }
                }
            }
        }
    }
}


/**
 * Apple Music's lyrics view: big tight type, the playing line crisp and
 * everything else falling out of focus the further it is from it. Blur needs
 * API 31+, so alpha carries the same hierarchy on older devices.
 *
 * Scrolling by hand clears the blur and suspends the auto-follow, so you can
 * read ahead; a couple of seconds after you stop it snaps back to the song.
 */
@Composable
private fun LyricsPanel(
    lines: List<LyricLine>,
    trackKey: String,
    positionMs: Long,
    /** Whether a lookup for this track is still in flight. */
    looking: Boolean,
    isPlaying: Boolean,
    onSeekToLine: (Long) -> Unit,
    onUserScroll: (Boolean) -> Unit = {},
    translationProgress: State<Float>? = null,
    modifier: Modifier = Modifier,
) {
    // The offset is applied to the clock rather than to the lines: one shift,
    // read by everything downstream — the active line, the sweep, and the
    // translated timing alike — so nothing can end up on a different clock from
    // anything else, and the auto-follow keeps following the same line the
    // sweep is lighting.
    val lyricsOffsetMs by AppSettings.lyricsOffsetMs.collectAsStateWithLifecycle()
    val clock = rememberLyricClock(positionMs + lyricsOffsetMs, isPlaying)

    // Which line is playing right now: the last one whose stamp has passed.
    //
    // Read off the frame clock rather than the player's own position, which
    // only lands twice a second. Taken from there, a line change was up to
    // half a second late — and with the highlight itself running on the frame
    // clock, that lateness was visible: the sweep would finish a line and sit
    // at the end of it, waiting for the screen to admit the next one had
    // started. derivedStateOf keeps the cost of the finer clock off
    // composition; it only notifies when the index actually changes, not on
    // every frame that feeds it.
    // Whether the source stamps its lines at all. Unsynced lyrics are a wall of
    // text to read by hand; nothing lights up, nothing scrolls itself, and the
    // panel draws section headers and stanza breaks instead of timings.
    val isSynced = remember(lines) { lines.any { it.timeMs > 0L } }
    // Only a song that actually names a second voice is laid out as one. A
    // single-voice song has every line on the left already, so splitting the
    // panel into lanes for it would just be a narrower panel.
    val duet = remember(lines) { lines.any { it.alignment == LyricAlignment.End } }
    // Every unfinished vocal kept visible, including overlaps spanning more
    // than two rows — a backing vocal routinely holds past the next line's own
    // stamp, see [LyricLine.background]. The uppermost owns the scroll anchor
    // until its end.
    val activeRows by remember(lines, isSynced) {
        derivedStateOf {
            if (!isSynced) emptyList() else activeLyricRows(lines, clock.longValue)
        }
    }
    val activeLine = activeRows.firstOrNull() ?: -1
    val listState = rememberLazyListState()
    val keepScroll = remember(listState) { keepScrollInList(listState) }
    // Tells the player's transport auto-hide whether a finger is genuinely
    // scrolling this list (down or the fling it left behind) — programmatic
    // scrolls like auto-follow never count.
    val panelScrolling = rememberPanelScrolling(listState)
    LaunchedEffect(panelScrolling) { onUserScroll(panelScrolling) }
    var browsing by remember { mutableStateOf(false) }
    val reduceDynamicBlur by AppSettings.reduceDynamicBlur.collectAsStateWithLifecycle()
    val reduceAnimation by AppSettings.reduceAnimation.collectAsStateWithLifecycle()
    val lyricsBlur by AppSettings.lyricsBlur.collectAsStateWithLifecycle()

    // The bloom is a blurred copy of the line, so it is off wherever blur is:
    // below API 31 Modifier.blur does nothing and the "glow" would land as a
    // second sharp copy of the text — fake bold, not light. Both of the
    // reduce-* settings turn it off too. Reduce animation because it is the
    // switch for exactly this kind of flourish, and reduce dynamic blur
    // because adding a blur under a setting that says it drops them would be
    // the app disagreeing with itself.
    val glowing = !reduceAnimation && !reduceDynamicBlur &&
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.S

    // Only a finger on the list counts as browsing — watching
    // isScrollInProgress would trip on our own auto-scroll.
    LaunchedEffect(listState) {
        listState.interactionSource.interactions.collect { interaction ->
            if (interaction is DragInteraction.Start) browsing = true
        }
    }

    // Hand control back as soon as the playing line is on screen again,
    // whether the user scrolled to it or the song caught up to them.
    // rememberUpdatedState matters: read plainly, the derived state would
    // capture whichever line was active when it was first created.
    val currentLine by rememberUpdatedState(activeLine)
    val activeOnScreen by remember(listState) {
        derivedStateOf {
            listState.layoutInfo.visibleItemsInfo.any { it.index == currentLine }
        }
    }
    LaunchedEffect(browsing, activeOnScreen, listState.isScrollInProgress) {
        if (browsing && activeOnScreen && !listState.isScrollInProgress) {
            delay(600)
            browsing = false
        }
    }

    // And give up browsing on its own after a while, wherever the list is.
    LaunchedEffect(browsing, listState.isScrollInProgress) {
        if (browsing && !listState.isScrollInProgress) {
            delay(5_000)
            browsing = false
        }
    }

    // Follow the song, keeping the active line a third of the way down.
    //
    // Gated on isScrollInProgress as well as browsing: browsing flips true from
    // a Flow collecting DragInteraction.Start, which lags a frame or two behind
    // the actual touch. A line change landing in that gap started this
    // animated scroll underneath a finger already dragging, and the ensuing
    // fight over the list's MutatorMutex was what leaked a stray scroll past
    // keepScrollInList and down to the sheet — reading the list's own
    // (synchronous) scroll state closes that window.
    //
    // The very first placement is a jump, not a scroll. The panel is built
    // fresh each time it is opened, so an animated scroll there is the whole
    // song racing past from the top before settling — which is where the
    // stutter on opening came from. Later moves, which are one line at a time,
    // still animate.
    var placed by remember(lines) { mutableStateOf(false) }
    LaunchedEffect(activeLine, browsing, isSynced) {
        if (isSynced && !browsing && !listState.isScrollInProgress &&
            activeLine >= 0 && activeLine in lines.indices
        ) {
            // A third of the way down the panel, whatever the panel's size — a
            // fixed pixel offset lands in a different place on every screen,
            // and on a tablet it put the playing line near the very top.
            // Measured height is 0 until the list has been laid out once,
            // which on the opening frame is exactly when this runs.
            val viewport = snapshotFlow { listState.layoutInfo.viewportSize.height }
                .first { it > 0 }
            val third = viewport / 3
            if (placed) {
                listState.animateScrollToItem(activeLine, scrollOffset = -third)
            } else {
                listState.scrollToItem(activeLine, scrollOffset = -third)
                placed = true
            }
        }
    }

    if (lines.isEmpty()) {
        // "None" is a finding, and it is only worth reporting once the lookup
        // has actually come back with it.
        if (looking) {
            LyricsSkeleton(modifier)
        } else {
            Box(modifier, contentAlignment = Alignment.Center) {
                Text(
                    text = "No lyrics for this track",
                    style = MaterialTheme.typography.titleMedium,
                    color = Color.White.copy(alpha = 0.6f),
                )
            }
        }
        return
    }

    Box(modifier) {
    LazyColumn(
        state = listState,
        modifier = Modifier
            .fillMaxSize()
            .bleedHorizontally(PLAYER_GUTTER)
            .nestedScroll(keepScroll)
            .fadingEdges(),
        // Each row carries GLOW_ROOM of its own inset for the halo, so the
        // list hands that much back — otherwise the lines would sit a glow's
        // width further apart and further in than they used to.
        contentPadding = PaddingValues(
            vertical = 40.dp - GLOW_ROOM,
            horizontal = PLAYER_GUTTER - GLOW_ROOM,
        ),
        verticalArrangement = Arrangement.spacedBy(0.dp),
    ) {
        itemsIndexed(
            items = lines,
            key = { index, line -> "${line.timeMs}_$index" },
        ) { index, line ->
            if (!isSynced && Genius.isSectionHeader(line.text)) {
                val sectionTitle = line.text.removePrefix("[").removeSuffix("]").trim()
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = if (index == 0) 6.dp else 24.dp, bottom = 8.dp)
                        .padding(horizontal = GLOW_ROOM),
                ) {
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(6.dp))
                            .background(Color.White.copy(alpha = 0.14f))
                            .padding(horizontal = 11.dp, vertical = 4.dp),
                    ) {
                        Text(
                            text = sectionTitle.uppercase(),
                            style = MaterialTheme.typography.labelMedium.copy(
                                letterSpacing = 1.3.sp,
                                fontWeight = FontWeight.Bold,
                                fontSize = 11.5.sp,
                            ),
                            color = Color.White.copy(alpha = 0.9f),
                        )
                    }
                }
                return@itemsIndexed
            }

            if (!isSynced && line.isGap) {
                Spacer(Modifier.height(14.dp))
                return@itemsIndexed
            }

            // Signed rather than absolute: a line already sung and one still to
            // come are not the same distance from being read, even at the same
            // number of rows away, so the two fade at different rates below.
            val offset = if (activeLine < 0) 0 else index - activeLine
            val distance = abs(offset)
            val isActive = isSynced && index in activeRows
            val sung = offset < 0
            // Symmetric either side of the playing line, and shallow: the two
            // rows around it stay readable so you can follow back over what was
            // just sung as well as ahead, and everything past that recedes to
            // the same floor rather than fading to nothing.
            val step = distance.coerceAtMost(LINE_FALLOFF_ALPHA.lastIndex)
            val blur by animateDpAsState(
                targetValue = when {
                    !isSynced || !lyricsBlur || reduceDynamicBlur || browsing || isActive -> 0.dp
                    else -> LINE_FALLOFF_BLUR[step]
                },
                animationSpec = tween(LYRIC_SETTLE_MS, easing = LYRIC_EASING),
                label = "lyricBlur",
            )
            // Reading by hand is not following along: the stack flattens to one
            // brightness so no row is being pointed at, and the panel stops
            // claiming to know where the song is while somebody scrolls away
            // from it.
            val lineAlpha by animateFloatAsState(
                targetValue = when {
                    !isSynced -> 0.95f
                    isActive -> 1f
                    browsing -> BROWSING_ALPHA
                    else -> LINE_FALLOFF_ALPHA[step]
                },
                animationSpec = tween(LYRIC_SETTLE_MS, easing = LYRIC_EASING),
                label = "lyricAlpha",
            )
            if (isSynced && line.isGap) {
                // A break counts itself out rather than being marked: three dots
                // lighting in turn across the interlude, so a long one reads as
                // time running down instead of a symbol parked on screen waiting
                // for the singing to come back.
                val until = lines.getOrNull(index + 1)?.timeMs ?: line.endMs
                // The row itself opens and closes with the break, so the list
                // carries no dead space through the verses either side of it —
                // which is also what stops the panel scrolling past a hole to
                // reach the next line that is actually sung.
                val swell by animateFloatAsState(
                    targetValue = if (isActive) 1f else 0f,
                    animationSpec = tween(
                        durationMillis = if (isActive) 400 else 350,
                        easing = LYRIC_EASING,
                    ),
                    label = "gapSwell",
                )
                Box(
                    modifier = Modifier
                        .height((GAP_ROW_HEIGHT + GAP_ROW_SPACING) * swell)
                        .clipToBounds(),
                ) {
                    Box(
                        modifier = Modifier
                            .then(if (blur > 0.dp && !reduceDynamicBlur) Modifier.blur(blur, BlurredEdgeTreatment.Unbounded) else Modifier)
                            .clip(RoundedCornerShape(10.dp))
                            .clickable { onSeekToLine(line.timeMs) }
                            // Matches the inset every sung line carries, so the
                            // rhythm of the list doesn't break at a break.
                            .padding(GLOW_ROOM)
                            .size(
                                width = GAP_DOT_SIZE * GAP_DOTS + GAP_DOT_GAP * (GAP_DOTS - 1),
                                height = GAP_DOT_SIZE,
                            )
                            .graphicsLayer {
                                val grow = GAP_REST_SCALE + (1f - GAP_REST_SCALE) * swell
                                scaleX = grow
                                scaleY = grow
                                transformOrigin = TransformOrigin(0f, 0.5f)
                                alpha = lineAlpha * swell
                            }
                            .drawBehind {
                                // Read here rather than in composition: the fill
                                // moves every frame, and this way a break costs
                                // a redraw of three circles, not a recomposition.
                                val span = (until - line.timeMs).coerceAtLeast(1L)
                                val through = ((clock.longValue - line.timeMs).toFloat() / span)
                                    .coerceIn(0f, 1f)
                                val radius = GAP_DOT_SIZE.toPx() / 2f
                                val stride = (GAP_DOT_SIZE + GAP_DOT_GAP).toPx()
                                repeat(GAP_DOTS) { dot ->
                                    // Each dot owns its share of the break and
                                    // fills across it, so they light left to
                                    // right.
                                    val lit = (through * GAP_DOTS - dot).coerceIn(0f, 1f)
                                    drawCircle(
                                        color = Color.White.copy(
                                            alpha = GAP_DOT_REST + (1f - GAP_DOT_REST) * lit,
                                        ),
                                        radius = radius,
                                        center = Offset(radius + dot * stride, size.height / 2f),
                                    )
                                }
                            },
                    )
                }
            } else {
                // Held so a press can dip the row without recomposing the list:
                // one interaction source per row, read in the draw phase by the
                // graphics layer above.
                val interaction = remember { MutableInteractionSource() }
                val pressed by interaction.collectIsPressedAsState()
                // Sized and weighted to be read at a glance from a phone held at
                // arm's length: extra-bold, and large enough that the sweep's
                // leading edge is legible while it travels. The line height
                // leaves room for descenders at this weight so two rows never
                // touch.
                val style = if (isSynced) {
                    MaterialTheme.typography.headlineLarge.copy(
                        fontSize = 34.sp,
                        lineHeight = 41.sp,
                        fontWeight = FontWeight.ExtraBold,
                        textAlign = if (duet && line.alignment == LyricAlignment.End) {
                            TextAlign.End
                        } else {
                            TextAlign.Start
                        },
                    )
                } else {
                    MaterialTheme.typography.headlineMedium.copy(
                        fontSize = 30.sp,
                        lineHeight = 38.sp,
                        fontWeight = FontWeight.ExtraBold,
                    )
                }
                // The stack sits fractionally back and the playing line comes
                // forward to meet you, rather than the playing line swelling past
                // the others — a smaller move, and one that doesn't push the type
                // around the line it hands over to. A row under a finger dips,
                // the way a button does. Anchored to the left edge, so the words
                // don't slide sideways under the highlight; scaling about the
                // centre would fight the sweep.
                val alignEnd = duet && line.alignment == LyricAlignment.End
                val scale by animateFloatAsState(
                    targetValue = when {
                        pressed -> PRESSED_LYRIC_SCALE
                        isActive -> 1f
                        else -> INACTIVE_LYRIC_SCALE
                    },
                    label = "lyricScale",
                )
                // Apple's bloom on the line being sung. Fades in and out with
                // the line rather than switching, so a handover is one line's
                // light going down as the next one's comes up.
                val glow by animateFloatAsState(
                    targetValue = if (isActive && glowing) GLOW_ALPHA else 0f,
                    animationSpec = tween(durationMillis = 420),
                    label = "lyricGlow",
                )
                val shape = Modifier
                    .fillMaxWidth()
                    // The lane the other voice sings in, kept clear. Applied
                    // before the layer below so the row scales about the edge
                    // it is actually written from.
                    .padding(
                        start = if (alignEnd) DUET_LANE else 0.dp,
                        end = if (duet && !alignEnd) DUET_LANE else 0.dp,
                    )
                    .graphicsLayer {
                        scaleX = scale
                        scaleY = scale
                        transformOrigin = TransformOrigin(if (alignEnd) 1f else 0f, 0.5f)
                        alpha = lineAlpha
                    }
                    .then(if (blur > 0.dp && !reduceDynamicBlur) Modifier.blur(blur, BlurredEdgeTreatment.Unbounded) else Modifier)
                    .clip(RoundedCornerShape(10.dp))
                    .clickable(enabled = isSynced, interactionSource = interaction, indication = null) {
                        onSeekToLine(line.timeMs)
                    }
                // Lead and answering vocal are one row: they are one line of
                // the song, they scale and dim together, and tapping either
                // seeks to the same place.
                Column(modifier = shape) {
                    PanelVoice(
                        line = line,
                        clock = clock,
                        style = style,
                        isActive = isActive,
                        sung = sung,
                        synced = isSynced,
                        browsing = browsing,
                        glowAlpha = glow,
                        room = GLOW_ROOM,
                        alignEnd = alignEnd,
                        // Only the rows actually in front of the reader get the
                        // particle pass. Sixty rows' worth of glyph boxes is a
                        // layout walk per frame for text nobody is looking at.
                        translationProgress = translationProgress.takeIf {
                            if (isSynced) abs(index - activeLine) <= 1 else index < 4
                        },
                        modifier = Modifier.fillMaxWidth(),
                    )
                    line.background?.let { backing ->
                        PanelVoice(
                            line = backing.withoutBracketPunctuation(),
                            clock = clock,
                            style = style.copy(
                                fontSize = BACKING_FONT_SIZE,
                                lineHeight = BACKING_LINE_HEIGHT,
                            ),
                            isActive = isActive,
                            sung = sung,
                            synced = isSynced,
                            browsing = browsing,
                            // No bloom on the second voice. The glow marks
                            // what is being sung *at you*; putting it on both
                            // makes the row read as two equal lines, which is
                            // the thing this split exists to stop.
                            glowAlpha = 0f,
                            room = 0.dp,
                            alignEnd = alignEnd,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(start = GLOW_ROOM, end = GLOW_ROOM, bottom = GLOW_ROOM)
                                .graphicsLayer { alpha = BACKING_ALPHA },
                        )
                    }
                }
            }
        }
        }
    }
}

/**
 * The translate button in the lyrics panel's corner.
 *
 * Four states rather than the usual two: idle, a request in flight — which
 * takes the icon's place rather than sitting beside it — a translation on
 * screen, and a track whose lyrics are already in the language asked for, which
 * dims to say the button has nothing left to do.
 */
@Composable
private fun TranslationToggleButton(
    state: LyricsTranslationUiState,
    showingTranslation: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val active = showingTranslation || state is LyricsTranslationUiState.Loading
    val tint = when {
        state is LyricsTranslationUiState.SameLanguage -> Color.White.copy(alpha = 0.42f)
        active -> Color.White
        else -> Color.White.copy(alpha = 0.78f)
    }
    val discAlpha by animateFloatAsState(
        targetValue = if (active) 0.34f else 0.18f,
        label = "translateDisc",
    )
    Box(
        modifier = modifier
            .size(34.dp)
            .clip(CircleShape)
            .background(Color.White.copy(alpha = discAlpha))
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick,
            ),
        contentAlignment = Alignment.Center,
    ) {
        if (state is LyricsTranslationUiState.Loading) {
            CircularProgressIndicator(
                modifier = Modifier.size(16.dp),
                color = tint,
                strokeWidth = 1.7.dp,
            )
        } else {
            Icon(
                imageVector = Icons.Rounded.Translate,
                contentDescription = if (showingTranslation) "Show original lyrics" else "Translate lyrics",
                tint = tint,
                modifier = Modifier.size(19.dp),
            )
        }
    }
}

@Composable
private fun PanelVoice(
    line: LyricLine,
    clock: MutableLongState,
    style: TextStyle,
    isActive: Boolean,
    /** Whether the panel has already left this line behind. */
    sung: Boolean,
    /** Whether the source stamps its lines at all. */
    synced: Boolean,
    browsing: Boolean,
    glowAlpha: Float,
    room: Dp,
    /** Whether this line is one of the right-hand voice's; see [LyricAlignment]. */
    alignEnd: Boolean,
    translationProgress: State<Float>? = null,
    modifier: Modifier = Modifier,
) {
    if (line.isWordSynced && !browsing) {
        // Every word-synced line goes through the sweep, not just the playing
        // one — a line that has already been sung is fully revealed and one
        // still to come is not, which falls out of the same arithmetic.
        //
        // Running it only on the active line meant swapping this composable
        // for a plain Text the instant a line handed over, and the two
        // disagreed about the brightness of the words: the tail of the line
        // popped up to meet the rest of it in a single frame. Animating the
        // tail instead lets a finished line close up as it dims away.
        val tail by animateFloatAsState(
            targetValue = if (sung) 1f else UNSUNG_ALPHA,
            label = "lyricTail",
        )
        SweptLyricLine(
            line = line,
            clock = clock,
            style = style,
            dimAlpha = tail,
            modifier = modifier,
            glowAlpha = glowAlpha,
            glowRoom = room,
            feather = isActive,
            alignEnd = alignEnd,
            translationProgress = translationProgress,
        )
    } else if (line.isWordSynced) {
        // Browsing: keep the sweep so sung lines stay fully lit and unsung
        // ones stay dim, but skip the bloom — it is a playback flourish, not
        // a browsing aid. Non-active lines get the same dim tail as when we
        // are not browsing; the active line stays at full brightness.
        val tail by animateFloatAsState(
            targetValue = if (sung) 1f else UNSUNG_ALPHA,
            label = "lyricTail",
        )
        SweptLyricLine(
            line = line,
            clock = clock,
            style = style,
            dimAlpha = tail,
            modifier = modifier,
            glowAlpha = 0f,
            glowRoom = room,
            alignEnd = alignEnd,
            translationProgress = translationProgress,
        )
    } else {
        // No word timings, so there is no sweep to light the words as they are
        // sung: the line lights whole, the moment it starts.
        //
        // It still has to hold itself back until then. The parent's falloff
        // alone left a line not yet sung reading brighter here than the same
        // line does on a word-synced source, where the unsung words sit at
        // [UNSUNG_ALPHA] underneath it — the two have to agree about what "not
        // yet" looks like, or changing provider changes the panel rather than
        // the words. Lyrics with no timing at all are all "now", and stay lit.
        val lit by animateFloatAsState(
            targetValue = if (!synced || sung || isActive) 1f else UNSUNG_ALPHA,
            label = "lyricLit",
        )
        var layout by remember(line.text) { mutableStateOf<TextLayoutResult?>(null) }
        Text(
            text = line.text,
            style = style,
            color = Color.White.copy(alpha = lit),
            onTextLayout = { layout = it },
            modifier = modifier.lyricParticles(layout, translationProgress, room).padding(room),
        )
    }
}

private fun LyricLine.withoutBracketPunctuation(): LyricLine = copy(
    text = text.stripParens(),
    words = words.mapNotNull { word ->
        word.text.stripParens().takeIf { it.isNotEmpty() }?.let { word.copy(text = it) }
    },
)

private fun String.stripParens(): String = replace("(", "").replace(")", "").trim()


/**
 * The single lyric line above the scrubber.
 *
 * A line dims away just before its time is up and the next one arrives at full
 * strength — no fade in, so the change reads as a cut rather than a dissolve.
 * The fade is a fraction of the line's own length, so rapid-fire lines snap and
 * long held ones ebb out.
 *
 * Position is interpolated between the player's twice-a-second reports,
 * otherwise the fade would step. The alpha is applied in a graphicsLayer so
 * only the draw phase runs each frame; the text itself recomposes just once
 * per line.
 */
@Composable
private fun CurrentLyricLine(
    lines: List<LyricLine>,
    trackKey: Any,
    positionMs: Long,
    isPlaying: Boolean,
    durationMs: Long,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val isSynced = remember(lines) { lines.any { it.timeMs > 0L } }
    if (!isSynced) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = modifier
                .clip(RoundedCornerShape(8.dp))
                .clickable(onClick = onClick)
                .padding(vertical = 4.dp),
        ) {
            Icon(
                imageVector = VelthyIcons.MusicNote,
                contentDescription = null,
                tint = Color.White,
                modifier = Modifier.size(16.dp),
            )
            Spacer(Modifier.width(6.dp))
            Text(
                text = "Open lyrics",
                style = MaterialTheme.typography.titleMedium,
                color = Color.White,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f, fill = false),
            )
            Spacer(Modifier.width(6.dp))
            Icon(
                imageVector = VelthyIcons.ChevronRight,
                contentDescription = null,
                tint = Color.White.copy(alpha = 0.5f),
                modifier = Modifier.size(14.dp),
            )
        }
        return
    }

    val clock = rememberLyricClock(positionMs, isPlaying)

    val index by remember(lines) {
        derivedStateOf { lines.indexOfLast { it.timeMs <= clock.longValue } }
    }
    val current = lines.getOrNull(index)
    // Before the first line, and through instrumental breaks, show the note.
    val instrumental = current == null || current.isGap
    // Everything ahead of the first sung line is the intro — LRC files open on a
    // bare [00:00.00] gap, so that stretch is gap lines rather than nothing.
    val firstSung = remember(lines) { lines.indexOfFirst { !it.isGap } }
    val intro = instrumental && firstSung >= 0 && index < firstSung
    // The intro gets one of the slang lines; mid-song breaks stay plain.
    val introLine = remember(trackKey) { INTRO_LINES.random() }
    // The strip is one line and switches the moment the next one is due, so
    // the answering vocal — where there is one — has nowhere to go: showing it
    // would mean either cutting it short when the next line arrives or holding
    // the strip back. [LyricsPanel] has the room to draw it properly.
    val text = when {
        intro -> introLine
        instrumental -> INSTRUMENTAL_MARK
        else -> current.text
    }

    val reduceAnimation by AppSettings.reduceAnimation.collectAsStateWithLifecycle()

    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier
            .clip(RoundedCornerShape(8.dp))
            .clickable(onClick = onClick)
            .padding(vertical = 4.dp),
    ) {
        if (instrumental) {
            Icon(
                imageVector = VelthyIcons.MusicNote,
                contentDescription = null,
                tint = Color.White,
                modifier = Modifier.size(16.dp),
            )
            Spacer(Modifier.width(6.dp))
        }
        // A line change slides in from below and out above while it fades, so
        // the strip reads as a handover rather than a cut. The playback clock
        // is untouched: only the text swaps.
        AnimatedContent(
            targetState = Triple(index, current, text),
            transitionSpec = {
                val duration = if (reduceAnimation) 0 else 340
                if (reduceAnimation) {
                    (fadeIn(snap()) togetherWith fadeOut(snap())).using(
                        SizeTransform(clip = false, sizeAnimationSpec = { _, _ -> snap() }),
                    )
                } else {
                    (
                        fadeIn(animationSpec = tween(duration, easing = FastOutSlowInEasing)) +
                            slideInVertically(
                                animationSpec = tween(duration, easing = FastOutSlowInEasing),
                            ) { height -> (height * 0.35f).toInt() }
                        ).togetherWith(
                        fadeOut(animationSpec = tween(duration, easing = FastOutSlowInEasing)) +
                            slideOutVertically(
                                animationSpec = tween(duration, easing = FastOutSlowInEasing),
                            ) { height -> -(height * 0.35f).toInt() },
                    ).using(
                        SizeTransform(
                            clip = false,
                            sizeAnimationSpec = { _, _ ->
                                tween(duration, easing = FastOutSlowInEasing)
                            },
                        ),
                    )
                }
            },
            label = "currentLyricTransition",
            modifier = Modifier.weight(1f, fill = false),
        ) { (_, lineItem, lineText) ->
            val itemInstrumental = lineItem == null || lineItem.isGap
            val swept = lineItem?.takeIf { !itemInstrumental && it.isWordSynced }
            if (swept != null) {
                SweptLyricLine(
                    line = swept,
                    clock = clock,
                    style = MaterialTheme.typography.titleMedium,
                    dimAlpha = UNSUNG_ALPHA_STRIP,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    // No rise on a single line: it has nothing to rise away
                    // from and would read as the strip itself twitching.
                    rise = false,
                )
            } else {
                Text(
                    text = lineText,
                    style = MaterialTheme.typography.titleMedium,
                    color = if (itemInstrumental) Color.White.copy(alpha = 0.5f) else Color.White,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        Spacer(Modifier.width(6.dp))
        // Disclosure hint: this strip opens the full lyrics screen.
        Icon(
            imageVector = VelthyIcons.ChevronRight,
            contentDescription = null,
            tint = Color.White.copy(alpha = 0.5f),
            modifier = Modifier.size(14.dp),
        )
    }
}

/**
 * Stands in for [CurrentLyricLine] once a lookup has come back empty — shown
 * for a few seconds so it registers, then left to fade rather than snapping
 * out or lingering for the rest of the track.
 */
@Composable
private fun LyricsUnavailableLine(trackKey: Any, modifier: Modifier = Modifier) {
    var visible by remember(trackKey) { mutableStateOf(true) }
    LaunchedEffect(trackKey) {
        delay(LYRICS_UNAVAILABLE_HOLD_MS)
        visible = false
    }
    val alpha by animateFloatAsState(
        targetValue = if (visible) 0.55f else 0f,
        animationSpec = tween(durationMillis = LYRICS_UNAVAILABLE_FADE_MS),
        label = "lyricsUnavailableAlpha",
    )
    Text(
        text = "Lyrics not available",
        style = MaterialTheme.typography.titleMedium,
        color = Color.White,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        modifier = modifier
            .padding(vertical = 4.dp)
            .graphicsLayer { this.alpha = alpha },
    )
}

/** Stands in for [CurrentLyricLine] while a lookup is still in flight. */
@Composable
private fun LyricsLoadingLine(trackKey: Any, modifier: Modifier = Modifier) {
    val text = remember(trackKey) { LYRICS_LOADING_LINES.random() }
    Text(
        text = text,
        style = MaterialTheme.typography.titleMedium,
        color = Color.White.copy(alpha = 0.55f),
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        modifier = modifier.padding(vertical = 4.dp),
    )
}

/**
 * Translucent circular button used for the track menu and the like control.
 *
 * [active] brightens the disc rather than only the glyph: this sits on album
 * artwork of any colour, and a white icon on a white-ish sleeve has no tint
 * change left to make. The filled heart carries the state as a shape too —
 * see [VelthyIcons.HeartFilled].
 */
@Composable
private fun CircleGlyph(
    icon: ImageVector,
    contentDescription: String,
    onClick: () -> Unit,
    active: Boolean = false,
) {
    val discAlpha by animateFloatAsState(
        targetValue = if (active) 0.34f else 0.18f,
        label = "glyphDisc",
    )
    Box(
        modifier = Modifier
            .size(34.dp)
            .clip(CircleShape)
            .background(Color.White.copy(alpha = discAlpha))
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick,
            ),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = contentDescription,
            tint = Color.White,
            modifier = Modifier.size(19.dp),
        )
    }
}

/**
 * Transport / bottom glyphs. The circular clip belongs on the touch target,
 * never on the [Icon] — clipping the icon itself shaves the corners off wide
 * glyphs like fast-forward and the queue list.
 */
@Composable
private fun TransportGlyph(
    icon: ImageVector,
    contentDescription: String,
    size: androidx.compose.ui.unit.Dp,
    onClick: () -> Unit,
    enabled: Boolean = true,
) {
    // Faded rather than hidden: the row keeps its shape at the ends of a queue.
    val alpha by animateFloatAsState(
        targetValue = if (enabled) 1f else 0.3f,
        label = "transportAlpha",
    )
    Box(
        modifier = Modifier
            .size(size + 12.dp)
            .clip(CircleShape)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                enabled = enabled,
                onClick = onClick,
            ),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = contentDescription,
            tint = Color.White.copy(alpha = alpha),
            modifier = Modifier.size(size),
        )
    }
}

/**
 * A row of controls joined into one capsule.
 *
 * The join is a hairline rather than a gap, which is what makes several
 * controls read as a single object — the shape the player uses for a set of
 * choices that all answer the same question: where the sound is going.
 */
@Composable
private fun Pill(content: @Composable RowScope.() -> Unit) {
    Row(
        modifier = Modifier
            .height(BOTTOM_ACTION_SIZE)
            .clip(CircleShape)
            .background(Color.White.copy(alpha = 0.12f)),
        verticalAlignment = Alignment.CenterVertically,
        content = content,
    )
}

@Composable
private fun PillDivider() {
    Box(
        Modifier
            .width(1.dp)
            .height(20.dp)
            .background(Color.White.copy(alpha = 0.20f)),
    )
}

/**
 * The two ends of "where is this playing": the output capsule.
 *
 * Both halves answer the same question and so belong to one control rather than
 * two glyphs that happen to sit side by side — headphones for which speaker the
 * sound leaves by, the person for which *people* it reaches.
 *
 * The halves are the same width in every state, party or no party, so the
 * capsule never resizes under the finger.
 */
@Composable
private fun OutputPartyPill(
    onSleep: () -> Unit,
    onOutput: () -> Unit,
    onParty: () -> Unit,
    inParty: Boolean = false,
    /** The sleep timer's remaining time, or null when none is running. */
    sleepBadge: String? = null,
) {
    // Exactly [BOTTOM_ACTION_SIZE] tall, the same as a [BottomGlyph]'s own
    // square, so the three items of the row share one top edge and one height.
    // The capsule is wider than a glyph by design — it is three controls — and
    // that width is what [pillWidth] accounts for in the row's inset.
    //
    // Three segments rather than three separate glyphs because all three answer
    // the same question — *what is happening around this song that is not the
    // song* — and one capsule says so without a gap inviting the eye to read
    // them as unrelated.
    Box(
        modifier = Modifier.height(BOTTOM_ACTION_SIZE),
        contentAlignment = Alignment.Center,
    ) {
        Pill {
            PillSegment(
                icon = VelthyIcons.Moon,
                contentDescription = "Sleep timer",
                onClick = onSleep,
                highlighted = sleepBadge != null,
                badgeText = sleepBadge,
            )
            PillDivider()
            PillSegment(
                icon = Icons.Rounded.Headphones,
                iconSize = PILL_HEADPHONES_SIZE,
                contentDescription = "Audio output",
                onClick = onOutput,
            )
            PillDivider()
            PillSegment(
                icon = Icons.Rounded.Person,
                iconSize = PILL_PARTY_SIZE,
                contentDescription = if (inParty) "Listening together — open party" else "Listen together",
                onClick = onParty,
                highlighted = inParty,
            )
        }
    }
}

/**
 * One control inside a [Pill] — [BottomGlyph]'s twin, squared off.
 *
 * Same behaviour down to the tap window, and deliberately not the same
 * composable: a glyph's highlight is a circle sized to itself, and a segment's
 * has to fill its share of the capsule edge to edge or the join stops reading
 * as one.
 */
@Composable
private fun PillSegment(
    contentDescription: String,
    onClick: () -> Unit,
    icon: ImageVector? = null,
    iconSize: Dp = 24.dp,
    label: String? = null,
    highlighted: Boolean = false,
    /** A short count drawn in the corner — the sleep timer's remaining minutes. */
    badgeText: String? = null,
) {
    val haptics = rememberHaptics()
    Box(
        modifier = Modifier
            .width(PILL_SEGMENT_WIDTH)
            .height(BOTTOM_ACTION_SIZE)
            .background(if (highlighted) Color.White.copy(alpha = 0.14f) else Color.Transparent)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
            ) {
                haptics.play(Haptic.Tap)
                onClick()
            }
            .semantics { this.contentDescription = contentDescription },
        contentAlignment = Alignment.Center,
    ) {
        val tint = Color.White.copy(alpha = if (highlighted) 1f else 0.75f)
        if (icon != null) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = tint,
                modifier = Modifier.size(iconSize),
            )
        } else if (label != null) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelMedium,
                color = tint,
                maxLines = 1,
            )
        }
        // Inside the segment's own bounds, for the same reason the glyph's badge
        // is: an offset that hangs past the edge lands on the segment next door,
        // and in a capsule the neighbours are joined with no gap at all.
        if (badgeText != null) {
            Box(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .background(Color.White, RoundedCornerShape(8.dp))
                    .widthIn(min = 18.dp)
                    .padding(horizontal = 4.dp, vertical = 1.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = badgeText,
                    style = MaterialTheme.typography.labelSmall.copy(
                        fontSize = 8.5.sp,
                        fontWeight = FontWeight.Bold,
                        lineHeight = 10.sp,
                    ),
                    color = Color.Black,
                    maxLines = 1,
                )
            }
        }
    }
}

/**
 * The queue's own three settings, in the slot the output capsule occupies while
 * the artwork is showing: shuffle, repeat, and AutoPlay.
 *
 * Same three segments as [OutputPartyPill] so the capsule never changes width
 * between the two states — which is what keeps the lyrics and queue glyphs
 * either side of it from shifting when the panel opens.
 */
@Composable
private fun QueueModesPill(
    shuffleEnabled: Boolean,
    repeatMode: Int,
    autoplayEnabled: Boolean,
    onShuffle: () -> Unit,
    onRepeat: () -> Unit,
    onAutoplay: () -> Unit,
) {
    Box(
        modifier = Modifier.height(BOTTOM_ACTION_SIZE),
        contentAlignment = Alignment.Center,
    ) {
        Pill {
            PillSegment(
                icon = VelthyIcons.Shuffle,
                contentDescription = if (shuffleEnabled) "Shuffle on" else "Shuffle off",
                onClick = onShuffle,
                highlighted = shuffleEnabled,
            )
            PillDivider()
            PillSegment(
                icon = if (repeatMode == Player.REPEAT_MODE_ONE) VelthyIcons.RepeatOne else VelthyIcons.Repeat,
                contentDescription = when (repeatMode) {
                    Player.REPEAT_MODE_ONE -> "Repeat one"
                    Player.REPEAT_MODE_ALL -> "Repeat all"
                    else -> "Repeat off"
                },
                onClick = onRepeat,
                highlighted = repeatMode != Player.REPEAT_MODE_OFF,
            )
            PillDivider()
            PillSegment(
                icon = VelthyIcons.Infinity,
                contentDescription = if (autoplayEnabled) "Autoplay on" else "Autoplay off",
                onClick = onAutoplay,
                highlighted = autoplayEnabled,
            )
        }
    }
}

@Composable
private fun BottomGlyph(
    icon: ImageVector,
    contentDescription: String,
    onClick: () -> Unit,
    highlighted: Boolean = false,
    /** A short count drawn in the corner - the sleep timer's remaining minutes. */
    badgeText: String? = null,
) {
    // The whole control is one [BOTTOM_ACTION_SIZE] square — the same square a
    // capsule segment is — so the row's gaps land where its maths says they do
    // and every item's *visual* centre is its own centre. A wider column with a
    // narrower circle inside it (which this used to be) put a band of empty
    // space on either side of every glyph, and those bands are what made the
    // spacing read as uneven once the capsule was added beside them.
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.width(BOTTOM_ACTION_SIZE),
    ) {
        Box(
            modifier = Modifier.size(BOTTOM_ACTION_SIZE),
            contentAlignment = Alignment.Center,
        ) {
            // Background & click ripple clipped to circle
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .clip(CircleShape)
                    .background(
                        if (highlighted) Color.White.copy(alpha = 0.20f) else Color.Transparent,
                    )
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = onClick,
                    ),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = contentDescription,
                    tint = Color.White.copy(alpha = if (highlighted) 1f else 0.75f),
                    modifier = Modifier.size(24.dp),
                )
            }

            // Held *inside* the square rather than allowed to hang off it: a
            // badge offset past the edge lands on the next control along, which
            // is the overlap this row used to have as soon as the sleep timer
            // was counting. Pinned to the top-end corner with a hair of inset,
            // it stays within its own hit area at every size.
            if (badgeText != null) {
                Box(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .background(Color.White, RoundedCornerShape(8.dp))
                        .widthIn(min = 18.dp)
                        .padding(horizontal = 4.dp, vertical = 1.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = badgeText,
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontSize = 8.5.sp,
                            fontWeight = FontWeight.Bold,
                            lineHeight = 10.sp,
                        ),
                        color = Color.Black,
                        maxLines = 1,
                    )
                }
            }
        }
    }
}

/**
 * Swallows whatever scroll the queue list itself didn't use. The player is a
 * ModalBottomSheet, and the sheet's own nested-scroll handler reads that
 * leftover as "drag me down" — so scrolling the queue would slide the player
 * away. Consuming it here keeps the gesture inside the list.
 *
 * A downward *fling* has to be caught in the pre-phase, before the sheet sees
 * it, but only at the top of the list — otherwise the queue could never fling.
 */
private fun keepScrollInList(listState: LazyListState) = object : NestedScrollConnection {
    override fun onPostScroll(
        consumed: Offset,
        available: Offset,
        source: NestedScrollSource,
    ): Offset = available

    override suspend fun onPreFling(available: Velocity): Velocity =
        if (available.y > 0f && !listState.canScrollBackward) available else Velocity.Zero

    override suspend fun onPostFling(consumed: Velocity, available: Velocity): Velocity = available
}

/**
 * True while a finger is scrolling [listState] — while it is down, and for the
 * momentum fling it leaves behind.
 *
 * Programmatic scrolls never count: the lyrics auto-follow and the queue
 * settling on the current track move the same list, and they must not duck the
 * transport, so only a real drag (DragInteraction.Start) raises this.
 *
 * It stays true for a beat after the list rests ([PANEL_REST_MS]), so a short
 * pause mid-browse doesn't flash the controls in and out; letting go after
 * that stillness is what brings the transport back.
 */
@Composable
private fun rememberPanelScrolling(listState: LazyListState): Boolean {
    var dragging by remember { mutableStateOf(false) }
    var scrolling by remember { mutableStateOf(false) }

    LaunchedEffect(listState) {
        listState.interactionSource.interactions.collect { interaction ->
            when (interaction) {
                is DragInteraction.Start -> {
                    dragging = true
                    scrolling = true
                }
                is DragInteraction.Stop -> dragging = false
                is DragInteraction.Cancel -> {
                    dragging = false
                    scrolling = false
                }
                else -> Unit
            }
        }
    }

    LaunchedEffect(listState) {
        snapshotFlow { Triple(scrolling, dragging, listState.isScrollInProgress) }
            .collect { (stillScrolling, fingerDown, moving) ->
                // A drag has lifted and the list is no longer moving — neither
                // on its own momentum nor a follow-on (auto) scroll. Wait a
                // beat, then, if nothing has grabbed it again, it is truly at
                // rest and the controls can come back.
                if (stillScrolling && !fingerDown && !moving) {
                    delay(PANEL_REST_MS)
                    if (scrolling && !dragging && !listState.isScrollInProgress) {
                        scrolling = false
                    }
                }
            }
    }
    return scrolling
}

/** A credit that links somewhere, when [browseId] is known. */
private fun Modifier.opensPage(browseId: String?, onOpen: (String) -> Unit): Modifier =
    if (browseId == null) {
        this
    } else {
        clip(RoundedCornerShape(6.dp)).clickable { onOpen(browseId) }
    }

/**
 * Measure a child wider than its slot by [gutter] on each side and place it back
 * over that margin, still reporting the original width to the parent.
 *
 * The lists are the only things in the player you can scroll, and the side
 * padding left a strip of bare sheet down each edge. A finger that drifted into
 * one scrolled nothing and closed the player instead. Matching content padding
 * puts every row back exactly where it was drawn, so this is invisible.
 */
private fun Modifier.bleedHorizontally(gutter: Dp): Modifier = layout { measurable, constraints ->
    val extra = gutter.roundToPx() * 2
    val widened = if (constraints.hasBoundedWidth) {
        constraints.copy(
            minWidth = constraints.minWidth + extra,
            maxWidth = constraints.maxWidth + extra,
        )
    } else {
        constraints
    }
    val placeable = measurable.measure(widened)
    val width = (placeable.width - extra).coerceAtLeast(0)
    layout(width, placeable.height) {
        placeable.place(-(placeable.width - width) / 2, 0)
    }
}

/** Softens the list where it meets the header and the scrubber. */
private fun Modifier.fadingEdges(): Modifier = this
    .graphicsLayer(compositingStrategy = CompositingStrategy.Offscreen)
    .drawWithContent {
        drawContent()
        val fade = 28.dp.toPx()
        drawRect(
            brush = Brush.verticalGradient(
                colors = listOf(Color.Transparent, Color.Black),
                startY = 0f,
                endY = fade,
            ),
            blendMode = BlendMode.DstIn,
        )
        drawRect(
            brush = Brush.verticalGradient(
                colors = listOf(Color.Black, Color.Transparent),
                startY = size.height - fade,
                endY = size.height,
            ),
            blendMode = BlendMode.DstIn,
        )
    }

/** The live queue, in the player itself. */
@Composable
private fun InlineQueue(
    queue: List<Song>,
    currentIndex: Int,
    /** Only for the AutoPlay heading — whether the section is drawn at all. */
    autoplayEnabled: Boolean,
    onJumpTo: (Int) -> Unit,
    onRemove: (Int) -> Unit,
    onMove: (Int, Int) -> Unit,
    onClear: () -> Unit,
    onUserScroll: (Boolean) -> Unit = {},
    modifier: Modifier = Modifier,
) {
    val listState = rememberLazyListState()
    val keepScroll = remember(listState) { keepScrollInList(listState) }
    // Tells the player's transport auto-hide whether a finger is genuinely
    // scrolling this list (down or the fling it left behind) — programmatic
    // scrolls like auto-follow never count.
    val panelScrolling = rememberPanelScrolling(listState)
    LaunchedEffect(panelScrolling) { onUserScroll(panelScrolling) }
    // Where AutoPlay's tracks start. The queue is kept with them last, so this
    // is one boundary rather than a category to test row by row.
    val autoplayStart = remember(queue, currentIndex) {
        autoplaySectionStart(queue.map { it.fromAutoplay }, currentIndex)
    }
    // Open on what's playing, not at the top of a long queue. The heading sits
    // between the two sections, so it counts as a row once it's above this one.
    LaunchedEffect(currentIndex) {
        if (currentIndex in queue.indices) {
            listState.scrollToItem(currentIndex + if (currentIndex >= autoplayStart) 1 else 0)
        }
    }

    val manualRows = queue.subList(0, autoplayStart)
    val autoplayRows = queue.subList(autoplayStart, queue.size)
    val manualKeys = remember(manualRows) { manualRows.stableQueueKeys() }
    val autoplayKeys = remember(autoplayRows) { autoplayRows.stableQueueKeys("autoplay/") }

    val headingShown = autoplayEnabled || autoplayStart < queue.size
    val headingCount = if (headingShown) 1 else 0
    val firstMovable = (currentIndex + 1).coerceIn(0, autoplayStart)
    val manualDrag = rememberQueueDragState(
        listState = listState,
        lazyRange = firstMovable until autoplayStart,
        lazyOffset = 0,
        onMove = onMove,
    )
    val autoplayDrag = rememberQueueDragState(
        listState = listState,
        lazyRange = (autoplayStart + headingCount) until (autoplayStart + headingCount + autoplayRows.size),
        lazyOffset = headingCount,
        onMove = onMove,
    )

    Column(modifier.fillMaxWidth()) {
        // Shuffle, repeat and AutoPlay used to live here as a row of pills.
        // They now share the player's capsule, which swaps to them the moment
        // the queue opens â€” see QueueModesPill. Keeping both would have been
        // two controls for one setting, side by side.
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 6.dp),
            horizontalArrangement = Arrangement.End,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // Clear keeps its place here rather than moving up: it acts on the
            // *list* (it empties what is below it), while the capsule's three
            // govern how the list *plays*. Different subjects, different homes.
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(16.dp))
                    .background(Color.White.copy(alpha = 0.10f))
                    .clickable(onClick = onClear)
                    .padding(horizontal = 10.dp, vertical = 4.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = "Clear",
                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold, fontSize = 11.sp),
                    color = Color.White.copy(alpha = 0.75f),
                )
            }
        }
        Spacer(Modifier.height(2.dp))
        LazyColumn(
            state = listState,
            modifier = Modifier
                .fillMaxWidth()
                .bleedHorizontally(PLAYER_GUTTER)
                // Without this the sheet treats the list's leftover scroll as a
                // drag on itself and slides the whole player away.
                .nestedScroll(keepScroll)
                .fadingEdges(),
            contentPadding = PaddingValues(horizontal = PLAYER_GUTTER),
        ) {
            // What was asked for: the album, playlist or station the queue was
            // started from, plus anything queued by hand since.
            itemsIndexed(
                items = manualRows,
                key = { index, _ -> manualKeys[index] },
            ) { index, song ->
                val key = manualKeys[index]
                val dragging = manualDrag.draggedKey == key
                InlineQueueRow(
                    song = song,
                    isCurrent = index == currentIndex,
                    onClick = { onJumpTo(index) },
                    onRemove = { onRemove(index) },
                    // Only what's still queued ahead. The playing track and
                    // everything already played sit above the line a drag
                    // can't cross.
                    draggable = index >= firstMovable,
                    dragging = dragging,
                    onDragStart = { manualDrag.onDragStart(key) },
                    onDrag = manualDrag::onDrag,
                    onDragEnd = manualDrag::onDragEnd,
                    modifier = Modifier
                        .zIndex(if (dragging) 1f else 0f)
                        .graphicsLayer { translationY = if (dragging) manualDrag.dragOffset else 0f }
                        // The dragged row follows the finger, so it is the one
                        // row that must not also be animating to a slot.
                        .then(if (dragging) Modifier else Modifier.animateItem()),
                )
            }
            // Heading first, then what AutoPlay has lined up under it. With
            // nothing lined up yet it closes the queue as a promise instead.
            if (autoplayEnabled || autoplayStart < queue.size) {
                item(key = "autoplay-heading") {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(
                            VelthyIcons.Infinity,
                            contentDescription = null,
                            tint = Color.White.copy(alpha = 0.75f),
                            modifier = Modifier.size(18.dp),
                        )
                        Spacer(Modifier.width(8.dp))
                        Column {
                            Text(
                                text = "AutoPlay",
                                style = MaterialTheme.typography.titleMedium,
                                color = Color.White,
                            )
                            Text(
                                text = if (autoplayStart < queue.size) {
                                    "Similar music, picked to follow on"
                                } else {
                                    "Similar music will keep playing"
                                },
                                style = MaterialTheme.typography.bodyMedium,
                                color = Color.White.copy(alpha = 0.55f),
                            )
                        }
                    }
                }
            }
            itemsIndexed(
                items = autoplayRows,
                key = { index, _ -> autoplayKeys[index] },
            ) { index, song ->
                val at = autoplayStart + index
                val key = autoplayKeys[index]
                val dragging = autoplayDrag.draggedKey == key
                InlineQueueRow(
                    song = song,
                    isCurrent = at == currentIndex,
                    onClick = { onJumpTo(at) },
                    onRemove = { onRemove(at) },
                    draggable = true,
                    dragging = dragging,
                    onDragStart = { autoplayDrag.onDragStart(key) },
                    onDrag = autoplayDrag::onDrag,
                    onDragEnd = autoplayDrag::onDragEnd,
                    modifier = Modifier
                        .zIndex(if (dragging) 1f else 0f)
                        .graphicsLayer { translationY = if (dragging) autoplayDrag.dragOffset else 0f }
                        .then(if (dragging) Modifier else Modifier.animateItem()),
                )
            }
        }
    }
}

/**
 * A key per row, stable across a reorder and unique even when the same song
 * appears twice — the Nth time a given videoId is seen gets suffixed with
 * that count, so two copies of one song each keep their own identity instead
 * of colliding on the same LazyColumn key.
 */
private fun List<Song>.stableQueueKeys(prefix: String = ""): List<String> {
    val seen = HashMap<String, Int>()
    return map { song ->
        val n = seen.getOrDefault(song.videoId, 0)
        seen[song.videoId] = n + 1
        if (n == 0) "$prefix${song.videoId}" else "$prefix${song.videoId}#$n"
    }
}

/**
 * Drag-to-reorder for one contiguous section of [InlineQueue]'s LazyColumn —
 * the user's own queue and AutoPlay's each get their own instance, since a
 * drag never crosses the boundary between them.
 *
 * Each swap goes to the player the moment the dragged row crosses a
 * neighbour, so the live queue is always what's on screen and the rows the
 * drag displaces animate to their new slots off it. The dragged row is
 * tracked by its LazyColumn key rather than by index, because the index under
 * it changes with every swap; [dragOffset] is corrected by the same distance
 * the row jumps so it stays put under the finger while its slot moves.
 *
 * [lazyRange] is the section's span of LazyColumn indices, and [lazyOffset]
 * the distance from those to queue indices — the AutoPlay heading is a row
 * of the list too, so below it the two no longer line up.
 */
@Composable
private fun rememberQueueDragState(
    listState: LazyListState,
    lazyRange: IntRange,
    lazyOffset: Int,
    onMove: (Int, Int) -> Unit,
): QueueDragState {
    val state = remember(listState) { QueueDragState(listState) }
    state.lazyRange = lazyRange
    state.lazyOffset = lazyOffset
    state.onMove = onMove
    return state
}

private class QueueDragState(private val listState: LazyListState) {
    var lazyRange: IntRange = IntRange.EMPTY
    var lazyOffset: Int = 0
    var onMove: (Int, Int) -> Unit = { _, _ -> }

    /** LazyColumn key of the row being dragged; null at rest. */
    var draggedKey by mutableStateOf<Any?>(null)
        private set
    var dragOffset by mutableFloatStateOf(0f)
        private set

    /** Where the last swap put the row, until the list is laid out with it. */
    private var awaiting: Int? = null

    fun onDragStart(key: Any) {
        draggedKey = key
        dragOffset = 0f
        awaiting = null
    }

    fun onDrag(deltaY: Float) {
        val key = draggedKey ?: return
        dragOffset += deltaY
        val items = listState.layoutInfo.visibleItemsInfo
        val dragged = items.find { it.key == key } ?: return
        // A swap already sent but not yet laid out: deciding the next one off
        // a position the list has moved on from would send a second move for
        // a swap that has already happened, and the two would fight.
        awaiting?.let { if (dragged.index != it) return else awaiting = null }
        val draggedCenter = dragged.offset + dragged.size / 2f + dragOffset
        // Only rows of this section are fair targets — the heading and the
        // other section's rows share the LazyColumn but not this range.
        val target = items
            .filter { it.index in lazyRange && it.index != dragged.index }
            .minByOrNull { abs((it.offset + it.size / 2f) - draggedCenter) }
            ?: return
        // Held short of halfway the rows would swap back and forth over a
        // single pixel of travel; a full half-height of overlap is what makes
        // one swap per row crossed.
        if (abs(draggedCenter - (target.offset + target.size / 2f)) > target.size / 2f) return
        onMove(dragged.index - lazyOffset, target.index - lazyOffset)
        // The row is about to land where the target was — fold that jump back
        // into the offset so it doesn't move out from under the finger.
        dragOffset += (dragged.offset - target.offset)
        awaiting = target.index
    }

    fun onDragEnd() {
        draggedKey = null
        dragOffset = 0f
        awaiting = null
    }
}

@Composable
private fun InlineQueueRow(
    song: Song,
    isCurrent: Boolean,
    onClick: () -> Unit,
    onRemove: () -> Unit,
    modifier: Modifier = Modifier,
    draggable: Boolean = false,
    dragging: Boolean = false,
    onDragStart: () -> Unit = {},
    onDrag: (Float) -> Unit = {},
    onDragEnd: () -> Unit = {},
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(if (dragging) Color.White.copy(alpha = 0.06f) else Color.Transparent)
            .clickable(onClick = onClick)
            .padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (draggable) {
            Icon(
                Icons.Rounded.DragHandle,
                contentDescription = "Drag to reorder",
                tint = Color.White.copy(alpha = 0.4f),
                modifier = Modifier
                    .size(20.dp)
                    // DragHandle's glyph sits well inset from the edges of
                    // its own bounding box — this pulls it back to the row's
                    // actual left edge instead of leaving a gap in front of it.
                    .offset(x = (-4).dp)
                    .pointerInput(Unit) {
                        detectDragGestures(
                            onDragStart = { onDragStart() },
                            onDragEnd = { onDragEnd() },
                            onDragCancel = { onDragEnd() },
                            onDrag = { change, dragAmount ->
                                change.consume()
                                onDrag(dragAmount.y)
                            },
                        )
                    },
            )
            Spacer(Modifier.width(4.dp))
        }
        AsyncImage(
            model = song.thumbnailUrl,
            contentDescription = null,
            modifier = Modifier
                .size(44.dp)
                .clip(RoundedCornerShape(6.dp))
                .thumbnailBorder(RoundedCornerShape(6.dp))
                .background(Color.White.copy(alpha = 0.08f)),
        )
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(
                text = song.title,
                style = MaterialTheme.typography.titleMedium,
                color = if (isCurrent) Color.White else Color.White.copy(alpha = 0.92f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = song.artist,
                style = MaterialTheme.typography.bodyMedium,
                color = Color.White.copy(alpha = 0.55f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        if (isCurrent) {
            Icon(
                Icons.Rounded.GraphicEq,
                contentDescription = "Now playing",
                tint = Color.White,
                modifier = Modifier.size(18.dp),
            )
            Spacer(Modifier.width(10.dp))
        }
        Box(
            modifier = Modifier
                .size(32.dp)
                .clip(CircleShape)
                .clickable(onClick = onRemove),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                Icons.Rounded.Close,
                contentDescription = "Remove from queue",
                tint = Color.White.copy(alpha = 0.55f),
                modifier = Modifier.size(18.dp),
            )
        }
    }
}

private fun formatTime(ms: Long): String {
    if (ms <= 0) return "0:00"
    val minutes = TimeUnit.MILLISECONDS.toMinutes(ms)
    val seconds = TimeUnit.MILLISECONDS.toSeconds(ms) % 60
    return "%d:%02d".format(minutes, seconds)
}

/**
 * The gap between the two timestamps under the seek bar: just the "Lossless"
 * badge when one applies, and nothing otherwise. The measured stats line
 * that used to fall back to lives inside the sleeve now (see the bottom-centre
 * overlay on the artwork Box above), so there is no tap here to swap it in —
 * the badge is a claim, the sleeve is where the evidence is.
 */
@Composable
private fun LosslessOrStats(
    isLoading: Boolean,
    stillRacing: Boolean,
    losslessRequested: Boolean,
    nerdStats: NerdStats.Snapshot?,
    modifier: Modifier = Modifier,
) {
    when {
        // Still resolving — either the player itself is buffering, or a
        // module is still racing YouTube for this track in the background
        // (see [NerdStats.racingLossless]) even though YouTube already won
        // and is audible. Either way nothing measured yet to confirm with,
        // so this is a statement of intent, not a result — no shimmer, so
        // it never reads as "confirmed" before it is.
        // [stillRacing] on its own, not gated on the lossless preference: a
        // module outranks YouTube on the strength of the source order alone,
        // so the lookup runs — and can come back lossless — with that switch
        // off. Gating this on it left the badge blank through the wait and
        // then jumped straight to "Hi-Res Lossless".
        (stillRacing || (isLoading && losslessRequested)) && nerdStats?.isLossless != true -> LosslessLabel(
            text = "Upgrading Quality",
            animated = false,
            modifier = modifier,
        )
        nerdStats?.isLossless == true -> LosslessLabel(
            // Same line Tidal, Qobuz and Apple Music draw it at — see
            // [NerdStats.Snapshot.isHiRes].
            text = if (nerdStats.isHiRes) "Hi-Res Lossless" else "Lossless",
            // Shimmer is reserved for the thing that was asked for and
            // confirmed. It is what makes the badge read as an achievement
            // rather than a label, which only one of these two is.
            animated = true,
            modifier = modifier,
        )
        // Lossy, but the good end of lossy — a module's 320kbps tier, which
        // for a great many tracks is the best copy that exists anywhere the
        // app can reach. See [NerdStats.Snapshot.isHiQuality].
        nerdStats?.isHiQuality == true -> LosslessLabel(
            text = "Hi-Quality",
            animated = false,
            modifier = modifier,
        )
        else -> {}
    }
}

/** A headphone glyph ahead of the quality tag — "Upgrading Quality", "Hi-Quality", "Lossless". */
@Composable
private fun LosslessLabel(text: String, animated: Boolean, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = Icons.Rounded.Headphones,
            contentDescription = null,
            tint = Color.White.copy(alpha = if (animated) 0.7f else 0.45f),
            modifier = Modifier.size(13.dp),
        )
        Spacer(Modifier.width(4.dp))
        if (animated) {
            ShimmerText(text = text)
        } else {
            Text(
                text = text,
                style = MaterialTheme.typography.labelMedium.copy(
                    fontSize = (MaterialTheme.typography.labelMedium.fontSize.value + 1).sp,
                ),
                color = Color.White.copy(alpha = 0.45f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

/**
 * "Lossless", with a highlight band sweeping left to right across it every
 * three seconds — confirmed, not just claimed, so it's worth the shine.
 *
 * The band's width is measured off the text itself via [onSizeChanged]
 * rather than assumed, so the sweep always clears the word fully at both
 * ends instead of being sized for whatever length happened to be typical.
 */
@Composable
private fun ShimmerText(text: String) {
    var widthPx by remember { mutableIntStateOf(0) }
    val transition = rememberInfiniteTransition(label = "lossless-shimmer")
    val progress by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 3_000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart,
        ),
        label = "lossless-shimmer-progress",
    )
    val baseColor = Color.White.copy(alpha = 0.55f)
    val brush = if (widthPx <= 0) {
        Brush.linearGradient(listOf(baseColor, baseColor))
    } else {
        val band = widthPx * 0.6f
        val center = -band + progress * (widthPx + 2 * band)
        Brush.linearGradient(
            colorStops = arrayOf(0f to baseColor, 0.5f to Color.White, 1f to baseColor),
            start = Offset(center - band, 0f),
            end = Offset(center + band, 0f),
        )
    }
    Text(
        text = text,
        style = MaterialTheme.typography.labelMedium.copy(
            brush = brush,
            fontWeight = FontWeight.SemiBold,
            fontSize = (MaterialTheme.typography.labelMedium.fontSize.value + 1).sp,
        ),
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        modifier = Modifier.onSizeChanged { widthPx = it.width },
    )
}

/**
 * "FLAC · 24-bit · 96.0 kHz · Stereo" — whichever of those the player has
 * actually reported. A figure it hasn't is dropped rather than filled in, so a
 * short line means little was known, never that something was invented.
 *
 * Bitrate is omitted once the stream is known to be lossless: the number is
 * real but says nothing useful about the quality, and reading "1411 kbps" next
 * to "FLAC" invites the comparison with a lossy figure that the two do not
 * support.
 *
 * A stream that arrived worse than its source promised gets that stated
 * outright rather than left to be spotted — see [NerdStats.Snapshot.downgraded].
 */
private fun NerdStats.Snapshot.describe(): String? {
    val parts = buildList {
        codecLabel(mimeType)?.let(::add)
        bitDepth?.let { add("$it-bit") }
        if (!isLossless) bitrateKbps?.let { add("$it kbps") }
        sampleRateHz?.let { add("%.1f kHz".format(it / 1000f)) }
        channels?.let {
            add(
                when (it) {
                    1 -> "Mono"
                    2 -> "Stereo"
                    else -> "$it ch"
                },
            )
        }
        if (downgraded) add("↓ from ${claimed?.summary}")
    }
    return parts.joinToString(" · ").takeIf { it.isNotEmpty() }
}

/** The codec under its usual name rather than its MIME type. */
private fun codecLabel(mimeType: String?): String? = when {
    mimeType == null -> null
    mimeType.endsWith("opus") -> "Opus"
    mimeType.endsWith("mp4a-latm") -> "AAC"
    mimeType.endsWith("vorbis") -> "Vorbis"
    mimeType.endsWith("mpeg") -> "MP3"
    mimeType.endsWith("flac") -> "FLAC"
    mimeType.endsWith("alac") -> "ALAC"
    else -> mimeType.substringAfter('/').uppercase()
}

/** Wording for the stats line; see [TrackAnalysisState]. */
private fun TrackAnalysisState.label(): String = when (this) {
    TrackAnalysisState.ANALYSED -> "analysed"
    TrackAnalysisState.REFINING -> "analysed, refining…"
    TrackAnalysisState.ANALYSING -> "analysing…"
    TrackAnalysisState.WAITING -> "waiting"
    TrackAnalysisState.FAILED -> "failed"
}

/**
 * Android 13+ back callback for modal overlays.
 */
@RequiresApi(Build.VERSION_CODES.TIRAMISU)
private object OverlayBack {
    /** The registered callback, to hand back to [unregister]; null if it couldn't be. */
    fun register(view: View, onBack: () -> Unit): Any? {
        val dispatcher = view.findOnBackInvokedDispatcher() ?: return null
        val callback = OnBackInvokedCallback { onBack() }
        dispatcher.registerOnBackInvokedCallback(
            OnBackInvokedDispatcher.PRIORITY_OVERLAY,
            callback,
        )
        return callback
    }

    fun unregister(view: View, callback: Any?) {
        if (callback !is OnBackInvokedCallback) return
        view.findOnBackInvokedDispatcher()?.unregisterOnBackInvokedCallback(callback)
    }
}
