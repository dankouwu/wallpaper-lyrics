package com.dnk.wallpaperlyrics

enum class PreviewMode {
    SONG,
    IDLE_PLAY_HINT,
    IDLE_ACCESS_NEEDED,
    IDLE_FORCED
}

fun decidePreviewMode(
    hasNotificationAccess: Boolean,
    hasSong: Boolean,
    hasCover: Boolean,
    idleForced: Boolean
): PreviewMode {
    if (idleForced) {
        return PreviewMode.IDLE_FORCED
    }
    if (!hasNotificationAccess) {
        return PreviewMode.IDLE_ACCESS_NEEDED
    }
    if (hasSong && hasCover) {
        return PreviewMode.SONG
    }
    return PreviewMode.IDLE_PLAY_HINT
}

fun buildSongPreviewKey(
    title: String?,
    artist: String?,
    album: String?,
    artUri: String?
): String {
    // Null byte delimiter prevents field collisions when values contain arbitrary text.
    return "${title.orEmpty()}\u0000${artist.orEmpty()}\u0000${album.orEmpty()}\u0000${artUri.orEmpty()}"
}

fun isSameSongPreview(
    currentKey: String?,
    candidateKey: String?
): Boolean {
    if (currentKey == null || candidateKey == null) return false
    return currentKey == candidateKey
}
