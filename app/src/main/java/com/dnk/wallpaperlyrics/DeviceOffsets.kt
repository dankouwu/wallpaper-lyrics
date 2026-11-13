package com.dnk.wallpaperlyrics

import android.media.AudioDeviceInfo
import android.media.AudioManager
import android.os.Build

object DeviceOffsets {
    const val KEY_PREFIX = "bt_offset_"

    fun offsetKey(address: String): String = KEY_PREFIX + address.uppercase()

    fun resolve(activeAddress: String?, stored: Map<String, Int>): Int {
        if (activeAddress.isNullOrBlank()) return 0
        return stored[activeAddress.uppercase()] ?: 0
    }

    fun isActive(address: String, activeAddress: String?): Boolean =
        address.equals(activeAddress, ignoreCase = true)

    // The device the offset applies to, then the other connected ones, then the rest, each group
    // keeping its order.
    fun <T> connectedFirst(
        devices: List<T>,
        activeAddress: String?,
        connected: Set<String>,
        addressOf: (T) -> String
    ): List<T> {
        return devices.sortedBy {
            val address = addressOf(it)
            when {
                isActive(address, activeAddress) -> 0
                address.uppercase() in connected -> 1
                else -> 2
            }
        }
    }

    fun activeOutputAddress(audioManager: AudioManager): String? {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.P) return null
        val btDevice = audioManager.getDevices(AudioManager.GET_DEVICES_OUTPUTS).firstOrNull { dev ->
            dev.type == AudioDeviceInfo.TYPE_BLUETOOTH_A2DP ||
            dev.type == AudioDeviceInfo.TYPE_BLUETOOTH_SCO ||
            dev.type == AudioDeviceInfo.TYPE_HEARING_AID ||
            (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && (
                dev.type == AudioDeviceInfo.TYPE_BLE_HEADSET ||
                dev.type == AudioDeviceInfo.TYPE_BLE_SPEAKER ||
                dev.type == AudioDeviceInfo.TYPE_BLE_BROADCAST
            ))
        }
        return btDevice?.address
    }
}
