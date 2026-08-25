# 📋 Changelog — Velthy Android (Client-Side Edition)

Semua pembaruan dan perubahan teknis pada Velthy Android didokumentasikan dalam file ini.

---

## 📋 Changelog

### [Unreleased]

#### ✨ Fitur Baru & Rebranding
- **Transformasi Identitas & Rebranding Menyeluruh ke Velthy (`velthy.my.id`)**:
  - Mengubah identitas brand aplikasi menjadi **Velthy** dengan Application ID `com.velthy.client` (Dev: `com.velthy.client.dev`).
  - Mengganti seluruh aset launcher icon (`ic_launcher.png`), round icon (`ic_launcher_round.png`), adaptive foreground (`ic_launcher_foreground.xml` / `ic_launcher_foreground.png`), dan seluruh folder mipmap (`mdpi` hingga `xxxhdpi`) langsung dari file resmi `ic_launcher.png` (resolusi 2000x2000).
  - Memperbarui ikon header in-app pada *Frosted Top Bar* (`FrostedTopBar.kt`) dengan siluet resmi Letter V VectorDrawable (`ic_logo.xml`) dan tipografi tebal modern "Velthy".
  - Memperbarui ikon notifikasi media dan download di status bar (`ic_notification_logo.xml`) dengan Letter V silhouette VectorDrawable.
  - Memperbarui teks in-app pada Settings, Notifications, dan Software Updater (`SettingsSheet.kt`, `NotificationsSheet.kt`, `AppUpdateSheet.kt`).
  - Memperbarui folder unduhan publik perangkat ke `Downloads/Velthy` (`DownloadStore.kt`).
  - Menambahkan deep link scheme `velthy://` pada `AndroidManifest.xml` berdampingan dengan `musique://` untuk kompatibilitas tautan Discord OAuth2.
  - Memperbarui Discord RPC buttons dan status presence ("Visit Velthy", "https://velthy.my.id").
  - Memperbarui endpoint live stats reporter dan browser live tracker ke `https://api.velthy.my.id/api/stats/ping` dan `https://velthy.my.id/stats` (`LiveStatsReporter.kt`, `SongActionsSheet.kt`).
- **Infrastruktur Repositori Resmi Standalone Open-Source (`Synxx12/velthy`)**:
  - Memisahkan repositori menjadi standalone repo mandiri publik resmi di GitHub ([`Synxx12/velthy`](https://github.com/Synxx12/velthy)) dengan lisensi open source, zero hardcoded secrets, dan integrasi CI/CD aman.
- **Refactor Namespace & Package Structure ke `com.velthy.client`**:
  - Memindahkan seluruh source package dari `com.music.musique` ke `com.velthy.client` (`app/src/main/java/com/velthy/client/` dan `app/src/test/java/com/velthy/client/`).
  - Mengubah `android.namespace` pada `build.gradle.kts` menjadi `com.velthy.client`.
  - Mengubah `rootProject.name` pada `settings.gradle.kts` menjadi `Velthy`.
  - Mengganti nama kelas aplikasi menjadi `VelthyApplication.kt`, ikon set `VelthyIcons.kt`, dan tema Compose `VelthyTheme.kt`.
- **Dukungan Arsitektur CPU Lengkap & Emulator (Multi-ABI Support)**:
  - Memperluas `ndk.abiFilters` pada `build.gradle.kts` mencakup `arm64-v8a`, `x86_64`, dan `armeabi-v7a`, menyelesaikan error instalasi `INSTALL_FAILED_NO_MATCHING_ABIS` pada emulator Android x86_64.

#### ⚡ Performa & Stabilitas
- **Smart Fade & DJ Automix Memory Optimization (Release PCM Downmix Buffers & Direct ByteBuffers)**:
  - Mengoptimalkan pass 1 (DSP feature analysis) dan pass 2 (region models) di `TrackAnalyzer.kt` dengan mengekstrak `Structural` dan `RegionInputs` untuk melepaskan alokasi buffer audio mono downmix PCM (~43MB pada durasi penuh dan ~23MB pada 48kHz) dari JVM heap sebelum menjalankan ONNX inference model.
  - Memperbarui `VocalTracker.kt` untuk menggunakan direct memory allocation (`ByteBuffer.allocateDirect` + native byte order) pada input ONNX spectrogram dan relative buffer sequential puts, mengeliminasi duplikasi memori frame per frame.
  - Membaca tensor output ONNX secara langsung via `FloatBuffer` pada `reduceToBandCurve()`, menghilangkan overhead boxing ke 4.098 object array Java (~16MB per pemanggilan).

#### 🎵 Player, Audio & Lirik
- **Default Lifecycle Setting: Stop Music on Close (`stopOnTaskRemoved = true`)**:
  - Mengubah nilai bawaan (default) `stopOnTaskRemoved` di `AppSettings.kt` menjadi `true` sehingga pemutaran musik otomatis berhenti saat aplikasi ditutup/di-swipe dari recent apps secara default.
- **Enhanced Word-by-Word & TTML Synchronized Lyrics (Vocals Tracking & Non-Overlapping Instrumentals)**:
  - Menambahkan field `sungUntilMs` dan flag `hasKnownEnd` pada `LyricLine.kt` untuk melacak durasi vokal yang sebenarnya pada format lirik TTML dan LRC per kata (`LyricsPlus.kt`, `TtmlLyrics.kt`).
  - Memperbaiki `LyricGaps.kt` agar hanya menambahkan gap instrumental ketika baris lirik memiliki batas akhir yang pasti (`hasKnownEnd == true`), mencegah teks lirik tumpang tindih dengan jeda instrumental.
- **Enhanced Stream Recovery, Substitute Tracking & Quality Tag Resilience**:
  - Memperbarui `StreamResolver.kt` dengan pelacakan client refusal (`REFUSALS_BEFORE_STANDING_DOWN = 3`), domain guard untuk validasi host `googlevideo.com`, dan penanganan `LinkageError`.
  - Memperbarui `StreamChoice.kt` dengan pelacakan flag `substituted`, deteksi kegagalan substitusi module hifi (`refuseSubstitutes(videoId)` dengan timeout cooldown 10 menit), dan pengecekan cepat `substitutesRefused` di `PlaybackService.kt`.
  - Menambahkan `forgetLastSession()` pada `NerdStats.kt` dan `QualityUpgrade.kt` untuk mereset riwayat badge audio format saat warm start service, memastikan badge kualitas selalu akurat dan tidak mewarisi status sesi sebelumnya.
  - Menambahkan isolasi try-catch pada passthrough `ChunkedDataSource.kt` dengan pelaporan error diagnostik yang aman.
  - Menambahkan helper `cachedFloor` dan durasi validasi pada `adoptCachedTrack()` di `PlaybackService.kt`.

#### 🌐 Client-Side & Innertube
- **Rebuilt & Resilient Discord Gateway RPC (Kizzy)**:
  - Menata ulang arsitektur `DiscordWebSocket.kt` ke dalam single persistent connection loop dengan pengawasan watchdog heartbeat (`HEARTBEAT_ACK`), automatic exponential backoff, dan auto-reconnect instan saat koneksi pulih (`wakeUp()` & `AppSettings.meteredConnection`).
  - Mengimplementasikan `clearActivity()` yang andal tanpa memblokir socket teardown (`DISCORD_TEARDOWN_TIMEOUT_MS = 3_000L`) dan memastikan replay presence yang akurat pada sesi gateway baru.
  - Memperbaiki `OpCodesSerializer.kt` agar memetakan opcode gateway yang belum dikenali ke `OpCode.UNKNOWN` alih-alih melempar exception yang memutus koneksi websocket.

#### 🎨 UI, Gestures & Animasi
- **Frosted Glass Navigation Bar**:
  - Memperbarui `FloatingBottomBar.kt` dan `MainActivity.kt` untuk menggunakan `dev.chrisbanes.haze.hazeEffect` dengan style `HazeMaterials.regular(container)` terpadu bersama shared `hazeState`, menghadirkan estetika *frosted glass* modern dengan fallback solid saat `reduceDynamicBlur` aktif.
- **Full-Bleed Motion & Still Artwork Across All Android Versions**:
  - Memperbaiki `CanvasArtworkPlayer.kt` dengan menambahkan callback `onCoverChanged` dan helper `FadingBottomFrame` (menggunakan offscreen `saveLayer` dan `PorterDuff.Mode.DST_IN` shader gradient) untuk perangkat Android 12 ke bawah (API < 31), serta `RenderEffect` pada API 31+.
  - Memperbarui `NowPlayingScreen.kt` dengan `canvasCover` state dan transisi halus handover alpha antara `AsyncImage` dan live canvas player.
  - Menetapkan `shape = RectangleShape` pada `ModalBottomSheet` player di `MainActivity.kt` agar artwork layar penuh tidak terpotong radius sudut default pada status bar.

#### 🐛 Bug Fixes
- **Fix Android <= 12 NewPipeExtractor URL Decoder Crash**:
  - Menambahkan vendored `Utils.java` dengan `(String, String)` URL encoder/decoder di `org.schabi.newpipe.extractor.utils` dan konfigurasi Gradle bytecode strip task untuk mencegah crash `NoSuchMethodError` pada Android 12 dan versi sebelumnya.

#### 🔧 Build & CI/CD
- **Gradle Build Strip Task for NewPipe Extractor**:
  - Menambahkan task `stripNewPipeExtractorUtils` pada `app/build.gradle.kts` untuk membersihkan class duplikat dari dependency `NewPipeExtractor` jar secara otomatis saat kompilasi.

### [v1.3.8] - 2026-08-24

#### ⚡ Performa & Stabilitas
- **GPU 60/120 FPS Lyrics & LazyList Optimization**:
  - Mengoptimalkan rendering baris lirik di `NowPlayingScreen.kt` dengan meniadakan alokasi shader `Modifier.blur()` saat nilai blur `0.dp` atau saat opsi `reduceDynamicBlur` aktif, mengeliminasi puluhan offscreen buffer Skia per frame.
  - Menambahkan parameter `key` unik dan stabil pada `itemsIndexed` (Lirik) serta seluruh `LazyRow` di `HomeScreen.kt` (`HeroShelf`, `QuickPicksShelf`, dan `Shelf`) untuk mencegah recomposition berlebihan saat scrolling feed.

#### 🎵 Player, Audio & Lirik
- **Background Lossless Transition & Mid-Song Swap Stabilization**:
  - Memperbaiki `StreamChoice.kt` agar selalu mengingat stream aktif yang sedang diputar (termasuk YouTube Opus), sehingga permintaan pembacaan chunk 2MB berikutnya di background tidak lagi memicu *resolveWithModulePriority* ulang secara *runBlocking* pada thread loader ExoPlayer.
  - Menyinkronkan fungsi `smoothMicroFadeUp` di `swapIn` agar menunggu status `STATE_READY` ExoPlayer sebelum menaikkan volume, mencegah lonjakan audio menghentak atau kegagalan saat decoder FLAC hardware sedang diinisialisasi.
  - Memperbaiki `recoverFrom` saat pemulihan stream gagal agar membersihkan marker `q=hifi` dan beralih ke fallback MediaItem yang bersih tanpa terjebak dalam error loop.
  - Menjaga persistensi entri stream di `QualityUpgrade.kt` selama pemutaran lagu berlangsung sehingga seeking dan buffer range requests tidak kehilangan URL stream.
  - **Module Stream URL Sanitization (Fix 404 on Tidal CDN)**: Menambahkan `sanitizeStreamUrl` pada `ModuleSource.kt` untuk membersihkan duplikasi prefix skema/host yang dikembalikan oleh server modul API, sehingga file stream FLAC langsung dapat diakses dengan respons HTTP 200 OK oleh ExoPlayer.
  - **Fast Lossless Fan-Out & Early Completion**:
    - Mengoptimalkan pencarian multi-modul di `ModuleSource.kt` dengan memprioritaskan modul lossless dan mengimplementasikan *early exit* begitu modul FLAC/Hi-Res (seperti Tidal/Qobuz) mengembalikan hasil, memotong waktu tunggu dari 25 detik menjadi < 1 detik tanpa menunggu modul radio/audiobook lambat.
    - Mempercepat inisiasi upgrade transisi audio (`UPGRADE_PREBUFFER_MS` dari 12s ke 5s, `UPGRADE_NOT_BEFORE_MS` ke 1.5s) agar lagu dapat segera berpindah ke kualitas lossless secara instan dan mulus.

#### 🌐 Client-Side & Innertube
- **YouTube Music Playback History & Watchtime Scrobbling Parity (ArchiveTune/InnerTune Standard)**:
  - Memperbaiki `Innertube.playbackTracking` agar menggunakan client `ANDROID_MUSIC` (klien resmi YouTube Music Android) sebagai sumber utama tracking player response, dengan fallback ke `WEB_REMIX` dan `IOS`, menghilangkan error `UNPLAYABLE: Video unavailable` yang sebelumnya memblokir pendaftaran riwayat.
  - Memperbarui versi `WEB_REMIX_VERSION` ke `1.20260707.12.00` agar selaras dengan standar Innertube terkini dan didukung penuh oleh Google API.
  - Memperbaiki `pingStats`, `pingPlayback`, dan `pingWatchtime` agar menyertakan identitas client yang sesuai (`c=ANDROID_MUSIC` / `c=WEB_REMIX`) beserta token `SAPISIDHASH` dan cookie autentikasi akun Google pengguna.
  - Memperbarui `PlaybackTracker.kt` dan `PlaybackHistoryManager.kt` agar otomatis mengirimkan sinyal watchtime berkala dan merefleksikan riwayat putar cloud (`remoteHistory`) secara real-time saat pengguna login.
- **Account Settings Integration & "More Content" Feature Parity**:
  - Mengintegrasikan toggle *More content* (`AppSettings.accountMoreContent`) ke dalam `InnertubeParser.kt` dan `Innertube.kt`, sehingga saat diaktifkan, aplikasi tidak lagi memfilter video/live carousels, album versi alternatif/eksplisit, dan rilis komunitas.
  - Menghubungkan observer `accountMoreContent` di `MainViewModel.kt` untuk langsung menyegarkan (*auto-refresh*) feed Home, Explore, dan Library seketika saat pengaturan diubah tanpa perlu me-restart aplikasi.
  - Memastikan tombol *Sync with account now*, *Auto sync with account*, dan *Force Sync on Switch Account* berjalan mulus untuk memperbarui profil akun, playlist, library, dan cloud history.

#### 🎨 UI, Gestures & Animasi
- **Informative Audio Source Quality Label in Song Actions Sheet**:
  - Memperbaiki baris opsi *Audio Source* pada `SongActionsSheet.kt` agar menampilkan status kualitas aktif secara presisi (contoh: `Audio Source (Hi-Quality 320k)`, `Audio Source (Lossless FLAC)`, `Audio Source (Hi-Res FLAC)`, atau `Audio Source (YouTube)`).
  - Menyediakan opsi pengalihan aksi yang akurat (`Switch to YouTube` jika lagu sedang diputar via modul, dan `Switch to Lossless FLAC` jika lagu diputar via YouTube standar).
- **Unified In-App Update Flow (Modal & Navbar)**:
  - Memperbarui tombol "Download Now" pada dialog `UpdateAvailableDialog.kt` dan tombol ikon update pada top app bar `MainActivity.kt` agar langsung membuka sheet pembaruan in-app (`AppUpdateSheet.kt`) yang dilengkapi progres download real-time dan instalasi otomatis APK, alih-alih melempar pengguna ke browser web GitHub Releases.

#### 🔧 Build & CI/CD
- **GitHub Actions Auto `local.properties` & `MODULE_INDEX_URL` Injection**:
  - Menambahkan step konfigurasi otomatis pada `.github/workflows/build_release_apk.yml` untuk menghasilkan `local.properties` dan menyuntikkan `MODULE_INDEX_URL` secara aman dari repository secrets `secrets.MODULE_INDEX_URL` selama proses build APK release di runner GitHub Actions.
  - Memperbarui `app/build.gradle.kts` agar mendukung pembacaan `MODULE_INDEX_URL` dari environment variable sistem `System.getenv("MODULE_INDEX_URL")`.

### [v1.3.7] - 2026-08-24

#### ✨ Fitur Baru
- **Discord Rich Presence & Kizzy RPC Gateway Integration**:
  - Mengintegrasikan Discord Gateway WebSocket client (`com/my/kizzy/gateway`) dan Kizzy RPC engine (`DiscordRPC.kt`) untuk mempublikasikan status musik yang sedang diputar secara real-time ke profil Discord pengguna.
  - Mendukung penyesuaian status (*Online*, *Idle*, *Do Not Disturb*), activity verb (*Listening*, *Playing*, *Watching*, *Competing*), custom activity title, dan dua tombol interaktif (*Listen on Musique* / *GitHub*).
  - **Discord OAuth2 Web Login (PKCE)**: Login Discord kini meluncurkan aplikasi Chrome / browser default eksternal secara langsung dengan parameter otorisasi standar PKCE (`code_challenge_method=S256`, `scope=openid identify sdk.social_layer_presence`) dan kembali secara otomatis ke Musique melalui deep link callback (`discord-1165706613961789445:/authorize/callback`) untuk pertukaran token instan tanpa perlu mengetik kredensial di dalam aplikasi.
  - Tetap menyediakan dialog manual token input (`DiscordDialogHost`) bagi pengguna yang ingin menyematkan token secara langsung.
  - Menghadirkan layar konfigurasi Discord (`DiscordScreen.kt`) dengan live visual card preview yang merefleksikan tampilan profil Discord secara persis.
- **Lossless FLAC Downloader & Binary FLAC Tagging Engine**:
  - Menghubungkan resolusi audio lossless multi-sumber (`SourceResolver.forDownload`) ke dalam alur pengunduhan (`Downloads.kt` & `Downloader.kt`).
  - Mengimplementasikan `FlacTagger.kt` untuk menyematkan metadata Vorbis Comment (Title, Artist, Album) dan Picture block cover art JPEG/PNG langsung ke dalam file FLAC byte-by-byte tanpa dependensi library eksternal berat.
  - Memperbarui `DownloadStore.kt` dengan pemetaan codec `storable` untuk format FLAC, WAV, dan ALAC.
  - **Open Hi-Res FLAC / Qobuz / Tidal Lossless Integration**: Mengintegrasikan index modul sumber terverifikasi yang menyediakan modul JavaScript murni (**Claudo**, **Qobuz**, **Improved-All-In-One**) tanpa enkripsi `8SM1`, sehingga resolusi stream audio 16-bit / 24-bit Hi-Res FLAC langsung aktif dan berjalan otomatis di engine QuickJS.
  - **SourceRegistry Persistent Initialization Fix**: Menambahkan pemanggilan `SourceRegistry.init(this)` pada `MusiqueApplication.onCreate()` sehingga konfigurasi modul FLAC dan status toggle lossless tersimpan permanen dan tidak mati kembali saat aplikasi dibuka ulang.

#### ✨ Fitur Baru
- **App Update Modal Sheet (`AppUpdateSheet`)**:
  - Menghadirkan modal bottom sheet pembaruan aplikasi baru yang diselaraskan dengan tema desain Sleep Timer dan Audio Output (dark aesthetic, header badge versi, card detail rilis, dan scrollable changelog).
  - Mengintegrasikan tombol aksi unduhan langsung (*Download & Install*) dengan visual progres live (`bytesDownloaded`, `totalBytes`, `speed MB/s`) serta opsi instalasi instan saat unduhan selesai.
  - Menampilkan tampilan status cerdas *"You're all up to date"* jika pengguna telah menggunakan versi terbaru.
  - Memperbaiki alur di `SettingsSheet.kt` dan `NotificationsSheet.kt` agar modal update tidak pernah terbuka ganda (*single-source-of-truth state*).
- **Status Bar Download Notification & Live Progress**:
  - Mengintegrasikan notifikasi Android pada `AppUpdater.kt` dengan channel `app_updates`.
  - Menampilkan progress bar live, ukuran data yang terunduh, dan kecepatan download (`MB/s`) langsung di status bar / notification shade Android saat mengunduh APK pembaruan.
  - Saat unduhan selesai, notifikasi otomatis menampilkan status *"Siap Diinstal"* lengkap dengan `PendingIntent` untuk membuka installer APK secara instan ketika disentuh.

#### 🐛 Bug Fixes
- **YouTube Music Playback History Auto-Advance Registration Fix**:
  - Memperbaiki pencatatan riwayat pemutaran ke akun YouTube Music saat lagu berganti secara alami (*auto-advance* / habis sendiri) dengan memanggil `registerCurrentPlay()` dan membuka sesi `PlaybackTracker.onPlaying` pada event `onMediaItemTransition` di `PlaybackService.kt`.
  - Sebelumnya hanya lagu yang diputar secara manual yang terkirim ke YouTube Music history, sedangkan lagu berikutnya yang diputar otomatis tidak tercatat.
- **Playlist Instant UI Sync (Add & Remove Song without Reload)**:
  - Memperbaiki sinkronisasi state halaman detail playlist (`_detailStack`) saat menambahkan atau menghapus lagu ke playlist di `MainViewModel.kt`.
  - Lagu yang baru ditambahkan kini langsung muncul di daftar lagu playlist secara instan (*real-time reactive UI*) tanpa harus menutup/membuka ulang halaman playlist atau merestart aplikasi.
- **Audio Output Switcher Crash Fix & Safe Fallback**:
  - Menangani `ActivityNotFoundException` dan `SecurityException` pada beberapa vendor Android (Xiaomi HyperOS, Samsung OneUI) saat memicu intent `MEDIA_OUTPUT` sistem dengan memeriksa `resolveActivity` terlebih dahulu dan fallback aman ke Bluetooth / Sound Settings.
- **Kompilasi Jetpack Compose & State Scope**:
  - Memperbaiki import `Arrangement` pada `SongActionsSheet.kt`.
  - Mengangkat state `sleepRemaining` ke level root `NowPlayingScreen` sehingga badge countdown Sleep Timer dan modal sheet dapat mengakses state waktu tersisa secara sinkron tanpa error deklarasi.

#### 🎵 Player, Audio & Lirik
- **Sleep Timer Smart Countdown & Stable Non-Jitter Badge**:
  - Memperbaiki badge Sleep Timer agar beralih otomatis ke format detik (`59s`, `30s`, dst.) saat sisa waktu berada di bawah 1 menit.
  - Memperbaiki opsi *"After this song"* yang sebelumnya hardcoded menampilkan teks `1s`; kini secara dinamis menghitung sisa durasi lagu aktif (`durationMs - positionMs`) dalam format menit/detik atau `End` jika durasi habis.
  - Menetapkan batas lebar minimum (`widthIn(min = 18.dp)`) pada badge agar angka countdown yang berubah setiap detik tidak menggeser icon tombol atau menyebabkan efek bergetar/jitter pada layout.
- **ExoPlayer Direct Audio Output Routing (`setPreferredAudioDevice`)**:
  - Mengintegrasikan `AudioDeviceHelper.preferredDevice` langsung ke instance ExoPlayer di `PlaybackService.kt` via `ExoPlayer.setPreferredAudioDevice(AudioDeviceInfo)`.
  - Memilih **Phone Speaker** di dalam menu Audio Output kini menggunakan *multi-layer hardware routing* (`setCommunicationDevice`, `TYPE_BUILTIN_SPEAKER_SAFE`, `isSpeakerphoneOn`, dan `ExoPlayer.setPreferredAudioDevice`) sehingga aliran audio seketika dialihkan ke speaker internal ponsel tanpa nyangkut di Bluetooth TWS.
- **Bluetooth Audio Device Deduplication & Multi-Profile Unification**:
  - Memperbaiki duplikasi perangkat TWS (seperti Redmi Buds, Galaxy Buds, AirPods) di dalam bottom sheet Audio Output dengan mengelompokkan perangkat berdasarkan nama produk unik dan memprioritaskan profil media audio terbaik (`TYPE_BLUETOOTH_A2DP` / `TYPE_BLE_HEADSET`) dibanding profil panggilan suara (`SCO`).
  - Memperbaiki tombol aksi **System Media Output Panel** agar mendeteksi perangkat Xiaomi / Redmi / POCO HyperOS dan secara otomatis mengarahkan ke menu Suara & Getaran sistem tanpa memicu error crash internal `com.android.settingslib.androidx.Fragment` bawaan ROM Xiaomi.
- **On-The-Fly Audio Source Switching (Lossless FLAC $\leftrightarrow$ YouTube Audio)**:
  - Menambahkan tombol aksi **"Audio Source"** langsung di dalam menu opsi lagu (`SongActionsSheet.kt`) saat lagu sedang diputar.
  - Jika lagu mendapatkan stream FLAC yang tidak sesuai (misalnya versi instrumental atau potongan yang berbeda dari YouTube), pengguna dapat **mengalihkan sumber audio ke "Switch to YouTube" secara instan dengan 1 tap**.
  - Begitu pula sebaliknya, jika lagu sedang memutar YouTube audio, pengguna dapat memilih **"Switch to Lossless FLAC"**. Menu akan menampilkan status **"Searching Lossless..." dengan spinner berputar aktif** dan menonaktifkan klik ganda/spam hingga file FLAC berhasil ditemukan dan diputar.
- **Lossless Cellular Data Saver & Dynamic Live Switching (No Restart Needed)**:
  - Menambahkan pengaturan baru **"Lossless on mobile data"** pada menu Audio Quality di `SettingsSheet.kt` dan `SourcesScreen.kt`. Saat dimatikan, streaming FLAC otomatis dinonaktifkan saat menggunakan kuota seluler (menggunakan format standar hemat kuota) dan kembali aktif secara otomatis saat tersambung ke Wi-Fi.
  - Memperbaiki sistem pengalihan Lossless agar berjalan **100% *live on-the-fly* secara dinamis tanpa perlu me-restart aplikasi**, serta menghapus teks usang *"Restart required on change"* dan pembersihan cache paksa saat switch diaktifkan.
- **Lossless Stream Evaluation, Duration Slack & State Reset**:
  - Memperluas toleransi durasi lagu FLAC (`UPGRADE_LENGTH_SLACK_MS`) dari 3 detik menjadi 12 detik pada `watchUpgrade` di `PlaybackService.kt`. Hal ini mencegah pembatalan/revert sepihak ke YouTube Opus ketika versi master studio FLAC memiliki perbedaan durasi wajar (seperti intro/outro hening) dibanding versi video musik YouTube.
  - Memperbaiki `QualityUpgrade.forget(mediaId)` agar membersihkan `asked` dan `refused` cache saat lagu berganti atau dimainkan ulang (`onMediaItemTransition`), sehingga saat pengguna berpindah lagu bolak-balik, aplikasi selalu mengevaluasi dan mencari kualitas Lossless secara konsisten.
  - Mengimplementasikan `QualityUpgrade.markAsked(videoId)` saat lagu aktif sedang diputar agar proses seeking/scrubbing tidak memicu spam query background berulang-ulang.
- **In-App Direct Audio Output Switching (Speaker vs Bluetooth/USB DAC)**:
  - Menambahkan daftar perangkat output aktif langsung di dalam bottom sheet (*Phone Speaker*, *Connected Bluetooth TWS / Headphones*, *USB DAC*).
  - Pengguna kini dapat langsung beralih output suara (misal: memindahkan suara ke Phone Speaker meskipun Bluetooth TWS masih terhubung) secara instan via API `AudioManager.setCommunicationDevice` / `setSpeakerphoneOn` tanpa harus keluar dari aplikasi.
- **Lossless Queue Lookahead Prefetch (Instant Gapless Next-Track Playback)**:
  - Mengintegrasikan prefetching cerdas untuk lagu berikutnya di antrean pemutaran (`prefetchAround` di `PlaybackService.kt`).
  - Saat lagu aktif sedang diputar, sistem otomatis menjadwalkan pencarian format Hi-Res FLAC untuk 1 lagu berikutnya di antrean dengan jeda aman 3.5 detik (mencegah benturan bandwidth & bebas rate limit).
  - Ketika lagu berganti secara alami, lagu berikutnya **langsung berputar dalam format 24-bit Hi-Res Lossless FLAC sejak detik ke-0 ($t=0\text{s}$)** dengan 0ms penundaan dan tanpa perlu pergantian format di tengah lagu.
- **Instant Playback Start & Seamless Micro-Fade Quality Upgrade**:
  - Mengembalikan alur `resolveWithModulePriority` agar memutar stream tercepat (YouTube Opus / cache dalam ~100ms) secara instan tanpa menunggu modul FLAC selesai.
  - Modul FLAC melanjutkan pencarian di background; saat file Hi-Res FLAC siap, sistem melakukan pergantian format di tengah lagu (*mid-song upgrade*) dengan otomatisasi **Micro-Fade Gain Smoothing** (fade down 20ms $\to$ swap $\to$ seek $\to$ fade up 40ms) dan kompensasi offset presisi (`exactPosition + 35ms`).
  - Transisi kualitas di tengah lagu kini berjalan **100% mulus (*seamless liquid transition*) tanpa suara klik/glitch, tanpa jeda hening, dan tanpa lompatan/pengulangan audio**.
- **Restorasi Animasi Lirik 60/120 FPS & Frame-Clock Synchronization**:
  - Mengembalikan kalkulasi `activeLine` berbasis frame-clock (`derivedStateOf`) pada `LyricsPanel` dan `CurrentLyricLine` agar transisi highlight kata demi kata (*syllable sweep*), efek blur halo Apple-style bloom, dan auto-scroll berjalan 100% mulus tanpa lag atau patah-patah.
  - Memperbaiki perhitungan indeks baris lirik aktif dan fade out transisi baris lirik tunggal di atas scrubber.
- **Discord RPC Auto-Recovery & Timestamp Stability Fix**:
  - Memperbaiki perhitungan `endTime` pada `DiscordRPC.kt` agar tidak menghasilkan timestamp masa lalu (`endTime <= currentTime`) yang sebelumnya menyebabkan Discord menolak activity presence saat metadata durasi masih memuat atau 0.
  - Menambahkan lazy auto-instantiation pada `pushDiscordPresence` di `PlaybackService.kt` sehingga instance `DiscordRPC` langsung terhubung kembali secara otomatis saat pemutaran lagu berjalan tanpa perlu me-restart aplikasi.
  - Memperbaiki sinkronisasi Discord Rich Presence saat lagu berganti (*auto-advance* / playlist transition) maupun saat status player menjadi `STATE_READY`.
- **Smart Audio Device Classification & Unique Vector Icons**:
  - Menambahkan klasifikasi perangkat audio cerdas di `AudioDeviceHelper.kt` yang mendeteksi jenis perangkat secara spesifik: **Wireless Earbuds / TWS** (*Galaxy Buds, AirPods, Redmi Buds, In-Ear*), **Bluetooth Headphones / Over-Ear** (*Sony WH-1000, Bose, Studio*), **Bluetooth Speaker** (*JBL, Soundbar, Echo*), **USB DAC / Hi-Res Audio**, **Wired Headphones**, dan **Internal Phone Speaker**.
  - Menyediakan ikon vektor presisi khusus untuk masing-masing kategori perangkat di `MusiqueIcons.kt` (`Earbuds`, `Headphones`, `Speaker`, `UsbDac`, `VolumeUp`).
- **Sleep Timer Unclipped Badge & Solid Color Glyphs**:
  - Memperbaiki penempatan badge Sleep Timer agar tampil utuh di sudut atas tanpa terpotong radius lingkaran (`CircleShape`).
  - Mengembalikan palet solid putih elegan standar Musique pada seluruh 4 tombol bawah (Lyrics, Sleep, Audio Output, Queue) saat aktif/inaktif tanpa pergeseran warna aksen artwork.
  - Menambahkan subtitle nama perangkat aktif (misal: "Buds2 Pro", "Speaker") tepat di bawah ikon audio dengan slot ketinggian tetap, sehingga ke-4 ikon bawah tetap sejajar 100% secara horizontal tanpa pergeseran vertikal.

#### 🎨 UI, Gestures & Animasi
- **Quick Picks 4-Song Vertical Snap Fling Behavior (YouTube Music Parity)**:
  - Menerapkan `rememberSnapFlingBehavior` pada deretan rak lagu vertikal 4-baris (*QuickPicksShelf*) di Home screen.
  - Setiap usapan (*swipe horizontal*) kini berpindah kolom secara bertahap dan presisi (*snap per column/page*) dengan lebar responsif 88% layar, mencegah pergeseran liar tak terkontrol.
- **Local Files & On-Device Music Styling (BitChord Parity)**:
  - Mempercantik kartu rak *On Device* (Downloads, Local Music / Device Files, History) pada `HomeScreen.kt` dan `LibraryScreen.kt` dengan gradien warna modern, border melengkung halus, dan ikon kontras tinggi.
  - Menambahkan banner header gradien dinamis berikon besar pada `DetailScreen.kt` saat membuka Local Music, Downloads, atau Riwayat tanpa artwork internet.
- **User Profile Login Avatar pada Top Navbar (BitChord Style)**:
  - Mengganti ikon roda gigi Settings statis di bar atas (*FrostedTopBar*) dengan avatar profil pengguna interaktif.
  - Menampilkan foto profil Google/YouTube Music (`account.thumbnailUrl`) berbingkai lingkaran saat login, inisial nama jika tidak ada avatar foto, atau ikon avatar person minimalis saat belum login.
  - Membuka halaman Settings & Akun secara instan saat avatar ditekan.
- **Full-Bleed Hero Artwork & Still Photo Player**:
  - Menambahkan dukungan tampilan foto cover album layar penuh edge-to-edge (*full-bleed*) dengan masking gradien vertikal halus (`Brush.verticalGradient`) di belakang status bar.
  - Mendukung transisi dinamis antara thumbnail sleeve saat antrean/lirik dibuka dan hero artwork saat antrean/lirik ditutup.
  - Memperbarui `CanvasArtworkPlayer.kt` dengan `bottomFade` berbasis `RenderEffect` untuk looping video canvas.
  - Menambahkan toggle pengaturan *"Full-screen cover art"* pada `SettingsSheet.kt` (otomatis terfilter pada layar lebar/tablet).
- **Penyelarasan Presisi Layout FullPlayer, Queue Capsule Pills & 4 Tombol Bawah**:
  - Menyelaraskan layout NowPlaying utama dengan presisi:
    - Cover artwork hero di atas dengan gradien vertical fade (tanpa kotak ganda).
    - Judul lagu (marquee) & Artis di kiri, tombol Heart & Menu (3-dots) di kanan.
    - Baris lirik tunggal (*CurrentLyricLine*), scrubber, timestamps, transport controls (Rewind/Play/Next), dan volume bar.
    - 4 tombol utama di paling bawah: **Lyrics · Sleep Timer · Live Audio Device Switcher · Queue** (dengan status badge).
  - **Kontrol Bawah Persisten saat Lirik & Antrean Dibuka**: Memastikan seluruh kontrol pemutar bawah (Seekbar, Transport Play/Pause/Skip, Volume slider, dan 4 tombol bawah) tetap dapat diakses di bagian bawah layar saat panel lirik atau antrean dibuka di bagian atas.
  - **Capsule Pill Controls di Header Antrean**: Menyematkan deretan tombol kapsul interaktif (**Shuffle · Repeat/One · AutoPlay · Clear**) di bagian atas daftar `InlineQueue`.

#### 🐛 Bug Fixes
- **NowPlayingScreen Compile & OptIn Fix**:
  - Memperbaiki unresolved references dan smart cast `remaining` pada direct Sleep Timer sheet di `NowPlayingScreen.kt`.
  - Menambahkan `@OptIn(ExperimentalMaterial3Api::class)` untuk komponen `ModalBottomSheet` dan `rememberModalBottomSheetState`.

#### 🔧 Refactor & Infrastruktur
- **ADB Wireless Debugging & Multi-Pairing mDNS Parser Fix (`dev.ps1`)**:
  - Memperbaiki parser `Get-ConnectedDevices` pada `dev.ps1` agar mendukung nama serial perangkat nirkabel mDNS yang mengandung spasi dan tanda kurung (seperti `adb-... (2)._adb-tls-connect._tcp`).
  - Mengutip argumen `-s "$device"` pada seluruh eksekusi `adb install`, `adb am start`, dan `adb logcat` sehingga dev runner otomatis mendeteksi ponsel fisik secara instan tanpa error *"Tidak ada perangkat/emulator ADB yang aktif"*.
- **Penyelarasan Total Identitas & Namespace Musique Native**:
  - Mengubah seluruh namespace dan package aplikasi dari `com.music.bitchord` menjadi `com.music.musique`.
  - Memperbarui nama kelas inti, objek, dan composable dari `BitChord*` menjadi `Musique*` (`MusiqueApplication`, `MusiqueIcons`, `MusiqueTheme`, `MusiqueTypography`, `MusiqueApp`).
  - Menyelaraskan seluruh path direktori paket source code (`app/src/main/java/com/music/musique/`, `test/`, `androidTest/`).
  - Memperbarui konfigurasi build `rootProject.name = "Musique"` pada `settings.gradle.kts` serta `namespace = "com.music.musique"` pada `app/build.gradle.kts`.
  - Menyelaraskan `AndroidManifest.xml` (`.MusiqueApplication`, `@style/Theme.Musique`), resource theme styles, serta script dev tooling (`dev.ps1`, `.vscode/tasks.json`, `keystore.properties.example`).

### [v1.3.6] - 2026-08-23

#### ✨ Fitur Baru
- **Automix & Smart Transition Engine (DJ Mixing Bertenaga AI / On-Device DSP)**:
  - Mengintegrasikan model neural machine learning on-device (`beat_this_int8.onnx` untuk beat/downbeat tracking dan `vocals_umxhq_int8.onnx` untuk vocal presence tracking via ONNX Runtime Android).
  - Melakukan analisis audio in-memory real-time (`TrackAnalyzer`, `MelSpectrogram`, `BeatTracker`, `VocalTracker`) untuk mendeteksi tempo, ketukan downbeat, spektrum frekuensi, dan posisi jeda vokal secara presisi.
  - **TransitionPlanner & TransitionPolicy**: Menghitung titik transisi optimal (*smart transition window*) antar lagu berdasarkan keselarasan tempo (*BPM alignment*), kesamaan energi, dan penghindaran tabrakan vokal (*vocal avoidance*).
  - **TransitionFilterProcessor & CrossfadeController**: Menerapkan dynamic low-pass/high-pass filtering ala DJ profesional saat transisi berlangsung untuk pengalaman audio seamless tanpa jeda.
- **Engine Peningkatan Kualitas Audio Multi-Sumber (Multi-Source Lossless & HQ Audio Upgrade)**:
  - Mengintegrasikan arsitektur modular sumber audio pihak ketiga (`data/sources/`) yang ditenagai oleh QuickJS JavaScript virtual machine (`QuickJsExecutor`).
  - **SourceResolver & QualityUpgrade**: Secara otomatis mencari dan meningkatkan stream YouTube standar ke resolusi FLAC Lossless / High-Res / 320kbps dari sumber audio alternatif yang dikonfigurasi.
  - **SourcesScreen**: Layar konfigurasi prioritas sumber audio dan modul resolusi musik independen.
- **Layar Pengelolaan Lagu Lokal (Local Device Music Player)**:
  - Layar `LocalMusicScreen` terpadu dengan tab pemindaian berkas audio lokal di memori perangkat (**Songs**, **Artists**, **Albums**).
- **Penyempurnaan Layar Pencarian & Top Bar Input Terpadu (Search Tab Parity)**:
  - Mengintegrasikan `SearchFilterTabs` bergaya Material 3 `TabRow` 1.4 (**Songs**, **Albums**, **Artists**, **Playlists** dengan ikon native dan indikator transisi).
  - Menyematkan input pencarian kapsul interaktif (`SearchTopBarField`) langsung di dalam navbar atas (`FrostedTopBar`) dengan placeholder otentik *"Artists, Songs, Lyrics and More"*, tombol pembersih satu-ketukan (*one-tap clear*), dan tombol *Music Recognition*.
  - Menjaga layout daftar hasil pencarian (`SongRow`, `BrowseRow`, `HorizontalDivider`, recent searches, dan explore categories) sinkron dan responsif.
- **Apple Music Bottom Control Bar (Lyrics, Sleep Timer, Audio Switcher, Queue)**:
  - Mengembalikan 4 tombol kontrol bawah player ala Apple Music:
    1. **Lyrics Button (`LyricsQuote`)**: Membuka/menutup layar lirik penuh secara instan dengan indikator status aktif.
    2. **Sleep Timer Button (`Moon`)**: Membuka `SleepTimerModalSheet` dengan live countdown dan preset waktu (15m, 30m, 45m, 1h, after song).
    3. **Audio Output Switcher (`AirPlay` / `Headphones`)**: Menampilkan nama perangkat output aktif secara live (Bluetooth / Speaker) dan membuka audio route picker saat diklik.
    4. **Queue Button (`QueueMusic`)**: Membuka daftar antrean dengan badge mode playback dinamis (Shuffle, Repeat, AutoPlay) dan kapsul pills reordering di dalam antrean.
- **Word-Synced Karaoke Lyrics (Lirik Sinkron Kata-demi-Kata Apple Music Style)**:
  - Menghadirkan rendering lirik sinkronisasi tingkat kata (*word-level timestamps*) dengan animasi sweep highlight yang mulus menggunakan interpolasi frame clock `withFrameNanos`.
  - **Animasi Menyusut, Membesar & Multi-Aksi Penutupan Lirik (*Seamless Lyrics Dismiss Interactions*)**:
    - Saat lirik dibuka (`lyricsProgress`), cover album besar menyusut (*shrink*) secara mulus dengan kurva `FastOutSlowInEasing` ke thumbnail kecil di kiri atas, sementara teks judul bergeser ke sampingnya.
    - **5 Cara Menutup Lirik Seketika**:
      1. **Tombol Close (✕) di Header**: Ikon titik tiga di pojok kanan atas bertransformasi menjadi tombol `Close` lingkaran (✕) saat lirik terbuka.
      2. **Pill Bawah `[ Lyrics by ... · Close ]`**: Mengetuk pil kredit lirik di bagian bawah langsung menutup lirik.
      3. **Ketuk Thumbnail Cover / Header Judul**: Menyentuh cover thumbnail kecil di kiri atas atau bar judul akan menutup lirik dan membesarkan (*expand*) cover album kembali ke ukuran penuh.
      4. **Tombol Lirik di Bottom Control**: Menekan kembali ikon 💬 lirik di baris kontrol bawah.
      5. **Gestur Kembali (Back Gesture)**: Mengusap layar untuk kembali langsung menutup lirik tanpa menutup player.
  - Efek visual Apple Music Bloom / Glow: Efek pendaran cahaya dinamis yang melingkupi kata yang sedang dinyanyikan sesuai durasi ketukan intonasi vokal.
  - Preview satu baris dinamis (*swept single line strip*) di atas slider scrubber player saat lirik ditutup.
  - **Optimalisasi Jarak & Tata Letak Player (Seamless Symmetrical Spacing)**:
    - Eliminasi Total `VerifyError` Dalvik DEX Register Overflow: Memecah composable raksasa `NowPlayingScreen` menjadi sub-komponen terpisah (`NowPlayingSleeveAndHeader` dan `NowPlayingBottomControls`) sehingga bytecode terverifikasi 100% aman oleh ART Runtime Android tanpa batas register.
    - Pembatasan Batas Layar Lirik & Antrean (Zero Horizontal Overflow): Menghapus offset bleed horizontal negatif (`bleedHorizontally`) pada `LyricsPanel` dan `InlineQueue`, serta menyelaraskan padding horizontal dengan margin player (`PLAYER_GUTTER`) sehingga teks lirik panjang dan baris antrean terpotong rapi (*clean ellipsis*) dan tidak pernah menembus / terpotong melewati tepi kanan layar.
    - Visibilitas Penuh Tombol Close / More Header: Mengganti offset horizontal (`offset(x = titleStart)`) pada baris judul header dengan padding awal dinamis (`padding(start = titleStart)`), sehingga tombol Close (`X`) dan tombol More di sudut kanan atas tetap berada tepat di dalam batas layar dan tidak terdorong keluar saat lirik/antrean dibuka.
    - Stabilitas Penuh Kontrol Bawah (Rock-Solid Bottom Controls): Mengembalikan kontrol bawah (Scrubber, Play/Pause/Skip, Volume slider, dan Bottom Actions) agar selalu dirender secara konstan di posisinya pada bagian bawah layar. Saat lirik atau antrean dibuka/tutup, kontrol bawah tidak bergerak atau melompat sama sekali, dan animasi transisi mengecilkan sleeve album berlangsung mulus di area atas.
    - Menghilangkan Celah Kosong Judul ke Preview Lirik (Tight Symmetrical Flow): Mengunci tinggi container sleeve artwork sesuai ukuran proporsional alami (`naturalArtBlockHeight = topArtPadding + fullArtWidth + titleGap + HEADER_HEIGHT`) saat lirik tertutup. Saat volume bar disembunyikan, jarak antara cover ke judul diturunkan secara proporsional (`titleGap = 26.dp`) tanpa menurunkan foto album, sehingga ruang di atas preview lirik terisi padat dan seimbang.
    - Menghilangkan Kekosongan Area Play/Pause (Fixed Balanced Bottom Spacing): Mengganti spacer lentur berlebih pada area bawah dengan spacer tetap yang rapat dan presisi (18-24dp antara scrubber ke tombol transport, 18-30dp antara transport ke bar volume/bottom actions) sehingga tombol Play/Pause tidak mengambang dan memiliki proporsi yang solid dan pas.
    - Breathing Room Atas Cover Album: Memberikan top margin yang nyaman (12-20dp) di bawah dismiss handle agar cover album tidak mepet ke tepi atas layar.
    - Memperbaiki sinkronisasi fokus lirik (*focusLine* & *CurrentLyricLine*) agar tidak melompat ke ikon not balok instrumental saat baris sedang dinyanyikan, melainkan tetap konsisten menyorot teks lirik aktif baik pada panel lirik penuh maupun *preview strip* di atas scrubber.
- **Pengenal Musik Cerdas & Visualizer Audio Live (Music Recognition & Live Waveform Graph)**:
  - **Perbaikan Mesin Pengenalan Lagu (Dual UUID Shazam Discovery)**: Memperbarui endpoint discovery `amp.shazam.com/discovery/v5/en-US/GB/android/-/tag/{uuid1}/{uuid2}` dengan header Android resmi, sehingga pencarian judul lagu dan artis dari rekaman mikrofon berhasil 100% akurat.
  - **Real-Time 22-Band Equalizer Sound Wave Visualizer Graph**: Mengganti animasi statis dengan visualizer bar spektrum multi-band yang berkedut dan melompat (*live bounce*) secara dinamis mengikuti intonasi nada dan kekuatan suara mikrofon secara real-time.
  - **Sensitivitas Respon Dinamis (Perceptual Dynamic Curve)**: Kalkulasi RMS mikrofon kini diproses dengan kurva logaritmik dan pembaruan 30ms sehingga visualizer bereaksi instan terhadap ketukan drum dan vokal lagu.
- **Pusat Pembaruan Aplikasi & Notifikasi Resmi (App Update Center & Notification Hub)**:
  - **Penyatuan Top Bar Notifikasi Tunggal**: Menghapus header internal redundan pada `NotificationsSheet.kt` sehingga tab notifikasi menggunakan *FrostedTopBar* sistem secara mulus tanpa double navbar.
  - **Tombol Notifikasi di Top Bar Home**: Menambahkan tombol lonceng notifikasi (`NotificationsNone`) di bar atas Home bersebelahan dengan ikon Settings untuk membuka lembar notifikasi (`NotificationsSheet`).
  - **Lembar Notifikasi Cerdas (`NotificationsSheet`)**: Menampilkan status update software terbaru, changelog rilis, dan notifikasi status pemutaran/komunitas secara terpusat.
  - **Menu Periksa Pembaruan di Settings**: Menambahkan section `About & Updates` di `SettingsSheet.kt` dengan opsi *Check for updates* live spinner (memeriksa langsung ke GitHub Releases), versi aktif saat ini, dan tautan kode sumber resmi.
  - **Penyelarasan Footer Settings**: Merapikan teks informasi versi dan engine di bagian paling bawah pengaturan agar bersih, konsisten, dan terstruktur.
- **Identitas Brand Resmi Musique (Official Musique Logo & Notification Mark)**:
  - Mengintegrasikan logo resmi Musique (`ic_logo.xml`) berbasis VectorDrawable resolusi tak terbatas pada *Frosted Top Bar* di seluruh tab layar.
  - Memperbarui ikon notifikasi status bar (*playback media notification* & *download notification*) menggunakan siluet resmi Musique (`ic_notification_logo.xml`).
  - Memperbarui nama aplikasi menjadi **Musique** di `strings.xml` serta memperbarui tautan repository di Settings ke `https://github.com/Synxx12/musique-android`.
- **Home Feed Otentik & Kecepatan Muat Instan (Instant 0ms Cache-First Feed & Quick Picks Skeleton)**:
  - **Otentik Quick Picks Skeleton**: Skeleton loading layar Home kini 100% merefleksikan tata letak aslinya dengan kolom 4-lagu vertikal, tombol *Play all*, serta shimmer kartu carousel dan album.
  - **Instant Cache-First Architecture (`cachedHomeShelves`)**: Menyimpan feed beranda dalam memori lokal sehingga aplikasi terbuka seketika (*0ms latency*) tanpa menunggu putaran jaringan berulang, sambil melakukan sinkronisasi data baru di latar belakang (*silent background refresh*).
- **Manajemen Audio Focus & Panggilan Telepon (Professional Audio Engine)**:
  - ExoPlayer dikonfigurasi dengan `C.USAGE_MEDIA`, `C.AUDIO_CONTENT_TYPE_MUSIC`, `handleAudioFocus = true`, dan `handleAudioBecomingNoisy = true`. Musik otomatis jeda (*pause/duck*) saat ada panggilan telepon masuk atau notifikasi navigasi, dan otomatis berlanjut (*resume*) saat panggilan berakhir tanpa intervensi manual.
- **Pencatatan Riwayat Putar Real-Time (Playback History Recording)**:
  - Mengintegrasikan pencatatan otomatis ke `PlaybackHistoryManager.recordPlay(song)` pada saat audio mulai berbunyi (`registerCurrentPlay`) dan saat transisi lagu baru (`onMediaItemTransition`), sehingga tab History langsung mencatat setiap pemutaran lagu secara instan.
- **Deteksi Rute Audio Output Real-Time (Live Audio Device Output Updates)**:
  - Mengintegrasikan `AudioManager.AudioDeviceCallback` pada `rememberActiveAudioDevice` untuk mendeteksi penambahan atau pelepasan perangkat audio (Speaker, Bluetooth TWS/Headphones, USB DAC) secara instan tanpa perlu membuka-tutup layar player.
- **Web Live Ticker / Tracker Integration (Anonymous Now-Playing Ping)**:
  - Mengirimkan broadcast judul lagu secara 100% anonim (`LiveStatsReporter`) ke ticker live web Velthy (`https://velthy.my.id/api/stats/ping`) tanpa melacak data pribadi.
  - Menyediakan toggle kendali di **Settings > Privacy & Community** (*Share to Web Live Ticker*).
  - Menambahkan menu **Web Live Tracker** pada lembar aksi lagu (*SongActionsSheet*) untuk membuka langsung dashboard statistik live di browser (`velthy.my.id/stats`).
- **Penyedia Lirik Multi-Sumber Cerdas (Intelligent Multi-Source Lyrics Engine Cascade)**:
  - **In-Memory Thread-Safe Cache**: Menyimpan lirik yang berhasil ditemukan ke dalam cache memori instan (`0ms`) berdasarkan `videoId` dan kombinasi `cleanTitle + cleanArtist`, mencegah hilangnya lirik secara tiba-tiba (*disappearing lyrics*).
  - **Pembersihan Metadata Pintar (`LyricsCleaner`)**: Menghapus noise tag YouTube seperti `(Official Video)`, `(feat. ...)`, `[Visualizer]`, `(4K)` dan mengekstrak primary artist untuk memaksimalkan kecocokan pencarian di semua server lirik.
  - **Eksekusi Paralel Kompetitif 4 Provider Global**:
    1. **BetterLyrics**: Lirik TTML Apple Music resmi dengan sinkronisasi suku kata/kata berkualitas tinggi.
    2. **LyricsPlus**: Database lirik komunitas terkurasi dengan dukungan word-synced & line-synced.
    3. **SimpMusic**: Provider ekstrak lirik kaya dari database Spotify/YouTube Music berbasis `videoId`.
    4. **LRCLIB**: Penyedia lirik publik bebas kunci (*key-less*) dengan 3 tahap fallback pencarian (Exact -> Field Search -> Fuzzy Query `q="$title $artist"` dengan toleransi durasi lagu).
  - **Prioritas Word-Synced**: Memberikan prioritas mutlak pada lirik sinkron kata (*word-synced / rich-sync*) dari BetterLyrics / LyricsPlus / SimpMusic, dan otomatis fallback mulus ke lirik baris (*line-synced*) jika lirik kata tidak tersedia.
  - Dialog pemilihan sumber lirik (**LyricsSourcesDialog**): Memungkinkan pengguna mengaktifkan/menonaktifkan masing-masing penyedia lirik dan melihat kredit sumber lirik yang aktif.
- **Latar Belakang Video Motion Canvas (Animated Artwork Canvas Player)**:
  - Menghadirkan pemutaran video canvas gerak loop vertikal (*Apple Music Motion Canvas, Tidal Video Canvas, dan Community Canvas*) di balik cover album player.
  - Ditenagai oleh engine video ExoPlayer in-memory dengan auto-fallback mulus ke artwork statis resolusi tinggi jika video canvas tidak tersedia.
- **Modal Manajemen Multi-Akun (Saved Accounts Bottom Sheet)**:
  - Menekan tombol kapsul `[ 👤 Account ▾ ]` pada kartu profil kini membuka bottom sheet interaktif **Saved accounts** untuk mengelola dan beralih antarakun Google YouTube Music secara instan.
  - **Daftar Akun Tersimpan**: Menampilkan nama akun, avatar/ikon profil, handle/email, indikator akun aktif (`✓`), dan tombol hapus (`🗑️`).
  - **Save Current Account**: Tombol kapsul `[ 🔖 Save current account ]` untuk menyimpan sesi akun yang sedang aktif ke penyimpanan lokal terenkripsi secara persisten.
  - **Add Another Account**: Tombol kapsul `[ ➕ Add another account ]` untuk masuk ke akun Google baru via WebView tanpa menghilangkan akun yang sudah ada.
  - Beralih akun (*switch account*) otomatis menyinkronkan ulang data Home, Library, Playlist, dan Listening History akun terpilih.

#### 🐛 Bug Fixes
- **Audio Output Switcher BroadcastReceiver Safety (Android 14/15 Compatibility)**:
  - Memperbaiki potensi `SecurityException` saat membuka player dengan menggunakan `ContextCompat.RECEIVER_NOT_EXPORTED` pada registrasi `BroadcastReceiver` status audio/Bluetooth.
  - Membungkus pembacaan perangkat output audio aktif dalam mekanisme `runCatching` sehingga 100% tahan error (*crash-proof*) di semua versi Android dan vendor perangkat.
- **FocusRequester Initialization Safety pada Search Tab**:
  - Menghapus instansiasi duplikat `searchFocusRequester` yang tidak terikat di `MainActivity.kt`.
  - Mengamankan pemanggilan `focusRequester.requestFocus()` di `SearchScreen.kt` dengan penundaan frame layout (`delay(100)`) dan pelindung `runCatching` guna mencegah `IllegalStateException: FocusRequester is not initialized`.

#### 🔧 Build & Infrastruktur
- **Pembaruan Toolchain Build**:
  - Memperbarui Gradle ke **8.11.1**, Android Gradle Plugin (AGP) ke **8.10.1**, dan Kotlin Compiler ke **2.3.20**.
  - Mengintegrasikan dependensi `onnxruntime-android:1.28.0`, `quickjs-kt-android:1.0.5`, dan `media3-exoplayer-hls:1.5.1`.
  - Mengoptimalkan alokasi memori heap Gradle JVM menjadi 6GB (`-Xmx6144m`) untuk proses Dex Merging D8 yang cepat dan stabil.

---

### [v1.3.5] - 2026-08-23

#### ✨ Fitur Baru
- **Sinkronisasi Riwayat Cloud Otomatis (Pure Background Auto-Sync)**:
  - Memisahkan riwayat lagu yang diputar di perangkat lokal (**Local: Played on this device**) dan riwayat autentik murni yang tersinkronisasi dari YouTube Music cloud (**Remote: Synced from YouTube Music**).
  - Sinkronisasi dengan server YouTube Music berjalan 100% otomatis di latar belakang saat aplikasi dibuka, saat playback terdaftar (`registeredPlays`), dan saat pengguna membuka/beralih ke tab `[ Remote ]`, tanpa mengharuskan aksi manual dari pengguna.
  - Menyediakan kartu ringkasan interaktif dengan segmented control `[ Local ]` dan `[ Remote ]` lengkap dengan penghitung total lagu dan status sinkronisasi.
  - Memperkuat transmisi ping statistik `pingPlayback` dan `pingWatchtime` di `Innertube.kt` (`lact`, `el=detailpage`, `ns=yt`, `docid`, `volume=100`, `state=playing`) serta deteksi cookie `SAPISID` guna memastikan riwayat putar tervalidasi 100% di server YouTube Music.
- **Pengaturan Sinkronisasi Akun YouTube Music (Account & General Settings Parity)**:
  - **More content**: Opsi preferensi untuk menyertakan konteks autentikasi akun pada query katalog Innertube guna menampilkan album premium dan konten yang dipersonalisasi.
  - **Auto sync with account**: Opsi untuk mengaktifkan atau menonaktifkan sinkronisasi otomatis riwayat (*history*), playlist, dan *liked songs* di latar belakang.
  - **Force Sync on Switch Account**: Opsi untuk melakukan rekonsiliasi ulang (*deep re-sync*) semua data playlist, riwayat putar, artis, dan album saat berganti akun Google.
  - **Sync with account now**: Tombol aksi manual untuk menarik pembaruan cloud riwayat mendengarkan dan library YouTube Music secara instan kapan saja.

- **Transmisi Watchtime Cepat & Andal (Fast 5s Watchtime Tracking)**:
  - Mempercepat ping awal watchtime ke Google di `PlaybackTracker.kt` menjadi 5 detik pertama (kemudian setiap 15 detik), serta auto-flush saat player di-pause agar riwayat putar langsung tercatat di server YouTube Music tanpa harus menunggu 30 detik.
  - Memperkuat parsing `videoId` pada `parseResponsiveListItem` di `InnertubeParser.kt` dengan fallback menyeluruh (`watchEndpoint`, `navigationEndpoint`, flex columns, dan `findStringDeep`) sehingga seluruh entri riwayat cloud terekstrak dengan andal.
- **Pencatatan Riwayat Putar Menyeluruh (Comprehensive Playback Recording)**:
  - Memastikan setiap lagu yang diputar langsung tercatat ke riwayat lokal melalui trigger terpadu di `MediaController.playSongs`, `onIsPlayingChanged`, dan `onMediaItemTransition`, sehingga tidak ada lagu yang terlewat meskipun diputar dari klik langsung, shuffle, maupun transisi trek.
- **Ekstraksi Riwayat Cloud YouTube Music Lengkap & Akurat**:
  - Memperbarui `parseHistorySections` di `InnertubeParser.kt` dengan pencarian rekursif seluruh `musicShelfRenderer` dari payload `FEmusic_history` YouTube Music sehingga semua grup riwayat (*Today, Yesterday, This week, dll.*) terekstrak 100% tanpa ada yang terlewat.
- **Deduplikasi Riwayat Putar Otomatis (Move to Top on Replay)**:
  - Saat lagu yang sudah ada di riwayat diputar kembali, entri lama otomatis dihapus dan posisinya dipindahkan ke urutan paling atas (*newest/recent*) sehingga tidak ada duplikasi lagu yang sama di dalam daftar riwayat.
- **Optimasi Rendering Zero-Lag 120 FPS pada Layar Riwayat**:
  - Mengubah struktur LazyColumn agar menggunakan recycling item native (`items(key = ...)`) per baris lagu, menggantikan nested column yang sebelumnya menyebabkan frame drop dan rendering berat.
  - Menghilangkan swipe box merah berat yang menyebabkan glitch visual saat scrolling.

#### 🎨 UI, Gestures & Animasi
- **Penyelarasan Desain Modern, Bersih & Minimalis pada History Screen**:
  - Mengintegrasikan navigasi layar History langsung ke dalam navbar terpadu `FrostedTopBar` (satu tombol kembali dan judul *History* yang elegan di atas, tanpa duplikasi tombol back).
  - Mengadopsi tata letak baris lagu modern sesuai standar `SongRow` Musique (`PAGE_GUTTER`, thumbnail 52dp bersih, tipografi `titleMedium` & `bodyMedium`).
  - Kartu ringkasan atas (*Summary Card*) dengan segmented control `[ Local ]` & `[ Remote ]` yang presisi dan responsif.
  - Penyorotan lagu yang sedang aktif diputar dengan warna `primary` lembut dan indikator visualizer equalizer soundwave (`ıll`).
  - Tombol aksi mengambang **Shuffle** (`[ 🔀 Shuffle ]`) Material 3 di sudut kanan bawah untuk mengacak antrean lagu.
- **Penyelarasan Desain Layar Akun (YouTube Music Account Menu Parity)**:
  - Kartu profil akun modern dengan avatar besar ber-badge centang biru terverifikasi (*verified badge*), nama tampilan tebal, dan handle `@username`/email.
  - Tombol aksi terintegrasi di dalam kartu profil: tombol kapsul `[ 👤 Account ▾ ]` untuk *switch account* / login ulang, serta tombol `Log out` berwarna merah/salmon dengan modal dialog konfirmasi.
  - Pengelompokan baris pengaturan yang bersih ke dalam grup **General** dan **Integration** (ListenBrainz, Last.fm, dan slider timing scrobble).

---

### [v1.3.4] - 2026-08-22

#### ✨ Fitur Baru
- **Halaman Riwayat Mendengarkan (Listening History) Sesuai Layout Asli YouTube Music**:
  - Menyelaraskan desain antarmuka dengan tema YouTube Music (Header bar ramping `← History`, filter chip `All`, `Music`, `Podcasts`, dan section header `Today`, `Yesterday`, `This week`).
  - Menampilkan badge tipe lagu (`REMIX`, `Song • Artist`) dan menu 3-titik di setiap baris lagu.
  - Menambahkan tombol pintasan **History (🕒)** di pojok kanan atas tab **Library** tepat di samping tombol *Settings* (⚙️).
- **Perbaikan Sinkronisasi Real-Time YouTube Music & Watchtime Tracking**:
  - Memperbaiki binding `cpn` (*client-playback-nonce*) pada `postMusic("player")` dan `pingPlayback`/`pingWatchtime` di `Innertube.kt`, sehingga sesi pemutaran audio di Musique tervalidasi secara sah oleh server YouTube Music dan langsung masuk ke riwayat akun YouTube resmi.
  - Memperluas dukungan ekstraksi cookie SAPISID (`__Secure-3PAPISID`, `__Secure-1PAPISID`, `PAPISID`) untuk autentikasi signature watchtime.
  - Menambahkan event listener `onIsPlayingChanged` pada `PlaybackService.kt` untuk memastikan pemutaran lagu selalu terdaftar ke Google Account saat status audio aktif.
- **Fitur Music Recognition (Pengenal Musik / Song Identifier) 100% Client-Side**:
  - Mengintegrasikan engine pengenal musik cerdas berbasis mikrofon (`AudioRecord` 16-bit PCM 44.1kHz) yang dapat mengidentifikasi musik yang sedang diputar di sekitar pengguna secara instan (*serverless*).
  - Menyediakan modal interaktif `MusicRecognitionSheet` dengan animasi gelombang suara dinamis (*soundwave visualizer*), pencocokan fingerprint, dan resolusi lagu otomatis ke YouTube Music untuk langsung diputar atau dimasukkan ke antrean.
  - Menambahkan tombol aktivasi pengenal musik (**`Icons.Rounded.GraphicEq` / `ıll`**) di samping kanan *Search Top Bar*.
- **Auto-Reseed AutoPlay Saat Mode Loop Dimatikan**:
  - Memperbaiki `autoplaySeed` di `MainActivity.kt` agar otomatis di-reset saat mode pengulangan (*Repeat / Loop*) dimatikan, sehingga radio mix AutoPlay otomatis ditarik kembali dan antrean terus berlanjut tanpa henti.
- **Default `stopOnTaskRemoved` Diubah Menjadi Aktif (`true`)**:
  - Mengubah konfigurasi bawaan `stopOnTaskRemoved` di `AppSettings.kt` menjadi `true`, sehingga audio pemutaran otomatis berhenti saat aplikasi ditutup/dibersihkan dari daftar Recent Apps.
- **Penyempurnaan Sleep Timer Modal Sheet & Countdown Realtime**:
  - Menyelaraskan tampilan `SleepTimerModalSheet` di `NowPlayingScreen.kt` dengan `SongActionsSheet.kt`: menampilkan countdown menit & detik yang berjalan realtime (*live countdown*), opsi *"After this song"* di bagian teratas, pilihan preset durasi (15m, 30m, 45m, 1 jam), dan tombol *"Turn Off Timer"* merah yang elegan.
- **Penyempurnaan Tata Letak & Spasi 4 Tombol Bottom Bar**:
  - Mengatur spasi horizontal dan kontainer 44dp yang seimbang (`SpaceEvenly`), menyelaraskan baseline vertikal ikon audio switcher dan label nama perangkat TWS/speaker tanpa menekan atau merusak kerapian baris tombol.

#### 🎨 UI, Gestures & Animasi
- **Penyelarasan Desain Bottom Bar 4-Tombol & Badge Mode Antrean (Apple Music Parity)**:
  - **4 Tombol Bottom Bar Utama**:
    1. **Lyrics (Paling Kiri)**: Ikon gelembung dialog tanda kutip (`MusiqueIcons.LyricsQuote`) untuk membuka/menutup tab lirik secara instan.
    2. **Sleep Timer (Kiri-Tengah)**: Ikon bulan sabit (`MusiqueIcons.Moon`) dengan indikator dot biru saat aktif; membuka modal sheet pilihan waktu tidur (15m, 30m, 45m, 1 jam, Akhir lagu, Matikan timer).
    3. **Audio Output Switcher (Tengah / Kanan-Tengah)**: Menampilkan ikon dinamis (`Headphones`, `AirPlay`, `Speaker`) dan teks nama perangkat output aktif secara live (seperti nama TWS/AirPods/Bluetooth headset atau Phone Speaker) via `AudioDeviceHelper`. Posisi ikon sejajar presisi horizontal dengan tombol lainnya tanpa terdorong oleh teks.
    4. **Queue dengan Badge Mode (Paling Kanan)**: Ikon daftar antrean dilengkapi **badge lingkaran mini di sudut kanan atas** yang menampilkan ikon mode aktif secara dinamis (`🔀` Shuffle, `🔁`/`🔂` Repeat, `♾️` AutoPlay).
  - **Tombol Kapsul di Panel Antrean**: Memindahkan tombol **Shuffle**, **Repeat/Loop**, dan **AutoPlay** ke bagian atas panel antrean (*Queue*) dalam bentuk 3 tombol kapsul pil modern dengan feedback visual aktif/nonaktif yang kontras.
- **Penyelarasan Desain Search Tab & Pinned Top Search Bar**:
  - Memindahkan input pencarian ke **Top Bar yang ter-pin (*pinned top bar*)** di dalam `FrostedTopBar`, sehingga input pencarian tidak ikut tergeser/hilang saat halaman di-scroll ke bawah.
  - Menghilangkan logo aplikasi dan tombol *Settings* pada tab Search untuk tampilan pencarian yang bersih dan fokus.
  - Menambahkan section **Recent searches** dengan baris kartu lagu yang baru saja diputar (*horizontal carousel*) serta daftar kata kunci riwayat pencarian lengkap dengan tombol isi-cepat (↖).
  - Menambahkan 2x2 grid **Explore Categories** dengan kartu gradien modern (*New Releases, Top Charts, Moods & Genres, Podcasts & Shows*).
- **Adaptasi Kontras Otomatis Strip Handle & Status Bar pada Artwork Terang**:
  - Mengimplementasikan deteksi luminansi dinamis pada area atas album cover (`isBitmapTopLight` di `MeshGradient.kt`).
  - Apabila cover lagu memiliki latar belakang putih/terang di bagian atas, strip handle atas (*grab handle*) dan ikon status bar sistem (jam, baterai, sinyal) otomatis beralih menjadi gelap/hitam dengan border kontras halus, sehingga navigasi geser selalu terlihat jelas (*high-contrast visibility*).
- **Animasi Buka/Tutup Tab Lirik yang Mulus & Ringan (Spring Physics Parity)**:
  - Mengintegrasikan state animasi pegas `lyricsProgress` (`Spring.StiffnessMediumLow`) yang selaras 100% dengan panel antrean (*Queue*).
  - Saat tab lirik dibuka/ditutup, teks lirik meluncur naik-turun secara halus (`translationY = 26.dp` + `alpha fade`), artwork mengempis/mengembang secara proporsional, dan judul lagu berpindah posisi tanpa kekakuan atau efek patah sama sekali.
- **Transisi Crossfade Menyatu & Eliminasi Foto Ganda/Double Saat Menutup Tab**:
  - Menyambungkan transisi foto kotak album dan foto *fullscreen edge-to-edge* melalui interpolasi `expandProgress` yang mulus di `NowPlayingScreen.kt`.
  - Memperbaiki geometri thumbnail mini saat menutup tab lirik/antrean pada mode *fullscreen*: thumbnail mini memudar lembut di posisinya tanpa mengembang ke tengah, sehingga **melenyapkan efek foto ganda (double artwork)** sepenuhnya.
  - Menghilangkan pergeseran horizontal canggung pada latar belakang fullscreen saat gesture geser lagu (*swipe gesture*), sehingga foto layar penuh tetap terkunci stabil dan proporsional di area atas layar.

#### 🌐 Client-Side & Innertube
- **Zero-Rate-Limit In-App Update Resolution**: Mengimplementasikan pengecekan rilis via HTTP 302 `/releases/latest` redirect header pada `AppUpdateChecker.kt` untuk melenyapkan kendala limitasi kuota 60 request/jam GitHub API REST tanpa autentikasi, sehingga pembaruan versi APK terdeteksi 100% realtime di seluruh jaringan/perangkat.

---

### [v1.3.3] - 2026-08-22

#### 🎨 UI, Gestures & Animasi
- **Navigasi & Gesture Penutupan Lirik/Antrean yang Lebih Intuitif**:
  - Mengubah perilaku handle bar di bagian atas saat panel lirik/antrean terbuka: geser ke bawah (*swipe down*) atau tap pada handle atas kini **menutup panel lirik/antrean** kembali ke player, bukan menutup seluruh sheet player.
  - Mengubah ikon menu header kanan atas menjadi tombol **Close (X)** saat lirik atau antrean aktif agar pengguna dapat menutup panel lirik langsung dari atas dengan sekali tap.
- **Default Immersive Full Artwork Player**:
  - Mengatur mode artwork cover album edge-to-edge full screen sebagai tampilan standar bawaan saat player dibuka, dengan transisi halus dan persistensi antarlagu.
- **Optimalisasi Spacing Player & Lyrics Preview Touch Target**:
  - Menggeser baris *lyrics preview* ke atas menjauhi area scrubber bar untuk mencegah sentuhan lirik tidak sengaja menggeser posisi waktu lagu.
  - Memangkas jarak kosong (*dead space*) antara judul & nama artis dengan baris preview lirik sehingga tata letak player lebih proporsional, padat, dan rapi.
- **Penyempurnaan Visual & Kontras Player Controls (Protective Bottom Scrim & Text Shadow)**:
  - Menambahkan lapisan *protective dark gradient scrim* halus di separuh bawah layar player agar teks judul, artis, scrubber, dan tombol play/pause selalu memiliki kontras tinggi dan tidak bertabrakan dengan gambar cover album yang terang.
  - Memperhalus kurva gradien vertikal cover album edge-to-edge agar bertransisi mulus ke dalam backdrop mesh player.
  - Menambahkan *soft typography drop shadow* pada judul lagu dan artis untuk memastikan teks tetap tajam dan mudah dibaca pada kondisi cover album apapun.
- **Peningkatan Visual Drag & Reorder Antrean (Fluid Floating Card)**:
  - Mengubah background baris antrean yang sedang digeser menjadi solid frosted dark surface (`#1E1E22` 96% opacity) dengan border aksen lembut dan soft drop shadow untuk mencegah teks dan cover baris di bawahnya tembus pandang atau bertumpukan saat digeser.
  - Memperhalus padding vertikal dan sudut melengkung `12.dp` yang menyatu rapi dengan backdrop player.

#### 🌐 Client-Side & Innertube
- **MWEB & WEB_REMIX Stream Resolution**: Menambahkan client identitas `MWEB` dan `WEB_REMIX` ke daftar resolver `StreamResolver.kt` sehingga URL stream terpecahkan secara instan dengan decipher signature otomatis.
- **NewPipe Extractor Direct ID Resolution**: Memperbaiki pemanggilan `extractStream` dengan `fromId(videoId)` langsung untuk mencegah kegagalan ekstraksi URLDecoder di Android 8.0–12.
- **Penyelarasan Tag In-App Update Checker**: Memperbarui parser rilis pada `AppUpdateChecker.kt` agar memprioritaskan tag `native-v*` dan mengabaikan tag rilis versi lawas edisi Flutter (`v1.0.*`).

---

### [v1.3.2] - 2026-08-22

#### 🐛 Bug Fixes
- **Perbaikan Buffering / Loading Tak Berhenti di Android 8–11 (Redmi Note 8 Pro / MediaTek & Older HALs)**:
  - Mematikan output `Float PCM` di `DefaultAudioSink` (`setEnableFloatOutput(false)`) dan mengunci ke standard 16-bit PCM untuk mencegah driver hardware AudioTrack MediaTek / Android 10 membeku (*freeze/hang*) saat menulis buffer audio.
  - Memperbaiki validasi MIME type di `StreamResolver.kt` `probe()` agar tidak lagi menolak container `video/webm`, `video/mp4`, atau `application/octet-stream` yang dikembalikan server YouTube.
  - Mengoptimasi ukuran chunk verifikasi probe dari 2 MB menjadi 128 KB untuk mencegah timeout probe di jaringan seluler/4G pada HP versi Android lama.

---

### [v1.3.1] - 2026-08-22

#### ✨ Fitur Baru
- **Immersive Fullscreen Player**: Double-tap pada artwork lagu untuk memperluas cover album secara edge-to-edge dari bagian atas dengan gradien fade halus ke bawah ala YouTube Music.
- **Quick Picks di Home Feed**: Menampilkan seksi Quick Picks di bagian teratas Home screen dalam tata letak kolom 4-lagu yang interaktif dengan tombol "Play all" dan 1-tap playback.
- **Opsi Background Lifecycle ("Stop playback on close")**: Menambahkan toggle di menu Settings > Playback untuk menghentikan atau melanjutkan pemutaran musik saat aplikasi disapu dari Recent Apps (Task Manager).
- **Web Live Ticker Integration (Anonymous Now-Playing Ping)**: Menambahkan `LiveStatsReporter` yang mengirimkan broadcast judul lagu secara 100% anonim ke ticker live web Musique tanpa melacak data pribadi, disertai toggle kendali penuh di Settings > Privacy & Community.
- **In-App APK Direct Updater (OTA Update Parity dengan Edisi Flutter)**: Menambahkan sistem pembaruan APK langsung di dalam aplikasi (download progress real-time dengan speed/persentase, preview changelog, integrasi Android `FileProvider`, dan peluncur otomatis Package Installer tanpa perlu buka browser). Disertai tombol manual "Check for updates" di menu Settings.

#### ⚡ Performa & Stabilitas
- **Optimasi Tab Lirik (Lyrics 60/120 FPS)**: Mengalihkan perubahan alpha & skala lirik langsung ke GPU `graphicsLayer` dan mem-bypass komputasi blur saat tidak diperlukan untuk memastikan scroll lirik ultra-smooth dan bebas jank.
- **Fluid Queue Reordering**: Memperluas area sentuh handle geser antrean menjadi 38x44dp, menambahkan efek animasi `Spring` Compose saat bertukar posisi, serta efek elevasi mengambang dan highlight visual.

#### 🎵 Player, Audio & Gestures
- **Perbaikan Swipe Backward/Previous**: Memperbaiki gesture gesek ke kanan pada artwork player agar selalu responsif mengulang lagu dari awal (`0:00`) atau kembali ke lagu sebelumnya.

#### 🐛 Bug Fixes
- **Perbaikan Force Close saat Memutar Lagu (SpatialAudioProcessor & ResolvingDataSource)**: 
  - Memperbaiki potensi error `ArithmeticException: / by zero` dan `ArrayIndexOutOfBoundsException` di `SpatialAudioProcessor.kt` saat buffer delay audio belum terisi penuh atau saat Spatial Audio / Dolby Atmos aktif.
  - Membungkus seluruh error stream resolution di `PlaybackService.kt` (`ResolvingDataSource.Factory`) dengan `java.io.IOException` agar kegagalan jaringan/resolusi ditangani secara graceful oleh ExoPlayer (`onPlayerError`) dan tidak mematikan process/aplikasi (*Fatal Exception*).
  - Menambahkan pengecekan batas index (`index in songs.indices`) dan pelindung `runCatching` pada lambda pemutaran lagu di `MainActivity.kt`.
  - Mengamankan proses ekstraksi warna `rememberArtworkPalette` di `ArtworkPalette.kt` dari potensi crash akibat format gambar bitmap yang tidak valid.

#### 🔧 Build & CI/CD
- **Rebranding ke Musique Android (Client-Side Edition)**: Menyesuaikan nama aplikasi, metadata, dokumentasi `README.md`, dan `.agents/AGENTS.md`.
- **GitHub Actions Release Pipeline**: Menambahkan `.github/workflows/build_release_apk.yml` untuk build otomatis APK rilis (`Musique-vX.X-client.apk`) dan publikasi ke `Synxx12/musique-app-releases` beserta trigger webhook.
- **Arsitektur Rilis Tag Prefix (`native-v*` / `cloud-v*`)**: Migrasi tag rilis dari `v*` ke `native-v*` (Kotlin) dan `cloud-v*` (Flutter) agar kedua edisi bisa dirilis independen di satu repo `musique-app-releases` tanpa bentrok versi atau auto-increment yang saling ganggu.
- **Multi-Source Release API**: Memperbarui `musique-web/app/api/release/route.ts` untuk mendukung resolusi rilis dari tag prefix (`native-v*`, `cloud-v*`) dengan backward compatibility ke tag legacy `v*`.
- **Diferensiasi Package ID (`com.musique.client`)**: Mengubah `applicationId` menjadi `com.musique.client` dan nama aplikasi menjadi `Musique Native` agar kedua versi (Client-Side Native vs Flutter Cloud) bisa diinstal berdampingan di satu HP tanpa saling menimpa (*coexist side-by-side*).
- **Perbaikan Release APK Signing (V2/V3 Certificate)**: Mengonfigurasi `signingConfigs` agar rilis APK selalu ditandatangani dengan keystore resmi `musique-release.jks` sehingga tidak lagi menghasilkan APK *unsigned* yang memicu error *"package appears to be invalid"* di Android.
- **In-App Update Checker Update**: Memperbarui `AppUpdateChecker.kt` agar otomatis memeriksa tag rilis `native-v*` dari repositori rilis resmi.
- **Interactive Dev Runner**: Memperbaiki script `dev.ps1` agar perintah log (`l`) mencetak log tanpa memblokir terminal.

---

### [v1.3.0] - 2026-08-22
- Rilis awal migrasi Musique Android Client-Side Edition.
- Dukungan pemutaran offline, gapless crossfade (0–12 detik), dan integrasi lirik LRCLIB.
