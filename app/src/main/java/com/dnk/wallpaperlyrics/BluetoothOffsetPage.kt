package com.dnk.wallpaperlyrics

import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothManager
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.graphics.Color
import android.media.AudioManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.view.Gravity
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import com.dnk.wallpaperlyrics.LyricsSettings as LS

class BluetoothOffsetPage(activity: MainActivity) : SettingsPage(activity) {

    private lateinit var prefs: SharedPreferences
    private lateinit var contentContainer: LinearLayout
    private lateinit var scrollView: ScrollView
    private var offsetDialog: android.app.Dialog? = null

    companion object {
        private const val REQUEST_BLUETOOTH_CONNECT = 202
        private const val KEY_BLUETOOTH_PERMISSION_REQUESTED = "bluetooth_permission_requested"
    }

    override fun onCreate() {

        prefs = getSharedPreferences("settings", Context.MODE_PRIVATE)

        try {
            val rootLayout = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                gravity = Gravity.CENTER_HORIZONTAL
                setPadding(
                    LS.dpToPx(this@BluetoothOffsetPage, 24f),
                    LS.dpToPx(this@BluetoothOffsetPage, 16f),
                    LS.dpToPx(this@BluetoothOffsetPage, 24f),
                    LS.dpToPx(this@BluetoothOffsetPage, 40f)
                )
                setBackgroundColor(Color.parseColor("#242424"))
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.MATCH_PARENT
                )
            }

            val headerLayout = LS.pageHeader(this, "Bluetooth Offsets") { finish() }
            rootLayout.addView(headerLayout)

            val explanationView = TextView(this).apply {
                text = "Configure a separate lyrics sync offset for each paired Bluetooth device. When that device is the active audio output, its offset is added on top of the global sync offset."
                textSize = 13f
                setTextColor(Color.parseColor("#8E8E93"))
                setLineSpacing(LS.dpToPx(this@BluetoothOffsetPage, 2f).toFloat(), 1.15f)
                setPadding(
                    LS.dpToPx(this@BluetoothOffsetPage, 12f),
                    LS.dpToPx(this@BluetoothOffsetPage, 4f),
                    LS.dpToPx(this@BluetoothOffsetPage, 12f),
                    LS.dpToPx(this@BluetoothOffsetPage, 16f)
                )
            }
            rootLayout.addView(explanationView)

            contentContainer = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                )
            }
            rootLayout.addView(contentContainer)

            scrollView = ScrollView(this).apply {
                isFillViewport = true
            }
            scrollView.addView(rootLayout)
            setContentView(scrollView)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    // A device connecting or dropping moves it between the Connected and Paired Devices sections.
    private val connections by lazy { BluetoothConnections(this) { rebuildDeviceList() } }

    override fun onResume() {
        super.onResume()
        rebuildDeviceList()
        connections.start()
    }

    override fun onPause() {
        super.onPause()
        connections.stop()
    }

    override fun onStop() {
        super.onStop()
        offsetDialog?.dismiss()
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == REQUEST_BLUETOOTH_CONNECT) {
            rebuildDeviceList()
        }
    }

    private fun hasBluetoothPermission(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            checkSelfPermission(android.Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED
        } else {
            true
        }
    }

    private fun requestBluetoothPermissionOrOpenSettings() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val deniedBefore = prefs.getBoolean(KEY_BLUETOOTH_PERMISSION_REQUESTED, false)
            val canShowRationale = shouldShowRequestPermissionRationale(android.Manifest.permission.BLUETOOTH_CONNECT)

            if (deniedBefore && !canShowRationale) {
                try {
                    val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                        data = Uri.fromParts("package", packageName, null)
                    }
                    startActivity(intent)
                } catch (e: Exception) {
                    Toast.makeText(this, "Could not open settings", Toast.LENGTH_SHORT).show()
                }
            } else {
                prefs.edit().putBoolean(KEY_BLUETOOTH_PERMISSION_REQUESTED, true).apply()
                requestPermissions(arrayOf(android.Manifest.permission.BLUETOOTH_CONNECT), REQUEST_BLUETOOTH_CONNECT)
            }
        }
    }

    private fun addSectionHeader(title: String): TextView {
        return TextView(this).apply {
            text = title
            textSize = 13f
            setTextColor(Color.parseColor("#8E8E93"))
            setTypeface(android.graphics.Typeface.create("sans-serif-medium", android.graphics.Typeface.BOLD))
            setPadding(
                LS.dpToPx(this@BluetoothOffsetPage, 12f),
                LS.dpToPx(this@BluetoothOffsetPage, 16f),
                LS.dpToPx(this@BluetoothOffsetPage, 12f),
                LS.dpToPx(this@BluetoothOffsetPage, 8f)
            )
        }
    }

    private fun rebuildDeviceList() {
        if (!::contentContainer.isInitialized) return
        contentContainer.removeAllViews()

        if (!hasBluetoothPermission()) {
            contentContainer.addView(addSectionHeader("Paired Devices"))
            val card = LS.SettingsCard(this)
            val permissionRow = LS.SettingsRow(
                this,
                LS.IconType.INFO,
                "Bluetooth Permission Required",
                "Permission is needed to list paired devices. Tap to grant.",
                LS.TrailingType.NONE,
                onClick = {
                    requestBluetoothPermissionOrOpenSettings()
                }
            )
            card.addRow(permissionRow)
            contentContainer.addView(card)
            return
        }

        val connected = connections.connectedAddresses()
        val activeAddress = getActiveAddress()
        val bondedDevices = DeviceOffsets.connectedFirst(getBondedDevices(), activeAddress, connected) { it.address }
        // The active device counts as connected even when only AudioManager reports it.
        val (connectedDevices, pairedDevices) = bondedDevices.partition {
            DeviceOffsets.isActive(it.address, activeAddress) || it.address.uppercase() in connected
        }

        if (connectedDevices.isNotEmpty()) {
            contentContainer.addView(addSectionHeader("Connected"))
            val card = LS.SettingsCard(this)
            for (device in connectedDevices) card.addRow(deviceRow(device, activeAddress))
            contentContainer.addView(card)
        }

        if (pairedDevices.isNotEmpty() || connectedDevices.isEmpty()) {
            contentContainer.addView(addSectionHeader("Paired Devices"))
            val card = LS.SettingsCard(this)
            if (pairedDevices.isEmpty()) {
                val emptyRow = LS.SettingsRow(
                    this,
                    LS.IconType.BLUETOOTH,
                    "No Paired Devices",
                    "No paired Bluetooth devices found",
                    LS.TrailingType.NONE
                )
                card.addRow(emptyRow)
            }
            for (device in pairedDevices) card.addRow(deviceRow(device, activeAddress))
            contentContainer.addView(card)
        }
    }

    private fun deviceRow(device: BluetoothDevice, activeAddress: String?): LS.SettingsRow {
        val address = device.address
        val name = try {
            if (!device.name.isNullOrBlank()) device.name else address
        } catch (e: SecurityException) {
            address
        }
        val offset = prefs.getInt(DeviceOffsets.offsetKey(address), 0)
        val isActive = DeviceOffsets.isActive(address, activeAddress)
        return LS.SettingsRow(
            this,
            LS.IconType.BLUETOOTH,
            name,
            address,
            LS.TrailingType.VALUE,
            "${offset}ms",
            onClick = {
                val currentOffset = prefs.getInt(DeviceOffsets.offsetKey(address), 0)
                // Only the device playing audio can be previewed: the engine adds the
                // delta to whatever output is active, so any other device would shift
                // the lyrics by an offset that is not in use.
                offsetDialog = SettingsDialogs.showCustomEditDialog(
                    activity,
                    "Set Offset",
                    currentOffset.toString(),
                    -1000f,
                    1000f,
                    false,
                    "ms",
                    hint = if (isActive) {
                        "Negative shows lyrics earlier. Positive delays them."
                    } else {
                        "Negative shows lyrics earlier. Positive delays them. Connect this device to preview it on the wallpaper."
                    },
                    previewBehind = if (isActive) scrollView else null
                ) { newVal ->
                    prefs.edit().putInt(DeviceOffsets.offsetKey(address), newVal.toInt()).apply()
                    // The list may have been rebuilt while the dialog was open.
                    rebuildDeviceList()
                }
            }
        )
    }

    // The same device the wallpaper takes its offset from.
    private fun getActiveAddress(): String? {
        connections.mediaAddress()?.let { return it }
        val audioManager = getSystemService(Context.AUDIO_SERVICE) as? AudioManager ?: return null
        return DeviceOffsets.activeOutputAddress(audioManager)
    }

    private fun getBondedDevices(): List<BluetoothDevice> {
        return try {
            val bluetoothManager = getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager
            val adapter = bluetoothManager?.adapter ?: BluetoothAdapter.getDefaultAdapter()
            adapter?.bondedDevices?.sortedBy { (it.name ?: it.address).lowercase() } ?: emptyList()
        } catch (e: Exception) {
            emptyList()
        }
    }
}
