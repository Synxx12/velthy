# HANDOFF — Sesi Party & Backend (lanjutan)

> **Baca file ini dulu** sebelum grep apa pun. Semua temuan di bawah sudah diverifikasi.
> Ini file **tambahan**, bukan pengganti `HANDOFF_SESSION.md` (yang berisi 521 baris
> riwayat port v1.6 dan masih berlaku untuk fitur-fitur lama).

---

## ⚡ MULAI DARI SINI (untuk sesi baru)

Kamu **tidak punya memori** dari sesi sebelumnya. File ini adalah memorimu. Ikuti urutan ini:

1. **Baca seluruh file ini** (§0 sampai §8) sebelum menyentuh apa pun.
2. **Baca `.agents/AGENTS.md`** — aturan changelog & rilis.
3. **Cek working tree** — harus **bersih**, HEAD `73e92bf` atau lebih baru:
   ```cmd
   cd /d "D:\Vs code\Velthy"
   git status --short
   git log --oneline -3
   ```
4. **Verifikasi build masih hijau** sebelum menambah apa pun:
   ```cmd
   gradlew.bat compileDevDebugKotlin --console=plain 2>&1 | findstr /R /C:"BUILD SUCCESSFUL" /C:"BUILD FAILED" /C:"^e: "
   ```
5. **TANYA USER** apa yang mau dikerjakan berikutnya (§3 daftar permintaan yang belum selesai,
   §3a sudah ada daftar fitur party v1.7 + blocker-nya).
6. **JANGAN commit** sampai user bilang. Lihat §0.

**Sudah selesai & sudah di-commit** (jangan dikerjakan ulang):
- Fix lagu bolak-balik saat party (§2) — `e416134`
- Kurangi jeda party + Personal Queue Stash (§1a, §1b, §1e) — `7e215f3`
- `startup.mjs` auto-pull + graceful shutdown fix (§1c) — `36b8fa4`
- Handoff docs + `.gitignore` — `73e92bf`

**Belum dikerjakan:** §3a (sisa fitur party v1.7), §3b (peningkatan backend).
**Blocker:** §3a — `QueueTier` tidak ada di Velthy.

---

Repositori: `D:\Vs code\Velthy` — branch `main`, HEAD `73e92bf` (party stash + backend + docs)
Backend: `D:\Vs code\Velthy\backend-nest` (NestJS + `ws`, mandiri)
Sumber referensi: `D:\Vs code\BitChord-latest` — **tag `v1.7`** (sudah di-checkout)

---

## 0. ATURAN DARI USER (jangan dilanggar)

1. **JANGAN commit apa pun tanpa instruksi eksplisit.** User sudah menegur keras:
   *"jgn commit dlu lah woi nunggu intruksi dari saya"*. Kerjakan perubahan, biarkan di
   working tree, tunggu aba-aba.
2. **Jangan push / tag / rilis** tanpa aba-aba versi dari user (AGENTS.md).
3. Setiap perubahan kode **wajib** menambah entri di `docs/CHANGELOG.md` `[Unreleased]` + severity.
4. Changelog = release notes publik. **Tanpa** nama file, **tanpa** "seperti BitChord".
5. Jangan sentuh fitur yang sudah jalan.
6. Verifikasi dengan grep + nomor baris (LSP tidak bisa dipercaya di repo ini).
7. Shell = **cmd.exe**. Task Gradle = **`compileDevDebugKotlin`** (bukan `compileDebugKotlin`).
8. **`dev.ps1` harus UTF-8 with BOM** kalau ditulis ulang.
9. User: *"kalo fitur bagus tapi gk memberatkan mah gpp"* — fitur berat/berisiko ditanyakan dulu.

---

## 1. STATUS WORKING TREE — **BERSIH** (semua sudah di-commit)

```
(e416134..73e92bf)
7e215f3 feat(party): antrean pribadi pulih setelah dengar bareng + jeda party dipersingkat
36b8fa4 fix(backend): startup.mjs auto-pull opsional + drain shutdown yang benar
73e92bf chore: abaikan file Signature* + dokumen handoff party
```

Semua sudah **build hijau** dan **backend 16/16 test lulus**.

### 1a. `PartySync.kt` — jeda & anti-osilasi
| Perubahan | Nilai |
|---|---|
| `AUTO_ADVANCE_GRACE_MS` | **200L** (konstanta baru) |
| `DEFERRED_PLAY_TIMEOUT_MS` | 1_800L → **900L** |
| `onAutoAdvance()` | **metode baru** — lihat §2 |
| `load()` | clamp `startAt` ke 0 bila `startAt >= track.durationMs` |

### 1b. `ListenTogether.kt` — sinkron jam saat connect
| Perubahan | Nilai |
|---|---|
| `PING_BURST_COUNT` | **6** (baru) |
| `PING_BURST_STEP_MS` | **120L** (baru) |
| `pingLoop()` | `repeat(4){ping();delay(300)}` → `repeat(6){ping();delay(120)}` |

Waktu tunggu sync: ~1,2s → ~0,7s.

### 1c. `backend-nest/startup.mjs` — auto-update + graceful shutdown
| Perubahan | Detail |
|---|---|
| `autoUpdate()` | **baru** — git pull opsional via `GIT_PULL=1` |
| `rebuild()` | **baru** — build dengan rollback `dist/` |
| `run()` / `capture()` | **baru** — helper spawnSync |
| `flagOn()` | **baru** — parse flag env |
| Graceful shutdown | `process.exit(0)` di handler signal **DIHAPUS**, diganti watchdog 10s |

**Env var baru:** `GIT_PULL` (default off), `GIT_BRANCH` (default `main`), `GIT_REMOTE` (default `origin`).

### 1d. `.gitignore`
Tambah `Signature*` — mencegah file asing (`Signature 2 For Poco f5 ... .xml`) ikut ter-commit.

### 1e. `PartyPersonalQueueStash.kt` — **FILE BARU, SELESAI** ✅ (commit `7e215f3`)
Antrean pribadi disimpan sebelum masuk party, dikembalikan saat keluar.

| Bagian | Detail |
|---|---|
| File baru | `playback/PartyPersonalQueueStash.kt` (~200 baris) |
| `init()` | dipanggil di `VelthyApplication.kt` (setelah `ListenTogether.init`) |
| Stash saat masuk | `PartySync.onEnteredParty()` — dipicu perubahan `code` null→non-null di collector |
| Restore saat keluar | `PartySync.onLeftParty()` — `code` non-null→null |
| Restore saat cold start | `PlaybackService.restoreLastQueue()` — **stash menang atas `LastPlayed`** |
| Prefs | `velthy_party_queue_stash` / `stashed_queue` |

**Adaptasi dari v1.7 (PENTING):** v1.7 pakai `queueTier`/`queueEntryId`/`isExplicit` yang
**tidak ada di Velthy**. Versi Velthy pakai `fromAutoplay: Boolean` + `setVideoId` +
`sourceQuality`. Field yang disimpan: 18 field `Song` Velthy.

**Catatan desain:** `commit()` bukan `apply()` (harus tahan proses mati); indeks dicari dari
`videoId` bukan dari index player (karena device-file difilter keluar, index bergeser).

---

## 2. TEMUAN PENTING (jangan diulang risetnya)

### Bug yang SUDAH DIPERBAIKI — lagu bolak-balik saat party
Terukur di perangkat: osilasi periode **~640ms tanpa henti**, `reason=1` (AUTO) ↔
`reason=3` (PLAYLIST_CHANGED), 10+ kali.

Akar masalah: BitChord v1.7 punya hook `partySync?.onLocalIntent()` saat
`reason == MEDIA_ITEM_TRANSITION_REASON_AUTO` (di `PlaybackService.kt:912`), tapi **hook itu
hilang di Velthy** — Velthy hanya punya jalur publish dari aksi *user* (`onUserIntent`).
Jadi lagu yang habis sendiri tidak pernah dilaporkan → `reconcile()` mengira device berjalan
sendiri → `load()` tarik balik ke lagu yang baru selesai → habis lagi → loop.

Perbaikan: `PartySync.onAutoAdvance()` + hook di `PlaybackService.onMediaItemTransition`.

### Bug yang SUDAH DIPERBAIKI — backend graceful shutdown
`startup.mjs` memanggil `process.exit(0)` di handler SIGTERM/SIGINT **sebelum**
`bootstrap.ts`'s `await app.close()` selesai — drain tidak pernah tuntas. Diganti watchdog 10s.

### Perangkap `startup.mjs`
- `dist/` **selalu menang** atas `src/` (baris ~80-83) **tanpa cek kesegaran**. Jadi `git pull`
  saja tidak berpengaruh — **harus** diikuti rebuild. `autoUpdate()` sudah menangani ini.
- `backend-nest` **bukan repo git sendiri** — `.git` ada di `D:\Vs code\Velthy`, jadi pull
  mengenai seluruh monorepo. Di Pterodactyl source di-upload, jadi kemungkinan tidak ada `.git`
  → `autoUpdate()` menangani dengan warning.
- **JANGAN** `npm ci` di setiap restart — ia menghapus `node_modules`. Sudah digate pada
  perubahan `package-lock.json`.

---

## 3. YANG BELUM DIKERJAKAN — PERMINTAAN USER

### 3a. Party listen di-update seperti BitChord v1.7 ⬜ BELUM
User: *"di party listen itu di update kyk bitchord sekarang"*

**Gap yang sudah dipetakan (dari riset v1.7):**

| Fitur v1.7 | Velthy | File v1.7 |
|---|---|---|
| **`PartyPersonalQueueStash`** — simpan antrean pribadi sebelum masuk party, pulihkan saat keluar | ✅ **SELESAI** (§1e) | `playback/PartyPersonalQueueStash.kt` |
| **Shared queue 25 lagu** + delta (`queueAdd`/`queueRemove`/`queueClear`/`queueMove`) | ❌ tidak ada | `PartySync.kt` (`MAX_PARTY_UPCOMING_QUEUE = 25`) |
| **Activity feed** — 100 entri, `PartyActivity(action, by, atMs, detail)` | ❌ tidak ada | `ListenTogether.kt` (`_activity`) |
| **Host controls** — `hostOnlyControl`, `controlsLocked`, `kick`, `setMaxMembers` | ❌ tidak ada | `ListenTogether.kt` |
| **Custom server editor** — validasi + probe + fallback | ⚠️ parsial | `ui/screens/PartyServerEditor.kt` |
| **QR code** | ❌ tidak ada | `ui/components/QrCode.kt` (161 baris) |
| **Sheets** (Create/Join/Confirm/Invite + `GlowingAvatar`, `MemberAvatarStack`) | ❌ tidak ada | `ui/screens/ListenTogetherSheets.kt` (732 baris) |
| **Members sheet di player** | ❌ tidak ada | `ui/player/ListenTogetherMembersSheet.kt` (207 baris) |
| **Invite link** `?server=` + preview sebelum join | ⚠️ parsial | `JamInviteLink.kt` |

### ⚠️ BLOCKER BESAR untuk shared queue & host controls

Velthy **TIDAK PUNYA** `QueueTier` / `queueEntryId` / `isExplicit` di `Song`, sedangkan
v1.7 memakainya di **11 file, 78 referensi**. Ini prasyarat yang harus diputuskan dulu:

| Field v1.7 | Velthy | Dampak |
|---|---|---|
| `queueTier: QueueTier` (USER_QUEUE/CONTEXT/AUTOPLAY) | ❌ | Shared queue 25 lagu bergantung padanya untuk memisahkan antrean user vs AutoPlay |
| `queueEntryId: String?` | ❌ | Identitas baris antrean (satu lagu bisa muncul 2×) |
| `isExplicit: Boolean?` | ❌ | Badge "E" di kredits |

**Opsi:** (a) tambahkan `QueueTier` ke `Song` Velthy (port besar, menyentuh ~11 file),
(b) shared queue versi sederhana tanpa tier (hanya hitung 25 dari `fromAutoplay`),
(c) lewati shared queue, kerjakan fitur lain dulu.

**Ukuran file (Velthy vs v1.7):**
```
ListenTogether.kt            765  vs 1662   (+897)
PartyModels.kt               142  vs  188
PartySync.kt                 936  vs 1165
ListenTogetherScreen.kt      695  vs 1139
PartyPersonalQueueStash.kt     -  vs  189   (baru)
ListenTogetherSheets.kt        -  vs  732   (baru)
ListenTogetherMembersSheet.kt  -  vs  207   (baru)
QrCode.kt                      -  vs  161   (baru)
```

**Dependency:** `QrCode.kt` butuh **ZXing** — Velthy **belum punya**.
v1.7 pakai `implementation("com.google.zxing:core:3.5.3")`.

**PENTING:** backend Velthy (NestJS) **tidak punya** `queueAdd`/`queueRemove`/`queueClear`/
`queueMove` — riset backend mengonfirmasi hanya ada `setQueue` (full replace). Jadi port shared
queue butuh **kerja backend juga**.

### 3b. Peningkatan backend ⬜ BELUM
User: *"backendnya juga perlu peningkatatan kyknya"*
Belum ada lingkup konkret. Kandidat dari riset:
- Queue deltas (lihat 3a) — **wajib** untuk shared queue
- Activity feed broadcast (`{type:'activity'}` frame)
- Host privileges (`hostOnlyControl`, `kick`, `setMaxMembers`)
- `maxQueueLength` saat ini 500; v1.7 pakai 25 upcoming

### 3c. Auto-update di `startup.mjs` ✅ SELESAI (commit `36b8fa4`)
Lihat §1c.

---

## 4. HASIL RISET BACKEND (referensi cepat)

**Frame types:** `welcome`, `state`, `queue`, `members`, `pong`, `error`, `bye`
**Client → server:** `ping`, `sync`, `syncQueue`, `report`, `control`
**Control actions:** `play`, `pause`, `seek`, `setTrack`, `setQueue`, `next`, `previous`
**REST:** `GET /`, `GET /healthz`, `GET /api/time`, `POST /api/parties`,
`POST /api/parties/:code/join`, `GET /api/parties/:code`, `POST /api/parties/:code/leave`

**Config (semua di `src/common/config.ts`):**
| Env | Default | Range |
|---|---|---|
| `PORT` / `SERVER_PORT` | 8080 | 1–65535 |
| `JAM_MAX_MEMBERS` | 5 | 2–32 |
| `JAM_STATE_HEARTBEAT_MS` | 5000 | 1000–60000 |
| `JAM_PLAY_LEAD_MS` | 350 | 0–5000 |
| `JAM_DISCONNECT_GRACE_MS` | 45000 | 5000–600000 |
| `JAM_EMPTY_PARTY_TTL_MS` | 120000 | 10000–3600000 |
| `JAM_PARTY_MAX_AGE_MS` | 43200000 | ≥60000 |
| `JAM_CONTROL_RATE_PER_SECOND` | 25 | 1–200 |
| `JAM_MAX_QUEUE_LENGTH` | 500 | 1–5000 |
| `JAM_ALLOWED_ORIGINS` | `''` (allow any) | CSV |

**Test:** `checks/party.test.ts` (16 kasus, `npm test`) + `checks/smoke.mjs` (butuh server jalan).
Port deploy: **33183**. Domain: `api.velthy.my.id`.

---

## 5. BUKTI DARI PERANGKAT (cara verifikasi)

User punya **ADB terhubung** (Redmi `23049PCD8G`, Android 15). Bisa dipakai verifikasi nyata.

```
adb devices -l
adb logcat -d -t 6000 | findstr /c:" D Velthy"
```

**Perangkap:** build **release** (`com.velthy.client`) **tidak menulis logcat** — `TrackLog`
hanya aktif saat `BuildConfig.DEBUG`. Untuk diagnosis **harus pakai build dev**
(`com.velthy.client.dev`, dari `.\dev.ps1`).

**Pola yang harus hilang setelah fix:** `TIMING track selected:` bergantian `reason=1` ↔ `reason=3`
dalam hitungan detik.

---

## 6. VERIFIKASI YANG SUDAH LULUS

- `gradlew.bat compileDevDebugKotlin` → **BUILD SUCCESSFUL**
- `npm test` (backend) → **16 pass, 0 fail**
- `node --check startup.mjs` → OK
- `GIT_PULL=1 node startup.mjs` → terdeteksi "no .git here", server tetap naik normal
  (`listening on 0.0.0.0:33183`), cloudflared start

---

## 7. LANGKAH BERIKUTNYA (urutan yang disarankan)

1. ~~Tunggu instruksi commit dari user.~~ **Selesai** — commit `7e215f3`, `36b8fa4`, `73e92bf`.
2. **Uji di perangkat** (`.\dev.ps1`): buat party, mainkan 3–4 lagu **sampai habis otomatis**,
   baca log, pastikan osilasi hilang. Sekaligus uji antrean pribadi: susun antrean, masuk party,
   keluar party → antrean harus kembali utuh (daftar, posisi, status putar).
3. **Tentukan lingkup 3a (party v1.7)** — ini besar (~2.000+ baris klien + kerja backend).
   Tanyakan ke user mau yang mana dulu:
   - Shared queue 25 (butuh backend)
   - ~~Personal queue stash~~ ✅ selesai
   - Activity feed (butuh backend)
   - Host controls (butuh backend)
   - QR + sheets (murni klien, butuh ZXing)
   - Members sheet di player (murni klien)
4. **Tentukan lingkup 3b (backend)** setelah 3a jelas — keduanya saling terkait.

---

## 8. PERINTAH YANG TERBUKTI BEKERJA

```cmd
:: Build Android (cek error)
cd /d "D:\Vs code\Velthy"
gradlew.bat compileDevDebugKotlin --console=plain 2>&1 | findstr /R /C:"BUILD SUCCESSFUL" /C:"BUILD FAILED" /C:"^e: "

:: Backend
cd /d "D:\Vs code\Velthy\backend-nest"
npm test
set GIT_PULL=1 && node startup.mjs

:: Perangkat
adb devices -l
adb logcat -c
adb logcat -d -t 6000 | findstr /c:" D Velthy"
```
