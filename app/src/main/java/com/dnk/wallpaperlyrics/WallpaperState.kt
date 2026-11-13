package com.dnk.wallpaperlyrics

// What the wallpaper is showing, for other apps through WallpaperStateProvider. A launcher that
// colors itself from the wallpaper could only watch the same media sessions and guess which one
// the wallpaper picked, and the guess went wrong whenever the wallpaper went idle on its own.
object WallpaperState {

    // title is null on the idle screen. colors are accent, base, mid, highlight.
    data class Snapshot(
        val playerPackage: String?,
        val title: String?,
        val artist: String?,
        val colors: List<Int>
    )

    @Volatile
    var current: Snapshot? = null
        private set

    // With the same wallpaper on home and lock there can be two engines. The last one to change the
    // background owns the state, so the other one going away does not clear it.
    private var owner: Any? = null

    /** Returns true when readers need to be told. */
    @Synchronized
    fun publish(owner: Any, snapshot: Snapshot): Boolean {
        this.owner = owner
        if (snapshot == current) return false
        current = snapshot
        return true
    }

    /** Returns true when readers need to be told. */
    @Synchronized
    fun clear(owner: Any): Boolean {
        if (this.owner !== owner) return false
        this.owner = null
        if (current == null) return false
        current = null
        return true
    }
}
