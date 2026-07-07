# Wallpaper Lyrics

A high-performance Android live wallpaper that renders a dynamic aurora background based on current album art, overlaid with smoothly animated, synced lyrics from the active media session.

## Features

- **Dynamic Background:** Utilizes an AGSL dual-texture domain-warping shader (Android 13+) with independent time offsets (`u_time` and `u_time_next`) for stutter-free crossfades, a 5-second swirl pre-simulation, and custom warmth and saturation filters.
- **Auto Bluetooth Delay:** Automatically detects and compensates for wireless audio delay (Bluetooth A2DP) dynamically on route changes and track starts using reflection APIs and `AudioDeviceCallback`.
- **Spotify Exclusive:** Optimized specifically for Spotify media sessions to ensure the highest reliability and metadata accuracy.
- **Color Extraction:** Implements an asynchronous 4-color palette extraction system using the Android Palette API with bilinear downsampling and monochromatic safeguards.
- **Synced Lyrics:** Fetches and displays timestamped lyrics from LRCLIB, featuring a predictive timing model and instrumental progress indicators.
- **Aesthetic Design:** Mimics the Apple Music "Lyrics" interface with extreme high-contrast Inter typography (Inter Black 900 active, Inter Bold 700 with a faux-extra-bold stroke inactive), chained blur effects, and a vibrant Neon/Midnight idle palette.
- **Performance:** Targets 60+ FPS using Hardware Accelerated Canvas, `Choreographer` callbacks, and background-thread color processing.

## Requirements

- Android 12 (API 31) or higher (AGSL features require Android 13+).
- **Battery Optimization:** Must be set to "Unrestricted" to ensure consistent rendering and network access.
- **Media Access:** Notification access is required to observe Spotify media metadata.
- **Spotify Settings:** "Device Broadcast Status" must be enabled in Spotify settings.

## Configuration
 
- **Sync Offset:** Precision control from -1000ms to +1000ms with bi-directional slider and manual numeric input.
- **Auto Bluetooth Delay:** Toggle to enable automatic real-time Bluetooth latency detection and alignment.
- **Background Speed:** Configurable fluid motion multiplier from 0.1x up to 10.0x with manual input support.
- **Dynamic Theming:** Optional support for Material You system-wide color synchronization.
- **Cache Management:** Built-in lyrics cache utility with a confirmation-protected clear function.

## Build Instructions

Ensure `ANDROID_HOME` and `JAVA_HOME` (JDK 17+) are correctly set in your environment.

```bash
export ANDROID_HOME=~/Android/Sdk
export JAVA_HOME=/usr/lib/jvm/java-17-openjdk
./gradlew assembleDebug
```

The resulting APK will be located at `app/build/outputs/apk/debug/app-debug.apk`.

## Architecture

- **LyricsWallpaperService:** Core engine handling AGSL shader rendering, hardware canvas synchronization, and screen-state transitions.
- **MediaObserver:** Monitors `MediaSessionManager` specifically for active Spotify controllers.
- **LyricsManager:** Handles LRCLIB API communication, local JSON caching, and dynamic gap heuristics for instrumental breaks.
