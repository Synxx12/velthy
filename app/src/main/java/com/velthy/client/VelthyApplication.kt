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
import com.velthy.client.playback.AudioCache
import com.velthy.client.playback.DolbyAtmos
import com.velthy.client.playback.LastPlayed
import com.velthy.client.data.innertube.Innertube
import com.velthy.client.data.scrobbling.LastFM
import com.velthy.client.data.settings.AppSettings
import com.velthy.client.data.settings.SearchHistory
import com.velthy.client.download.Downloads

open class VelthyApplication : Application(), SingletonImageLoader.Factory {

    @OptIn(UnstableApi::class)
    override fun onCreate() {
        super.onCreate()
        // PlaybackService shares this process, so seeding the cookie here means
        // stream resolution is authenticated from the first play onwards.
        authStore = AuthStore(this)
        Innertube.cookie = authStore.cookie
        AppSettings.init(this)
        // After AppSettings: a device with Atmos switched off retires the
        // spatial audio preference on the spot, and that needs prefs open.
        DolbyAtmos.init(this)
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
        // Initialize LastFM with saved settings if available
        initLastfm()
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
        val apiKey = AppSettings.lastfmApiKey.value.trim()
        val secret = AppSettings.lastfmSecret.value.trim()
        if (apiKey.isBlank() || secret.isBlank()) return
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
