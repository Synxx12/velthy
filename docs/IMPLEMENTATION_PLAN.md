# Rencana Implementasi — Port Fitur BitChord v1.6 ke Velthy

> Dokumen perencanaan. Dibuat setelah pull BitChord terbaru ke `D:\Vs code\BitChord-latest` (branch `main`, tag **v1.6**).
> Status: **TAHAP 0 SELESAI** (terverifikasi build), TAHAP 1-4 menunggu.

## Progres

| Tahap | Status |
|---|---|
| 0 — Kecepatan mulai lagu | ✅ **SELESAI** — build sukses, CHANGELOG ditambah |
| Backend Party (NestJS) | ✅ **SELESAI** — di `backend-nest/`, build + 16 unit test + smoke end-to-end lulus |
| 1 — Mesin Addon | ✅ **SELESAI** (build sukses) — editor lewat `SourceEditorAlert` + self-naming |
| 2 — Subscribe halaman artis | ✅ **SELESAI** (build sukses) — parser fallback + `toggleSubscription` + tombol subscribe |
| 3 — Canvas wiring | ✅ **SELESAI** (build sukses) — init fix + Spotify di-wire + disk cache player + refresh backdrop |
| 4 — Lyrics translation | ✅ **SELESAI** (build sukses) — `timingSource` + 3 file baru + integrasi; provider lirik ditunda atas keputusan user |
| 5 — Listen Together / Party | ✅ **SELESAI** — UI + data + `PartySync` ke Media3 + backend `api.velthy.my.id` |
| 6 — Kontrol lirik (offset+gesture) | ✅ **SELESAI** |
| 7 — Paritas tampilan BitChord | 🟡 **SEBAGIAN** — kapsul/notifikasi/lirik-UI/Playing-from/output/Video-Music/audio-sheet selesai |
| 7-A — Port provider lirik (15) | ⬜ **TERTUNDA** — user sudah minta; detail + fakta di `HANDOFF_SESSION.md` §1b-A |
| 7-B — Hapus animasi artwork ke mini player | ⬜ **TERTUNDA** — detail + peringatan di `HANDOFF_SESSION.md` §1b-B |

> **Handoff sesi:** baca `docs/HANDOFF_SESSION.md` sebelum melanjutkan di sesi baru.
> **Pekerjaan tertunda ada di §1b handoff itu** — dibaca lebih dulu sebelum grep apa pun.

---

## 0. Konteks & Temuan Awal

| Item | Nilai |
|---|---|
| Repo BitChord sumber | `https://github.com/kushagrasinghx/BitChord` |
| Salinan lokal (up-to-date) | `D:\Vs code\BitChord-latest` → branch `main`, **v1.6** (`7072e7b`) |
| Salinan lama (jangan dipakai membandingkan) | `D:\Vs code\BitChord-original` → branch `pr-5` (`83f4398`) |
| Repo target | `D:\Vs code\Velthy` (package `com.velthy.client`), versi `1.4.6.5` |
| Jarak versi | BitChord v1.6 = **396 file berubah, +115.391 baris** dari titik fork Velthy |
| Pola kerja (sesuai HANDOFF) | Agent **hanya edit file + tambah entri CHANGELOG**. Tidak build/install. User menjalankan `.\dev.ps1` sendiri. |

**Aturan wajib (dari `.agents/AGENTS.md` & `docs/HANDOFF.md`):**
1. Setiap perubahan kode menambah entri di `docs/CHANGELOG.md` bagian `[Unreleased]` dengan severity.
2. Changelog = release notes publik (gaya Apple/Spotify), tanpa nama file internal, tanpa "seperti BitChord".
3. Jangan sentuh fitur Velthy yang sudah berfungsi (mis. auto-hide kontrol saat scroll lirik/antrean).
4. Verifikasi dengan `grep` + nomor baris (LSP tidak bisa dipercaya di repo ini).
5. Port besar harus **sekaligus** — file saling merujuk.

---

## 1. Ruang Lingkup (disetujui user)

**Prioritas yang dipilih user:**
- ✅ **Mesin Addon** (`SourceKind.ADDON`) — subsistem terbesar yang belum ada.
- ✅ **Subscribe/Stats halaman artis** (parser + tombol Subscribe/Subscribed).
- ✅ **Canvas artwork + Lyrics translation**.
- ❌ **JANGAN disentuh**: fitur Velthy yang sudah jalan (scroll lirik, auto-hide kontrol, dst).

**Dikecualikan dari lingkup sesi ini** (bisa ditambah nanti jika diminta):
- Listen Together / Party sync, Replay, Media Widget, Equalizer, provider lirik 7→16 (kecuali yang dibutuhkan translation).

**Pendekatan:** bertahap per fitur, tiap irisan harus bisa dikompilasi sendiri.

---

## 1b. TAHAP 0 — Kecepatan Mulai Lagu (disetujui user, dikerjakan lebih dulu)

### Diagnosa
Keluhan user: lagu awal di BitChord terasa cepat, di Velthy lama. Penyebab **bukan** buffer ExoPlayer / cache / player init (semuanya identik) — melainkan **strategi `SourceResolver`**:

1. **Velthy menanya sumber secara berurutan; BitChord balapan.** `substituteForYouTube` Velthy pakai `for` loop serial, BitChord pakai `bestAcross` (`async` + `select{}`). Modul kustom ~13,5s vs JioSaavn ~0,4s → di Velthy JioSaavn yang cepat ngantre di belakang modul lambat.
2. **Velthy tidak punya `prefetchSubstitute`.** BitChord menyiapkan sumber untuk lagu berikutnya lebih awal (hanya dari sumber cepat/`worthPrefetching`). Di Velthy `worthPrefetching` cuma dead config.
3. **Velthy tidak re-enable byte read-ahead saat substitute sudah dipin** (`warmed != null`).
4. **`upgradeFor` Velthy digate `if (request !is Lossless) return null` + serial.** BitChord tidak digate + balapan + `servedBy`.
5. **`resolve` Velthy tidak mengisi `sourceConfigId`** → `servedBy` tak bisa dipakai.

### Rencana
| # | File | Aksi |
|---|---|---|
| 0-T0-1 | `data/sources/MusicSource.kt` | Tambah `sourceConfigId: String? = null` ke `SourceStream`. |
| 0-T0-2 | `data/sources/TrackMatcher.kt` | Tambah `Target.album/isExplicit/isVideo`, `sharesArtist()`, `hasConflictingAlbums()`, `uniquelyMostCreditedCloseMatch()` (port dari BitChord). |
| 0-T0-3 | `data/sources/SourceRegistry.kt` | Tambah `activeForPlayback()` (untuk `substituteForYouTube`/`prefetchSubstitute`/`upgradeFor`). |
| 0-T0-4 | `data/sources/SourceResolver.kt` | Port `bestAcross()` (race) + `prefetchSubstitute()`; ubah `substituteForYouTube`/`resolve` fallback/`upgradeFor` agar memakai race; tambah `servedBy`, `isBetter`, `beatsYouTubeAac`, `requireSharedArtist`. |
| 0-T0-5 | `playback/AudioCache.kt` | Wire `prefetchSubstitute` + `StreamChoice` + `warmed != null` pada `cacheBytes`. |
| 0-T0-6 | `playback/QualityUpgrade.kt` | Hapus gate lossless, salurkan `servedBy`. |

### Risiko
- `SourceStream.sourceConfigId` baru → cek semua konstruktor `SourceStream` (named args aman karena default).
- `Target` bertambah field → cek semua pemanggil `Target(...)`.
- `AudioCache.prefetchQueue` harus terima `target` (bukan hanya id) agar bisa `prefetchSubstitute`.

---

## 2. TAHAP 1 — Mesin Addon (`SourceKind.ADDON`)

### 2.1 Ringkasan
Protokol addon = server HTTP biasa (`/manifest.json`, `/search?q=`, `/stream/{id}` balas JSON), alternatif dari modul QuickJS. Lebih sederhana: 1 server = 1 katalog, tanpa JS.

**Penting:** `SourceResolver` **tidak tahu** addon ada — `AddonSource` dipanggil lewat kontrak `MusicSource` yang sama. Kontrak `MusicSource` Velthy **identik** dengan BitChord (`configId/kind/displayName/health/search/stream`) → port aman.

### 2.2 Prasyarat (Stage 0) — dikerjakan lebih dulu
| # | File Velthy | Aksi | Alasan |
|---|---|---|---|
| 0a | `data/sources/module/SharedCalls.kt` | **BARU** (salin dari BitChord) | `AddonClient` butuh. Velthy belum punya. |
| 0b | `playback/StreamContainer.kt` | **BARU** (salin apa adanya) | `AddonSource` panggil `StreamContainer.declare`. Hanya butuh Media3 `MimeTypes`. |
| 0c | `data/sources/ModuleSource.kt` | **Tambah** `fun unplayable(format, atmosAllowed)` di companion | `AddonSource.openable` butuh. `malformed`/`qualityTier`/`LOSSLESS` sudah ada. |
| 0d | `data/sources/MusicSource.kt` | **Tambah** `val sourceConfigId: String? = null` ke `SourceStream` | Dipakai resolver. |
| 0e | `data/sources/SourceKind.kt` | **Tambah** entry `ADDON` (`rank=0`, `needsServer=true`, `canServeLossless=true`) | `SourceRegistry.build` butuh. `CUSTOM_MODULE` tetap `rank=0` agar dua-duanya `isUserAdded`. |
| 0f | `ui/components/…` + `res/values/strings.xml` | **Tambah** `AddonEditorAlert` + string `add_addon`/`add_addon_detail`/`addon_url_description` | Untuk UI. Boleh ditunda sampai Stage 3. |

### 2.3 Layer protokol (Stage 1) — bisa kompilasi sendiri
| # | File | Dependensi | Catatan |
|---|---|---|---|
| 1a | `data/sources/addon/AddonModels.kt` | kotlinx.serialization saja | Port apa adanya (rename package). |
| 1b | `data/sources/addon/AddonClient.kt` | 0a, `Http.client` (ada), `TrackLog` | Port apa adanya (rename package + `TAG`). |
| 1c | `data/sources/addon/SourceFormats.kt` | 1a, 1b, `ModuleIndex.parseModules` (ada) | Port apa adanya. |

### 2.4 Wiring sumber (Stage 2)
| # | File | Aksi |
|---|---|---|
| 2a | `data/sources/AddonSource.kt` | **BARU** — port apa adanya (rename package). |
| 2b | `data/sources/SourceRegistry.kt` | **Merge** (JANGAN salin utuh!): branch `build()` → `AddonSource(config)`; `tidied()` normalisasi URL addon; `publish()` sapu `AddonSource.release()`; tambah `identify()`, `duplicateOf()`/`canonicalUrl()`. **Pertahankan** seeding `MODULE`/`migrateLegacySources` milik Velthy. |
| 2c | `data/sources/SourceResolver.kt` | Opsional: samakan dengan versi v1.6 (raced `bestAcross`, `sourceConfigId`, `upgradeFor(servedBy)`). **Tidak wajib** untuk sekadar memakai addon. |

### 2.5 UI (Stage 3)
| # | File | Aksi |
|---|---|---|
| 3a | `ui/components/AccountAlerts.kt` (atau file baru) | Tambah `AddonEditorAlert`. |
| 3b | `ui/screens/SourcesScreen.kt` | Import `AddonSource`; pass self-naming (`manifestName()` → `config.label`); ganti baris "Add custom module" jadi "Add source" (ADDON); panggil `AddonEditorAlert`. |

### 2.6 Draft entri CHANGELOG (Tahap 1)
> Severity: **Important**
> "Sumber Addon: aplikasi kini bisa menyambung ke server addon lewat satu tautan — masukkan alamatnya, uji koneksi, lalu simpan. Addon menambah katalog, pencarian, dan streaming lossless/Hi-Res di samping sumber yang sudah ada. Bisa ditambah beberapa sekaligus, diuji, diubah, dihapus, dan diurutkan dengan geser."

### 2.7 Risiko
- `SourceResolver` Velthy (602 baris) jauh lebih lama dari BitChord (1081 baris). Menambah `AddonSource` **tidak** memaksa port resolver — addon cukup masuk `active()` dan di-walk seperti sumber biasa. Efek `declare` Atmos/DASH + dedup `sourceConfigId` baru muncul kalau resolver ikut di-port.
- Jangan salin `SourceRegistry.kt` BitChord mentah — ia menghapus `SourceKind.MODULE` saat init dan tak punya seeding `MODULE_INDEX_URL` milik Velthy.

---

## 3. TAHAP 2 — Subscribe & Stats Halaman Artis

### 3.1 Temuan penting
Velthy **sudah punya seluruh backend + UI `ArtistStatsRow`** (menunggu data). Yang kurang hanya:
1. Parser membaca key lama → stats sering kosong.
2. Tidak ada `toggleSubscription` di ViewModel.
3. UI tidak menyalurkan `onToggleSubscription`.
4. String `subscribe`/`unsubscribe` belum ada.

### 3.2 Yang SUDAH ada di Velthy (jangan dikerjakan ulang)
- `Innertube.setSubscribed` ✅ identik
- `YtMusicRepository.setSubscribed` ✅ identik
- `SubscriptionState`, `DetailPage.{subscriberCountText,monthlyListenerCount,subscription}` ✅ identik
- `ArtistStatsRow` + `StatChip` UI ✅ lengkap
- `VelthyIcons.Plus/Check`, `Haptic.ToggleOn/ToggleOff` ✅ ada

### 3.3 Gap & rencana (berurutan)
| Layer | File | Aksi |
|---|---|---|
| L4 Parser | `data/innertube/InnertubeParser.kt` | Tambah fallback key jumlah subscriber: `subscriberCountWithSubscribeText` (button2), `longSubscriberCountText`/`shortSubscriberCountText` (button1). Pertahankan `collectRenderers` + fallback visual-header untuk monthly. Opsional: fallback `serviceEndpoints[].subscribeEndpoint.channelIds` untuk channelId. |
| L5 ViewModel | `ui/MainViewModel.kt` | Tambah `toggleSubscription(browseId)` + `setSubscribedOnPage(browseId, subscribed)` meniru `toggleLibrary`/`setSavedOnPage` (optimistic + revert). Pakai `requireSignIn()` + `libraryStale`. |
| L6 UI | `ui/screens/DetailScreen.kt` | Tambah param `onToggleSubscription: (() -> Unit)? = null`; salurkan `subscription`+`onToggleSubscription` ke `ActionRow`; tambah `CircleIconButton` (Plus/Check) di depan. |
| L6b Strings | `res/values/strings.xml` | Tambah `subscribe`/`unsubscribe` (atau ikuti konvensi hardcode Velthy). |
| L7 Wiring | `MainActivity.kt` | Tambah `onToggleSubscription = if (signedIn) { { viewModel.toggleSubscription(page.browseId) } } else null`. |

### 3.4 Draft entri CHANGELOG (Tahap 2)
> Severity: **Important**
> "Halaman artis kini menampilkan jumlah subscriber dan pendengar bulanan, lengkap dengan tombol Subscribe/Subscribed yang bisa langsung ditekan (perubahan langsung terlihat, dan dibatalkan otomatis bila YouTube menolak)."

### 3.5 Risiko
- Cakupan key parser = item paling berisiko. Verifikasi dengan respons artis nyata bahwa kedua chip stat terisi.
- `ActionRow` Velthy urutannya Shuffle→Play (beda dari BitChord Subscribe→Play→Shuffle). Ikuti pola Velthy, cukup tambah tombol subscribe di depan.

---

## 4. TAHAP 3 — Canvas Artwork

### 4.1 Temuan penting
Canvas di Velthy **90% filenya sudah ada** tapi **mati**:
- `CanvasCache.init()` & `SpotifyToken.init()` **tidak pernah dipanggil** di `VelthyApplication` → `CanvasCache.cache` (`lateinit`) **crash** saat dipakai.
- Spotify **tidak di-wire** ke `CanvasRepository` (hanya Apple→Tidal→Community). `SpotifyCanvas`/`SpotifyToken` jadi dead code.
- `resolve`/`firstHit` Velthy pakai lambda **non-suspend** → harus diubah `suspend` agar `SpotifyCanvas.search` bisa dipakai.
- `SpotifyCanvasAuthScreen` ada tapi tidak punya jalan masuk dari Settings/MainActivity.
- `refreshFrameEveryMs` tidak dipakai → backdrop tidak re-tint dari klip live.

### 4.2 Rencana (mayoritas wiring)
| # | File | Aksi |
|---|---|---|
| 4a | `VelthyApplication.kt` | Tambah `CanvasCache.init(this)` + `SpotifyToken.init(this)`. **Wajib, kalau tidak crash.** |
| 4b | `data/canvas/CanvasRepository.kt` | Ubah `resolve`/`firstHit` → `suspend () -> CanvasArtwork?`; tambah `SpotifyCanvas` ke urutan (Apple→Tidal→Community→Spotify). |
| 4c | `ui/screens/SettingsScreen.kt` | Tambah baris "Set up Spotify Canvas" → `onSpotifyCanvasAuth`. |
| 4d | `MainActivity.kt` | Tambah state `showSpotifyCanvasAuth` + render `SpotifyCanvasAuthScreen` (di balik `BackHandler`). |
| 4e | `ui/player/NowPlayingScreen.kt` | Tambah `refreshFrameEveryMs = meshRefreshMs` di 2 call site `CanvasArtworkPlayer` + konstanta `MESH_REFRESH_MS`. |
| 4f | `res/values/strings.xml` | Tambah string setup Spotify Canvas (auth screen sekarang hardcode Inggris). |

### 4.3 Draft entri CHANGELOG (Tahap 3a)
> Severity: **Critical** (memperbaiki crash potensial) + **Important** (fitur)
> "Sampul animasi (Canvas) kini aktif penuh: klip bergerak dari Spotify ikut diputar di layar pemutar, dengan penyimpanan sementara sehingga tidak mengunduh ulang tiap putaran dan hemat kuota. Ada opsi mengizinkan Canvas lewat data seluler, serta panduan menyambungkan akun Spotify."

### 4.4 Catatan AppSettings
- Velthy: `spotifySpdcToken: MutableStateFlow<String?>(null)` di `secretsPrefs`; `KEY_CANVAS_CELLULAR = "canvas_over_cellular"`. **Jangan ubah nilai key** (sudah kompatibel dengan BitChord).
- `SpotifyCanvasAuthScreen` Velthy sudah pakai tipe `String?` — biarkan.

---

## 5. TAHAP 4 — Lyrics Translation

### 5.1 Temuan penting
Velthy **belum punya** kode translation sama sekali. Ini port greenfield ~1.200 baris baru. **Terpisah** dari provider lirik (translation tidak mengambil lirik; ia hanya menerjemahkan teks dengan menjaga timing).

### 5.2 File baru
| File | Baris | Isi |
|---|---|---|
| `data/lyrics/LyricsTranslation.kt` | 380 | `object LyricsTranslation` → `translate(context, trackId, lines, targetLanguageTag): Result` (cache Lru + disk GZIP, batch ≤3500 char, endpoint `translate.googleapis.com`) |
| `data/lyrics/TranslationLanguages.kt` | 189 | `TranslationLanguage`, `TRANSLATION_LANGUAGES` (~130), `translationLanguageName()` |
| `ui/components/TranslationLanguageDialog.kt` | 320 | Dialog pemilih bahasa (pakai `haze` — Velthy sudah punya dependensi haze) |

### 5.3 Integrasi
| # | File | Aksi |
|---|---|---|
| 5a | `data/settings/AppSettings.kt` | Tambah `translationLanguage` + setter + `KEY_TRANSLATION_LANGUAGE` (default blank = ikut bahasa app). |
| 5b | `ui/player/NowPlayingScreen.kt` | Tambah `LyricsTranslationUiState`, `toggleTranslation`, tombol translate di dekat kredit sumber, status string. |
| 5c | `ui/screens/SettingsScreen.kt` | Baris "Translation language" (ikon Translate) → dialog. |
| 5d | `MainActivity.kt` | State `showTranslationLanguage` + render dialog. |
| 5e | `res/values/strings.xml` | String translation (list di bawah). |

### 5.4 Adaptasi wajib
- `LyricsTranslation.flatten` BitChord memanggil `Genius.isSectionHeader` — Velthy **tidak punya `Genius.kt`**. Ganti dengan cek header lokal, ATAU port `Genius.kt` (naik lingkup).
- `LyricLine` BitChord (22.374 byte) jauh lebih besar dari Velthy (6.259 byte) — pastikan mengekspos `background`, `words`, `timingSource`, `isWordSynced`, `sungUntilMs`, `withInstrumentalGaps`. Kemungkinan titik adaptasi terbesar.
- Velthy tidak punya `ProviderLyrics.kt`/`KaraokeLrc`.

### 5.5 Draft entri CHANGELOG (Tahap 4)
> Severity: **Important**
> "Terjemahan lirik: pilih bahasa tujuan di Pengaturan, lalu ketuk tombol terjemah di layar pemutar — lirik langsung berganti bahasa tanpa kehilangan sinkronisasi waktu. Bahasa mengikuti bahasa aplikasi secara bawaan, dan hasil terjemahan disimpan sementara agar hemat kuota."

### 5.6 Keputusan user (sudah dikonfirmasi & dikerjakan)
- **Cek header lokal** — `Genius.kt` TIDAK di-port; `isSectionHeader` di-inline di `LyricsTranslation.kt`.
- **Tambah `timingSource`** ke `LyricLine` (bukan adaptasi minimal), supaya animasi karaoke tetap mulus pada teks terjemahan.
- **Translation saja dulu.** Port provider lirik (Genius/LyricsQuery/ProviderLyrics/dll) ditunda: akan merewrite `LyricsRepository`/`LyricsSource` yang sudah jalan, melanggar aturan AGENTS.md. Jadikan tahap terpisah bila diminta.

---

## 6. Urutan Pengerjaan & Verifikasi

```
TAHAP 1 (Addon)      →  TAHAP 2 (Subscribe artis)  →  TAHAP 3 (Canvas)  →  TAHAP 4 (Translation)
  Stage 0 → 1 → 2 → 3     L4 → L5 → L6 → L7            wiring saja          file baru + integrasi
```

**Setiap tahap:** selesai → tambah entri CHANGELOG → user jalankan `.\dev.ps1` → konfirmasi build hijau → lanjut tahap berikutnya.

**Estimasi besar:**
| Tahap | File baru | File diedit | Berat |
|---|---|---|---|
| 1 — Addon | 5 | 6 | Berat |
| 2 — Subscribe | 0 | 5 | Ringan |
| 3 — Canvas | 0 | 6 | Ringan–sedang (mayoritas wiring) |
| 4 — Translation | 3 | 5 | Sedang (+ perluasan lirik terpisah) |

---

## 7. Yang JANGAN disentuh (arahan user)
- Auto-hide kontrol saat scroll lirik & antrean (sudah otomatis di Velthy).
- Semua fitur Velthy yang sudah berfungsi; port hanya **menambah**, bukan mengganti.
- Jangan salin `SourceRegistry.kt`/`SourceResolver.kt` BitChord mentah-mentah.
- Jangan ubah nilai key AppSettings yang sudah kompatibel.
