# Project Handoff: Wallpaper Lyrics (Android Live Wallpaper)

This document outlines the current status, recent refactoring, runtime constraints, and instructions for any coding agent resuming development on this project.

---

## 1. Project Context & Purpose
A high-performance Android live wallpaper mimicking the Apple Music "Lyrics" aesthetic. It dynamically warps a blurred album cover background using AGSL runtime shaders (Android 13+) or orbital radial gradients (fallback) and overlays smoothly animated, synced lyrics using a high-precision word-sync progress sweep.

---

## 2. Recent Architecture Refactoring
`LyricsWallpaperService.kt` has been modularized to decouple rendering logic and improve code maintainability:

| Source File | Description / Key Responsibilities |
| :--- | :--- |
| **[`LyricsWallpaperService.kt`](file:///home/dnk/projects/Android/wallpaper-lyrics/app/src/main/java/com/dnk/wallpaperlyrics/LyricsWallpaperService.kt)** | Manages Engine lifecycle, SharedPreferences change observers, active `MediaSessionManager` controllers, OkHttp async lyric requests, and the core 60 FPS Choreographer drawing loop. |
| **[`LyricsRenderer.kt`](file:///home/dnk/projects/Android/wallpaper-lyrics/app/src/main/java/com/dnk/wallpaperlyrics/LyricsRenderer.kt)** | Builds static layout constructs, measures character coordinate sweeps, manages the top/bottom decay fade gradient overlays, and renders the 3-dot instrumental countdowns. |
| **[`AuroraRenderer.kt`](file:///home/dnk/projects/Android/wallpaper-lyrics/app/src/main/java/com/dnk/wallpaperlyrics/AuroraRenderer.kt)** | Drives background color palette extraction (perceptual salience based), low-res art downscaling and box blur, and sets up GPU uniforms for the fluid domain warping. |
| **[`WordGradientSpan.kt`](file:///home/dnk/projects/Android/wallpaper-lyrics/app/src/main/java/com/dnk/wallpaperlyrics/WordGradientSpan.kt)** | Houses `WordGradientSpan` which sweeps a LinearGradient across characters based on playback progress, and the `AuroraPalette` data container. |

---

## 3. Core Mandates & Crucial Details

### A. Performance & Frame Snapping
* **Target:** Consistent 60+ FPS using `Choreographer` and `Hardware Accelerated Canvas`.
* **Zero Flicker on Wake:** On physical screen wake, the engine immediately snaps the scrolling lyrics offset to the correct current playback timestamp (`snapScrollToPosition()`) and forces **two consecutive synchronous canvas rendering passes** (`drawFrame(0f)`) to update both double/triple backbuffers and completely eliminate stale frame flashing.
* **Window Manager Sync:** Overridden `onSurfaceRedrawNeeded` handles synchronous snaps and double-draw passes, blocking the Android Window Manager compositor until the new frames are successfully posted.

### B. Aesthetic Specifications
* **Active Line:** Inter Black (Weight 900), Size 96f, 90% Opacity (alpha 230). Sweeps left-to-right from 35% to 100% opacity as the song plays.
* **Inactive Lines:** Inter Bold (Weight 700) + 1.5px stroke (faux-bolding), Size 96f, 35% Opacity, 0.95x scale-down. Active words transition from `activeAlpha` (230) to `inactiveAlpha` (80) using the `WordGradientSpan` extension.
* **Fade-out Gradients:** Top and Bottom 25% of the screen are overlaid with a multi-stop cubic `LinearGradient` (Black to Transparent decaying cubically), eliminating any visible hard edge lines where the gradient ends.
* **Instrumental Progress Dots:** Features a 3-dot visual countdown for musical breaks. A constraint enforces that countdown dots **do not render** if the line is the final one of the song.

---

## 4. Build, Test & Deploy Commands

* **Compile Command:**
  ```bash
  export JAVA_HOME=/usr/lib/jvm/java-17-openjdk && export ANDROID_HOME=~/Android/Sdk && export ANDROID_SDK_ROOT=~/Android/Sdk && gradle assembleDebug
  ```
* **Output Artifact:** `app/build/outputs/apk/debug/app-debug.apk`
* **Google Drive Upload Command (File ID: `1mMvycX8ZdiDhLSlCX1TEz6T6W7yqlQMw`):**
  ```bash
  gws drive files update --params '{"fileId": "1mMvycX8ZdiDhLSlCX1TEz6T6W7yqlQMw"}' --upload ./app/build/outputs/apk/debug/app-debug.apk --json '{"name": "app-debug.apk"}'
  ```

---

## 5. Next Steps & Current Direction
The refactoring is complete, and the application compiles successfully. Any incoming tasks should focus on:
1. Implementing additional UI customization features in the settings page.
2. Expanding support for alternative media players or customizable alignment formats.
3. Fine-tuning the dynamic gap heuristic for Rap vs. Ballad instrumental detection.
