package dubrowgn.wattz

import android.annotation.SuppressLint
import android.app.Activity
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.util.Log
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.RadioGroup
import android.widget.Switch
import android.widget.TextView
import android.widget.Toast

const val settingsName = "settings"
const val settingsUpdateInd = "$namespace.settings-update-ind"
const val refreshIntervalKey = "refreshIntervalMs"
const val capacityMahKey = "capacityMah"
const val restThresholdmAKey = "restThresholdmA"
const val restStableSecondsKey = "restStableSeconds"
const val pullToOcvKKey = "pullToOcvK"
const val rateLimitUpPerMinKey = "rateLimitUpPerMin"
const val rateLimitDownPerMinKey = "rateLimitDownPerMin"
const val monotonicThresholdmAKey = "monotonicThresholdmA"

const val defaultCapacityMah = 5000.0
const val defaultRestThresholdmA = 100.0
const val defaultRestStableSeconds = 10.0
const val defaultPullToOcvK = 0.02
const val defaultRateLimitUpPerMin = 1.0
const val defaultRateLimitDownPerMin = 1.0
const val defaultMonotonicThresholdmA = 50.0

class SettingsActivity : Activity() {
    private val batteryReceiver = BatteryDataReceiver()

    private lateinit var charging: TextView
    private lateinit var currentScalar: RadioGroup
    private lateinit var indicatorUnits: RadioLayout
    private lateinit var indicatorDigits: EditText
    private lateinit var refreshInterval: EditText
    private lateinit var capacityMah: EditText
    private lateinit var restThresholdmA: EditText
    private lateinit var restStableSeconds: EditText
    private lateinit var pullToOcvK: EditText
    private lateinit var rateLimitUpPerMin: EditText
    private lateinit var rateLimitDownPerMin: EditText
    private lateinit var monotonicThresholdmA: EditText
    @SuppressLint("UseSwitchCompatOrMaterialCode")
    private lateinit var invertCurrent: Switch
    private lateinit var power: TextView
    private lateinit var importVoltageCurve: Button
    private lateinit var exportVoltageCurve: Button
    private lateinit var voltageCurveFields: List<VoltageCurveField>
    private var loadingVoltageCurve = false
    private var indicatorDigitsValue = defaultIndicatorDigits
    private var refreshIntervalValue = defaultRefreshIntervalMs
    private var capacityMahValue = defaultCapacityMah
    private var restThresholdmAValue = defaultRestThresholdmA
    private var restStableSecondsValue = defaultRestStableSeconds
    private var pullToOcvKValue = defaultPullToOcvK
    private var rateLimitUpPerMinValue = defaultRateLimitUpPerMin
    private var rateLimitDownPerMinValue = defaultRateLimitDownPerMin
    private var monotonicThresholdmAValue = defaultMonotonicThresholdmA

    private fun debug(msg: String) {
        Log.d(this::class.java.name, msg)
    }

    inner class BatteryDataReceiver : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            debug("BatteryDataReceiver.onReceive()")

            if (intent == null)
                return

            val ind = getString(R.string.indeterminate)

            charging.text = intent.getStringExtra("charging") ?: ind
            power.text = intent.getStringExtra("power") ?: ind
        }
    }

    private fun loadPrefs() {
        val settings = getSharedPreferences(settingsName, MODE_PRIVATE)
        currentScalar.check(
            when (settings.getFloat("currentScalar", -1f)) {
                1000f -> R.id.currentScalar1000
                .001f -> R.id.currentScalar0_001
                else -> R.id.currentScalar1
            }
        )
        indicatorUnits.check(
            when (settings.getString("indicatorUnits", null)) {
                "A" -> R.id.indicatorA
                "Ah" -> R.id.indicatorAh
                "C" -> R.id.indicatorC
                "V" -> R.id.indicatorV
                "Wh" -> R.id.indicatorWh
                "%" -> R.id.indicatorPerc
                "%V" -> R.id.indicatorPercV
                else -> R.id.indicatorW
            }
        )
        indicatorDigitsValue = settings.getInt("indicatorDigits", defaultIndicatorDigits)
            .coerceIn(1, 9)
        indicatorDigits.setText(indicatorDigitsValue.toString())
        refreshIntervalValue = settings.getLong(refreshIntervalKey, defaultRefreshIntervalMs)
            .coerceIn(minRefreshIntervalMs, maxRefreshIntervalMs)
        refreshInterval.setText(refreshIntervalValue.toString())
        capacityMahValue = settings.getFloat(capacityMahKey, defaultCapacityMah.toFloat()).toDouble()
        capacityMah.setText(formatNumber(capacityMahValue))
        restThresholdmAValue = settings.getFloat(restThresholdmAKey, defaultRestThresholdmA.toFloat()).toDouble()
        restThresholdmA.setText(formatNumber(restThresholdmAValue))
        restStableSecondsValue = settings.getFloat(restStableSecondsKey, defaultRestStableSeconds.toFloat()).toDouble()
        restStableSeconds.setText(formatNumber(restStableSecondsValue))
        pullToOcvKValue = settings.getFloat(pullToOcvKKey, defaultPullToOcvK.toFloat()).toDouble()
        pullToOcvK.setText(formatNumber(pullToOcvKValue))
        rateLimitUpPerMinValue = settings.getFloat(
            rateLimitUpPerMinKey,
            defaultRateLimitUpPerMin.toFloat()
        ).toDouble()
        rateLimitUpPerMin.setText(formatNumber(rateLimitUpPerMinValue))
        rateLimitDownPerMinValue = settings.getFloat(
            rateLimitDownPerMinKey,
            defaultRateLimitDownPerMin.toFloat()
        ).toDouble()
        rateLimitDownPerMin.setText(formatNumber(rateLimitDownPerMinValue))
        monotonicThresholdmAValue = settings.getFloat(
            monotonicThresholdmAKey,
            defaultMonotonicThresholdmA.toFloat()
        ).toDouble()
        monotonicThresholdmA.setText(formatNumber(monotonicThresholdmAValue))
        invertCurrent.isChecked = settings.getBoolean("invertCurrent", false)
        loadingVoltageCurve = true
        setVoltageCurveFields(VoltageCurve.loadFromPrefs(settings))
        loadingVoltageCurve = false
    }

    @SuppressLint("ApplySharedPref")
    private fun onChange() {
        getSharedPreferences(settingsName, MODE_PRIVATE)
            .edit()
            .putBoolean("invertCurrent", invertCurrent.isChecked)
            .putFloat(
                "currentScalar",
                when(currentScalar.checkedRadioButtonId) {
                    R.id.currentScalar1000 -> 1000f
                    R.id.currentScalar0_001 -> 0.001f
                    else -> 1f
                }
            )
            .putString(
                "indicatorUnits",
                when (indicatorUnits.checkedRadioButtonId) {
                    R.id.indicatorA -> "A"
                    R.id.indicatorAh -> "Ah"
                    R.id.indicatorC -> "C"
                    R.id.indicatorV -> "V"
                    R.id.indicatorWh -> "Wh"
                    R.id.indicatorPerc -> "%"
                    R.id.indicatorPercV -> "%V"
                    else -> "W"
                }
            )
            .commit()

        sendBroadcast(Intent().setPackage(packageName).setAction(settingsUpdateInd))
    }

    @SuppressLint("ApplySharedPref")
    private fun saveIndicatorDigits(value: Int) {
        getSharedPreferences(settingsName, MODE_PRIVATE)
            .edit()
            .putInt("indicatorDigits", value)
            .commit()
        sendBroadcast(Intent().setPackage(packageName).setAction(settingsUpdateInd))
    }

    @SuppressLint("ApplySharedPref")
    private fun saveRefreshInterval(value: Long) {
        getSharedPreferences(settingsName, MODE_PRIVATE)
            .edit()
            .putLong(refreshIntervalKey, value)
            .commit()
        sendBroadcast(Intent().setPackage(packageName).setAction(settingsUpdateInd))
    }

    @SuppressLint("ApplySharedPref")
    private fun saveDoublePref(key: String, value: Double) {
        getSharedPreferences(settingsName, MODE_PRIVATE)
            .edit()
            .putFloat(key, value.toFloat())
            .commit()
        sendBroadcast(Intent().setPackage(packageName).setAction(settingsUpdateInd))
    }

    private fun formatNumber(value: Double): String {
        return if (value % 1.0 == 0.0) {
            value.toLong().toString()
        } else {
            value.toString()
        }
    }

    private data class VoltageCurveField(val percent: EditText, val volts: EditText)

    private fun setVoltageCurveFields(points: List<VoltagePoint>) {
        val fallback = VoltageCurve.defaultPoints
        for (i in voltageCurveFields.indices) {
            val point = points.getOrNull(i) ?: fallback[i]
            voltageCurveFields[i].percent.setText(point.p.toString())
            voltageCurveFields[i].volts.setText(point.v.toString())
        }
    }

    private fun readVoltageCurveFromInputs(): List<VoltagePoint>? {
        var hasErrors = false
        val points = mutableListOf<VoltagePoint>()
        for (field in voltageCurveFields) {
            val percentText = field.percent.text.toString().trim()
            val voltsText = field.volts.text.toString().trim()
            val percent = percentText.toDoubleOrNull()
            val volts = voltsText.toDoubleOrNull()

            if (percent == null) {
                field.percent.error = getString(R.string.invalidNumber)
                hasErrors = true
            } else {
                field.percent.error = null
            }

            if (volts == null) {
                field.volts.error = getString(R.string.invalidNumber)
                hasErrors = true
            } else {
                field.volts.error = null
            }

            if (percent != null && volts != null) {
                points.add(VoltagePoint(volts, percent))
            }
        }
        return if (hasErrors) null else points
    }

    private fun saveVoltageCurveFromInputs(): Boolean {
        val points = readVoltageCurveFromInputs() ?: return false
        VoltageCurve.saveToPrefs(getSharedPreferences(settingsName, MODE_PRIVATE), points)
        sendBroadcast(Intent().setPackage(packageName).setAction(settingsUpdateInd))
        return true
    }

    private fun handleVoltageCurveImport(uri: android.net.Uri) {
        val resolver = contentResolver
        val json = resolver.openInputStream(uri)?.bufferedReader()?.use { it.readText() }
        if (json.isNullOrBlank()) {
            Toast.makeText(this, R.string.voltageCurveImportError, Toast.LENGTH_LONG).show()
            return
        }
        try {
            val points = VoltageCurve.parseJson(json)
            if (points.size != voltageCurveFields.size) {
                Toast.makeText(this, R.string.voltageCurveImportSizeError, Toast.LENGTH_LONG).show()
                return
            }
            loadingVoltageCurve = true
            setVoltageCurveFields(points)
            loadingVoltageCurve = false
            saveVoltageCurveFromInputs()
        } catch (e: Exception) {
            Log.e(this::class.java.name, "Failed to import voltage curve", e)
            Toast.makeText(this, R.string.voltageCurveImportError, Toast.LENGTH_LONG).show()
        }
    }

    private fun handleVoltageCurveExport(uri: android.net.Uri) {
        val points = readVoltageCurveFromInputs()
        if (points == null) {
            Toast.makeText(this, R.string.voltageCurveExportError, Toast.LENGTH_LONG).show()
            return
        }
        val json = VoltageCurve.toJson(points)
        contentResolver.openOutputStream(uri)?.bufferedWriter()?.use { it.write(json) }
        Toast.makeText(this, R.string.voltageCurveExported, Toast.LENGTH_LONG).show()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        debug("onCreate()")

        super.onCreate(savedInstanceState)

        setContentView(R.layout.activity_settings)

        charging = findViewById(R.id.charging)
        currentScalar = findViewById(R.id.currentScalar)
        indicatorUnits = findViewById(R.id.indicatorUnits)
        indicatorDigits = findViewById(R.id.indicatorDigits)
        refreshInterval = findViewById(R.id.refreshInterval)
        capacityMah = findViewById(R.id.capacityMah)
        restThresholdmA = findViewById(R.id.restThresholdmA)
        restStableSeconds = findViewById(R.id.restStableSeconds)
        pullToOcvK = findViewById(R.id.pullToOcvK)
        rateLimitUpPerMin = findViewById(R.id.rateLimitUpPerMin)
        rateLimitDownPerMin = findViewById(R.id.rateLimitDownPerMin)
        monotonicThresholdmA = findViewById(R.id.monotonicThresholdmA)
        invertCurrent = findViewById(R.id.invertCurrent)
        power = findViewById(R.id.power)
        importVoltageCurve = findViewById(R.id.importVoltageCurve)
        exportVoltageCurve = findViewById(R.id.exportVoltageCurve)

        voltageCurveFields = listOf(
            VoltageCurveField(findViewById(R.id.curvePercent0), findViewById(R.id.curveVolt0)),
            VoltageCurveField(findViewById(R.id.curvePercent1), findViewById(R.id.curveVolt1)),
            VoltageCurveField(findViewById(R.id.curvePercent2), findViewById(R.id.curveVolt2)),
            VoltageCurveField(findViewById(R.id.curvePercent3), findViewById(R.id.curveVolt3)),
            VoltageCurveField(findViewById(R.id.curvePercent4), findViewById(R.id.curveVolt4)),
            VoltageCurveField(findViewById(R.id.curvePercent5), findViewById(R.id.curveVolt5)),
            VoltageCurveField(findViewById(R.id.curvePercent6), findViewById(R.id.curveVolt6)),
            VoltageCurveField(findViewById(R.id.curvePercent7), findViewById(R.id.curveVolt7)),
            VoltageCurveField(findViewById(R.id.curvePercent8), findViewById(R.id.curveVolt8)),
            VoltageCurveField(findViewById(R.id.curvePercent9), findViewById(R.id.curveVolt9)),
        )

        loadPrefs()

        currentScalar.setOnCheckedChangeListener { _, _ -> onChange() }
        indicatorUnits.checkChangedCallback = { _ -> onChange() }
        invertCurrent.setOnCheckedChangeListener { _, _ -> onChange() }
        indicatorDigits.setOnFocusChangeListener { _, hasFocus ->
            if (!hasFocus) {
                val parsed = indicatorDigits.text.toString().trim().toIntOrNull()
                val valid = parsed?.takeIf { it in 1..9 }
                if (valid == null) {
                    indicatorDigits.setText(indicatorDigitsValue.toString())
                    indicatorDigits.error = getString(R.string.invalidNumber)
                } else {
                    indicatorDigitsValue = valid
                    indicatorDigits.error = null
                    saveIndicatorDigits(valid)
                }
            }
        }
        refreshInterval.setOnFocusChangeListener { _, hasFocus ->
            if (!hasFocus) {
                val parsed = refreshInterval.text.toString().trim().toLongOrNull()
                val valid = parsed?.coerceIn(minRefreshIntervalMs, maxRefreshIntervalMs)
                if (valid == null || parsed != valid) {
                    refreshInterval.setText(refreshIntervalValue.toString())
                    refreshInterval.error = getString(R.string.invalidNumber)
                } else {
                    refreshIntervalValue = valid
                    refreshInterval.error = null
                    saveRefreshInterval(valid)
                }
            }
        }
        capacityMah.setOnFocusChangeListener { _, hasFocus ->
            if (!hasFocus) {
                val parsed = capacityMah.text.toString().trim().toDoubleOrNull()
                val valid = parsed?.takeIf { it > 0.0 }
                if (valid == null) {
                    capacityMah.setText(formatNumber(capacityMahValue))
                    capacityMah.error = getString(R.string.invalidNumber)
                } else {
                    capacityMahValue = valid
                    capacityMah.error = null
                    saveDoublePref(capacityMahKey, valid)
                }
            }
        }
        restThresholdmA.setOnFocusChangeListener { _, hasFocus ->
            if (!hasFocus) {
                val parsed = restThresholdmA.text.toString().trim().toDoubleOrNull()
                val valid = parsed?.takeIf { it >= 0.0 }
                if (valid == null) {
                    restThresholdmA.setText(formatNumber(restThresholdmAValue))
                    restThresholdmA.error = getString(R.string.invalidNumber)
                } else {
                    restThresholdmAValue = valid
                    restThresholdmA.error = null
                    saveDoublePref(restThresholdmAKey, valid)
                }
            }
        }
        restStableSeconds.setOnFocusChangeListener { _, hasFocus ->
            if (!hasFocus) {
                val parsed = restStableSeconds.text.toString().trim().toDoubleOrNull()
                val valid = parsed?.takeIf { it >= 0.0 }
                if (valid == null) {
                    restStableSeconds.setText(formatNumber(restStableSecondsValue))
                    restStableSeconds.error = getString(R.string.invalidNumber)
                } else {
                    restStableSecondsValue = valid
                    restStableSeconds.error = null
                    saveDoublePref(restStableSecondsKey, valid)
                }
            }
        }
        pullToOcvK.setOnFocusChangeListener { _, hasFocus ->
            if (!hasFocus) {
                val parsed = pullToOcvK.text.toString().trim().toDoubleOrNull()
                val valid = parsed?.takeIf { it in 0.0..1.0 }
                if (valid == null) {
                    pullToOcvK.setText(formatNumber(pullToOcvKValue))
                    pullToOcvK.error = getString(R.string.invalidNumber)
                } else {
                    pullToOcvKValue = valid
                    pullToOcvK.error = null
                    saveDoublePref(pullToOcvKKey, valid)
                }
            }
        }
        rateLimitUpPerMin.setOnFocusChangeListener { _, hasFocus ->
            if (!hasFocus) {
                val parsed = rateLimitUpPerMin.text.toString().trim().toDoubleOrNull()
                val valid = parsed?.takeIf { it >= 0.0 }
                if (valid == null) {
                    rateLimitUpPerMin.setText(formatNumber(rateLimitUpPerMinValue))
                    rateLimitUpPerMin.error = getString(R.string.invalidNumber)
                } else {
                    rateLimitUpPerMinValue = valid
                    rateLimitUpPerMin.error = null
                    saveDoublePref(rateLimitUpPerMinKey, valid)
                }
            }
        }
        rateLimitDownPerMin.setOnFocusChangeListener { _, hasFocus ->
            if (!hasFocus) {
                val parsed = rateLimitDownPerMin.text.toString().trim().toDoubleOrNull()
                val valid = parsed?.takeIf { it >= 0.0 }
                if (valid == null) {
                    rateLimitDownPerMin.setText(formatNumber(rateLimitDownPerMinValue))
                    rateLimitDownPerMin.error = getString(R.string.invalidNumber)
                } else {
                    rateLimitDownPerMinValue = valid
                    rateLimitDownPerMin.error = null
                    saveDoublePref(rateLimitDownPerMinKey, valid)
                }
            }
        }
        monotonicThresholdmA.setOnFocusChangeListener { _, hasFocus ->
            if (!hasFocus) {
                val parsed = monotonicThresholdmA.text.toString().trim().toDoubleOrNull()
                val valid = parsed?.takeIf { it >= 0.0 }
                if (valid == null) {
                    monotonicThresholdmA.setText(formatNumber(monotonicThresholdmAValue))
                    monotonicThresholdmA.error = getString(R.string.invalidNumber)
                } else {
                    monotonicThresholdmAValue = valid
                    monotonicThresholdmA.error = null
                    saveDoublePref(monotonicThresholdmAKey, valid)
                }
            }
        }

        val watcher = object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) = Unit
            override fun afterTextChanged(s: Editable?) {
                if (!loadingVoltageCurve) {
                    saveVoltageCurveFromInputs()
                }
            }
        }
        for (field in voltageCurveFields) {
            field.percent.addTextChangedListener(watcher)
            field.volts.addTextChangedListener(watcher)
        }

        importVoltageCurve.setOnClickListener { openVoltageCurveImport() }
        exportVoltageCurve.setOnClickListener { openVoltageCurveExport() }
    }

    override fun onPause() {
        debug("onPause()")

        unregisterReceiver(batteryReceiver)

        super.onPause()
    }

    override fun onResume() {
        debug("onResume()")

        super.onResume()

        registerReceiver(batteryReceiver, IntentFilter(batteryDataResp), RECEIVER_NOT_EXPORTED)
        sendBroadcast(Intent().setPackage(packageName).setAction(batteryDataReq))
    }

    override fun onDestroy() {
        debug("onDestroy()")

        super.onDestroy()
    }

    fun onBack(view: View) {
        finish()
    }

    private fun openVoltageCurveImport() {
        val intent = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            type = "application/json"
        }
        startActivityForResult(intent, RequestCode.ImportCurve.id)
    }

    private fun openVoltageCurveExport() {
        val intent = Intent(Intent.ACTION_CREATE_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            type = "application/json"
            putExtra(Intent.EXTRA_TITLE, "battery_curve.json")
        }
        startActivityForResult(intent, RequestCode.ExportCurve.id)
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (resultCode != RESULT_OK) {
            return
        }
        val uri = data?.data ?: return
        when (requestCode) {
            RequestCode.ImportCurve.id -> handleVoltageCurveImport(uri)
            RequestCode.ExportCurve.id -> handleVoltageCurveExport(uri)
        }
    }

    private enum class RequestCode(val id: Int) {
        ImportCurve(1001),
        ExportCurve(1002),
    }
}
