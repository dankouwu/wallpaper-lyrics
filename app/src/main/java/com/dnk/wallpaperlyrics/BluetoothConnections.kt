package com.dnk.wallpaperlyrics

import android.Manifest
import android.bluetooth.BluetoothA2dp
import android.bluetooth.BluetoothHeadset
import android.bluetooth.BluetoothHearingAid
import android.bluetooth.BluetoothLeAudio
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothProfile
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.os.Build
import android.util.Log

// Which paired devices are connected, from the Bluetooth profile proxies. These return the same
// BluetoothDevice objects as the paired list, so the addresses match it. Matching the address of
// the current audio output did not: on a real phone nothing was marked Connected. Without
// BLUETOOTH_CONNECT everything stays empty and callers fall back to the audio output.
class BluetoothConnections(private val context: Context, private val onChange: () -> Unit) {

    // Media profiles come first, so the device playing music wins over one connected only for calls.
    private val profiles: List<Int> = buildList {
        add(BluetoothProfile.A2DP)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) add(BluetoothProfile.LE_AUDIO)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) add(BluetoothProfile.HEARING_AID)
        add(BluetoothProfile.HEADSET)
    }

    private val proxies = HashMap<Int, BluetoothProfile>()
    private var started = false

    private val listener = object : BluetoothProfile.ServiceListener {
        override fun onServiceConnected(profile: Int, proxy: BluetoothProfile) {
            if (!started) {
                // Arrived after stop.
                try { adapter()?.closeProfileProxy(profile, proxy) } catch (e: Exception) {}
                return
            }
            proxies[profile] = proxy
            onChange()
        }

        override fun onServiceDisconnected(profile: Int) {
            if (proxies.remove(profile) != null && started) onChange()
        }
    }

    private val receiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) = onChange()
    }

    private fun adapter() = context.getSystemService(BluetoothManager::class.java)?.adapter

    private fun hasPermission(): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.S ||
            context.checkSelfPermission(Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED

    fun start() {
        if (started || !hasPermission()) return
        val adapter = adapter() ?: return
        started = true
        for (profile in profiles) {
            try {
                adapter.getProfileProxy(context, listener, profile)
            } catch (e: Exception) {
                Log.w("BluetoothConnections", "No proxy for profile $profile", e)
            }
        }
        val filter = IntentFilter().apply {
            addAction(BluetoothA2dp.ACTION_CONNECTION_STATE_CHANGED)
            addAction(BluetoothHeadset.ACTION_CONNECTION_STATE_CHANGED)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) addAction(BluetoothHearingAid.ACTION_CONNECTION_STATE_CHANGED)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) addAction(BluetoothLeAudio.ACTION_LE_AUDIO_CONNECTION_STATE_CHANGED)
        }
        // System broadcasts still arrive at a receiver that is not exported.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            context.registerReceiver(receiver, filter, Context.RECEIVER_NOT_EXPORTED)
        } else {
            context.registerReceiver(receiver, filter)
        }
    }

    fun stop() {
        if (!started) return
        started = false
        try {
            context.unregisterReceiver(receiver)
        } catch (e: Exception) {}
        val adapter = adapter()
        // closeProfileProxy calls onServiceDisconnected right away, which removes from proxies,
        // so iterating proxies itself here threw ConcurrentModificationException.
        val open = proxies.toList()
        proxies.clear()
        for ((profile, proxy) in open) {
            try {
                adapter?.closeProfileProxy(profile, proxy)
            } catch (e: Exception) {}
        }
    }

    private fun connectedOn(profile: Int): List<String> = try {
        proxies[profile]?.connectedDevices?.map { it.address.uppercase() } ?: emptyList()
    } catch (e: SecurityException) {
        emptyList()
    }

    fun connectedAddresses(): Set<String> = profiles.flatMap { connectedOn(it) }.toSet()

    /** The connected device the lyrics offset applies to, or null when none is known. */
    fun mediaAddress(): String? = profiles.firstNotNullOfOrNull { connectedOn(it).firstOrNull() }
}
