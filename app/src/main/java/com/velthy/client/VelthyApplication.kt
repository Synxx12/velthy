package com.velthy.client

import android.app.Application
import androidx.annotation.OptIn
import androidx.media3.common.util.UnstableApi
import coil3.ImageLoader
import coil3.PlatformContext
import coil3.SingletonImageLoader
import coil3.disk.DiskCache
import coil3.disk.directory
import coil3.memory.MemoryCache
import coil3.request.crossfade
import com.velthy.client.auth.AuthStore
import com.velthy.client.data.canvas.CanvasCache
import com.velthy.client.data.canvas.SpotifyToken
import com.velthy.client.data.listentogether.ListenTogether
import com.velthy.client.playback.AudioCache
import com.velthy.client.playback.LastPlayed
import com.velthy.client.playback.PartyPersonalQueueStash
import com.velthy.client.data.innertube.Innertube
import com.velthy.client.data.innertube.StreamResolver
import com.velthy.client.data.scrobbling.LastFM
import com.velthy.client.data.settings.AppSettings
import com.velthy.client.data.settings.SearchHistory
import com.velthy.client.download.Downloads
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

open class VelthyApplication : Application(), SingletonImageLoader.Factory {

    @OptIn(UnstableApi::class)
    override fun onCreate() {
        super.onCreate()
        // PlaybackService shares this process, so seeding the cookie here means
        // stream resolution is authenticated from the first play onwards.
        authStore = AuthStore(this)
        Innertube.cookie = authStore.cookie
        AppSettings.init(this)
        SearchHistory.init(this)
        LastPlayed.init(this)
        com.velthy.client.data.sources.SourceRegistry.init(this)
        com.velthy.client.data.history.PlaybackHistoryManager.init(this)
        com.velthy.client.data.stats.ArtistFacts.init(this)
        com.velthy.client.data.stats.ListeningStats.init(this)
        // What's already saved to Downloads, so the song menu can say so
        // without a media-store query per row.
        Downloads.init(this)
        // One cache directory can only be opened once per process, and
        // PlaybackService shares this one — so it's opened here, not there.
        AudioCache.init(this)
        // Canvas clips are looped video behind the cover art, and without a
        // disk cache every loop of a five-second clip would be a fresh download
        // — see [CanvasCache]. Opened once here for the same single-open-per-
        // process reason as AudioCache.
        CanvasCache.init(this)
        // The Spotify Canvas source mints a bearer token from the listener's
        // session cookie in an offscreen WebView, and has no Context of its
        // own to reach for — so it is handed the app context here. Without it
        // the source is a free no-op rather than a crash, but with it set up
        // the original Canvas becomes reachable.
        SpotifyToken.init(this)
        // Listen Together needs prefs to hand a previous process's party slot
        // back on launch, and a device id that survives a sign-out.
        ListenTogether.init(this)
        // The listener's own queue, set aside for the length of a party. Prefs
        // rather than memory because the case it exists for is the process being
        // killed while in one — see [PartyPersonalQueueStash].
        PartyPersonalQueueStash.init(this)
        // Registers the daily background release check. KEEP policy, so this is
        // a no-op once the schedule exists — see [UpdateCheckWorker.schedule].
        com.velthy.client.data.UpdateCheckWorker.schedule(this)
        // Initialize LastFM with saved settings if available
        initLastfm()
        warmStreamResolution()
    }

    /**
     * Pays the one-time costs of the first stream resolve up front.
     *
     * The visitor id and NewPipe's extractor init are both charged to whoever
     * resolves first, which is the first track a listener plays — the exact
     * report of "the first song always loads, the ones after it are instant".
     * Both are network and CPU work that can happen while the listener is still
     * looking at the home screen, so they happen here rather than on the
     * critical path of the tap. See [StreamResolver.warmUp].
     *
     * On a background thread and silent on failure: this buys latency when it
     * works and is invisible when it does not.
     */
    private fun warmStreamResolution() {
        CoroutineScope(Dispatchers.IO).launch { StreamResolver.warmUp() }
    }

    /**
     * Artwork loading, which was previously left entirely on Coil's defaults.
     *
     * The defaults aren't unreasonable, but the disk cache is sized at 2% of
     * free space — which on a full phone is the 10MB floor, a few screens of
     * covers, and covers are exactly the thing worth still having tomorrow.
     * Naming a directory alongside it keeps that cache somewhere identifiable
     * rather than in the process's temp dir.
     */
    override fun newImageLoader(context: PlatformContext): ImageLoader =
        ImageLoader.Builder(context)
            .memoryCache {
                MemoryCache.Builder()
                    .maxSizePercent(context, 0.20)
                    .build()
            }
            .diskCache {
                DiskCache.Builder()
                    .directory(cacheDir.resolve("image_cache"))
                    .maxSizeBytes(100L * 1024 * 1024)
                    .build()
            }
            // Covers arriving with a hard cut read as the list flickering as
            // it scrolls; a short fade reads as them developing.
            .crossfade(200)
            .build()

    private fun initLastfm() {
        val sessionKey = AppSettings.lastfmSessionKey.value
        if (sessionKey.isBlank()) return
        val endpoint = AppSettings.lastfmEndpoint.value.ifBlank { LastFM.DEFAULT_API_ENDPOINT }
        val apiKey = AppSettings.lastfmApiKey.value.trim().ifBlank { LastFM.FALLBACK_COMPAT_API_KEY }
        val secret = AppSettings.lastfmSecret.value.trim().ifBlank { LastFM.FALLBACK_COMPAT_SECRET }
        LastFM.configure(
            endpoint = endpoint,
            apiKey = apiKey,
            secret = secret,
            sessionKey = sessionKey,
        )
    }

    companion object {
        lateinit var authStore: AuthStore
            private set
    }
}
