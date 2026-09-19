package com.dnk.wallpaperlyrics

import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothManager
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.text.InputType
import android.util.TypedValue
import android.view.Gravity
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.dnk.wallpaperlyrics.LyricsSettings as LS

class BluetoothOffsetActivity : AppCompatActivity() {

    private lateinit var prefs: SharedPreferences
    private lateinit var contentContainer: LinearLayout

    companion object {
        private const val REQUEST_BLUETOOTH_CONNECT = 202
        private const val KEY_BLUETOOTH_PERMISSION_REQUESTED = "bluetooth_permission_requested"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        prefs = getSharedPreferences("settings", Context.MODE_PRIVATE)

        try {
            val rootLayout = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                gravity = Gravity.CENTER_HORIZONTAL
                setPadding(
                    LS.dpToPx(this@BluetoothOffsetActivity, 24f),
                    LS.dpToPx(this@BluetoothOffsetActivity, 16f),
                    LS.dpToPx(this@BluetoothOffsetActivity, 24f),
                    LS.dpToPx(this@BluetoothOffsetActivity, 40f)
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
                    topMargin = LS.dpToPx(this@BluetoothOffsetActivity, 24f)
                    bottomMargin = LS.dpToPx(this@BluetoothOffsetActivity, 16f)
                }
            }

            val backButton = android.widget.ImageView(this).apply {
                val arrowDrawable = LS.CustomIconDrawable(this@BluetoothOffsetActivity, LS.IconType.ARROW_LEFT)
                setImageDrawable(arrowDrawable)
                val size = LS.dpToPx(this@BluetoothOffsetActivity, 48f)
                layoutParams = android.widget.RelativeLayout.LayoutParams(size, size).apply {
                    addRule(android.widget.RelativeLayout.ALIGN_PARENT_LEFT)
                    addRule(android.widget.RelativeLayout.CENTER_VERTICAL)
                }
                setPadding(
                    LS.dpToPx(this@BluetoothOffsetActivity, 12f),
                    LS.dpToPx(this@BluetoothOffsetActivity, 12f),
                    LS.dpToPx(this@BluetoothOffsetActivity, 12f),
                    LS.dpToPx(this@BluetoothOffsetActivity, 12f)
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
                text = "Bluetooth Offsets"
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
                text = "Configure a separate lyrics sync offset for each paired Bluetooth device. When that device is the active audio output, its offset is added on top of the global sync offset."
                textSize = 13f
                setTextColor(Color.parseColor("#8E8E93"))
                setLineSpacing(LS.dpToPx(this@BluetoothOffsetActivity, 2f).toFloat(), 1.15f)
                setPadding(
                    LS.dpToPx(this@BluetoothOffsetActivity, 12f),
                    LS.dpToPx(this@BluetoothOffsetActivity, 4f),
                    LS.dpToPx(this@BluetoothOffsetActivity, 12f),
                    LS.dpToPx(this@BluetoothOffsetActivity, 16f)
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
        rebuildDeviceList()
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
                LS.dpToPx(this@BluetoothOffsetActivity, 12f),
                LS.dpToPx(this@BluetoothOffsetActivity, 16f),
                LS.dpToPx(this@BluetoothOffsetActivity, 12f),
                LS.dpToPx(this@BluetoothOffsetActivity, 8f)
            )
        }
    }

    private fun rebuildDeviceList() {
        if (!::contentContainer.isInitialized) return
        contentContainer.removeAllViews()

        contentContainer.addView(addSectionHeader("Paired Devices"))
        val card = LS.SettingsCard(this)

        if (!hasBluetoothPermission()) {
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
        } else {
            val bondedDevices = getBondedDevices()
            if (bondedDevices.isEmpty()) {
                val emptyRow = LS.SettingsRow(
                    this,
                    LS.IconType.BLUETOOTH,
                    "No Paired Devices",
                    "No paired Bluetooth devices found",
                    LS.TrailingType.NONE
                )
                card.addRow(emptyRow)
            } else {
                for (device in bondedDevices) {
                    val address = device.address
                    val name = try {
                        if (!device.name.isNullOrBlank()) device.name else address
                    } catch (e: SecurityException) {
                        address
                    }
                    val offset = prefs.getInt(DeviceOffsets.offsetKey(address), 0)
                    lateinit var row: LS.SettingsRow
                    row = LS.SettingsRow(
                        this,
                        LS.IconType.BLUETOOTH,
                        name,
                        address,
                        LS.TrailingType.VALUE,
                        "${offset}ms",
                        onClick = {
                            val currentOffset = prefs.getInt(DeviceOffsets.offsetKey(address), 0)
                            showCustomEditDialog(
                                "Set Offset",
                                currentOffset.toString(),
                                -1000f,
                                1000f,
                                false,
                                "ms",
                                hint = "Negative shows lyrics earlier. Positive delays them."
                            ) { newVal ->
                                val offsetVal = newVal.toInt()
                                prefs.edit().putInt(DeviceOffsets.offsetKey(address), offsetVal).apply()
                                row.updateValue("${offsetVal}ms")
                            }
                        }
                    )
                    card.addRow(row)
                }
            }
        }
        contentContainer.addView(card)
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

    private fun showCustomEditDialog(
        title: String,
        initialVal: String,
        minVal: Float,
        maxVal: Float,
        isFloat: Boolean,
        unit: String,
        hint: String = "",
        onValueSaved: (Float) -> Unit
    ) {
        val dialog = android.app.Dialog(this).apply {
            requestWindowFeature(android.view.Window.FEATURE_NO_TITLE)
            setCancelable(true)
        }

        val container = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(LS.dpToPx(this@BluetoothOffsetActivity, 24f), LS.dpToPx(this@BluetoothOffsetActivity, 24f), LS.dpToPx(this@BluetoothOffsetActivity, 24f), LS.dpToPx(this@BluetoothOffsetActivity, 20f))
            clipToPadding = false
            background = GradientDrawable().apply {
                setColor(Color.parseColor("#333333"))
                cornerRadius = LS.dpToPx(this@BluetoothOffsetActivity, 16f).toFloat()
            }
        }

        val titleText = TextView(this).apply {
            text = if (unit.isNotEmpty()) "$title ($unit)" else title
            textSize = 18f
            setTextColor(Color.WHITE)
            setTypeface(android.graphics.Typeface.create("sans-serif-medium", android.graphics.Typeface.BOLD))
            setPadding(0, 0, 0, LS.dpToPx(this@BluetoothOffsetActivity, 8f))
        }
        container.addView(titleText)

        if (hint.isNotEmpty()) {
            val hintText = TextView(this).apply {
                text = hint
                setTextColor(Color.parseColor("#8E8E93"))
                textSize = 12f
            }
            container.addView(hintText)
        }

        val seekBar = LS.SettingsSlider(this).apply {
            max = 1000
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LS.dpToPx(this@BluetoothOffsetActivity, 48f)
            ).apply {
                marginStart = LS.dpToPx(this@BluetoothOffsetActivity, -10f)
                marginEnd = LS.dpToPx(this@BluetoothOffsetActivity, -10f)
                topMargin = LS.dpToPx(this@BluetoothOffsetActivity, 12f)
                bottomMargin = LS.dpToPx(this@BluetoothOffsetActivity, -12f)
            }
            val parsedInitial = initialVal.toFloatOrNull()?.coerceIn(minVal, maxVal) ?: minVal
            progress = (((parsedInitial - minVal) / (maxVal - minVal)) * 1000f).toInt()
        }
        container.addView(seekBar)

        val rangeRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                bottomMargin = LS.dpToPx(this@BluetoothOffsetActivity, 16f)
            }
            setPadding(0, 0, 0, 0)
        }

        val minText = TextView(this).apply {
            text = if (isFloat) "$minVal" else "${minVal.toInt()}"
            setTextColor(Color.parseColor("#8E8E93"))
            textSize = 13f
            gravity = Gravity.START
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        }
        rangeRow.addView(minText)

        val maxText = TextView(this).apply {
            text = if (isFloat) "$maxVal" else "${maxVal.toInt()}"
            setTextColor(Color.parseColor("#8E8E93"))
            textSize = 13f
            gravity = Gravity.END
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        }
        rangeRow.addView(maxText)

        container.addView(rangeRow)

        val inputEdit = EditText(this).apply {
            setText(initialVal)
            inputType = if (isFloat) {
                InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_FLAG_DECIMAL
            } else {
                InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_FLAG_SIGNED
            }
            showSoftInputOnFocus = false
            setTextColor(Color.WHITE)
            textSize = 18f
            setPadding(LS.dpToPx(this@BluetoothOffsetActivity, 16f), LS.dpToPx(this@BluetoothOffsetActivity, 12f), LS.dpToPx(this@BluetoothOffsetActivity, 16f), LS.dpToPx(this@BluetoothOffsetActivity, 12f))
            background = GradientDrawable().apply {
                setColor(Color.parseColor("#242424"))
                cornerRadius = LS.dpToPx(this@BluetoothOffsetActivity, 10f).toFloat()
                setStroke(LS.dpToPx(this@BluetoothOffsetActivity, 1f), Color.parseColor("#444444"))
            }
            setSelection(text.length)

            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
                textCursorDrawable = android.graphics.drawable.ColorDrawable(Color.parseColor("#b7b7b7"))
            }

            setOnClickListener {
                val imm = getSystemService(Context.INPUT_METHOD_SERVICE) as? android.view.inputmethod.InputMethodManager
                imm?.showSoftInput(this, android.view.inputmethod.InputMethodManager.SHOW_IMPLICIT)
            }
        }
        container.addView(inputEdit)

        var syncing = false

        seekBar.onProgressChanged = { progress, fromUser ->
            if (fromUser && !syncing) {
                syncing = true
                val value = minVal + (maxVal - minVal) * (progress / 1000f)
                val formatted = if (isFloat) {
                    String.format("%.1f", value)
                } else {
                    value.toInt().toString()
                }
                inputEdit.setText(formatted)
                inputEdit.setSelection(inputEdit.text.length)
                syncing = false
            }
        }

        inputEdit.addTextChangedListener(object : android.text.TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: android.text.Editable?) {
                if (!syncing) {
                    syncing = true
                    val parsed = s?.toString()?.toFloatOrNull()
                    if (parsed != null && parsed in minVal..maxVal) {
                        val progress = (((parsed - minVal) / (maxVal - minVal)) * 1000f).toInt()
                        seekBar.progress = progress
                    }
                    syncing = false
                }
            }
        })

        val buttonLayout = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.END
            setPadding(0, LS.dpToPx(this@BluetoothOffsetActivity, 20f), 0, 0)
        }

        val cancelButton = Button(this).apply {
            text = "Cancel"
            setTextColor(Color.parseColor("#8E8E93"))
            transformationMethod = null
            background = null
            setOnClickListener { dialog.dismiss() }
        }
        buttonLayout.addView(cancelButton)

        val saveButton = Button(this).apply {
            text = "Save"
            setTextColor(Color.parseColor("#E0E0E0"))
            transformationMethod = null
            background = null
            setOnClickListener {
                val textStr = inputEdit.text.toString()
                val floatVal = textStr.toFloatOrNull()
                if (floatVal != null) {
                    val clamped = floatVal.coerceIn(minVal, maxVal)
                    onValueSaved(clamped)
                }
                dialog.dismiss()
            }
        }
        buttonLayout.addView(saveButton)
        container.addView(buttonLayout)

        dialog.setContentView(container)

        dialog.setOnShowListener {
            inputEdit.requestFocus()
        }

        dialog.window?.apply {
            setSoftInputMode(android.view.WindowManager.LayoutParams.SOFT_INPUT_STATE_HIDDEN)
            setBackgroundDrawable(android.graphics.drawable.ColorDrawable(Color.TRANSPARENT))
            setLayout(
                (resources.displayMetrics.widthPixels * 0.85f).toInt(),
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
        }

        dialog.show()
    }
}
