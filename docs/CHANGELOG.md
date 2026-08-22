# 📋 Changelog — Musique Android (Client-Side Edition)

Semua pembaruan dan perubahan teknis pada Musique Android didokumentasikan dalam file ini.

---

## 📋 Changelog

### [Unreleased]

#### ✨ Fitur Baru
- **Halaman Riwayat Mendengarkan (Listening History) Parity Apple Music & YouTube Music**:
  - Menghadirkan halaman riwayat interaktif (`HistoryScreen.kt`) dengan pengelompokan tanggal otomatis (*Hari Ini*, *Kemarin*, *Minggu Ini*, *Bulan Ini*, *Sebelumnya*).
  - Dilengkapi kontrol cepat **Play All** (Putar Semua), **Shuffle** (Acak Riwayat), dan tombol hapus riwayat (*Clear History*).
  - Mendukung gesture geser (*swipe-to-dismiss*) untuk menghapus lagu tertentu dari riwayat, menu 3-titik, dan kartu pintasan di tab **Library**.
- **Sinkronisasi Otomatis YouTube Watch History & Local Playback Storage**:
  - `PlaybackHistoryManager.kt` mencatat seluruh pemutaran lokal secara persisten di penyimpanan perangkat dengan deduplikasi cerdas.
  - Setiap lagu yang berbunyi terhubung langsung dan disinkronkan ke riwayat akun YouTube Music pengguna via `PlaybackTracker` (`videostatsPlaybackUrl` & `videostatsWatchtimeUrl`).

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
