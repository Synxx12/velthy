# Musique Android (Client-Side Edition) — Agent Rules & Guidelines

Selamat datang di repository **Musique Android (Client-Side Native Edition)**. File ini berisi petunjuk wajib yang **HARUS** dipatuhi oleh semua Agent AI (termasuk Antigravity / Gemini / Claude) saat membaca, mengedit, menambah fitur, atau merefactor kode di repository ini.

---

## 📋 Rule Utama: Wajib Catat Changelog Setiap Perubahan Kode

Setiap kali melakukan perubahan kode (edit file, tambah fitur, perbaiki bug, optimasi, refactor), **WAJIB** menambahkan entri ke dalam section `## 📋 Changelog` di bawah header `### [Unreleased]` pada file:

```text
docs/CHANGELOG.md
```

### Format Penulisan Changelog:
```markdown
- **Judul Singkat**: Deskripsi perubahan teknis yang jelas, padat, dan akurat.
```

### Kategori Changelog:
- `#### ✨ Fitur Baru` — untuk penambahan fitur baru
- `#### ⚡ Performa & Stabilitas` — untuk optimasi memori, GPU `graphicsLayer`, 60/120 FPS rendering
- `#### 🎵 Player, Audio & Lirik` — untuk Media3, ExoPlayer, crossfade, Spatial Audio, LRCLIB lyrics, queue
- `#### 🌐 Client-Side & Innertube` — untuk Ktor client, NewPipe stream resolution, Google auth cookies
- `#### 🎨 UI, Gestures & Animasi` — untuk Jetpack Compose, Material 3, fullscreen player, Home Quick Picks
- `#### 🐛 Bug Fixes` — untuk perbaikan error dan bug
- `#### 🔧 Build & CI/CD` — untuk Gradle, GitHub Actions (`build_release_apk.yml`), signing

---

## 🛑 Aturan Ketat Rilis & Versi Produksi (Release Protocol)

1. **DILARANG Trigger / Push Release Tanpa Aba-Aba**:
   - Agent **TIDAK BOLEH** secara mandiri mengeksekusi `gh workflow run`, membuat tag rilis rilis produksi, atau menerbitkan rilis ke `Synxx12/musique-app-releases` sebelum mendapat **aba-aba / persetujuan eksplisit** dari User.
   - Pengembangan dan pengujian difokuskan pada branch lokal / development sampai User siap merilis.

2. **Penetapan Ekor Versi / Tag Rilis**:
   - User **SELALU** yang menentukan dan memberikan aba-aba pembaruan nomor versi (ekor versi / patch version, contoh: `native-v1.3.1`, `cloud-v1.0.43`).
   - Agent tidak boleh menaikkan versi atau men-trigger rilis secara sepihak tanpa instruksi nomor versi dari User.

---

## 🏛️ Arsitektur & Prinsip Pengodingan (Clean Code Standards)

### 1. 100% Client-Side Architecture (Serverless Native)
- Aplikasi ini berjalan **100% Client-Side tanpa backend server perantara**.
- Semua interaksi YouTube Music dilakukan langsung melalui Ktor Innertube client (`data/innertube`) dan ekstraksi audio lokal in-memory (`StreamResolver.kt`).
- Lirik diambil langsung dari LRCLIB public API.
- Download lagu disimpan langsung ke `Downloads/Musique` dengan embedded ID3/Vorbis metadata tagging lokal.

### 2. Jetpack Compose & Rendering Performance
- **Zero-Lag Drawing**: Perubahan transparansi (alpha), rotasi, dan skala dinamis HARUS menggunakan `Modifier.graphicsLayer { ... }` untuk melewati fase *recomposition* dan *relayout*.
- **LazyList Optimization**: Selalu berikan `key` yang unik dan stabil (contoh: `videoId` / `browseId`) pada setiap `item` dan `items` di `LazyColumn` dan `LazyRow`.
- **Blur & RenderEffect Caution**: Hindari menerapkan `Modifier.blur()` berlebihan secara bersamaan pada banyak item teks yang bergerak untuk menjaga frame rate 60–120 FPS di perangkat mid-range.

### 3. Media3 & Audio Engine Safety
- `PlaybackService` adalah `MediaSessionService` yang mengelola siklus hidup audio di background.
- Perubahan state playback, crossfade, dan volume automation harus thread-safe dan sinkron dengan `MediaSession`.
- Sediakan opsi background lifecycle (`AppSettings.stopOnTaskRemoved`) yang konsisten.

### 4. YouTube Music UI & Gestures Parity
- **Home Screen**: Prioritaskan *Quick Picks* dalam format kolom 4-lagu yang responsif dan interaktif di bagian atas feed.
- **Immersive Fullscreen Artwork**: Double-tap untuk memperbesar foto edge-to-edge dengan vertical fade halus ke bawah, single tap untuk kembali.
- **Gesture Swiping**: Gesture swipe horizontal pada cover player harus selalu responsif (geser kiri = next, geser kanan = previous / restart).

---

## 💎 Standar Kualitas Pengkodean (Production-Grade)

> **"Saya mau ini tuh melihat semua aspek dan super kompleks dan kestabilan dan kesempurnaan dan rapih dan terstruktur dan melihat jangka panjang dan production grade, dan profesional."**

Setiap pengembangan fitur, perbaikan bug, atau refactor arsitektur HARUS:
- Mempertimbangkan aspek teknis secara komprehensif (performa, memori, error handling, daya tahan jaringan offline/online).
- Terstruktur rapi menggunakan arsitektur MVVM & Jetpack Compose standar industri Android.
- Siap digunakan untuk lingkungan produksi (*production-grade quality*) tanpa solusi jalan pintas (*hacky workarounds*).

---

## 📖 Referensi Repositori & Ekosistem Musique
- **Owner**: `Synxx12`
- **Releases Repository**: [`Synxx12/musique-app-releases`](https://github.com/Synxx12/musique-app-releases)
- **Web Portal Workspace**: `d:\Vs code\musique-web`
- **Flutter Edition Workspace**: `d:\Vs code\musique-flutter`
- **Backend API Workspace**: `d:\Vs code\song-api`
