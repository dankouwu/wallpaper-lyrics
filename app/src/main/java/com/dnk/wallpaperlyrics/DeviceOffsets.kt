package com.dnk.wallpaperlyrics

object DeviceOffsets {
    const val KEY_PREFIX = "bt_offset_"

    fun offsetKey(address: String): String = KEY_PREFIX + address.uppercase()

    fun resolve(activeAddress: String?, stored: Map<String, Int>): Int {
        if (activeAddress.isNullOrBlank()) return 0
        return stored[activeAddress.uppercase()] ?: 0
    }
}
