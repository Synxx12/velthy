# HANDOFF — Sesi Lanjutan (Velthy ↔ BitChord v1.6)

> ⚠️ **SUDAH USANG SEBAGIAN.** File ini ditulis untuk port **v1.6**. Sesi berikutnya
> (port **v1.7** + party + backend) ada di **`docs/HANDOFF_PARTY.md`** — **baca itu dulu**.
> File ini tetap berguna untuk riwayat port v1.6 dan perangkap yang masih berlaku.
>
> Baca file ini **dulu**, sebelum grep apa pun. Semua temuan di bawah sudah diverifikasi.
> Tujuan: **tidak ada pengetahuan yang perlu diulang.**

Repositori:
- **Target**: `D:\Vs code\Velthy` — package `com.velthy.client`, versi `1.4.6.5`
- **Sumber (up-to-date)**: `D:\Vs code\BitChord-latest` — sekarang di tag **v1.7** (`c83ff32`)
- **JANGAN dipakai membandingkan**: `D:\Vs code\BitChord-original` (masih branch lama `pr-5`)
- Backend party: `D:\Vs code\Velthy\backend-nest` (NestJS, mandiri)
- Rencana: `D:\Vs code\Velthy\docs\IMPLEMENTATION_PLAN.md`

---

## 0. Yang WAJIB dibaca lebih dulu

1. `D:\Vs code\Velthy\.agents\AGENTS.md` — aturan changelog & severity.
2. `D:\Vs code\Velthy\docs\HANDOFF.md` — handoff lama, masih relevan untuk fitur yang sudah ada.
3. `D:\Vs code\Velthy\docs\IMPLEMENTATION_PLAN.md` — rencana tahap 0–4.

**Aturan kerja (jangan dilanggar):**
- Setiap perubahan kode **wajib** menambah entri di `docs/CHANGELOG.md` bagian `[Unreleased]` + severity.
- Changelog = release notes **publik** (gaya Apple/Spotify). Jangan sebut nama file, jangan tulis "seperti BitChord".
- Jangan sentuh fitur Velthy yang sudah jalan (scroll lirik, auto-hide kontrol, dll).
- Verifikasi dengan **grep + nomor baris** (LSP tidak bisa dipercaya di repo ini).
- User menjalankan `.\dev.ps1` sendiri. Agent boleh build untuk cek error, tapi jangan install ke perangkat.

---

## 1. STATUS PROGRES

| Tahap | Status | Catatan |
|---|---|---|
| **0 — Kecepatan mulai lagu** | ✅ **SELESAI**, build sukses | Detail di §2 |
| **Backend Party (NestJS)** | ✅ **SELESAI**, build + 16 test + smoke lulus | Detail di §3 |
| **1 — Mesin Addon** | ✅ **SELESAI**, build sukses | UI + self-naming + wiring editor. Detail di §4 |
| **2 — Subscribe halaman artis** | ✅ **SELESAI**, build sukses | Detail di §5 |
| **3 — Canvas wiring** | ✅ **SELESAI**, build sukses | Detail di §6 |
| **4 — Lyrics translation** | ✅ **SELESAI**, build sukses | Detail di §7 |
| **5 — Listen Together / Party** | ✅ **SELESAI** (UI + data + **sync playback**), build sukses | Kapsul audio+party + client + `PartySync` ke Media3. Detail di §7b |
| **6 — Kontrol lirik (offset+gesture)** | ✅ **SELESAI**, build sukses | Offset sheet + reveal-on-tap; auto-scroll/auto-hide dipertahankan. Detail di §7c |
| **7 — Paritas tampilan BitChord** | 🟡 **SEBAGIAN**, build sukses | Kapsul, notifikasi, lirik (tipografi+falloff+gap), Playing-from, OutputCaption, Video/Music, audio sheet, .gitignore backend. **Detail di §7d + §1b** |
| **8 — Provider lirik (16)** | ✅ **SELESAI**, build + unit test sukses | Port 8 file + rewrite repository + UI urutan/key. Detail di §1b-A |
| **9 — Animasi artwork ke mini player** | ❌ **BELUM** | Hapus animasi posisi mandiri mesh backdrop. Detail di §1b-B |

---

## 1b. PEKERJAAN TERTUNDA

### A. Lirik: paritas **provider** + **UI/UX** — ✅ **SELESAI** (sesi ini)

**Sudah dikerjakan (build + unit test sukses):**

| Bagian | Aksi |
|---|---|
| File baru | `BiniLyrics.kt`, `Unison.kt`, `Megalobiz.kt`, `YouTubeLyrics.kt` (music+transcript), `Genius.kt`, `ProviderLyrics.kt` (berisi `KaraokeLrc`), `LyricsQuery.kt`, `LyricAlignments.kt` |
| `LyricLine.kt` | `enum LyricAlignment { Start, End }` + field `alignment` |
| `TtmlLyrics.kt` | `qualified()` (Android Expat baca `ttm:agent`/`ttm:role`/`xml:id` beda), `agentTypes()`, side per baris, `harden()` opsional |
| `BetterLyrics.kt` | `portato()` (QQ karaoke) + `ProviderLyrics.parse` |
| `LyricsPlus.kt` | param `isrc`, `element`/`singer`, `metadata.agents`, alignment |
| `PaxSenix.kt` | `spotifyLyrics`, `musixmatchLyrics`, `setApiKey`, `parseLrcGet`, `parseTimedApple` |
| `LyricsHttp.kt` | `lyricsGetBearer` + `authenticatedClient` (15s) |
| `Innertube.kt` | `transcript(videoId)` untuk takarir YouTube |
| `LyricsSource.kt` | **16 entry** (BiniLyrics, BetterLyrics, Portato, PaxSenix×3, LyricsPlus, SimpMusic, Unison, YouTube captions, YouTube Music, Megalobiz, KuGou, LRCLIB, Musixmatch, Genius) |
| `LyricsRepository.kt` | rantai 16 provider + ISRC identify (BiniLyrics) + Genius lazy + `prioritizeSyllableSync`; **API Velthy `sources`/`order` + cache tetap** |
| `AppSettings.kt` | `lyricsSourceOrder`, `prioritizeSyllableSync`, `paxSenixApiKey` (di `secretsPrefs`), `resetLyricsSourceSettings`, `KEY_LYRICS_SOURCES_SEEN` + `LEGACY_SOURCES` |
| `LyricsSourcesDialog.kt` | drag-reorder (`DragHandle` + `key(source)`), toggle word-by-word, kolom API key (masked), **Reset to default** |
| `MainViewModel.kt` / `LyricsTag.kt` | meneruskan `order` + `prioritizeSyllableSync` |
| `LyricsTranslation.kt` | `isSectionHeader` lokal → `Genius.isSectionHeader` |

**Catatan / belum:**
- **`alignment` (duet) sudah terisi tapi BELUM digambar UI.** Panel Velthy sengaja centering dan belum punya lane kiri/kanan ala BitChord. Bila mau baris duet tampil beda sisi, itu perubahan UI terpisah di `NowPlayingScreen.LyricsPanel` (`val duet = lines.any { it.alignment == LyricAlignment.End }` + `textAlign` per baris, lihat BitChord ~5027/5389).
- Verifikasi user di perangkat (`.\dev.ps1`) belum; `compileDevDebugKotlin` + `testDevDebugUnitTest` sukses.
- **Keputusan lama yang tetap berlaku:** centering Velthy (`scrollOffset = -third`) dipertahankan, auto-scroll + auto-hide kontrol + `timingSource` terjemahan tidak disentuh.

### B. Animasi artwork "terbang ke mini player" — **hilangkan** (BELUM)

**Klarifikasi user (sesi ini):** yang dimaksud adalah **artnetwork (mesh/network gradient) yang bergerak dari mini player ke full player** — saat buka/tutup full player, mesh harus **mengikuti animasi** dan **tidak** menggambar jalur sendiri yang "jalan ke kiri bawah" dari/ke posisi mini player. Jadi **bukan** `CoverMorphOverlay` (itu justru harus dipertahankan), melainkan **mesh backdrop** (`MeshGradient.kt` / `ArtworkMeshBackdrop.kt` / `rememberArtworkMesh` di `NowPlayingScreen` ~681-1090) yang perlu ikut animasi transisi, bukan punya animasi posisi sendiri.

**Belum dikerjakan.** Rencana sesi berikutnya: baca `NowPlayingScreen` blok mesh, cari animasi offset/posisi mandiri (bukan fade), dan buat ia mengikuti `morph`/`playerProgress` yang sama dengan `CoverMorphOverlay`.

---

## 2. TAHAP 0 — Kecepatan Mulai Lagu (SELESAI)

**Diagnosa:** Velthy menanya sumber berurutan; BitChord balapan. Modul ~13,5s vs JioSaavn ~0,4s → di Velthy yang cepat ngantre di belakang yang lambat.

**File yang diubah:**
| File | Perubahan |
|---|---|
| `data/sources/MusicSource.kt` | `SourceStream` + `sourceConfigId: String? = null` |
| `data/sources/TrackMatcher.kt` | `Target` + `album/isExplicit/isVideo`; `sharesArtist()`; `hasConflictingAlbums()`; `uniquelyMostCreditedCloseMatch()`; `albumKey()`; import `Locale` |
| `data/sources/SourceRegistry.kt` | `activeForPlayback()` (= `active()` untuk sekarang) |
| `data/sources/SourceResolver.kt` | `bestAcross()` (race pakai `async`+`select`); `prefetchSubstitute()`; `isBetter()`; `matchAndStream(requireSharedArtist)`; `resolve`/`substituteForYouTube`/`upgradeFor` pakai race; `canSubstituteForYouTube()` tanpa gate lossless |
| `playback/AudioCache.kt` | `prefetchQueue(List<Upcoming>)` + `data class Upcoming(mediaId, target)`; wire `prefetchSubstitute` + `StreamChoice.remember`; `cacheBytes = !substitutable \|\| warmed != null`; import `TrackMatcher` |
| `playback/PlaybackService.kt` | `prefetchAround` bikin `Upcoming` + target; hapus job lossless 3,5s; `servedBy = quick.sourceConfigId` |
| `playback/QualityUpgrade.kt` | `settledForLess(servedBy)`; buang gate lossless; `Pending.servedBy`; `couldStillUpgrade` buang gate lossless + guard `m=1`; hapus import `StreamRequest` |
| `docs/CHANGELOG.md` | Entri `[Unreleased]` → "Lagu Mulai Jauh Lebih Cepat" + "Lagu Berikutnya Disiapkan Lebih Awal" + "Peningkatan Kualitas Suara Lebih Lincah" |

**Verifikasi:** `gradlew compileDevDebugKotlin` → BUILD SUCCESSFUL.
**Belum:** dijalankan user di perangkat (`.\dev.ps1`).

---

## 3. BACKEND PARTY — NestJS (SELESAI)

Lokasi: `D:\Vs code\Velthy\backend-nest\` — **folder mandiri**, jalan sendiri.

**Struktur:** `startup.mjs` (entry point tunggal) → `src/bootstrap.ts` (1 HTTP server untuk REST+WS) → `src/app.module.ts` → `src/app.controller.ts` (REST) → `src/party/party.socket.ts` (WS pakai `ws` langsung) + `src/common/{clock,codes,config,protocol,heartbeat.service,all-exceptions.filter}.ts` + `src/hub/hub.ts` + `src/party/{party,party.service}.ts`.

**Verifikasi:** `tsc --noEmit` 0 error; `npm test` 16/16 lulus; `npm run build` → 12 file JS; smoke produksi & dev **OK**.

**Dua bug yang sudah diperbaiki (jangan diulang):**
1. `.tsbuildinfo` basi → `dist` kosong. Fix: `clean` sebelum `tsc`, buang `incremental`.
2. DI by-type `undefined` di dev path (tsx tidak punya `emitDecoratorMetadata`). Fix: **semua provider pakai token eksplisit** (`@Inject(CONFIG)`, `@Inject(PartyService)`).

**Cloudflare Tunnel:** `npm run tunnel` (skrip `scripts/tunnel.mjs`) baca `TUNNEL_TOKEN` dari `.env`. Binary asli di `node_modules/cloudflared/bin/cloudflared.exe` (JANGAN pakai `.cmd` shim — bikin `spawn EINVAL` + path spasi pecah). Egg: `egg-velthy-party.json` (12 variable, termasuk `TUNNEL_TOKEN`).

**Cara jalan (cmd, bukan PowerShell):**
```
cd backend-nest
npm run build
set PORT=8080 && node startup.mjs
```

⚠️ **Satu instance saja** — party hidup di memori proses.

---

## 4. TAHAP 1 — MESIN ADDON (SELESAI)

### Sudah dikerjakan (build sukses)
| File | Aksi |
|---|---|
| `data/sources/module/SharedCalls.kt` | **BARU** — salinan BitChord, package di-rename |
| `playback/StreamContainer.kt` | **BARU** — salinan BitChord, `bitchord://` → `velthy://` |
| `data/sources/ModuleSource.kt` | **Tambah** `internal fun unplayable(format, atmosAllowed)` di companion |
| `data/sources/SourceKind.kt` | **Tambah** entry `ADDON` (rank 0, needsServer=true, canServeLossless=true) |
| `data/sources/addon/AddonModels.kt` | **BARU** — salinan BitChord, di-rename |
| `data/sources/addon/AddonClient.kt` | **BARU** — salinan BitChord, di-rename |
| `data/sources/addon/SourceFormats.kt` | **BARU** — salinan BitChord, di-rename |
| `data/sources/AddonSource.kt` | **BARU** — salinan BitChord, di-rename |
| `data/sources/SourceRegistry.kt` | **Edit**: import addon; `build()` + branch `ADDON -> AddonSource(config)`; `publish()` + sapu `AddonSource.release()`; `identify()`; `duplicateOf()`; `canonicalUrl()`; `defaultPort()` |
| `ui/screens/SourcesScreen.kt` | **Edit**: `when(config.kind)` + branch `ADDON -> Icons.Rounded.Extension` |
| `ui/screens/SourcesScreen.kt` | **Edit (UI)**: baris "Add custom module" → `AddSourceRow` "Add source" (buat `SourceConfig(kind = ADDON)`); self-naming addon di loop probe (`AddonSource.manifestName()` → `SourceRegistry.update(copy(label=))`); import `AddonSource` |
| `MainActivity.kt` | **Edit (editor)**: `SourceEditorAlert` sekarang pakai `SourceRegistry.identify()` + `duplicateOf()` + `probeCandidate()`, bukan hanya `probeCandidate`. Helper lokal `identifyAndProbe()` + `SourceProbe` (top-level private data class). Tambah `SourceProbe` dekat konstanta `BOTTOM_CHROME_DROP` |
| `docs/CHANGELOG.md` | **Edit**: entri `[Unreleased]` "Sumber Addon (fondasi)" → "Sumber Addon" lengkap |

### Catatan penting
- Rename otomatis: `com.music.bitchord` → `com.velthy.client`, `TAG = "BitChord"` → `"Velthy"`, `bitchord://` → `velthy://`. Skrip rename sudah dihapus; kalau perlu lagi, buat `_rewrite.cjs` baru.
- `TrackLog` Velthy (`data/TrackLog.kt`) **sudah kompatibel** — punya `d/i/w/e(tag, message)` + overload throwable. **JANGAN timpa dengan versi BitChord** (itu merusak ~200 referensi; sudah pernah terjadi dan diperbaiki).
- `Http.kt` ada di `com.velthy.client.data.Http`.
- `DeviceCodecs.kt` ada di `com.velthy.client.data.sources.DeviceCodecs` (same package dengan AddonSource).
- `SourceRegistry.kt` Velthy **tetap mempertahankan** seeding `MODULE`/`migrateLegacySources` (BitChord v1.6 menghapusnya) — itu disengaja.

### Yang sudah diselesaikan di sesi lanjutan (sebelumnya "YANG BELUM")
1. **Editor addon** — **TIDAK perlu port `AddonEditorAlert`**: Velthy sudah punya `SourceEditorAlert` di `ui/components/AccountAlerts.kt` (~baris 626) dengan tanda tangan & struktur identik. Yang dilakukan: `MainActivity` kini memanggil `SourceRegistry.identify()` + `duplicateOf()` di dalamnya (sebelumnya hanya `probeCandidate`), sehingga satu editor mengenali addon, manifes, maupun indeks modul sekaligus.
2. **String resources** — **TIDAK dilakukan**: Velthy konvensinya hardcode string (lihat `LastfmLoginAlert` dll). `AddonEditorAlert` BitChord memakai `stringResource`; Velthy `SourceEditorAlert` hardcode. Ikuti konvensi Velthy.
3. **`SourcesScreen.kt`** — baris "Add custom module" diganti `AddSourceRow` "Add source" (ADDON). ✅
4. **Self-naming pass** — ditambahkan di loop `LaunchedEffect(probeKey)`. ✅
5. **Entri CHANGELOG** — ✅ di `[Unreleased]`.

### Catatan desain Velthy (beda dari BitChord)
- BitChord menaruh `SourceEditorAlert` di dalam `SourcesScreen.kt` (composable internal). Velthy menaikkannya lewat `onEditSource` ke `MainActivity`. Jangan pindahkan — ikuti arsitektur Velthy.

### Verifikasi sesi ini
`gradlew.bat compileDevDebugKotlin` → **BUILD SUCCESSFUL**.

---

## 5. TAHAP 2 — SUBSCRIBE HALAMAN ARTIS (SELESAI)

**Temuan penting:** Velthy **sudah punya seluruh backend + UI `ArtistStatsRow`** (menunggu data). Yang kurang cuma 4 hal.

**Sudah ada (jangan dikerjakan ulang):**
- `Innertube.setSubscribed` (identik BitChord) — `data/innertube/Innertube.kt`
- `YtMusicRepository.setSubscribed` (identik) — `data/YtMusicRepository.kt`
- `SubscriptionState`, `DetailPage.{subscriberCountText,monthlyListenerCount,subscription}` (identik) — `data/model/Models.kt`
- `ArtistStatsRow` + `StatChip` UI lengkap — `ui/screens/DetailScreen.kt`
- `VelthyIcons.Plus/Check`, `Haptic.ToggleOn/ToggleOff` — ada

**Gap & rencana (SUDAH SEMUA DIKERJAKAN):**
| Layer | File | Aksi |
|---|---|---|
| L4 Parser | `data/innertube/InnertubeParser.kt` | ✅ Fallback key jumlah subscriber ditambah: `subscriberCountWithSubscribeText`, `longSubscriberCountText`, `shortSubscriberCountText` (di atas `subscribeButtonRenderer` yang sudah dikumpulkan `collectRenderers`). ✅ channelId fallback `serviceEndpoints[].subscribeEndpoint.channelIds`. |
| L5 ViewModel | `ui/MainViewModel.kt` | ✅ `toggleSubscription(browseId)` + `setSubscribedOnPage(browseId, subscribed)` (setelah `setSavedOnPage`). Optimistic + revert, `requireSignIn()` + `libraryStale`. |
| L6 UI | `ui/screens/DetailScreen.kt` | ✅ Param `onToggleSubscription`; `ActionRow` dapat param `subscription`/`onToggleSubscription`; `CircleIconButton` (Plus/Check) **di depan** Shuffle (Velthy urutannya Subscribe→Shuffle→Play). Import `SubscriptionState`. |
| L6b Strings | `res/values/strings.xml` | ✅ **TIDAK dipakai** — Velthy hardcode "Subscribe"/"Unsubscribe" (ikuti konvensi). |
| L7 Wiring | `MainActivity.kt` | ✅ `onToggleSubscription = if (signedIn) { { viewModel.toggleSubscription(page.browseId) } } else null`. |

**Catatan:** `ArtistStatsRow` sudah ada & lengkap sejak awal (tak diubah). `page.subscription` mengalir utuh parser → ArtistPage → DetailPage → DetailScreen (sudah diverifikasi di `MainViewModel` ~1438 & ~1482).
**Verifikasi sesi ini:** `gradlew.bat compileDevDebugKotlin` → **BUILD SUCCESSFUL**.

---

## 6. TAHAP 3 — CANVAS WIRING (SELESAI)

**Temuan penting:** Canvas di Velthy **90% filenya sudah ada** tapi **mati.**

**Gap (semua sudah diperbaiki):**
1. ✅ `CanvasCache.init()` & `SpotifyToken.init()` kini dipanggil di `VelthyApplication.kt`.
2. ✅ Spotify di-wire ke `CanvasRepository` (Apple→Tidal→Community→**Spotify**).
3. ✅ `resolve`/`firstHit` → `suspend () -> CanvasArtwork?`.
4. ✅ `SpotifyCanvasAuthScreen` kini punya jalan masuk: Settings → "Set up Spotify Canvas".
5. ✅ `refreshFrameEveryMs` sekarang dipakai di 2 call site.
6. ✅ **Temuan tambahan:** `CanvasCache.dataSourceFactory` **tidak pernah dipanggil** — `CanvasArtworkPlayer` bikin `OkHttpDataSource` sendiri, jadi disk cache benar-benar mati. Sudah di-wire ke player.

**Yang dikerjakan:**
| # | File | Aksi |
|---|---|---|
| 4a | `VelthyApplication.kt` | `CanvasCache.init(this)` + `SpotifyToken.init(this)`. |
| 4b | `data/canvas/CanvasRepository.kt` | `resolve`/`firstHit` → `suspend`; `SpotifyCanvas` ditambah ke urutan (track & album). |
| 4c | `ui/screens/SettingsSheet.kt` | Baris "Set up Spotify Canvas" (di dalam grup *Animated cover art*, hanya saat animatedCanvas on) → `onSpotifyCanvasAuth`; param baru di `SettingsScreen`. |
| 4d | `MainActivity.kt` | State `showSpotifyCanvasAuth` + routing `"spotify_canvas"` + `BackHandler` + title/scrolled/onBack. |
| 4e | `ui/player/NowPlayingScreen.kt` | `refreshFrameEveryMs = meshRefreshMs` di 2 call site + konstanta `MESH_REFRESH_MS = 500L`. |
| 4e' | `ui/player/CanvasArtworkPlayer.kt` | Param `refreshFrameEveryMs` + `frameCapturePx` (default `FRAME_CAPTURE_PX = 96`) + loop refresh + `captureAt()`; **wire `CanvasCache.dataSourceFactory`** ke OkHttp upstream. |
| 4f | `res/values/strings.xml` | ✅ **TIDAK dilakukan** — Velthy 0 referensi `R.string` (semua hardcode). |

**Catatan AppSettings:** `spotifySpdcToken` di `secretsPrefs`; `KEY_CANVAS_CELLULAR = "canvas_over_cellular"`. Nilai key tidak diubah.
**Verifikasi sesi ini:** `gradlew.bat compileDevDebugKotlin` → **BUILD SUCCESSFUL**.

---

## 7. TAHAP 4 — LYRICS TRANSLATION (SELESAI)

> **Keputusan user (dikonfirmasi):** (a) **tambah `timingSource`** ke `LyricLine`; (b) **cek header lokal**, tidak port `Genius.kt`; (c) **translation saja dulu** — port provider lirik di-*tunda*, pipeline lirik Velthy TIDAK disentuh (fitur yang sudah jalan tetap utuh).

### Yang dikerjakan
| # | File | Aksi |
|---|---|---|
| 1 | `data/lyrics/LyricLine.kt` | Tambah field `timingSource: LyricLine? = null` + hook di `revealedChars` (skala proporsi) & `glowIntensity` (delegasi ke sumber). |
| 2 | `data/lyrics/TranslationLanguages.kt` | **BARU** — salinan BitChord (rename package). |
| 3 | `data/lyrics/LyricsTranslation.kt` | **BARU** — salinan BitChord; `Genius.isSectionHeader` → fungsi lokal `isSectionHeader`; UA `BitChord/1.5.2` → `Velthy`. |
| 4 | `ui/components/TranslationLanguageDialog.kt` | **BARU** — ditulis ulang gaya Velthy (pakai `rememberCanBlur`/`hazeEffect`/`AlertRule`/`AlertAction`, string hardcode). Punya search field + `LazyColumn` (130+ bahasa). |
| 5 | `data/settings/AppSettings.kt` | `translationLanguage` + `setTranslationLanguage` + `KEY_TRANSLATION_LANGUAGE` (default blank = ikut bahasa app). |
| 6 | `ui/player/NowPlayingScreen.kt` | `LyricsTranslationUiState`, state blok `translationLanguage`/`showingTranslation`/`translationJob`, `toggleTranslation`, `displayedLyrics`; `LyricsPanel` dpt param + `TranslationToggleButton` (overlay sudut kanan-atas). Import: `Toast`, `Job`, `LyricsTranslation`, `translationLanguageName`, `Icons.Rounded.Translate`. |
| 7 | `ui/screens/SettingsSheet.kt` | Baris "Translation language" (ikon Translate) di grup lirik → `onTranslationLanguage`. |
| 8 | `MainActivity.kt` | State `showTranslationLanguage` + `BackHandler` + render `TranslationLanguageDialog` + param `onTranslationLanguage`. |

### Adaptasi yang dilakukan (beda dari BitChord)
- BitChord pakai `stringResource` + `BiChord.getApplicationLocales()` (per-app locale). Velthy: **hardcode string** + `Locale.getDefault()` (Velthy tidak punya per-app locale override).
- `TranslationLanguageDialog` ditulis ulang memakai primitif alert Velthy, bukan disalin mentah (BitChord pakai `optimizedHazeEffect` + string resources).
- `Genius.isSectionHeader` di-inline sebagai fungsi private di `LyricsTranslation.kt`.
- **Tidak** memakai `LyricAlignment`, `withInstrumentalGaps`, provider lirik tambahan.

### Yang SENGAJA belum (ditunda, atas keputusan user)
- Port provider lirik BitChord (Genius, LyricsQuery, ProviderLyrics, BiniLyrics, Megalobiz, Unison, YouTubeLyrics, LyricAlignments) — akan **merewrite** `LyricsRepository` (4.4→12KB) & `LyricsSource` (1.3→4.1KB) yang sekarang sudah jalan. Bertentangan dengan AGENTS.md; jadikan tahap terpisah.

**Verifikasi sesi ini:** `gradlew.bat compileDevDebugKotlin` → **BUILD SUCCESSFUL** tanpa warning.

Velthy **belum punya** kode translation sama sekali. ~1.200 baris baru. **Terpisah** dari provider lirik.

**File baru:**
| File | Baris | Isi |
|---|---|---|
| `data/lyrics/LyricsTranslation.kt` | 380 | `object LyricsTranslation` → `translate(context, trackId, lines, targetLanguageTag): Result` (cache Lru + disk GZIP, batch ≤3500 char, endpoint `translate.googleapis.com`) |
| `data/lyrics/TranslationLanguages.kt` | 189 | `TranslationLanguage`, `TRANSLATION_LANGUAGES` (~130), `translationLanguageName()` |
| `ui/components/TranslationLanguageDialog.kt` | 320 | Dialog pemilih bahasa (pakai `haze` — Velthy sudah punya) |

**Integrasi:** `AppSettings.translationLanguage` + `KEY_TRANSLATION_LANGUAGE`; `NowPlayingScreen` (`LyricsTranslationUiState`, `toggleTranslation`); `SettingsSheet` baris "Translation language"; `MainActivity` state + dialog; `strings.xml`.

**Adaptasi wajib:**
- `LyricsTranslation.flatten` BitChord panggil `Genius.isSectionHeader` — Velthy **tidak punya `Genius.kt`**. Ganti dengan cek header lokal, ATAU port `Genius.kt`.
- `LyricLine` BitChord (22.374 byte) >> Velthy (6.259 byte). Pastikan expose `background`, `words`, `timingSource`, `isWordSynced`, `sungUntilMs`, `withInstrumentalGaps`.
- Velthy tidak punya `ProviderLyrics.kt`/`KaraokeLrc`.

**Keputusan user:** sudah dikonfirmasi — lihat blok di awal §7.

---

## 7b. TAHAP 5 — LISTEN TOGETHER / PARTY (SELESAI, build sukses)

**Permintaan user:** "untuk di player juga bisa disamakan kyk bitchord itu yang di audio itu juga ada untuk party" → area **audio + party jadi satu kapsul** (`OutputPartyPill`), dan belahan party **tersambung ke backend NestJS** yang sudah dibuat.

### Yang dikerjakan
| # | File | Aksi |
|---|---|---|
| 1 | `app/build.gradle.kts` | `PARTY_SERVER_URL` buildConfigField (env/local.properties, default kosong = minta alamat manual). |
| 2 | `data/listentogether/PartyModels.kt` | **BARU** — port apa adanya (rename package). |
| 3 | `data/listentogether/ServerClock.kt` | **BARU** — port apa adanya. |
| 4 | `data/listentogether/JamInviteLink.kt` | **BARU** — port apa adanya. |
| 5 | `data/listentogether/ListenTogether.kt` | **BARU** — port BitChord, **identitas di-inject** (`setIdentity(Identity?)`) karena Velthy tidak punya `AuthStore.activeSession`/profiles. `BuildConfig.LISTEN_TOGETHER_SERVER` → `PARTY_SERVER_URL`. PREFS → `velthy_listen_together`. |
| 6 | `VelthyApplication.kt` | `ListenTogether.init(this)`. |
| 7 | `MainActivity.kt` | `LaunchedEffect(signedIn, account)` → `ListenTogether.setIdentity(...)` (userId = SHA-256 dari name:email); state `showListenTogether` + route `"listen_together"` + BackHandler/title/scrolled/onBack; `onOpenParty` ke `NowPlayingScreen`. |
| 8 | `ui/screens/ListenTogetherScreen.kt` | **BARU** — ditulis ulang gaya Velthy (SettingsGroup/SettingsRow, hardcode string). Join pakai kode, buat party, daftar anggota + host, copy kode, server health + alamat server. |
| 9 | `ui/player/NowPlayingScreen.kt` | Tambah `Pill`/`PillDivider`/`PillSegment`/`OutputPartyPill` + konstanta (`BOTTOM_ACTION_SIZE=44dp`, `PILL_SEGMENT_WIDTH=54dp`). Glyph audio tunggal → kapsul 2-belahan. Param `onOpenParty`, collect `ListenTogether.state` untuk `inParty`. |

### Catatan penting
- **Belahan party TIDAK memakai `PartySync`** (binding Media3). Velthy tidak punya layer itu; yang di-wire baru UI + data layer + kontrol. Binding playhead party → transport `PlaybackService` **belum** (tahap lanjutan bila diminta).
- `ListenTogether.partyPositionMs()`/`msUntilStart()` sudah ada dan siap dipakai untuk binding itu.
- Server default kosong → user mengisi alamat di halaman Listen Together (`setCustomServerUrl`), atau pasang `PARTY_SERVER_URL` saat build.
- Sumber backend: `D:\Vs code\Velthy\backend-nest` (REST `/api/parties`, WS `/ws/parties/{code}`).

**Verifikasi sesi ini:** `gradlew.bat compileDevDebugKotlin` → **BUILD SUCCESSFUL**, 0 warning.

---

## 7c. TAHAP 6 — KONTROL LIRIK (OFFSET + GESTURE) + BACKEND/TUNNEL (SELESAI)

**Permintaan user:** (a) samakan kontrol lirik player dengan BitChord (**offset sheet + gesture**), **tetapi pertahankan** auto-scroll lirik & auto-hide kontrol Velthy; (b) backend kok startup-nya tidak jalan; (c) `TUNNEL_ORIGIN` itu apa; (d) mau jalan di **api.velthy.my.id**.

### Yang dikerjakan (kontrol lirik)
| # | File | Aksi |
|---|---|---|
| 1 | `data/settings/AppSettings.kt` | `lyricsOffsetMs` + `setLyricsOffsetMs` + `KEY_LYRICS_OFFSET_MS` + `MIN/MAX_LYRICS_OFFSET_MS` = ±5000ms. |
| 2 | `ui/player/LyricsOffsetSheet.kt` | **BARU** — port BitChord, diadaptasi ke Velthy (`rememberCanBlur`/`hazeEffect`, string hardcode, `hazeState` default). Drawer geser-turun-untuk-tutup. |
| 3 | `ui/player/LyricsControlsGesture.kt` | **BARU** — port apa adanya (`revealLyricsControlsOnTap`, `PointerEventPass.Initial`). |
| 4 | `ui/player/NowPlayingScreen.kt` | Offset diterapkan **ke `rememberLyricClock(positionMs + offsetMs)`** (satu titik, semua downstream ikut). State `showLyricsOffset` + render + BackHandler + OverlayBack. Tombol offset (`PanelCircleButton`, ikon `Tune`) di sudut panel; `onOpenOffset`. Gesture dipasang **hanya saat `panelScrollHidden`** → auto-hide Velthy TETAP utuh. |

### Yang SENGAJA dipertahankan (arahan user)
- Auto-scroll/follow baris aktif — **tidak disentuh**.
- Auto-hide transport saat panel digulir (`panelScrollHidden`) — **tidak disentuh**; gesture hanya *memunculkan kembali*.
- Sweep/glow per-kata + `timingSource` terjemahan + `Blur unfocused lyrics` — **tidak disentuh**.
- Offset diterapkan di clock (bukan per-baris) supaya sweep, auto-scroll, dan terjemahan tak mungkin beda jam.

### Backend & tunnel — temuan & perbaikan
1. **Startup backend SEBENARNYA JALAN.** Dibuktikan: `node startup.mjs` boot penuh (routes `/healthz`, `/api/time`, `/api/parties`, `/api/parties/:code/join` ter-mapped), `/healthz` → `{"ok":true}`, dan `POST /api/parties` mengembalikan `code` + `token` + snapshot. Kalau di terminal tampak gagal, penyebabnya jalur pemanggilan (folder salah / sintaks `set` di PowerShell / port sudah dipakai).
2. **`TUNNEL_ORIGIN` adalah dead config.** Tidak dibaca `scripts/tunnel.mjs` maupun `src/`. Sudah dihapus dari `.env` + `.env.example`, diganti catatan bahwa origin & hostname diatur di **dashboard Cloudflare**.
3. **Domain `api.velthy.my.id`** didokumentasikan di `README.md` §5 (Public Hostname: subdomain `api`, domain `velthy.my.id`, Service `HTTP` → `localhost:8080`).
4. **Default app** `PARTY_SERVER_URL` (`app/build.gradle.kts`) = `https://api.velthy.my.id` (bisa di-override via env/`local.properties`/field di halaman Listen Together).

**Verifikasi sesi ini:** `compileDevDebugKotlin` → **BUILD SUCCESSFUL**, 0 warning. Backend: health + create party OK (uji langsung).

### Lanjutan — kapsul party sejajar + `AudioOutputSheet` disamakan + hapus grup ekstra
**Permintaan user:** kapsul audio+party "jelek dan tidak sejajar" dengan glyph lain → rapikan; **seluruh sheet audio output** disamakan BitChord, **grup ekstra dibuang**.

1. **Gate: `BottomGlyph` lama 68dp lebar dengan Column (ikon 44dp + label 14dp); kapsul `OutputPartyPill` (109dp, tanpa label box) → melenceng.** Perbaikan mengikuti pola BitChord: `BoxWithConstraints` + `Arrangement.SpaceBetween` + `edgeInset` dari baris terlebar (bukan `SpaceEvenly`), dan `OutputPartyPill` dibungkus Column 44dp + 14dp agar **tinggi & tepi atas sama** dengan `BottomGlyph`. Konstanta baru: `BOTTOM_GLYPH_WIDTH=68dp`, `pillWidth(n)`. `BottomGlyph` kini memakai `BOTTOM_ACTION_SIZE`/`BOTTOM_GLYPH_WIDTH`.
2. **`ui/player/AudioOutputSheet.kt` DITULIS ULANG** (file baru, gaya BitChord): drawer haze + scrim + grab handle + drag-down-to-dismiss, **daftar perangkat** (`AudioDeviceHelper.getAvailableAudioOutputs`, ikon bulatan + nama + "Playing" + check) lalu **slider volume** (`ThinSlider` + reread on route change + broadcast receiver). `hazeState` & `onDismiss` saja (tanpa `accountName`).
3. **Grup ekstra DIBUANG** (keputusan user): Smart TV/Cast discovery, "Connect Bluetooth Device", "System Media Output Panel", beserta `CompactActionRow` (dead code dihapus) dan 5 import yang jadi tak terpakai (`SmartTvCastManager`, `ActivityResultContracts`, `rememberLauncherForActivityResult`, `AudioDeviceType`, `AudioOutputOption`). Catatan: `SmartTvCastManager.kt`/`AudioDeviceHelper.openSystemMediaOutput` masih ada sebagai helper standalone (tak dipakai UI) — menghapus subsistemnya di luar lingkup.
4. **Bonus:** `current!!.text` di `CurrentLyricLine` diganti `current.text` (warning pre-existing di HEAD, kini bersih).

**Verifikasi:** `compileDevDebugKotlin` → **BUILD SUCCESSFUL**, 0 warning.

### §7d — Ringkasan paritas BitChord yang SUDAH dikerjakan (jangan diulang)

Semua di bawah sudah **build sukses** dan ada entri CHANGELOG-nya:

| Fitur | Status | File kunci |
|---|---|---|
| Kapsul 3 belahan: Sleep \| Audio \| Party | ✅ | `NowPlayingScreen.kt` (`OutputPartyPill`) |
| Kapsul berganti isi → Shuffle \| Repeat \| Autoplay saat queue | ✅ | `QueueModesPill` + `AnimatedContent` |
| Baris player rata (glyph 44dp, `SpaceBetween`+inset) | ✅ | `BottomGlyph`, `pillWidth`, `BOTTOM_ACTION_SIZE` |
| Ikon `Queue` custom (bukan `QueueMusic`) | ✅ | `VelthyIcons.Queue` |
| Notifikasi 5 tombol (+Station, +Revert) | ✅ | `PlaybackService.notificationButtons()` |
| Playing-from caption **di strip atas** + fade | ✅ | `PlayingFromCaption` (posisi di `DISMISS_STRIP_HEIGHT` box) |
| OutputCaption di bawah tombol (device / jam) | ✅ | `OutputCaption` |
| Video/Music version button | ✅ | `VideoAudioVersionButton` + `MainActivity.onToggleAudioVersion` |
| AudioOutputSheet = `ModalBottomSheet` + label tipe | ✅ | `AudioOutputSheet.kt` |
| Lirik: tipografi/falloff/gap-dots/easing | ✅ | `NowPlayingScreen.kt` (konstanta `LINE_FALLOFF_*`, `GAP_*`) |
| Lirik offset + reveal-on-tap | ✅ | `LyricsOffsetSheet.kt`, `LyricsControlsGesture.kt` |
| Lirik: **provider** (15) + UI/UX sisa | ❌ | **lihat §1b-A** |
| Animasi artwork ke mini player dihilangkan | ❌ | **lihat §1b-B** |
| `BottomFadeBlur` dihapus | ✅ | (file sudah di-delete) |
| Backend `.gitignore` lengkap | ✅ | `backend-nest/.gitignore` |

### Lanjutan — shuffle/repeat/autoplay ke kapsul + notifikasi 5 tombol
**Permintaan user:** (a) tombol queue (shuffle dkk) dipindah ke kapsul seperti BitChord; (b) notifikasi player disamakan (user pilih: **tambah Station + Revert**).

1. **Kapsul berganti isi** (`NowPlayingScreen.kt`). `AnimatedContent(targetState = queueOpen)` + `SizeTransform(clip = false)`: artwork → `OutputPartyPill` (Sleep|Audio|Party); queue → **`QueueModesPill` (Shuffle|Repeat|Autoplay)**. Keduanya 3 segmen jadi lebar kapsul tak berubah; glyph di kiri-kanan tak bergeser. Baris pill Shuffle/Repeat/AutoPlay **dihapus** dari `InlineQueue` (tombol **Clear** dipertahankan — ia mengurus isi daftar, bukan pemutaran). Param `shuffleEnabled`/`repeatMode`/`onToggleShuffle`/`onCycleRepeat`/`onToggleAutoplay` dihapus dari `InlineQueue` (dead). Import `AnimatedContent`/`SizeTransform`/`togetherWith` ditambah.
2. **Notifikasi 5 tombol** (`PlaybackService.kt`). Dari `[favorite, shuffle, autoplay]` → **`listOfNotNull(station, revert, favorite, shuffle, autoplay)`**.
   - **Station**: `stationCommand` + `ACTION_START_STATION` + `startStationFromSession()` (meniru BitChord: pakai `loadAutoplayTracks` yang sudah ada, ganti queue sekitar track sekarang, hanya sisakan track ini, snapshot manual-queue untuk cek basi). `canStartStation()` ditambah di akhir file. Muncul bila `canStartStation()`.
   - **Revert**: `revertCommand` + `ACTION_REVERT_ORIGINAL` + `toggleRevertFromNotification(mediaId)` → memanggil **`switchToOriginalYouTube(mediaId)` yang SUDAH ADA di Velthy** (temuan: revert bukan subsistem baru). Muncul bila `canStartStation() && localUri == null && StreamChoice.isSubstitute(videoId)`.
   - Refresh layout: sudah ada di `onMediaItemTransition`, `onTimelineChanged`, dll. **Ditambah** satu di jalur `Resolved.Module` (substitute terpilih mid-track) — tanpa itu tombol Revert tak pernah muncul untuk jalur ini karena tak ada transisi lagu.
   - **Tidak** memakai `OriginalVersion.pin` seperti BitChord (persistensi revert) — di luar lingkup; sinyal "revertible" di Velthy adalah `StreamChoice.isSubstitute`.

**Verifikasi:** `compileDevDebugKotlin` → **BUILD SUCCESSFUL**, nol error/peringatan dari file yang diubah.

### Lanjutan — POSISI caption "Playing from" diperbaiki (di strip atas, bukan atas judul)
**Koreksi user:** "playing from di bitchord itu bukan di atas judul tapi di atas layar."

**Temuan setelah baca ulang kode BitChord (`NowPlayingScreen.kt` ~2222-2325):** caption itu ada **di dalam `Box` strip atas** (`topStrip`, tempat drag handle) pada `Alignment.BottomCenter` — jadi **di bawah handle, di atas artwork**, bukan di Column judul. Itu memang "di atas layar". BitChord juga:
- Handle: `align(TopCenter).offset(y = lerp(6dp, ..., p))` — bergerak ke tengah saat panel terbuka.
- Caption: `align(BottomCenter)`, `graphicsLayer { alpha = 1f - p }` (memudar saat panel membuka), `clickable` → party / queue / source.
- Fallback terakhir: `song.playbackSource ?: song.albumName ?: R.string.queue` → **"Playing from Queue"**.

**Perbaikan di Velthy:** caption dipindah **keluar** dari Column judul, masuk ke `Box` strip dismiss (`DISMISS_STRIP_HEIGHT = 44dp`, lebih tinggi dari BitChord 32dp jadi cukup untuk handle+caption). Handle dipindah ke `TopCenter.offset(6dp)`; caption di `BottomCenter` dengan `alpha = 1f - p`. Parameter baru: `onParty`, `onOpenQueue`, `alpha`, `modifier`. Fallback jadi `playbackSource ?: albumName ?: "Queue"`, dan hanya dua kasus yang bisa diketuk (party & queue) — sisanya teks biasa. `PlaybackSourceType` di-import.

**Verifikasi:** `compileDevDebugKotlin` → **BUILD SUCCESSFUL**, nol error/peringatan dari `NowPlayingScreen`.

### Lanjutan — perbaiki format "Playing from" + OutputCaption yang hilang
**Keluhan user:** (a) label "Playing from" beda dari BitChord; (b) info device audio output ada di bawah baris tombol 5 tapi "yang di dot gk ada".

**Temuan & perbaikan:**
1. **Format caption salah.** BitChord punya **3 format terpisah**: `playing_from` → "Playing from %s", `playing_radio` → **"%s Radio"**, `played_by` → **"Played by %s"**. Saya sebelumnya menulis "Radio · X" dan "Listen Together" — keduanya salah. Sekarang persis: `"${radioName} Radio"` dan `"Played by $name"`.
2. **`playedBy` bukan host, tapi `playback.startedByName`** — siapa yang **memulai lagu ini**, dan hanya bila `party.playback.track?.videoId == song.videoId` (party tidak sedang di lagu lain). Fallback ke member list by `startedBy`. Ini yang saya lewatkan sebelumnya.
3. **OutputCaption HILANG** — inilah "yang di dot gk ada". BitChord menaruh satu baris di bawah baris tombol: nama device (atau "This phone"), dan saat party berganti menjadi **"<Nama> Jam"** (host). Ditambahkan `OutputCaption` di `NowPlayingScreen` sebagai **sibling** `AnimatedVisibility` transport → tetap tampil di mode player maupun queue (seperti BitChord). Tap mengikuti label: party → buka party, biasa → buka pemilih output.
   - Catatan: BitChord memakai `rememberAudioOutputName(accountName)` + i18n `personal_phone` ("%s's Phone"). Velthy tidak punya `accountName` di player dan belum pakai i18n, jadi versi Velthy memakai `activeDevice.name` (device eksternal) atau **"This phone"** + `typeLabel` yang sudah ada.

**Verifikasi:** `compileDevDebugKotlin` → **BUILD SUCCESSFUL**, nol error/peringatan dari file yang diubah.

### Lanjutan — panel lirik BitChord (centering Velthy DIPERTAHANKAN) + backend .gitignore

**Permintaan user:** (a) panel lirik **super identik BitChord**, TAPI centering tetap Velthy (BitChord menaruh baris aktif di **paling atas** — user tidak suka); (b) backend: lengkapi `.gitignore` untuk push ke GitHub.

1. **Lirik — perlakukan baris disamakan BitChord.** Konstanta baru (semua dari BitChord): `LINE_FALLOFF_ALPHA = [1f,0.8f,0.7f,0.58f,0.46f]`, `LINE_FALLOFF_BLUR = [0,1,1,1.7,2.4]dp`, `BROWSING_ALPHA = 0.8f`, `LYRIC_EASING` (CubicBezier 0.41/0/0.12/0.99), `LYRIC_SETTLE_MS = 400`, konstanta gap (`GAP_ROW_HEIGHT/SPACING/DOT_*`, `GAP_REST_SCALE`).
   - Alpha/blur: dari rumus asimetris (`offset<0` beda dari `else`) → **tabel simetris** + semua transisi pakai `tween(LYRIC_SETTLE_MS, LYRIC_EASING)`.
   - Browsing: sekarang **ratakan** ke `BROWSING_ALPHA` (bukan tetap gradasi).
   - Instrumental: ikon music-note → **3 titik menyala berurutan** (`drawBehind` + `swell` tinggi baris beranimasi).
2. **CENTERING TIDAK DISENTUH** (permintaan eksplisit). `listState.animateScrollToItem(activeLine, scrollOffset = -third)` / `scrollToItem(..., -third)` tetap di kedua jalur; `contentPadding` Velthy (`vertical = 40.dp - GLOW_ROOM`, simetris) juga dipertahankan — padding bawah `viewportHeight*0.8f` milik BitChord justru bagian dari centering-atas-nya, jadi **sengaja tidak diadopsi**.
   - Verifikasi: grep `scrollOffset = -third` masih ada di 2 tempat (animate + jump).
3. **Backend `.gitignore` dilengkapi.** Temuan penting: **seluruh folder `backend-nest/` belum pernah di-track git** (`?? backend-nest/`), dan `git add -n` menunjukkan **3 file `.zip` artefak** (Dockerfile/README/startup, ~90KB masing-masing) akan ikut ter-commit. `.gitignore` ditulis ulang: secrets (`.env`, `*.env`, `v.env_variable`, dengan `!.env.example`), deps, build, **`*.zip`/`*.tar*`**, logs, OS/editor, runtime state. **Diverifikasi** dengan `git add -n`: dari 30 file → **26 file bersih**; `.env`/`.zip`/`node_modules`/`dist` semuanya ter-ignore, `.env.example` tetap bisa di-commit.

**Verifikasi:** `compileDevDebugKotlin` → **BUILD SUCCESSFUL**, nol error/peringatan dari file yang diubah.

### Lanjutan — info tipe audio, Video/Music, caption "Playing from"
**Permintaan user:** (a) saat connect audio selain phone tidak ada info teksnya; (b) player disamain BitChord — tombol Video/Music di atas (saat lagu ada video) dan "playing from".

1. **Info tipe audio.** Temuan: `ConnectedAudioDevice.typeLabel` **sudah ada** tapi **tidak dipakai UI mana pun**; dan `AudioOutputOption` **tidak punya** label tipe sama sekali — itulah kenapa hanya muncul "Playing". Ditambahkan `AudioOutputOption.typeLabel` dan baris aktif di `AudioOutputSheet` sekarang menampilkan `"<Tipe> · Playing"` (mis. "Bluetooth Headphones · Playing").
2. **Tombol Video/Music** (`NowPlayingScreen.kt` + `MainActivity.kt`). Dulu Velthy **tidak punya** mekanisme tukar versi sama sekali. Ditambahkan: `VideoAudioVersionButton` (pill 2-tab, muncul bila `song.isVideo || isAudioVersion`, disembunyikan saat lirik/antrean terbuka), param `onToggleAudioVersion`/`isAudioVersion`/`audioVersionSwitching`. Logika di MainActivity: cari versi katalog via `YtMusicRepository.search(title + artist, SONGS)` → ambil `SearchResult.Track` pertama yang beda id & tidak `isVideo`; cache per-id (`audioVersionCache`) supaya tak cari dua kali; revert memutar versi video asli. Gagal = tombol diam (tanpa pesan galat), sesuai alasan di BitChord.
3. **Caption "Playing from"** (`NowPlayingScreen.kt`). Ditambahkan `PlayingFromCaption` di atas judul: party → "Listen Together"; `radioName` → "Radio · X"; `playbackSource` → "Playing from X"; `albumName` → "Playing from X"; selain itu tidak digambar. `Song` kini punya `playbackSource`, `playbackSourceType`, `playbackSourceId`, `radioName` + enum `PlaybackSourceType` (belum ada pengisi — struktur disiapkan, caption sudah pakai `albumName`/party yang memang terisi).

**Verifikasi:** `compileDevDebugKotlin` → **BUILD SUCCESSFUL**, nol error/peringatan dari file yang diubah.

### Lanjutan — kapsul 3 belahan (Sleep+Audio+Party), ikon queue mirip BitChord, notifikasi
**Permintaan user:** (a) sleep timer masuk ke kapsul audio+party supaya serasi; (b) glyph Lyrics & Queue dimiripin BitChord (ukuran & bentuk); (c) notifikasi player disamakan (urutan & tombolnya).

1. **Kapsul 3 belahan.** `OutputPartyPill` sekarang `PillSegment(Sleep) | PillSegment(Headphones) | PillSegment(Person)`; tombol Sleep terpisah **dihapus** → baris kembali **3 item** (Lyrics · kapsul · Queue). `rowWidth = BOTTOM_ACTION_SIZE*2 + pillWidth(3)` = **252dp** → muat 320–412dp (inset 2–25dp). `PillSegment` dapat param `badgeText` (badge sleep **di dalam** segmen, bukan `offset` keluar batas). `isSleepActive` dihapus (redundant — `sleepBadge != null` sudah menyatakan itu).
2. **Ikon `VelthyIcons.Queue` (BARU).** BitChord pakai ikon custom (3 garis + 3 titik), bukan Material `QueueMusic`. Ditambahkan ke `VelthyIcons` (konstanta `STROKE` Velthy sama: 2.2f) dan dipakai di baris; import `QueueMusic` dibuang. Sekarang `LyricsQuote` + `Queue` sepasang bentuk senada.
3. **Notifikasi** (`PlaybackService.notificationButtons`): dari `[favorite, shuffle]` → **`[favorite, shuffle, autoplay]`**. Autoplay sudah terdaftar (`autoplayCommand` + handler `toggleAutoplayFromNotification`) tapi belum pernah ditampilkan; ikon `ICON_REPEAT_ALL`/`ICON_REPEAT_OFF`. Handler-nya sudah memanggil `setCustomLayout` jadi ikon ikut berubah.

**Verifikasi:** `compileDevDebugKotlin` → **BUILD SUCCESSFUL**, nol error/peringatan dari file yang diubah.

### Lanjutan — sheet audio output halus + lirik disamakan BitChord (posisi tengah tetap)
**Permintaan user:** (a) sheet audio output "kaku & tidak sempurna"; (b) lirik disamakan BitChord **tapi centering/sync tetap seperti Velthy**.

1. **`AudioOutputSheet.kt` → `ModalBottomSheet`.** Akar "kaku": drawer buatan sendiri menaruh `detectVerticalDragGestures` **di atas** `verticalScroll` → dua gesture bertabrakan, velocity diabaikan, tidak ada fling/snap. Sekarang pakai `ModalBottomSheet` + `rememberModalBottomSheetState(skipPartiallyExpanded = true)` — **sama seperti sleep-timer sheet**, jadi physics & konsistensinya benar. Dead code dibuang: `DRAWER_SHAPE`, `DRAWER_MAX_WIDTH`, `SCRIM_COLOR`, `DISMISS_DRAG_FRACTION`, `AnimatedVisibility`, haze imports, `rememberCanBlur`, `hazeState` param. Anotasi jadi `@OptIn(ExperimentalMaterial3Api::class)`.
2. **Tipografi lirik disamakan BitChord, arsitektur tetap Velthy.** `fontSize 27→34sp`, `lineHeight 33→41sp`, `fontWeight ExtraBold`. Skala: dulu baris aktif **tumbuh** 1.04; sekarang baris lain **menyusut** ke `INACTIVE_LYRIC_SCALE = 0.98f` dan baris aktif = 1.0, plus `PRESSED_LYRIC_SCALE = 0.96f` saat disentuh (via `MutableInteractionSource` + `collectIsPressedAsState`). **TIDAK diubah:** `rememberLyricClock`, `activeLine`/`alsoActive`, auto-follow scroll, `revealedChars`/`glowIntensity`, `timingSource` terjemahan, offset, dan **centering kolom** — sesuai permintaan.
3. Konstanta baru `INACTIVE_LYRIC_SCALE`/`PRESSED_LYRIC_SCALE`; import `collectIsPressedAsState` ditambah.

**Verifikasi:** `compileDevDebugKotlin` → **BUILD SUCCESSFUL**. Nol error/peringatan dari file yang diubah (warning lain pre-existing di file berbeda).

### Lanjutan — baris player dirapikan, kolom kode party, PARTY_SERVER_URL di CI
**Permintaan user:** (a) 5 tombol bawa jadi 5 dan **rusak** (tidak rata, kapsul mendesak, badge tumpang tindih) — tapi **Sleep Timer JANGAN dipindah** (butuh akses cepat); (b) halaman party **disamakan BitChord** DAN kolom kode **harus bisa diketik** (di BitChord/HP lama tidak bisa); (c) `PARTY_SERVER_URL` ditambah ke env build produksi/release.

**Yang dikerjakan:**
1. **Baris player** (`NowPlayingScreen.kt`): akar masalahnya `BottomGlyph` punya kolom **68dp** padahal ikonnya cuma 44dp → ada "band" kosong di kiri-kanan tiap tombol, jadi gap terlihat tidak rata begitu kapsul (109dp) masuk. Diubah: `BottomGlyph` = **44dp** (`BOTTOM_ACTION_SIZE`, sama dengan segmen kapsul), `OutputPartyPill` dibungkus Box 44dp, `Row` pakai `Alignment.CenterVertically`, dan `rowWidth = BOTTOM_ACTION_SIZE*3 + pillWidth(2)` = **241dp** → rata di 320–412dp (edgeInset 4.8–27.8dp). Badge sleep timer **dipindah ke dalam** kotak 44dp (dulu `offset(x=6,y=-3)` keluar batas → tumpang tindih). `BOTTOM_GLYPH_WIDTH` + param `label` dihapus (dead).
2. **Kolom kode party** (`ListenTogetherScreen.kt` ditulis ulang gaya BitChord): 6 sel + `BasicTextField` di atasnya. **Penting:** field-nya **nyata & tappable** (bukan `matchParentSize` transparan seperti BitChord — itu yang bikin tak bisa diketik di sebagian perangkat). Teks/caret dibuat transparan, sel yang menggambar karakter; `capitalization = Characters`, filter `isLetterOrDigit().uppercase().take(6)`. Tampilan: title → ServerHealthRow → NotInAParty (kode + Join + Start) → InAParty (kode + Share + Copy + Leave + daftar anggota monogram) → Party server (terakhir, seperti BitChord).
3. **CI/release** (`build_release_apk.yml`, `ci.yml`): tambah `PARTY_SERVER_URL` (dari `secrets.PARTY_SERVER_URL`) → ditulis ke `local.properties` bila ada; default checked-in tetap `https://api.velthy.my.id`. **Diverifikasi**: `BuildConfig.PARTY_SERVER_URL = "https://api.velthy.my.id"` di `app/build/generated/.../BuildConfig.java`.

**Verifikasi:** `compileDevDebugKotlin` → **BUILD SUCCESSFUL**, 0 warning.

### Lanjutan — PARTY SYNC: playback benar-benar mengikuti party (SELESAI)
**Keluhan user:** "udah party kok gak terjadi apa-apa" → benar, memang belum ada binding party→player (tercatat sebagai gap di §7b).

**Yang dikerjakan:**
| # | File | Aksi |
|---|---|---|
| 1 | `playback/PartySync.kt` | **BARU** — port BitChord (717 baris, rename package). Mengikat `ListenTogether` ke Media3: inbound `reconcile()` (load track+queue, play/pause, koreksi drift), outbound `publish()` (setQueue/setTrack/play/pause/seek), `onLocalIntent()`, `shouldDeferPlay()`, `onPlayWhenReadyChanged()` (deteksi audio-focus-loss), focus-lost detach + rejoin, deferred-play fallback. |
| 2 | `PlaybackService.kt` | `partySync = PartySync(scope) { exoPlayer }.also { it.start() }`; `stop()` di `onDestroy`; `onPlayWhenReadyChanged` → hook; `SessionPlayer` dapat `onUserIntent` + `deferPlayToParty` dan meng-override `play/pause/seekTo/setMediaItems/add/remove/move` untuk mem-publish intent. |

**Kunci desain (dari BitChord, tetap dipertahankan):**
- **Dua arah lewat pintu berbeda** — outbound hanya dari `SessionPlayer` (yang dilewati aksi *user*); inbound `reconcile` menulis langsung ke `ExoPlayer` di bawah wrapper itu. Jadi koreksi sendiri tidak pernah kembali sebagai intent (tidak ada echo loop).
- **Resume menunggu jadwal server** (`msUntilStart`) — kalau tidak, perangkat yang memicu selalu unggul beberapa ratus ms dan itu di bawah ambang drift jadi tak pernah dikoreksi.
- **Drift dikoreksi hanya bila nyata** (2x strike + cooldown 6s), karena seek itu terdengar; tanpa ini terjadi osilasi.
- **Audio-focus-loss = keluar dari party** sampai user minta lagi (rejoin = catch-up, bukan kontrol) — supaya panggilan masuk satu orang tidak menghentikan musik semua.

**Verifikasi sesi ini:**
- `compileDevDebugKotlin` → **BUILD SUCCESSFUL**, 0 warning.
- **Uji relay langsung ke backend (2 device via WS)**: device A kirim `setQueue`+`setTrack(isPlaying)` → device B **menerima track `dQw4w9WgXcQ` dan `isPlaying=true`** → `RESULT: SYNC RELAY OK`. Inilah kontrak persis yang dipakai `PartySync`, jadi klien sudah punya lawan bicara yang benar.

**Gate: `MediaController`** — semua pemutaran UI (play/pause/seek/playSongs) lewat `controller`, jadi semuanya melewati `SessionPlayer` → ter-publish. `PlaybackService` memakai `MediaSession.Builder(this, SessionPlayer(...))`.

### Lanjutan — SATU perintah (`startup.mjs`) menjalankan server + tunnel
**Masalah:** panel penyedia hanya menjalankan **satu** perintah / satu main file (nama file maks 15 karakter termasuk ekstensi). Menjalankan `node startup.mjs` **dan** `npm run tunnel` di dua terminal tidak mungkin di situ.

**Solusi (keputusan user: `startup.mjs` yang mencakup tunnel):**
1. `startup.mjs` — 11 karakter, muat batas panel. Sekarang ia yang menaikkan tunnel:
   - `loadEnvFile()` (baca `.env` tanpa dependensi `dotenv`, tidak menimpa env panel).
   - `resolveCloudflared()` (pakai `.exe` asli, bukan shim `.cmd`).
   - `waitForServer(port)` — poll `/healthz` **sebelum** spawn cloudflared, supaya tidak 502 di detik-detik awal.
   - Spawn cloudflared sebagai **child**; output diberi prefix `[tunnel]`; `stopTunnel()` di `SIGINT`/`SIGTERM`/`exit` → **server & tunnel mati bersamaan** (ini yang dulu bocor: `&` di shell meninggalkan cloudflared hidup → "tunnel already connected").
   - **Tidak fatal**: `TUNNEL_TOKEN` kosong / cloudflared hilang → server tetap jalan, hanya diberi peringatan.
2. `egg-velthy-party.json` → `startup: "npm run start:prod"` (chain `if/fi/&` dibuang; tidak lagi butuh shell). Deskripsi variabel `TUNNEL_TOKEN` & `_comment` diperbarui.
3. `README.md` §4 ditulis ulang: satu perintah `npm start`; `npm run tunnel` tetap ada untuk kasus khusus.

**Verifikasi end-to-end (uji langsung):** `node startup.mjs` → **satu** proses memunculkan `node.exe` (LISTENING `0.0.0.0:33183`) **dan** `cloudflared.exe` (ESTABLISHED ke `127.0.0.1:33183`). Lalu:
- `http://127.0.0.1:33183/healthz` → `{"ok":true}`
- **`https://api.velthy.my.id/healthz` → `{"ok":true}`** ← domain publik live
- `POST https://api.velthy.my.id/api/parties` → `PARTY_CODE=HYRU9K` ← API party jalan lewat domain

### Lanjutan — port 33183 & hapus `BottomFadeBlur`
**Permintaan user:** host hanya memberi 3 port (**33183 / 37720 / 25620**), dan blur di sekitar luar bottom nav bar + mini player dihapus (samakan BitChord).

1. **Port → `33183`.** Diubah di: `.env` (`PORT=33183`), `.env.example` (contoh), `egg-velthy-party.json` (`SERVER_PORT` default `33183`, `user_editable: true`), `README.md` (tabel Public Hostname → `HTTP -> localhost:33183`, dan tabel variabel). **Dashboard Cloudflare juga harus menyebut `localhost:33183`** — origin yang beda dari `PORT` = "tunnel naik tapi app tak bisa menjangkau". Diverifikasi: server listening `0.0.0.0:33183`, `/healthz` → `{"ok":true}`.
2. **`BottomFadeBlur` dihapus.** Temuan: **BitChord tidak punya komponen ini** — itu tambahan khas Velthy (pita gradient blur 180dp / 254dp di bawah bar). Sudah dihapus dari `MainActivity` + file komponennya di-delete (nol dead code) + referensi doc di `MediaWidgetArt.kt` dibersihkan. Bar-nya **tetap punya blur sendiri** (`FloatingBottomBar` → liquidGlass/HazeMaterials.regular; `MiniPlayer` → liquidGlass/HazeMaterials.thin), jadi tidak ada konten yang tembus tajam.

---

## 8. PERANGKAP YANG SUDAH TERBUKTI

1. **JANGAN timpa `data/TrackLog.kt`** — versi BitChord butuh `BuildConfig`/`Song`/`NerdStats`/`SourceResolver` yang berbeda. Velthy punya sendiri yang kompatibel. (Sudah pernah terjadi, ~200 error, diperbaiki dengan `git checkout`.)
2. **`xcopy` dari BitChord-latest**: file yang di-copy harus di-rename package-nya, dan **cek dulu apakah file itu sudah ada di Velthy**.
3. **Shell = cmd.exe**, bukan PowerShell. Pakai `cmd /c`, `findstr`, `dir /b`. Jangan `Select-Object`, `Start-Job`, `>` untuk redirect gradle.
4. `gradlew.bat` task-nya **ambigu** (`compileDebugKotlin` gagal) — pakai **`compileDevDebugKotlin`**.
5. **`.tsbuildinfo` basi** membuat `tsc` no-op. `npm run clean` dulu.
6. **Nest DI**: pakai token eksplisit, bukan tipe (tsx tidak punya `emitDecoratorMetadata`).
7. **Windows spawn `.cmd`** → `spawn EINVAL`. Pakai `.exe` asli.
8. Jangan `start /b ... > file` lewat PowerShell; pakai `cmd /c` sederhana.
9. **`dev.ps1` harus UTF-8 with BOM.** PowerShell 5.1 membaca file tanpa BOM sebagai ANSI; emoji (📸/👋/⚡) lalu rusak jadi byte yang memuat kutip → syntax error seluruh skrip. Sudah diperbaiki (BOM ditambahkan). Kalau menulis ulang file itu, **jaga BOM-nya**.
10. **`dev.ps1` — array 1-elemen di-*unwrap* PowerShell** jadi objek tunggal → `.Count` jadi `$null`. Sudah diperbaiki dengan `return ,$devices` + `@(...)`. Jangan dihapus.
11. **`dev.ps1` screenshot** pakai `adb exec-out` + `Start-Process -RedirectStandardOutput` (bukan `screencap`+`pull`, dan bukan `>` PowerShell yang merusak byte biner).
12. **Edit file besar dengan Edit tool sering GAGAL** karena em-dash/… berbeda dari yang diketik. Pakai skrip PowerShell berbasis **nomor baris** (`ReadAllLines` → potong → `WriteAllLines` dengan `UTF8Encoding($true)`) untuk blok besar. File Velthy banyak memakai `-` ASCII, bukan `—`.
13. **`WriteAllLines` + `-` (stdout) bisa menampilkan "1 -> 350"** — itu artefak redirect, bukan kerusakan file. Verifikasi dengan `findstr` sesudahnya.
14. **Backend `backend-nest/` belum pernah di-track git** (`?? backend-nest/`). `.gitignore`-nya sudah lengkap; saat `git add`, pastikan `*.zip`/`.env`/`node_modules`/`dist` tetap ter-ignore (cek `git add -n` dulu).
15. **Port party = `33183`** (host hanya beri 33183/37720/25620). `PORT` di `.env`, `SERVER_PORT` di egg, dan **origin di dashboard Cloudflare** harus sama. `node startup.mjs` satu perintah menjalankan server + tunnel.

---

## 9. PERINTAH YANG TERBUKTI BEKERJA

```cmd
:: Build Android (cek error)
cd /d "D:\Vs code\Velthy"
gradlew.bat compileDevDebugKotlin --console=plain 2>&1 | findstr /R /C:"BUILD SUCCESSFUL" /C:"BUILD FAILED" /C:"^e: "

:: Backend
cd /d "D:\Vs code\Velthy\backend-nest"
npm run build
npm test
set PORT=8080 && node startup.mjs
```
