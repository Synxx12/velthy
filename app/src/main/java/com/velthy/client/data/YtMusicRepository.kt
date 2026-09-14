package com.velthy.client.data

import android.util.Log
import com.velthy.client.data.innertube.Innertube
import com.velthy.client.data.innertube.InnertubeParser
import com.velthy.client.data.model.Account
import com.velthy.client.data.model.AccountChannel
import com.velthy.client.data.model.ArtistPage
import com.velthy.client.data.model.HistorySection
import java.util.concurrent.ConcurrentHashMap
import com.velthy.client.data.model.HomeFeed
import com.velthy.client.data.model.HomeShelf
import com.velthy.client.data.model.LibraryPage
import com.velthy.client.data.model.LikeStatus
import com.velthy.client.data.model.MoodGenreSection
import com.velthy.client.data.model.PlaylistPrivacy
import com.velthy.client.data.model.SearchFilter
import com.velthy.client.data.model.SearchResult
import com.velthy.client.data.model.ShelfItem
import com.velthy.client.data.model.Song
import com.velthy.client.data.model.SongMenu
import com.velthy.client.data.model.UserPlaylist
import com.velthy.client.data.settings.AppSettings
import com.velthy.client.data.sources.SourceRegistry
import com.velthy.client.data.sources.TrackMatcher
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.JsonObject

/** Suspend API over Innertube. Every call returns a Result so the UI can show a real error. */
object YtMusicRepository {

    private const val TAG = "Velthy"

    /** How many queue tracks are resolved at once — see [resolveAudioAll]. */
    private const val RESOLVE_CONCURRENCY = 4

    /**
     * The personalised feed, led by what was actually just played and padded
     * out with new releases.
     *
     * FEmusic_home alone is thin when signed out (three shelves), so extra
     * rows are pulled from FEmusic_new_releases, which carries genuinely
     * different content. Charts (Daily/Weekly, Trending) live under Explore
     * in the real app — see [explore] — not here. Titles are de-duped in
     * case the home feed already surfaced the same shelf.
     *
     * FEmusic_home's own continuation token comes back, for [moreHome] —
     * signed in, it keeps paging into mood mixes and more personalised
     * shelves the same way the official app does as you scroll; signed out
     * it's empty and there's nothing more to fetch.
     */
    suspend fun home(): Result<HomeFeed> = call("home") {
        coroutineScope {
            val recent = async { runCatching { recentlyPlayed() }.getOrNull() }
            val homeRaw = async { Innertube.browse("FEmusic_home") }
            val newReleases = async { runCatching { shelvesOf("FEmusic_new_releases") }.getOrDefault(emptyList()) }
            val charts = async { runCatching { shelvesOf("FEmusic_charts") }.getOrDefault(emptyList()) }
            val home = homeRaw.await()
            val parsedHome = InnertubeParser.parseHome(home)

            // Prioritize shelves with playable songs (Quick picks, Listen again, Trending songs)
            val songShelves = parsedHome.filter { shelf -> shelf.items.any { it.videoId != null } }
            val otherShelves = parsedHome.filter { shelf -> shelf.items.none { it.videoId != null } }

            val fallbackSongShelves = if (recent.await() == null && songShelves.isEmpty()) {
                charts.await().filter { it.items.any { item -> item.videoId != null } }.take(1)
            } else {
                emptyList()
            }

            val combined = listOfNotNull(recent.await()) +
                songShelves +
                fallbackSongShelves +
                otherShelves +
                newReleases.await()

            HomeFeed(combined.distinctBy { it.title.lowercase() }, InnertubeParser.continuationToken(home))
        }
    }

    /**
     * More Home shelves past [home]'s first page, following FEmusic_home's
     * own continuation — the lever the official app pulls as you scroll
     * rather than a fixed one-shot page. "Recently played" and
     * FEmusic_new_releases are one-shot and don't participate.
     */
    suspend fun moreHome(token: String): Result<HomeFeed> = call("home:more") {
        val response = Innertube.browseContinuation(token)
        HomeFeed(
            shelves = InnertubeParser.parseHomeContinuation(response),
            continuation = InnertubeParser.continuationToken(response),
        )
    }

    /**
     * The lead shelf: the account's listening history, newest first.
     *
     * YouTube's home already carries a "Listen again", but it ranks by how
     * *often* something has been played rather than how recently — so it keeps
     * leading with last month's favourites for days after a change of mood,
     * which reads as the feed being broken. The history feed reflects a play
     * the moment it's registered, so it's what the top of the page is built
     * from. YouTube's own shelf stays below, where its ranking is a feature.
     *
     * Signed-in only; there is no history to read as a guest.
     */
    private suspend fun recentlyPlayed(): HomeShelf? {
        if (Innertube.cookie == null) return null
        val songs = fetchHistory().take(RECENT_LIMIT)
        if (songs.isEmpty()) return null
        return HomeShelf(
            title = RECENT_TITLE,
            items = songs.map {
                ShelfItem(
                    title = it.title,
                    subtitle = it.artist,
                    thumbnailUrl = it.thumbnailUrl,
                    videoId = it.videoId,
                    browseId = null,
                    albumName = it.albumName,
                )
            },
        )
    }

    private suspend fun fetchHistory(): List<Song> =
        InnertubeParser.collectSongsDeep(Innertube.browse(HISTORY)).distinctBy { it.videoId }

    suspend fun history(): Result<List<Song>> = call("history") { fetchHistory() }

    suspend fun historySections(): Result<List<HistorySection>> = call("history:sections") {
        fetchHistorySections()
    }

    private suspend fun fetchHistorySections(): List<HistorySection> {
        val parsed = InnertubeParser.parseHistorySections(Innertube.browse(HISTORY))
        return parsed.map { HistorySection(it.title, it.songs) }
    }

    private const val HISTORY = "FEmusic_history"
    private const val RECENT_TITLE = "Recently played"

    /** Enough to scroll through, short of turning the shelf into the history page. */
    private const val RECENT_LIMIT = 20

    private suspend fun shelvesOf(browseId: String): List<HomeShelf> =
        InnertubeParser.parseHome(Innertube.browse(browseId))

    /**
     * The mood & genre sections of the Explore page, straight from BitChord's
     * approach: one browse of the moods-and-genres hub parsed into headed
     * groups of mood buttons.
     */
    suspend fun moodAndGenres(): Result<List<MoodGenreSection>> = call("moods-and-genres") {
        InnertubeParser.parseMoodAndGenres(Innertube.browse("FEmusic_moods_and_genres"))
    }

    /**
     * The playlist shelves behind one mood/genre category — what BitChord's
     * Explore opens when a mood button is tapped. The mood's [params] are part
     * of its browse request; without them YouTube answers 404.
     */
    suspend fun moodGenreShelves(browseId: String, params: String?): Result<List<HomeShelf>> =
        call("mood-genre:$browseId") {
            InnertubeParser.parseHome(Innertube.browse(browseId, params))
        }

    private val moodThumbCache = ConcurrentHashMap<String, String?>()

    /**
     * The cover of the first song/album inside this mood: the first item in
     * the category's shelves that actually carries a thumbnail. Cached.
     */
    suspend fun moodGenreArtwork(browseId: String, params: String?): Result<String?> {
        val key = "$browseId|${params.orEmpty()}"
        moodThumbCache[key]?.let { return Result.success(it) }
        return call("mood-art:$browseId") {
            val thumb = moodGenreShelves(browseId, params).getOrNull()
                ?.firstNotNullOfOrNull { shelf ->
                    shelf.items.firstNotNullOfOrNull { it.thumbnailUrl }
                }
            moodThumbCache[key] = thumb
            thumb
        }
    }

    /**
     * Compatibility helper for callers that need candidates but not a scrolling
     * result screen. Those callers need the first, most relevant page only.
     */
    suspend fun search(query: String, filter: SearchFilter? = null): Result<List<SearchResult>> =
        searchPage(query, filter).map { it.rows }

    suspend fun searchPage(
        query: String,
        filter: SearchFilter? = null,
    ): Result<InnertubeParser.SearchPage> =
        call("search:${filter?.name ?: "all"}") {
            InnertubeParser.parseSearchPage(Innertube.search(query, filter?.params))
        }

    /** The page the first page's continuation token points at. */
    suspend fun searchContinuation(token: String): Result<InnertubeParser.SearchPage> =
        call("search:more") {
            InnertubeParser.parseSearchPage(Innertube.searchContinuation(token))
        }

    /**
     * What YouTube Music would suggest completing [input] to, for the search
     * field's typeahead. Unfiltered on purpose: a suggestion is a query, and
     * which tab it is then run against is the user's to pick afterwards.
     */
    suspend fun searchSuggestions(input: String): Result<List<String>> = call("suggest") {
        InnertubeParser.parseSearchSuggestions(Innertube.searchSuggestions(input))
    }

    suspend fun resolveAudio(song: Song): Song {
        val swapForAudio = song.isVideo && AppSettings.convertVideoToAudio.value
        // A row can reach the player with no album on it — a search hit and a
        // playlist row both routinely name only the artist — and then the
        // Discord card has no album line to draw, however well the catalogue
        // knows the release. The same lookup that swaps a video for its audio
        // release is what fills that in, so it runs for a plain song too.
        val fillAlbum = song.albumName == null && song.isCatalogueTrack
        if (!swapForAudio && !fillAlbum) return song
        val target = TrackMatcher.targetOf(song)
        for (query in TrackMatcher.queries(target)) {
            val candidates = search(query, SearchFilter.SONGS)
                .getOrNull()
                ?.filterIsInstance<SearchResult.Track>()
                ?.map { it.song }
                .orEmpty()
            val match = TrackMatcher.best(candidates, target) ?: continue
            // The match is the catalogue *recording* of the same song, so
            // anything already known about the release still holds — and the
            // Songs tab the match comes from frequently names no album at all.
            // Handing the match back bare is what made the album depend on
            // where playback started: a home card is never swapped (it carries
            // no video flag), while a search hit, an album page's row and a
            // playlist row all are, and each of them lost the album here.
            val albumName = match.albumName ?: song.albumName
            if (swapForAudio) {
                return match.copy(
                    albumName = albumName,
                    albumId = match.albumId ?: song.albumId,
                    artistId = match.artistId ?: song.artistId,
                )
            }
            // A plain song keeps its own id and title; it is only here for a
            // release its row never named, so a match that names none is no
            // help — the next query may.
            if (albumName != null) {
                return song.copy(
                    albumName = albumName,
                    albumId = match.albumId ?: song.albumId,
                    artistId = match.artistId ?: song.artistId,
                )
            }
        }
        return song
    }

    /**
     * [resolveAudio] across a whole queue, a few tracks at a time.
     *
     * Each track can now cost a search — for a video's audio release, or for an
     * album the row never named — and a long playlist would otherwise put every
     * one of those on the wire the moment playback starts. The cap is the same
     * work, spread out; nothing here is on the path of the track about to play,
     * which is resolved on its own.
     */
    suspend fun resolveAudioAll(songs: List<Song>): List<Song> = coroutineScope {
        val permits = Semaphore(RESOLVE_CONCURRENCY)
        songs.map { async { permits.withPermit { resolveAudio(it) } } }.awaitAll()
    }

    /**
     * Whether this is YouTube's own track, and so worth asking YouTube about.
     *
     * A local file, a module track and a JioSaavn track each carry their own
     * release already, and a YouTube search would only ever answer with a
     * different one.
     */
    private val Song.isCatalogueTrack: Boolean
        get() = localUri == null && localPath == null &&
            SourceRegistry.parseTrackKey(videoId) == null

    /**
     * Subscribes to an artist's channel, or unsubscribes. [channelId] is the one
     * the artist page is served under, which is also the id the write takes.
     */
    suspend fun setSubscribed(channelId: String, subscribed: Boolean): Result<Unit> =
        call("subscription:$channelId") { Innertube.setSubscribed(channelId, subscribed) }

    /** Signed-in profile for the settings header. Null when signed out. */
    suspend fun account(): Result<Account> = call("account") {
        InnertubeParser.parseAccount(Innertube.accountMenu())
            ?: error("No account details")
    }

    /**
     * Every channel this login can act as — its own, plus any brand channels.
     *
     * Two endpoints are asked in turn because either can come back with an
     * envelope holding no `accountItem` at all, and the two do not fail
     * together: `accounts_list` is the first-party route and the switcher is
     * what youtube.com's own avatar menu uses. An empty list from the first is
     * not an answer, it is a shape this parser didn't recognise, so it is
     * treated the same as a failure and the other route is tried.
     */
    suspend fun accountChannels(): Result<List<AccountChannel>> = call("channels") {
        val viaInnertube = runCatching {
            InnertubeParser.parseAccountChannels(Innertube.accountsList())
        }.onFailure { Log.w(TAG, "accounts_list unavailable: ${it.message}") }
            .getOrNull()
            .orEmpty()
        if (viaInnertube.isNotEmpty()) return@call viaInnertube
        InnertubeParser.parseAccountChannels(Innertube.accountSwitcher())
    }

    /**
     * The whole library in one shot — requires a signed-in session.
     *
     * YouTube Music has no single "my library" feed: Liked Music is the `LM`
     * auto-playlist, the songs added to the library are a separate feed, and
     * every saved collection has its own browse id. They're fetched in
     * parallel and a feed that fails or is simply empty (a fresh account has
     * no saved albums) is dropped rather than failing the whole page.
     */
    suspend fun library(): Result<LibraryPage> = call("library") {
        coroutineScope {
            val liked = async { runCatching { songsPaged(LIKED_MUSIC) }.getOrDefault(emptyList()) }
            val added = async { runCatching { songsPaged(LIBRARY_SONGS) }.getOrDefault(emptyList()) }
            val shelves = LIBRARY_FEEDS
                .map { (title, browseId) ->
                    async {
                        val items = runCatching {
                            InnertubeParser.parseLibraryItems(Innertube.browse(browseId))
                        }.getOrDefault(emptyList())
                        HomeShelf(title, items)
                    }
                }
                .awaitAll()
                .filter { it.items.isNotEmpty() }

            val likedSongs = liked.await()
            val likedIds = likedSongs.mapTo(HashSet()) { it.videoId }
            LikeState.seedLiked(likedIds)
            LibraryPage(
                likedSongs = likedSongs,
                // Thumbs-up'd tracks are also in the library feed; only what
                // the "Liked Music" list doesn't already cover is worth a
                // second section.
                librarySongs = added.await().filterNot { it.videoId in likedIds },
                shelves = shelves,
            )
        }
    }

    /**
     * What YouTube Music would play on after [videoId]. Feeds AutoPlay; the
     * seed track itself comes back first, so callers filter what they have.
     */
    suspend fun radio(videoId: String): Result<List<Song>> = call("radio:$videoId") {
        InnertubeParser.parseWatchQueue(Innertube.next(videoId))
    }

    /**
     * The artist and album pages a track links out to.
     *
     * Search rows carry them, but home cards and anything already sitting in a
     * queue often don't — and the credits in the player have to lead somewhere
     * either way. A track's own watch queue entry always names both.
     */
    suspend fun trackLinks(videoId: String): Result<Song> = call("links:$videoId") {
        InnertubeParser.parseWatchQueue(Innertube.next(videoId))
            .firstOrNull { it.videoId == videoId }
            ?: error("no watch entry for $videoId")
    }

    /**
     * One page of a browse feed's tracks, and the token for the page after
     * it — null once there is nothing more. [suggested] is only ever
     * non-empty for a playlist page — see [InnertubeParser.parsePlaylistShelf].
     */
    data class SongPage(
        val songs: List<Song>,
        val continuation: String?,
        val suggested: List<Song> = emptyList(),
    )

    /**
     * The first page of an album/playlist's tracks, and nothing more.
     *
     * Deliberately not the whole list. Following every continuation before
     * returning meant a long playlist spent up to ten round trips showing a
     * spinner, when every row needed to fill the first screenful was in the
     * first response. The rest arrives behind a page that is by then already
     * being read — see [moreSongs].
     */
    suspend fun browseSongs(browseId: String): Result<SongPage> = call("browse:$browseId") {
        pageOf(Innertube.browse(browseId))
    }

    suspend fun browseShelves(browseId: String): Result<List<HomeShelf>> = call("shelves:$browseId") {
        shelvesOf(browseId)
    }

    /** The page [SongPage.continuation] points at. */
    suspend fun moreSongs(token: String): Result<SongPage> = call("browse:more") {
        pageOf(Innertube.browseContinuation(token))
    }

    private fun pageOf(response: JsonObject): SongPage {
        // A playlist page is scoped to its own shelf so its "Suggested
        // tracks" never read as songs the user added — see
        // parsePlaylistShelf. Anything else (album, library, history) has no
        // such shelf, and falls back to the layout-agnostic walk.
        InnertubeParser.parsePlaylistShelf(response)?.let { shelf ->
            return SongPage(shelf.songs, shelf.continuation, shelf.suggested)
        }
        return SongPage(
            // One response can name the same track twice — an album page that
            // also carries a "you might also like" shelf, say. Collecting into a
            // map used to take care of that; paging by hand means saying so.
            songs = InnertubeParser.collectSongsDeep(response).distinctBy { it.videoId },
            continuation = InnertubeParser.continuationToken(response),
        )
    }

    /**
     * Every track behind a browse id, following continuations.
     *
     * A playlist page returns its first ~100 rows and a token for the rest, so
     * a long list otherwise arrives silently truncated. Capped at
     * [MAX_PAGES] so a runaway feed can't hold the UI open forever, and a
     * failed page keeps whatever was already collected.
     *
     * Holds its caller until the last page lands, so it belongs behind things
     * nobody is watching — the library sync, an artist's back catalogue. For
     * anything a screen is waiting on, use [browseSongs] and [moreSongs].
     */
    private suspend fun songsPaged(browseId: String): List<Song> {
        val out = LinkedHashMap<String, Song>()
        var response = Innertube.browse(browseId)
        var page = 1
        while (true) {
            // Same shelf-scoping as pageOf: a playlist (Liked Music and the
            // Library Songs auto-playlist included) is read from its own
            // shelf so a trailing "Suggested tracks" shelf never joins in.
            val shelf = InnertubeParser.parsePlaylistShelf(response)
            (shelf?.songs ?: InnertubeParser.collectSongsDeep(response)).forEach { out[it.videoId] = it }
            val token = shelf?.continuation ?: InnertubeParser.continuationToken(response)
            if (token == null || page++ >= MAX_PAGES) break
            response = runCatching { Innertube.browseContinuation(token) }.getOrNull() ?: break
        }
        return out.values.toList()
    }

    const val MAX_PAGES = 10

    /**
     * Liked Music: the `LM` auto-playlist, addressed as a playlist browse id.
     * Public because it is also the page a track has to disappear from the
     * moment it stops being liked — see MainViewModel's `dropFromLikedLists`.
     */
    const val LIKED_MUSIC = "VLLM"

    /** Songs explicitly added to the library — distinct from Liked Music. */
    private const val LIBRARY_SONGS = "FEmusic_liked_videos"

    /** Saved and own playlists; also what the "add to playlist" picker lists. */
    private const val LIBRARY_PLAYLISTS = "FEmusic_liked_playlists"

    private val LIBRARY_FEEDS = listOf(
        "Playlists" to LIBRARY_PLAYLISTS,
        "Albums" to "FEmusic_liked_albums",
        "Artists" to "FEmusic_library_corpus_track_artists",
        "Subscriptions" to "FEmusic_library_corpus_artists",
        "Podcasts" to "FEmusic_library_non_music_audio_list",
    )

    // ---- Writes -------------------------------------------------------------

    /**
     * The account's own state for one track — rating and library membership.
     *
     * Deliberately a lookup rather than something cached with the [Song]: a
     * track reaching the player through the queue has been round-tripped
     * through a MediaItem, which carries an id and little else, and the
     * feedback tokens are per-row anyway. Fetched when a menu is opened, which
     * is the only moment the answer is looked at.
     */
    suspend fun songMenu(videoId: String): Result<SongMenu> = call("menu:$videoId") {
        InnertubeParser.parseSongMenu(Innertube.next(videoId), videoId)
            ?: error("no menu for $videoId")
    }

    suspend fun rate(videoId: String, status: LikeStatus): Result<Unit> =
        call("rate:$videoId") { Innertube.rate(videoId, status) }

    /** Adds or removes a track from the library; [token] says which. */
    suspend fun setLibraryStatus(token: String): Result<Unit> =
        call("library:feedback") { Innertube.sendFeedback(token) }

    /**
     * The playlists a track can be added to. Not paged: an account with more
     * than one page of playlists is rare, and the picker is a list to scroll
     * rather than a feed to follow.
     */
    
    /** Saves an album or playlist to the library, or removes it. */
    suspend fun setSaved(playlistId: String, saved: Boolean): Result<Unit> =
        call("library:$playlistId") { com.velthy.client.data.innertube.Innertube.ratePlaylist(playlistId, saved) }

    suspend fun userPlaylists(): Result<List<UserPlaylist>> = call("playlists") {
        InnertubeParser.parseUserPlaylists(Innertube.browse(LIBRARY_PLAYLISTS))
    }

    /** Creates a playlist, optionally seeded with [videoIds]; returns its id. */
    suspend fun createPlaylist(
        title: String,
        privacy: PlaylistPrivacy,
        videoIds: List<String> = emptyList(),
    ): Result<String> = call("playlist:create") {
        Innertube.createPlaylist(title, privacy, videoIds = videoIds)
    }

    suspend fun addToPlaylist(playlistId: String, videoIds: List<String>): Result<Unit> =
        call("playlist:add") { Innertube.addToPlaylist(playlistId, videoIds) }

    /** [entries] are (setVideoId, videoId) pairs — see [Song.setVideoId]. */
    suspend fun removeFromPlaylist(
        playlistId: String,
        entries: List<Pair<String, String>>,
    ): Result<Unit> = call("playlist:remove") {
        Innertube.removeFromPlaylist(playlistId, entries)
    }

    suspend fun renamePlaylist(playlistId: String, title: String): Result<Unit> =
        call("playlist:rename") { Innertube.renamePlaylist(playlistId, title) }

    suspend fun deletePlaylist(playlistId: String): Result<Unit> =
        call("playlist:delete") { Innertube.deletePlaylist(playlistId) }

    /**
     * Artist page. The landing page only lists ~5 songs, so the linked
     * "Top songs" playlist is fetched to fill the list out.
     */
    suspend fun artistPage(browseId: String): Result<ArtistPage> = call("artist:$browseId") {
        val page = InnertubeParser.parseArtistPage(Innertube.browse(browseId))
        val fullSongs = page.moreSongsBrowseId?.let { playlistId ->
            runCatching { songsPaged(playlistId) }.getOrNull()
        }
        if (!fullSongs.isNullOrEmpty()) page.copy(songs = fullSongs) else page
    }

    private suspend fun <T> call(label: String, block: suspend () -> T): Result<T> =
        withContext(Dispatchers.IO) {
            // Timed so a page that feels slow can be pointed at the request
            // that is actually slow, instead of guessed at. "artist:…" taking
            // seconds means the follow-up song paging, not the browse.
            val started = System.nanoTime()
            fun ms() = (System.nanoTime() - started) / 1_000_000
            runCatching { block() }
                // runCatching catches Throwable, cancellation included, which
                // would turn "the user typed another letter" into a failed
                // Result and put the abandoned request's error on screen.
                // Cancellation isn't this call's to answer for.
                .onFailure { if (it is CancellationException) throw it }
                .onSuccess { Log.d(TAG, "$label ok in ${ms()}ms") }
                .onFailure { Log.w(TAG, "$label failed in ${ms()}ms: ${it.message}") }
        }
}
