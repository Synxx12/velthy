# HANDOFF — Sesi Party & Backend (lanjutan)

> **Baca file ini dulu** sebelum grep apa pun. Semua temuan di bawah sudah diverifikasi.
> Ini file **tambahan**, bukan pengganti `HANDOFF_SESSION.md` (yang berisi 521 baris
> riwayat port v1.6 dan masih berlaku untuk fitur-fitur lama).

---

## ⚡ MULAI DARI SINI (untuk sesi baru)

Kamu **tidak punya memori** dari sesi sebelumnya. File ini adalah memorimu. Ikuti urutan ini:

1. **Baca seluruh file ini** (§0 sampai §8) sebelum menyentuh apa pun.
2. **Baca `.agents/AGENTS.md`** — aturan changelog & rilis.
3. **Cek working tree** — harus **bersih**, HEAD `67315b1` atau lebih baru:
   ```cmd
   cd /d "D:\Vs code\Velthy"
   git status --short
   git log --oneline -3
   ```
4. **Verifikasi build masih hijau** sebelum menambah apa pun:
   ```cmd
   gradlew.bat compileDevDebugKotlin --console=plain 2>&1 | findstr /R /C:"BUILD SUCCESSFUL" /C:"BUILD FAILED" /C:"^e: "
   ```
5. **TANYA USER** apa yang mau dikerjakan berikutnya (§3 daftar permintaan yang belum selesai).
6. **JANGAN commit** sampai user bilang. Lihat §0.

**Sudah selesai & sudah di-commit** (jangan dikerjakan ulang):
- Fix lagu bolak-balik saat party (§2) — `e416134`
- Kurangi jeda party + Personal Queue Stash (§1a, §1b, §1e) — `7e215f3`
- `startup.mjs` auto-pull + graceful shutdown fix (§1c) — `36b8fa4`
- Handoff docs + `.gitignore` — `73e92bf`
- **Party v1.7 lengkap** (shared queue, host controls, activity feed, QR, preview) — `67315b1`

**Belum dikerjakan:** §3 (lihat daftar di bawah — sebagian besar sudah selesai di `67315b1`).

---

Repositori: `D:\Vs code\Velthy` — branch `main`, HEAD `67315b1` (party v1.7 lengkap)
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
(e416134..67315b1)
7e215f3 feat(party): antrean pribadi pulih setelah dengar bareng + jeda party dipersingkat
36b8fa4 fix(backend): startup.mjs auto-pull opsional + drain shutdown yang benar
73e92bf chore: abaikan file Signature* + dokumen handoff party
ada601e docs: perbarui handoff party pasca-commit
67315b1 feat(party): antrean bersama, kontrol host, activity feed, QR & preview
```

Semua sudah **build hijau**, **31/31 unit test backend lulus**, **159 test klien lulus**,
dan **smoke test end-to-end 15 langkah lulus**.

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

## 3. STATUS PERMINTAAN USER

### 3a. Party listen di-update seperti BitChord v1.7 ✅ SELESAI (commit `67315b1`)
User: *"di party listen itu di update kyk bitchord sekarang"*

| Fitur v1.7 | Velthy | Catatan |
|---|---|---|
| **`PartyPersonalQueueStash`** | ✅ SELESAI (`7e215f3`) | §1e |
| **Shared queue 25 lagu** + delta | ✅ SELESAI | `MAX_PARTY_UPCOMING_QUEUE = 25` |
| **Activity feed** — 100 entri | ✅ SELESAI | `ListenTogether.activity` |
| **Host controls** — `hostOnlyControl`, `controlsLocked`, `kick`, `setMaxMembers` | ✅ SELESAI | server + klien |
| **Custom server editor** — validasi | ✅ SELESAI | `normalizeServerAddress` (probe+fallback belum) |
| **QR code** | ✅ SELESAI | ZXing 3.5.3 ditambahkan |
| **Sheets** (Confirm/Invite) | ✅ SELESAI | `ListenTogetherSheets.kt` |
| **Members sheet di player** | ✅ SELESAI | `ListenTogetherMembersSheet.kt` |
| **Invite link** `?server=` + preview | ✅ SELESAI | `ParsedJamInvite` + `/preview` |

### Cara `QueueTier` diselesaikan (PENTING untuk sesi berikutnya)

Velthy **tidak** menambahkan `QueueTier` (opsi (b) dari handoff lama). Yang dipakai:

- `Song.fromAutoplay` sudah ada di Velthy dan sudah dipakai UI antrean
  (`autoplaySectionStart`) — jadi ia yang menggantikan batas manual/AutoPlay.
- `PartyTrack.fromAutoplay` ditambahkan ke wire, sehingga antrean yang diterima
  perangkat lain tetap punya batas section-nya.
- **Yang tidak dipakai dari v1.7:** `queueEntryId` (satu lagu bisa muncul 2×) dan
  `isExplicit`. Keduanya tidak dibutuhkan fitur ini. Antrean party mengidentifikasi
  baris lewat `videoId`, jadi **satu lagu yang sama dua kali di antrean party akan
  dianggap satu baris** saat `queueRemove`/`queueMove` — batasan yang diketahui,
  bukan bug yang belum ditemukan.
- v1.7 memakai `detectSingleMove` dengan `baseOffset`; versi Velthy tidak
  (offset selalu 0 karena indeks sudah relatif terhadap antrean party).

### 3b. Peningkatan backend ✅ SELESAI (commit `67315b1`)
User: *"backendnya juga perlu peningkatatan kyknya"*

Yang dikerjakan:
- Queue deltas (`queueAdd`/`queueRemove`/`queueClear`/`queueMove`)
- Activity feed broadcast (`{type:'activity'}` frame)
- Host privileges (`hostOnlyControl`, `kick`, `setMaxMembers`)
- `JAM_MAX_UPCOMING_QUEUE` (default 25); `maxQueueLength` diturunkan jadi `1 + upcoming`
- Endpoint `GET /api/parties/:code/preview` (tanpa auth)
- `create` menerima `maxMembers` (2–10)
- `seq` vs `queueSeq` dipisah: suntingan antrean tidak menaikkan `seq`
- Test: **31/31** unit + **smoke 15 langkah** (`node checks/smoke.mjs`, butuh server jalan)

### 3c. Auto-update di `startup.mjs` ✅ SELESAI (commit `36b8fa4`)
Lihat §1c.

### 3d. Belum dikerjakan (sisa kecil)
- **Probe + fallback server** (v1.7 `ServerConnectionState.CustomFallback`): Velthy
  hanya memvalidasi alamat dan punya health check sederhana. Belum ada fallback
  otomatis ke server bawaan saat server kustom mati.
- **`GlowingAvatar` / avatar remote di sheets**: Velthy sengaja memakai monogram
  (lihat komentar di `MemberRow`), bukan URL avatar.
- **`PartyServerEditor.kt` sebagai layar terpisah**: Velthy menaruh editor di
  dalam `ListenTogetherScreen`, bukan file sendiri.

---

## 4. HASIL RISET BACKEND (referensi cepat)

**Frame types:** `welcome`, `state`, `queue`, `members`, `pong`, `error`, `bye`, `activity`
**Client → server:** `ping`, `sync`, `syncQueue`, `report`, `control`
**Control actions:** `play`, `pause`, `seek`, `setTrack`, `setQueue`, `queueAdd`,
`queueRemove`, `queueClear`, `queueMove`, `next`, `previous`, `kick`,
`setMaxMembers`, `setHostOnlyControl`
**REST:** `GET /`, `GET /healthz`, `GET /api/time`, `POST /api/parties`,
`POST /api/parties/:code/join`, `GET /api/parties/:code`,
`GET /api/parties/:code/preview`, `POST /api/parties/:code/leave`


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
- `gradlew.bat testDevDebugUnitTest` → **BUILD SUCCESSFUL** (159 test, termasuk
  `PartyQueueTest.kt` — move detection, alamat server, invite link)
- `npm test` (backend) → **31 pass, 0 fail**
- `npm run build` (backend) → bersih
- `node checks/smoke.mjs` (server jalan di PORT=33183) → **SMOKE OK**, 15 langkah:
  REST + health + create + bad token + bad code + welcome + pong + state + sync +
  queueAdd + queueMove + host-only + listener refused + preview + kick
- `node --check startup.mjs` → OK
- `GIT_PULL=1 node startup.mjs` → terdeteksi "no .git here", server tetap naik normal
  (`listening on 0.0.0.0:33183`), cloudflared start

**Catatan menjalankan smoke test:**
```cmd
cd /d "D:\Vs code\Velthy\backend-nest"
:: PORT ada di .env (33183), BUKAN 8080. set harus tanpa spasi:
set "PORT=33183" && node checks/smoke.mjs
```
Server dijalankan terpisah (`node startup.mjs`) dan memegang port itu. Matikan dengan
`taskkill /PID <pid> /F` setelah selesai — jangan biarkan proses lama menahan port,
karena server berikutnya akan gagal `EADDRINUSE`.

---

## 7. LANGKAH BERIKUTNYA (urutan yang disarankan)

1. ~~Tunggu instruksi commit dari user.~~ **Selesai** — semua sudah di-commit (`67315b1`).
2. ~~Party v1.7~~ ✅ **SELESAI** (`67315b1`).
3. **Uji di perangkat** (`.\dev.ps1`) — belum pernah dijalankan untuk fitur party v1.7.
   Urutan yang disarankan:
   - Buat party, mainkan 3–4 lagu **sampai habis otomatis**; pastikan osilasi hilang
   - Susun antrean, masuk party, keluar → antrean pribadi harus kembali utuh
   - Dua perangkat: tambah lagu dari satu perangkat → harus muncul di yang lain
   - Seret satu baris antrean → perangkat lain hanya bergeser sekali, tanpa lagu tersendat
   - Nyalakan **Only I control the music** → perangkat lain tidak bisa mengganti lagu,
     tapi tetap bisa menjeda perangkatnya sendiri
   - Keluarkan satu anggota → perangkat itu keluar dan tidak bisa masuk lagi sehari
   - Buka QR, pindai dari perangkat lain → kode terisi otomatis
4. **Sisa kecil** (§3d): probe + fallback server otomatis ke server bawaan.

---

## 8. PERINTAH YANG TERBUKTI BEKERJA

```cmd
:: Build Android (cek error)
cd /d "D:\Vs code\Velthy"
gradlew.bat compileDevDebugKotlin --console=plain 2>&1 | findstr /R /C:"BUILD SUCCESSFUL" /C:"BUILD FAILED" /C:"^e: "

:: Test klien
gradlew.bat testDevDebugUnitTest --console=plain 2>&1 | findstr /R /C:"BUILD SUCCESSFUL" /C:"BUILD FAILED" /C:"FAILED"

:: Backend
cd /d "D:\Vs code\Velthy\backend-nest"
npm test
npm run build
set GIT_PULL=1 && node startup.mjs

:: Smoke test end-to-end (server harus jalan dulu; PORT dari .env = 33183)
set "PORT=33183" && node checks/smoke.mjs

:: Perangkat
adb devices -l
adb logcat -c
adb logcat -d -t 6000 | findstr /c:" D Velthy"
```
