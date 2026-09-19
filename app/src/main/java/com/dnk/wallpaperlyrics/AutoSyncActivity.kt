package com.dnk.wallpaperlyrics

import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.util.TypedValue
import android.view.Gravity
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.dnk.wallpaperlyrics.LyricsSettings as LS

class AutoSyncActivity : AppCompatActivity() {

    private lateinit var prefs: SharedPreferences
    private lateinit var autoSyncSwitchRow: LS.SettingsRow
    private lateinit var audioAccessRow: LS.SettingsRow
    private lateinit var statusRow: LS.SettingsRow
    private lateinit var clearOffsetsRow: LS.SettingsRow

    private val prefChangeListener = SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
        if (key == null || key == AutoSyncSettings.KEY_STATUS) {
            refreshStatusRow()
        }
        if (key == null || key.startsWith(AutoSyncSettings.AUTO_OFFSET_PREFIX)) {
            refreshClearOffsetsRow()
        }
        if (key == null || key == AutoSyncSettings.KEY_ENABLED) {
            refreshAutoSyncSwitchRow()
        }
    }

    companion object {
        private const val REQUEST_RECORD_AUDIO = 201
        private const val KEY_AUDIO_PERMISSION_REQUESTED = "audio_permission_requested"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        prefs = getSharedPreferences("settings", Context.MODE_PRIVATE)

        try {
            val rootLayout = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                gravity = Gravity.CENTER_HORIZONTAL
                setPadding(
                    LS.dpToPx(this@AutoSyncActivity, 24f),
                    LS.dpToPx(this@AutoSyncActivity, 16f),
                    LS.dpToPx(this@AutoSyncActivity, 24f),
                    LS.dpToPx(this@AutoSyncActivity, 40f)
                )
                setBackgroundColor(Color.parseColor("#242424"))
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.MATCH_PARENT
                )
            }

            val headerLayout = android.widget.RelativeLayout(this).apply {
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                ).apply {
                    topMargin = LS.dpToPx(this@AutoSyncActivity, 24f)
                    bottomMargin = LS.dpToPx(this@AutoSyncActivity, 16f)
                }
            }

            val backButton = android.widget.ImageView(this).apply {
                val arrowDrawable = LS.CustomIconDrawable(this@AutoSyncActivity, LS.IconType.ARROW_LEFT)
                setImageDrawable(arrowDrawable)
                val size = LS.dpToPx(this@AutoSyncActivity, 48f)
                layoutParams = android.widget.RelativeLayout.LayoutParams(size, size).apply {
                    addRule(android.widget.RelativeLayout.ALIGN_PARENT_LEFT)
                    addRule(android.widget.RelativeLayout.CENTER_VERTICAL)
                }
                setPadding(
                    LS.dpToPx(this@AutoSyncActivity, 12f),
                    LS.dpToPx(this@AutoSyncActivity, 12f),
                    LS.dpToPx(this@AutoSyncActivity, 12f),
                    LS.dpToPx(this@AutoSyncActivity, 12f)
                )
                isClickable = true
                val outVal = TypedValue()
                theme.resolveAttribute(android.R.attr.selectableItemBackgroundBorderless, outVal, true)
                setBackgroundResource(outVal.resourceId)
                setOnClickListener {
                    finish()
                }
            }
            headerLayout.addView(backButton)

            val titleView = TextView(this).apply {
                text = "Automatic Sync"
                textSize = 24f
                setTextColor(Color.WHITE)
                setTypeface(android.graphics.Typeface.create("sans-serif-black", android.graphics.Typeface.BOLD))
                paint.isFakeBoldText = true
                gravity = Gravity.CENTER
                layoutParams = android.widget.RelativeLayout.LayoutParams(
                    android.widget.RelativeLayout.LayoutParams.WRAP_CONTENT,
                    android.widget.RelativeLayout.LayoutParams.WRAP_CONTENT
                ).apply {
                    addRule(android.widget.RelativeLayout.CENTER_IN_PARENT)
                }
            }
            headerLayout.addView(titleView)
            rootLayout.addView(headerLayout)

            val explanationView = TextView(this).apply {
                text = "It listens to audio played on this phone to line up lyrics, once per song, and saves the result for that song. Android labels this as microphone access, but only playback on this phone is analysed. Nothing is recorded or sent anywhere. Headphones are fine. It cannot detect audio at volume 0. The global offset still applies on top, and a manual song delay always wins."
                textSize = 13f
                setTextColor(Color.parseColor("#8E8E93"))
                setLineSpacing(LS.dpToPx(this@AutoSyncActivity, 2f).toFloat(), 1.15f)
                setPadding(
                    LS.dpToPx(this@AutoSyncActivity, 12f),
                    LS.dpToPx(this@AutoSyncActivity, 4f),
                    LS.dpToPx(this@AutoSyncActivity, 12f),
                    LS.dpToPx(this@AutoSyncActivity, 16f)
                )
            }
            rootLayout.addView(explanationView)

            fun addSectionHeader(title: String) {
                val header = TextView(this).apply {
                    text = title
                    textSize = 13f
                    setTextColor(Color.parseColor("#8E8E93"))
                    setTypeface(android.graphics.Typeface.create("sans-serif-medium", android.graphics.Typeface.BOLD))
                    setPadding(
                        LS.dpToPx(this@AutoSyncActivity, 12f),
                        LS.dpToPx(this@AutoSyncActivity, 16f),
                        LS.dpToPx(this@AutoSyncActivity, 12f),
                        LS.dpToPx(this@AutoSyncActivity, 8f)
                    )
                }
                rootLayout.addView(header)
            }

            addSectionHeader("Settings")
            val card1 = LS.SettingsCard(this).apply {
                val isAutoSyncEnabled = prefs.getBoolean(AutoSyncSettings.KEY_ENABLED, false)
                autoSyncSwitchRow = LS.SettingsRow(
                    this@AutoSyncActivity,
                    LS.IconType.TIMER,
                    "Automatic Sync",
                    "Line up lyrics from audio playback",
                    LS.TrailingType.SWITCH,
                    isAutoSyncEnabled.toString(),
                    onCheckedChange = { checked ->
                        prefs.edit().putBoolean(AutoSyncSettings.KEY_ENABLED, checked).apply()
                        if (checked && !hasAudioPermission()) {
                            requestAudioPermissionOrOpenSettings()
                        }
                    }
                )
                addRow(autoSyncSwitchRow)

                val hasAccess = hasAudioPermission()
                audioAccessRow = LS.SettingsRow(
                    this@AutoSyncActivity,
                    LS.IconType.INFO,
                    "Audio Access",
                    "Required to analyse audio playback",
                    LS.TrailingType.VALUE,
                    if (hasAccess) "Granted" else "Not granted",
                    onClick = {
                        if (!hasAudioPermission()) {
                            requestAudioPermissionOrOpenSettings()
                        }
                    }
                )
                addRow(audioAccessRow)
            }
            rootLayout.addView(card1)

            addSectionHeader("Current Song")
            val card2 = LS.SettingsCard(this).apply {
                val rawStatus = prefs.getString(AutoSyncSettings.KEY_STATUS, null)
                val initialStatus = if (rawStatus.isNullOrEmpty()) "No status yet" else rawStatus
                statusRow = LS.SettingsRow(
                    this@AutoSyncActivity,
                    LS.IconType.INFO,
                    "Status",
                    initialStatus,
                    LS.TrailingType.NONE
                )
                addRow(statusRow)

                val redetectRow = LS.SettingsRow(
                    this@AutoSyncActivity,
                    LS.IconType.RELOAD,
                    "Re-detect This Song",
                    "Forget detected offset and listen again",
                    LS.TrailingType.NONE,
                    onClick = {
                        val intent = Intent("com.dnk.wallpaperlyrics.AUTO_SYNC_REDETECT").setPackage(packageName)
                        sendBroadcast(intent)
                        Toast.makeText(this@AutoSyncActivity, "Re-detecting current song", Toast.LENGTH_SHORT).show()
                    }
                )
                addRow(redetectRow)

                clearOffsetsRow = LS.SettingsRow(
                    this@AutoSyncActivity,
                    LS.IconType.DELETE,
                    "Clear All Detected Offsets",
                    "None stored",
                    LS.TrailingType.NONE,
                    onClick = {
                        val keys = getDetectedOffsetKeys()
                        if (keys.isEmpty()) return@SettingsRow
                        val count = keys.size
                        val message = if (count == 1) {
                            "This will remove the detected sync offset for 1 song."
                        } else {
                            "This will remove detected sync offsets for $count songs."
                        }
                        showCustomConfirmDialog(
                            title = "Clear Detected Offsets",
                            message = message,
                            confirmText = "Clear"
                        ) {
                            val editor = prefs.edit()
                            for (key in keys) {
                                editor.remove(key)
                            }
                            editor.apply()
                            val toastMsg = if (count == 1) "Cleared 1 detected offset" else "Cleared $count detected offsets"
                            Toast.makeText(this@AutoSyncActivity, toastMsg, Toast.LENGTH_SHORT).show()
                            refreshClearOffsetsRow()
                        }
                    }
                )
                addRow(clearOffsetsRow)
            }
            rootLayout.addView(card2)

            val scrollView = ScrollView(this).apply {
                isFillViewport = true
            }
            scrollView.addView(rootLayout)
            setContentView(scrollView)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    override fun onResume() {
        super.onResume()
        prefs.registerOnSharedPreferenceChangeListener(prefChangeListener)
        refreshAutoSyncSwitchRow()
        refreshAudioAccessRow()
        refreshStatusRow()
        refreshClearOffsetsRow()
    }

    override fun onPause() {
        super.onPause()
        prefs.unregisterOnSharedPreferenceChangeListener(prefChangeListener)
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == REQUEST_RECORD_AUDIO) {
            refreshAudioAccessRow()
            refreshStatusRow()
        }
    }

    private fun hasAudioPermission(): Boolean {
        return checkSelfPermission(android.Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED
    }

    private fun requestAudioPermissionOrOpenSettings() {
        val deniedBefore = prefs.getBoolean(KEY_AUDIO_PERMISSION_REQUESTED, false)
        val canShowRationale = shouldShowRequestPermissionRationale(android.Manifest.permission.RECORD_AUDIO)

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
            prefs.edit().putBoolean(KEY_AUDIO_PERMISSION_REQUESTED, true).apply()
            requestPermissions(arrayOf(android.Manifest.permission.RECORD_AUDIO), REQUEST_RECORD_AUDIO)
        }
    }

    private fun refreshAutoSyncSwitchRow() {
        if (::autoSyncSwitchRow.isInitialized) {
            val enabled = prefs.getBoolean(AutoSyncSettings.KEY_ENABLED, false)
            for (i in 0 until autoSyncSwitchRow.childCount) {
                val child = autoSyncSwitchRow.getChildAt(i)
                if (child is androidx.appcompat.widget.SwitchCompat) {
                    if (child.isChecked != enabled) {
                        child.isChecked = enabled
                    }
                    break
                }
            }
        }
    }

    private fun refreshAudioAccessRow() {
        if (::audioAccessRow.isInitialized) {
            val granted = hasAudioPermission()
            audioAccessRow.updateValue(if (granted) "Granted" else "Not granted")
        }
    }

    private fun refreshStatusRow() {
        if (::statusRow.isInitialized) {
            val rawStatus = prefs.getString(AutoSyncSettings.KEY_STATUS, null)
            val displayStatus = if (rawStatus.isNullOrEmpty()) "No status yet" else rawStatus
            statusRow.updateSubtitle(displayStatus)
        }
    }

    private fun getDetectedOffsetKeys(): List<String> {
        val all = prefs.all ?: return emptyList()
        return all.keys.filter { it.startsWith(AutoSyncSettings.AUTO_OFFSET_PREFIX) }
    }

    private fun refreshClearOffsetsRow() {
        if (::clearOffsetsRow.isInitialized) {
            val count = getDetectedOffsetKeys().size
            if (count == 0) {
                clearOffsetsRow.updateSubtitle("None stored")
                clearOffsetsRow.setRowEnabled(false)
            } else {
                val sub = if (count == 1) "1 song stored" else "$count songs stored"
                clearOffsetsRow.updateSubtitle(sub)
                clearOffsetsRow.setRowEnabled(true)
            }
        }
    }

    private fun showCustomConfirmDialog(
        title: String,
        message: String,
        confirmText: String,
        onConfirm: () -> Unit
    ) {
        val dialog = android.app.Dialog(this).apply {
            requestWindowFeature(android.view.Window.FEATURE_NO_TITLE)
            setCancelable(true)
        }

        val container = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(
                LS.dpToPx(this@AutoSyncActivity, 24f),
                LS.dpToPx(this@AutoSyncActivity, 24f),
                LS.dpToPx(this@AutoSyncActivity, 24f),
                LS.dpToPx(this@AutoSyncActivity, 20f)
            )
            background = GradientDrawable().apply {
                setColor(Color.parseColor("#333333"))
                cornerRadius = LS.dpToPx(this@AutoSyncActivity, 16f).toFloat()
            }
        }

        val titleText = TextView(this).apply {
            text = title
            textSize = 18f
            setTextColor(Color.WHITE)
            setTypeface(android.graphics.Typeface.create("sans-serif-medium", android.graphics.Typeface.BOLD))
            setPadding(0, 0, 0, LS.dpToPx(this@AutoSyncActivity, 10f))
        }
        container.addView(titleText)

        val msgText = TextView(this).apply {
            text = message
            textSize = 14f
            setTextColor(Color.parseColor("#8E8E93"))
            setPadding(0, 0, 0, LS.dpToPx(this@AutoSyncActivity, 20f))
        }
        container.addView(msgText)

        val buttonLayout = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.END
        }

        val cancelButton = Button(this).apply {
            text = "Cancel"
            setTextColor(Color.parseColor("#8E8E93"))
            transformationMethod = null
            background = null
            minHeight = LS.dpToPx(this@AutoSyncActivity, 48f)
            setOnClickListener { dialog.dismiss() }
        }
        buttonLayout.addView(cancelButton)

        val confirmButton = Button(this).apply {
            text = confirmText
            setTextColor(Color.parseColor("#FF453A"))
            transformationMethod = null
            background = null
            minHeight = LS.dpToPx(this@AutoSyncActivity, 48f)
            setOnClickListener {
                onConfirm()
                dialog.dismiss()
            }
        }
        buttonLayout.addView(confirmButton)
        container.addView(buttonLayout)

        dialog.setContentView(container)

        dialog.window?.apply {
            setBackgroundDrawable(android.graphics.drawable.ColorDrawable(Color.TRANSPARENT))
            setLayout(
                (resources.displayMetrics.widthPixels * 0.85f).toInt(),
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
        }

        dialog.show()
    }
}
