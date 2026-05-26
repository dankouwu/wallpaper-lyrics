# Wallpaper Lyrics

A high-performance Android live wallpaper that renders a dynamic aurora background based on current album art, overlaid with smoothly animated, synced lyrics from the active media session.

## Features

- **Dynamic Background:** Utilizes an AGSL domain-warping shader (Android 13+) to generate fluid, liquid-style gradients.
- **Color Extraction:** Implements an asynchronous 4-color palette extraction system using the Android Palette API with bilinear downsampling to prevent color fringing.
- **Synced Lyrics:** Fetches and displays timestamped lyrics from LRCLIB, featuring a predictive timing model for millisecond-perfect synchronization.
- **Aesthetic Design:** Mimics the Apple Music "Lyrics" interface with high-contrast Inter typography and chained blur effects.
- **Performance:** Targets 60+ FPS using Hardware Accelerated Canvas and Choreographer-aligned frame callbacks.

## Requirements

- Android 12 (API 31) or higher (AGSL features require Android 13+).
- **Battery Optimization:** Must be set to "Unrestricted" to ensure consistent rendering and background network access.
- **Media Access:** Notification access is required to observe media controller metadata.
- **Spotify Support:** "Device Broadcast Status" must be enabled in Spotify settings for metadata visibility.

## Configuration

- **Sync Offset:** Adjustable from -1000ms to +1000ms to account for Bluetooth latency or lyrics discrepancy.
- **Background Speed:** Configurable animation speed multiplier (0.1x - 2.0x).
- **Dynamic Theming:** Optional support for Material You system-wide color synchronization.

## Build Instructions

Ensure `ANDROID_HOME` is correctly set in your environment.

```bash
export ANDROID_HOME=~/Android/Sdk
./gradlew assembleDebug
```

The resulting APK will be located at `app/build/outputs/apk/debug/app-debug.apk`.

## Architecture

- **LyricsWallpaperService:** Core engine handling Hardware Canvas rendering and screen state management.
- **MediaObserver:** Monitors MediaSessionManager for active playback and position tracking.
- **LyricsManager:** Handles API communication, JSON caching, and dynamic gap heuristics for instrumental breaks.
