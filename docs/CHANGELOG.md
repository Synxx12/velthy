# 📋 Changelog — Musique Android (Client-Side Edition)

Semua pembaruan dan perubahan teknis pada Musique Android didokumentasikan dalam file ini.

---

## 📋 Changelog

### [Unreleased]

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
