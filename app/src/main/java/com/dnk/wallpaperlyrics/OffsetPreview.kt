package com.dnk.wallpaperlyrics

// Slider value minus the saved offset while an offset dialog previews on the wallpaper. The draw
// loop reads it every frame, and Cancel has to leave nothing behind, so it is not a preference.
object OffsetPreview {
    @Volatile
    var deltaMs = 0L
}
