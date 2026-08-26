package com.velthy.client.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.res.Configuration
import android.graphics.Bitmap
import android.os.Bundle
import android.view.View
import android.widget.RemoteViews
import com.velthy.client.MainActivity
import com.velthy.client.R
import com.velthy.client.playback.PlayerDeepLink
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.math.roundToInt

/**
 * The home-screen widget: the cover of what's playing, with a transport across
 * the foot of it.
 *
 * Two providers, [MediaWidgetSquare] and [MediaWidgetWide], so the picker offers
 * a square one and a full-width one — but they are the same widget, and both can
 * be resized across the whole range.
 */
abstract class MediaWidget : AppWidgetProvider() {

    protected abstract val fallbackWidthDp: Int

    override fun onUpdate(context: Context, manager: AppWidgetManager, ids: IntArray) {
        renderAsync(context, ids)
    }

    override fun onAppWidgetOptionsChanged(
        context: Context,
        manager: AppWidgetManager,
        id: Int,
        newOptions: Bundle?,
    ) {
        renderAsync(context, intArrayOf(id))
    }

    override fun onDisabled(context: Context) {
        MediaWidgetArt.clear()
    }

    private fun renderAsync(context: Context, ids: IntArray) {
        val pending = goAsync()
        val app = context.applicationContext
        val fallback = fallbackWidthDp
        scope.launch {
            try {
                withTimeoutOrNull(RENDER_TIMEOUT_MS) { render(app, ids, fallback) }
            } finally {
                runCatching { pending.finish() }
            }
        }
    }

    companion object {

        const val WIDE_LAYOUT_MIN_DP = 215

        fun refresh(context: Context) {
            val app = context.applicationContext
            scope.launch {
                withTimeoutOrNull(RENDER_TIMEOUT_MS) {
                    val manager =
                        runCatching { AppWidgetManager.getInstance(app) }.getOrNull() ?: return@withTimeoutOrNull
                    for ((provider, fallbackWidthDp) in PROVIDERS) {
                        val ids = runCatching {
                            manager.getAppWidgetIds(ComponentName(app, provider))
                        }.getOrNull()
                        if (ids == null || ids.isEmpty()) continue
                        render(app, ids, fallbackWidthDp)
                    }
                }
            }
        }

        private suspend fun render(context: Context, ids: IntArray, fallbackWidthDp: Int) {
            val manager = runCatching { AppWidgetManager.getInstance(context) }.getOrNull() ?: return
            val snapshot = MediaWidgetSnapshot.load(context)
            val key = snapshot.artworkUrl ?: KEY_NO_ARTWORK
            for (id in ids) {
                val size = measure(context, manager, id, fallbackWidthDp)
                val cached = MediaWidgetArt.peek(key, size.widthPx, size.heightPx, size.bandPx)
                if (cached == null) {
                    manager.push(id, views(context, snapshot, size, art = null))
                }
                val art = cached ?: MediaWidgetArt.render(
                    context = context,
                    artworkUrl = snapshot.artworkUrl,
                    widthPx = size.widthPx,
                    heightPx = size.heightPx,
                    bandPx = size.bandPx,
                    key = key,
                    cornerRadiusPx = size.cornerRadiusPx,
                )
                manager.push(id, views(context, snapshot, size, art))
            }
        }

        private fun views(
            context: Context,
            snapshot: MediaWidgetSnapshot,
            size: WidgetSize,
            art: Bitmap?,
        ): RemoteViews {
            val layout =
                if (size.wide) R.layout.widget_media_wide else R.layout.widget_media_compact
            val views = RemoteViews(context.packageName, layout)
            art?.let { views.setImageViewBitmap(R.id.widget_art, it) }

            views.setTextViewText(
                R.id.widget_title,
                if (snapshot.hasTrack) {
                    snapshot.title
                } else {
                    context.getString(R.string.widget_nothing_played)
                },
            )
            if (size.wide) {
                views.setTextViewText(R.id.widget_artist, snapshot.artist)
                views.setViewVisibility(
                    R.id.widget_artist,
                    if (snapshot.artist.isBlank()) View.GONE else View.VISIBLE,
                )
            }

            views.setImageViewResource(
                R.id.widget_toggle,
                if (snapshot.isPlaying) R.drawable.ic_widget_pause else R.drawable.ic_widget_play,
            )
            views.setContentDescription(
                R.id.widget_toggle,
                context.getString(
                    if (snapshot.isPlaying) R.string.widget_pause else R.string.widget_play,
                ),
            )

            val open = openPlayer(context)
            views.setOnClickPendingIntent(R.id.widget_root, open)

            if (snapshot.hasTrack) {
                views.setImageAlpha(R.id.widget_toggle, ALPHA_ENABLED)
                views.setImageAlpha(
                    R.id.widget_previous,
                    if (snapshot.hasPrevious) ALPHA_ENABLED else ALPHA_DISABLED,
                )
                views.setImageAlpha(
                    R.id.widget_next,
                    if (snapshot.hasNext) ALPHA_ENABLED else ALPHA_DISABLED,
                )
                views.setOnClickPendingIntent(
                    R.id.widget_toggle,
                    MediaWidgetActions.pendingIntent(context, MediaWidgetActions.ACTION_TOGGLE),
                )
                views.setOnClickPendingIntent(
                    R.id.widget_previous,
                    MediaWidgetActions.pendingIntent(context, MediaWidgetActions.ACTION_PREVIOUS),
                )
                views.setOnClickPendingIntent(
                    R.id.widget_next,
                    MediaWidgetActions.pendingIntent(context, MediaWidgetActions.ACTION_NEXT),
                )
            } else {
                for (button in TRANSPORT) {
                    views.setImageAlpha(button, ALPHA_DISABLED)
                    views.setOnClickPendingIntent(button, open)
                }
            }
            return views
        }

        private fun RemoteViews.setImageAlpha(viewId: Int, alpha: Int) =
            setInt(viewId, "setImageAlpha", alpha)

        private fun openPlayer(context: Context): PendingIntent {
            val intent = Intent(context, MainActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
                .putExtra(PlayerDeepLink.EXTRA_OPEN_PLAYER, true)
            return PendingIntent.getActivity(
                context,
                REQUEST_OPEN_PLAYER,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )
        }

        private fun AppWidgetManager.push(id: Int, views: RemoteViews) {
            runCatching { updateAppWidget(id, views) }
        }

        private class WidgetSize(
            val wide: Boolean,
            val widthPx: Int,
            val heightPx: Int,
            val bandPx: Int,
            val cornerRadiusPx: Float,
        )

        private fun measure(
            context: Context,
            manager: AppWidgetManager,
            id: Int,
            fallbackWidthDp: Int,
        ): WidgetSize {
            val options = runCatching { manager.getAppWidgetOptions(id) }.getOrNull()
            val landscape =
                context.resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE
            val widthDp = options?.getInt(
                if (landscape) {
                    AppWidgetManager.OPTION_APPWIDGET_MAX_WIDTH
                } else {
                    AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH
                },
            )?.takeIf { it > 0 } ?: fallbackWidthDp
            val heightDp = options?.getInt(
                if (landscape) {
                    AppWidgetManager.OPTION_APPWIDGET_MIN_HEIGHT
                } else {
                    AppWidgetManager.OPTION_APPWIDGET_MAX_HEIGHT
                },
            )?.takeIf { it > 0 } ?: FALLBACK_HEIGHT_DP

            val wide = widthDp >= WIDE_LAYOUT_MIN_DP
            val resources = context.resources
            val density = resources.displayMetrics.density
            val rawWidth = widthDp * density
            val rawHeight = heightDp * density
            val longest = maxOf(rawWidth, rawHeight)
            val scale = if (longest > MAX_BITMAP_PX) MAX_BITMAP_PX / longest else 1f
            val band = resources.getDimensionPixelSize(
                if (wide) R.dimen.widget_band_wide else R.dimen.widget_band_compact,
            )
            return WidgetSize(
                wide = wide,
                widthPx = (rawWidth * scale).roundToInt().coerceAtLeast(1),
                heightPx = (rawHeight * scale).roundToInt().coerceAtLeast(1),
                bandPx = (band * scale).roundToInt().coerceAtLeast(1),
                cornerRadiusPx = resources.getDimension(R.dimen.widget_corner_radius) * scale,
            )
        }

        private val PROVIDERS = listOf(
            MediaWidgetSquare::class.java to SQUARE_WIDTH_DP,
            MediaWidgetWide::class.java to WIDE_WIDTH_DP,
        )

        private val TRANSPORT =
            intArrayOf(R.id.widget_previous, R.id.widget_toggle, R.id.widget_next)

        private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

        private const val RENDER_TIMEOUT_MS = 8_000L
        private const val MAX_BITMAP_PX = 1_200f
        private const val ALPHA_ENABLED = 255
        private const val ALPHA_DISABLED = 90
        private const val KEY_NO_ARTWORK = "no-artwork"
        private const val REQUEST_OPEN_PLAYER = 0
    }
}

private const val SQUARE_WIDTH_DP = 110
private const val WIDE_WIDTH_DP = 250
private const val FALLBACK_HEIGHT_DP = 110

class MediaWidgetSquare : MediaWidget() {
    override val fallbackWidthDp = SQUARE_WIDTH_DP
}

class MediaWidgetWide : MediaWidget() {
    override val fallbackWidthDp = WIDE_WIDTH_DP
}
