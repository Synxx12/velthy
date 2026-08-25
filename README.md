<div align="center">

# 🎵 Velthy for Android
### *The 100% Client-Side, Serverless YouTube Music Player*

[![Android](https://img.shields.io/badge/Platform-Android_8.0+-3DDC84?style=for-the-badge&logo=android&logoColor=white)](https://android.com)
[![Kotlin](https://img.shields.io/badge/Kotlin-2.0+-7F52FF?style=for-the-badge&logo=kotlin&logoColor=white)](https://kotlinlang.org)
[![Jetpack Compose](https://img.shields.io/badge/UI-Jetpack_Compose-4285F4?style=for-the-badge&logo=jetpackcompose&logoColor=white)](https://developer.android.com/compose)
[![Website](https://img.shields.io/badge/Website-velthy.my.id-blue?style=for-the-badge)](https://velthy.my.id)
[![License](https://img.shields.io/badge/License-GPL_v3-blue?style=for-the-badge)](LICENSE)

<br/>

**Velthy for Android (Client-Side Edition)** is the official standalone client-side Android companion to [**Velthy**](https://velthy.my.id). Designed from the ground up to operate **100% on-device with zero backend server dependencies**, it directly interfaces with YouTube Music's public Innertube API and embedded stream extractors.

Enjoy high-fidelity audio, synchronized lyrics, offline downloads, seamless crossfade, and a gorgeous Material 3 design without ads or tracking.

[🌐 **Website**](https://velthy.my.id) • [📥 **Download Latest APK**](https://velthy.my.id) • [✨ **Features**](#-features) • [🏛️ **Architecture**](#-client-side-architecture) • [🚀 **Quick Start**](#-building-from-source)

</div>

---

## 🌟 Why Velthy Android?

| Feature | Velthy Android | Official YouTube Music | Standard Web Apps |
| :--- | :---: | :---: | :---: |
| **No Private Backend (100% Client-Side)** | ✅ Yes | ❌ Cloud Only | ❌ Needs Server |
| **Ad-Free Streaming** | ✅ Built-in | ❌ Requires Premium | ⚠️ Depends on Blocker |
| **True Audio Crossfade (0–12s)** | ✅ Equal-power Curve | ❌ No | ❌ No |
| **Direct Offline Downloads with ID3 Tags** | ✅ Standard MP4/WebM | ❌ DRM Locked | ❌ No |
| **Synced Lyrics (LRCLIB)** | ✅ Smooth Apple-style | ⚠️ Partial | ⚠️ Partial |
| **Configurable Background Playback** | ✅ Keep / Stop on Close | ❌ Always Kills / Premium | ❌ Limited |
| **Dolby Atmos / Spatial Audio Widening** | ✅ Custom AudioProcessor | ❌ No | ❌ No |
| **Last.fm & ListenBrainz Scrobbling** | ✅ Built-in | ❌ No | ⚠️ Third-party |

---

## ✨ Features

### 🎧 Audio & Playback Engine
- **Media3 & ExoPlayer Powered**: High-performance audio pipeline with hardware acceleration.
- **True Equal-Power Crossfade**: Configurable (0–12 seconds) seamless transitions between tracks.
- **Per-Network Audio Quality**: Set separate bitrate ceilings for Wi-Fi and Cellular data.
- **Dolby Atmos & Spatial Widening**: Custom stereo-widening audio processor integrated directly into the audio pipeline.
- **Skip Silence & Playback Speed**: Smart silence trimming and 0.5×–2.0× variable playback speed.
- **System Equalizer Integration**: Quick 1-tap launcher to your phone's native hardware equalizer.

### 🎨 Visuals & Now Playing Experience
- **Immersive Fullscreen Cover Art**: Double-tap on artwork to expand edge-to-edge with smooth gradient bottom fade.
- **Dynamic Mesh Gradient Palette**: Real-time color extraction from album artwork that softly washes over the screen.
- **YouTube Music-Style Quick Picks**: 4-row song grids on the Home screen for instant 1-tap playback.
- **Synchronized Lyrics**: Silky-smooth 60/120 FPS lyrics powered by LRCLIB with auto-follow and clickable timestamps.
- **Fluid Queue Reordering**: Spring-physics drag-and-drop queue management with touch-optimized handles.

### 📥 Offline & Downloads
- **Direct Device Downloads**: Saves high-quality audio directly to `Downloads/Musique`.
- **Embedded Metadata**: In-house MP4/WebM muxers embed artist, album, title, and HD cover art directly into the file without external tagging tools.
- **Integrated Local Media**: Scans and plays existing local music alongside streaming tracks.

### 🔒 Privacy & Account Freedom
- **No Backend Required**: All requests are made directly from your phone to public endpoints.
- **Optional Google Sign-In**: Login securely via official Google WebView (`accounts.google.com`) to access your personal "Quick picks", mixes, and listening history without exposing credentials.
- **Scrobbling**: Automatic, real-time scrobbling to **Last.fm** and **ListenBrainz**.
- **Configurable Background Lifecycle**: Choose whether playback continues in the background or cleanly stops when the app is swiped away from Recent Apps.

---

## 🏛️ Client-Side Architecture

Musique Android is engineered to be **completely autonomous** and serverless:

```
┌─────────────────────────────────────────────────────────────┐
│                       Musique Android                       │
│                                                             │
│   ┌─────────────────────────────────────────────────────┐   │
│   │                 Jetpack Compose UI                  │   │
│   │   • Home (Quick Picks)    • Immersive Player        │   │
│   │   • Synced Lyrics         • Settings & Theming      │   │
│   └───────────────────────────┬─────────────────────────┘   │
│                               │                             │
│   ┌───────────────────────────▼─────────────────────────┐   │
│   │              Client-Side Core Engine                │   │
│   │                                                     │   │
│   │   [ Innertube Parser ]    ──► Direct YTM Web API    │   │
│   │   [ Stream Resolver ]     ──► In-Memory Extractor   │   │
│   │   [ LRCLIB Client ]       ──► Synced Lyrics API     │   │
│   │   [ Local Media Tagger ]  ──► Embedded ID3/Vorbis   │   │
│   │   [ Media3 Session ]      ──► ExoPlayer + Spatial   │   │
│   └─────────────────────────────────────────────────────┘   │
└─────────────────────────────────────────────────────────────┘
```

1. **Innertube Direct Connect**: Communicates directly with YouTube Music's web endpoints via Ktor, without intermediate proxies.
2. **On-Device Stream Resolution**: Resolves audio URLs locally using client fallbacks (including Android Music clients).
3. **Standalone Tagging**: Downloads are tagged locally on storage using custom metadata writers.

---

## 📦 Download APK

You can download the compiled APK directly from:

1. **[GitHub Releases Page](https://github.com/Synxx12/musique-app-releases/releases)**
2. **Musique Web Portal**: Directly downloadable from the downloads section of the Musique web application.

> [!NOTE]
> When installing for the first time, allow your browser or file manager permission to *"Install unknown apps"*.

---

## 🛠️ Building from Source

### Prerequisites
- **Android Studio** (Ladybug / Koala or newer)
- **JDK 17**
- **Android SDK** (API 26+ to 36)

### Clone & Build
```bash
# Clone the repository
git clone https://github.com/Synxx12/velthy.git
cd velthy

# Interactive live dev runner (with fast reload)
.\dev.ps1

# Or build release APK via Gradle
./gradlew assembleProdRelease
```

The compiled APK will be output to:
`app/build/outputs/apk/prod/release/app-prod-release.apk`

---

## 🤖 GitHub Actions CI/CD

This repository includes automated GitHub Actions workflows located in [`.github/workflows/build_release_apk.yml`](.github/workflows/build_release_apk.yml).

- **Automated Releases**:
  - Automatically triggered on push tags (e.g. `v1.3.0`) or on-demand via `workflow_dispatch`.
  - Auto-increments version numbers.
  - Automatically compiles and signs APKs (`Velthy-v1.3.8.apk` and `Velthy-latest.apk`).
  - Publishes releases to [`Synxx12/musique-app-releases`](https://github.com/Synxx12/musique-app-releases) (with fallback to current repository).
  - Automatically sends release webhook triggers to `https://velthy.my.id/api/webhooks/github-release`.

---

## 📄 License & Disclaimer

- **Disclaimer**: Velthy is an independent client-side audio player and is not affiliated with, endorsed by, or sponsored by Google LLC or YouTube.
- **Fair Use**: Built for personal research, educational, and fair-use listening.
- **License**: Licensed under project terms. See [LICENSE](LICENSE) for details.
