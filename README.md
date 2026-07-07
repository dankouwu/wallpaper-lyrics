# Wallpaper Lyrics

A high-performance Android live wallpaper that dynamically renders a gorgeous, animated fluid/aurora background based on current album art, overlaying smoothly animated, synchronized lyrics from the active media session (matching the Apple Music "Lyrics" aesthetic).

## Core Mandates and System Config

- **Performance:** Target native 60+ FPS using `Choreographer` frame callbacks and `Hardware Accelerated Canvas`.
- **System Config:** Battery usage MUST be set to **"Unrestricted"** in system settings to prevent OS throttling of live background tasks and network calls.
- **Media Support:** Monitors active media sessions for Spotify and Tidal. Spotify requires "Device Broadcast Status" to be enabled in settings.
- **Privacy & Security:** Completely offline-first caching. No tracking, personal data collection, or sensitive API token leakage.

## Aesthetic Specifications (Apple Music Style)

### Background Fluid Animation (Domain Warping AGSL)
- **Domain Warp Shader (Android 13+):** Applies two octaves of 2D Simplex Noise. Large-scale noise has `0.22` frequency and medium-scale detail has `0.25` frequency (ensuring large, rounded fluid blobs with no thin, sharp streaks).
- **Image Preprocessing:** Asynchronously downscales the album cover to $128 \times 128$ pixels, applies a luminance-based shadow mask tint, and box-blurs it on a background thread.
- **Crossfade Transition:** Uses a dual-texture GPU shader crossfade over 1000ms. Current and next textures are warped independently using separate time parameters (`u_time` and `u_time_next`) to prevent animation stutters.
- **Swirl Pre-Simulation:** On track change or service startup, the incoming track's animation timeline is initialized with a randomized offset ($5.0\text{f} + \text{random offset}$ up to $10000\text{f}$), ensuring the background starts immediately in a beautifully fluid, non-repeating state.
- **Post-Processing:** Applies a vignette, $1.3\times$ base zoom (matrix cropped), $2.8\times$ saturation boost, a custom warmth filter (boosting red/green, dampening blue), and dynamic dithering to prevent gradient banding.
- **Fallback (< Android 13 / API 33):** High-performance animated 4-color `RadialGradient` mesh layers with softened cores.
- **Idle State:** A 4-color mesh gradient dynamically generated via bilinear interpolation of the default palette, sharing the same optimized shader pipeline.

### Typography & Layout
- **Font Face:** Uses **Inter** (Black and SemiBold) bundled as resources.
- **Active Line:** Inter Black (Weight 900), Size 96f, 90% Opacity (alpha 230), -0.02f Letter Spacing (Tracking), and a subtle 10f radius shadow.
- **Inactive Lines:** Inter Bold (Weight 700) + 1.5px stroke (faux-extra-bold), Size 96f, 35% Opacity, and a 0.95x scale-down.
- **Artist Title:** 60f size, 50% Opacity.
- **Unified Alpha Blending:** Uses `canvas.saveLayer` during state cross-fading for cohesive group alpha transitions, preventing text shadow artifacts.
- **Fade-out Gradients:** Top and bottom 25% of the screen are overlaid with a multi-stop cubic `LinearGradient` (Black to Transparent decaying cubically) to eliminate visible hard edge lines where the gradient ends.

### Screen-Wake Transition Sequence
- **Wake Timeline:**
  - `0 - 999 ms`: **Lyrics View** (enables immediate viewing of currently playing lyrics).
  - `1000 - 3000 ms`: **Metadata View** (fades in the Album Art and Title/Artist metadata).
  - `3000 ms+`: **Lyrics View** (transitions back to lyrics).
- **Double-Buffer Snapping & Redraw:** Overrides `onSurfaceRedrawNeeded` and visibility changes to execute a synchronous scroll snap (`snapScrollToPosition()`) and force two consecutive frame draws (`drawFrame(0f)`). This immediately updates both buffers in the double-buffer queue, avoiding stale frame flashes when waking.
- **Physical Wake Tracking:** Tracks `isScreenOff` via broadcast receivers (initialized via `PowerManager.isInteractive`). It intercepts transitions to the interactive state inside redraw and visibility callbacks to update `lastWakeTime`, preventing the animation from playing on normal homescreen app returns.

## Architecture Details

### 1. `LyricsWallpaperService.kt`
Handles native drawing lifecycle via Choreographer-aligned frame callbacks and AGSL shaders. It caches preference values locally and uses an `OnSharedPreferenceChangeListener` to avoid overhead inside the 60 FPS drawing loop.

### 2. `MediaObserver.kt`
Monitors active controllers using `MediaSessionManager`. Offloads blocking `getActiveSessions()` calls to a background thread to prevent UI thread latency on wakes. Playback position tracking calculates elapsed time using `SystemClock.elapsedRealtime()` relative to the last metadata update for millisecond-perfect lyric sync.

### 3. `LyricsManager.kt`
Fetches lyrics asynchronously from LRCLIB. Implements a **Dynamic Gap Heuristic** that dynamically calculates instrumental countdown lengths (visualized as a 3-dot countdown) based on line density and track characteristics. Lyrics are stored locally in a file-based JSON cache.

## Configuration

- **Sync Offset:** Precision control from -1000ms to +1000ms with bi-directional slider and manual numeric input.
- **Auto Bluetooth Delay:** Toggle to enable automatic real-time Bluetooth latency detection and alignment.
- **Background Speed:** Configurable fluid motion multiplier from 0.1x up to 10.0x with manual input support.
- **Dynamic Theming:** Optional support for Material You system-wide color synchronization.
- **Cache Management:** Built-in lyrics cache utility with a confirmation-protected clear function.

## Build and Deploy

Ensure `ANDROID_HOME` and `JAVA_HOME` (JDK 17+) are correctly set in your environment.

```bash
export ANDROID_HOME=~/Android/Sdk
export JAVA_HOME=/usr/lib/jvm/java-17-openjdk
gradle assembleDebug
```

The resulting APK will be located at `app/build/outputs/apk/debug/app-debug.apk`.
