package dubrowgn.wattz

import android.app.*
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.*
import android.graphics.drawable.Icon
import android.os.IBinder
import android.util.Log
import java.time.LocalDateTime
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min


class StatusService : Service() {
    private lateinit var battery: Battery
    private val dateFmt = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")
    private var indicatorUnits: String? = null
    private var indicatorDigits = defaultIndicatorDigits
    private lateinit var noteBuilder: Notification.Builder
    private lateinit var noteMgr: NotificationManager
    private var pluggedInAt: ZonedDateTime? = null
    private lateinit var snapshot: BatterySnapshot
    private val task = PeriodicTask({ update() }, defaultRefreshIntervalMs)
    private var voltageCurve: List<VoltagePoint> = VoltageCurve.defaultPoints
    private var refreshIntervalMs = defaultRefreshIntervalMs
    private var dtSeconds = refreshIntervalMs / 1000.0

    private var capacitymAh = defaultCapacityMah
    private var restThresholdmA = defaultRestThresholdmA
    private var restStableSeconds = defaultRestStableSeconds
    private var pullToOcvK = defaultPullToOcvK
    private var rateLimitUpPerMin = defaultRateLimitUpPerMin
    private var rateLimitDownPerMin = defaultRateLimitDownPerMin
    private var monotonicThresholdmA = defaultMonotonicThresholdmA

    private var socPercent: Double? = null
    private var restAccumSeconds = 0.0
    private var cellsCount = 1
    private var cellsCountDetermined = false

    private fun debug(msg: String) {
        Log.d(this::class.java.name, msg)
    }

    private inner class MsgReceiver : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            when (intent.action) {
                batteryDataReq -> updateData()
                settingsUpdateInd -> {
                    loadSettings()
                    task.start()
                }
                Intent.ACTION_POWER_CONNECTED -> {
                    pluggedInAt = ZonedDateTime.now()
                    update()
                }
                Intent.ACTION_POWER_DISCONNECTED -> {
                    pluggedInAt = null
                    update()
                }
                Intent.ACTION_SCREEN_OFF -> task.stop()
                Intent.ACTION_SCREEN_ON -> task.start()
            }
        }
    }

    private fun loadSettings() {
        val settings = getSharedPreferences(settingsName, MODE_MULTI_PROCESS)
        battery.currentScalar = settings.getFloat("currentScalar", 1f).toDouble()
        battery.invertCurrent = settings.getBoolean("invertCurrent", false)
        indicatorUnits = settings.getString("indicatorUnits", null)
        indicatorDigits = settings.getInt("indicatorDigits", defaultIndicatorDigits)
            .coerceIn(1, 9)
        refreshIntervalMs = settings.getLong(refreshIntervalKey, defaultRefreshIntervalMs)
            .coerceIn(minRefreshIntervalMs, maxRefreshIntervalMs)
        task.updateInterval(refreshIntervalMs)
        dtSeconds = refreshIntervalMs / 1000.0
        voltageCurve = VoltageCurve.loadFromPrefs(settings)
        capacitymAh = settings.getFloat(capacityMahKey, defaultCapacityMah.toFloat()).toDouble()
        restThresholdmA = settings.getFloat(restThresholdmAKey, defaultRestThresholdmA.toFloat()).toDouble()
        restStableSeconds = settings.getFloat(
            restStableSecondsKey,
            defaultRestStableSeconds.toFloat()
        ).toDouble()
        pullToOcvK = settings.getFloat(pullToOcvKKey, defaultPullToOcvK.toFloat()).toDouble()
        rateLimitUpPerMin = settings.getFloat(
            rateLimitUpPerMinKey,
            defaultRateLimitUpPerMin.toFloat()
        ).toDouble()
        rateLimitDownPerMin = settings.getFloat(
            rateLimitDownPerMinKey,
            defaultRateLimitDownPerMin.toFloat()
        ).toDouble()
        monotonicThresholdmA = settings.getFloat(
            monotonicThresholdmAKey,
            defaultMonotonicThresholdmA.toFloat()
        ).toDouble()
    }

    private fun init() {
        battery = Battery(applicationContext)
        snapshot = battery.snapshot()
        determineCellsCount(snapshot.volts)

        noteMgr = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        noteMgr.createNotificationChannel(
            NotificationChannel(
                noteChannelId,
                "Power Status",
                NotificationManager.IMPORTANCE_DEFAULT
            ).apply {
                description = "Continuously displays current battery power consumption"
            }
        )

        val noteIntent = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_SINGLE_TOP
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val ind = getString(R.string.indeterminate)
        val indicatorText = splitFixedDigits(null, indicatorDigits, ind)
        noteBuilder = Notification.Builder(this, noteChannelId)
            .setContentTitle("Battery Draw: $ind W")
            .setSmallIcon(renderIcon(indicatorText.first, indicatorText.second))
            .setContentIntent(noteIntent)
            .setOnlyAlertOnce(true)

        registerReceiver(
            MsgReceiver(),
            IntentFilter().apply {
                addAction(batteryDataReq)
                addAction(settingsUpdateInd)
                addAction(Intent.ACTION_POWER_CONNECTED)
                addAction(Intent.ACTION_POWER_DISCONNECTED)
                addAction(Intent.ACTION_SCREEN_OFF)
                addAction(Intent.ACTION_SCREEN_ON)
            },
            RECEIVER_NOT_EXPORTED,
        )
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        debug("onStartCommand()")

        super.onStartCommand(intent, flags, startId)

        init()
        loadSettings()
        task.start()

        try {
            startForeground(noteId, noteBuilder.build())
        } catch (e: Exception) {
            error("Failed to foreground StatusService: ${e.message}")
        }

        return START_STICKY;
    }

    override fun onDestroy() {
        debug("onDestroy()")

        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? {
        return null
    }

    private fun renderIcon(topLine: String, bottomLine: String): Icon {
        val density = resources.displayMetrics.density
        val w = (48f * density).toInt()
        val bitmap = Bitmap.createBitmap(w, w, Bitmap.Config.ALPHA_8)
        val canvas = Canvas(bitmap)

        val textSize = 28f * density
        val paint = Paint()
        paint.textSize = textSize
        paint.typeface = Typeface.DEFAULT_BOLD
        paint.style = Paint.Style.FILL
        paint.color = Color.WHITE
        paint.textAlign = Paint.Align.CENTER

        canvas.drawText(topLine, w / 2f, w * 0.55f, paint)
        canvas.drawText(bottomLine, w / 2f, w * 0.95f, paint)

        return Icon.createWithBitmap(bitmap)
    }

    private fun updateData() {
        val plugType = snapshot.plugType?.name?.lowercase()
        val indeterminate = getString(R.string.indeterminate)
        val digits = indicatorDigits
        val fullyCharged = getString(R.string.fullyCharged)
        val no = getString(R.string.no)
        val yes = getString(R.string.yes)
        val voltageCellV = snapshot.volts?.div(cellsCount)
        val chargeLevel = socPercent
            ?: snapshot.levelPercent
            ?: VoltageCurve.percentForVoltage(voltageCellV, voltageCurve)

        val intent = Intent()
            .setPackage(packageName)
            .setAction(batteryDataResp)
            .putExtra("charging",
                when (snapshot.charging) {
                    true -> if (plugType == null) yes else "$yes ($plugType)"
                    false -> no
                }
            )
            .putExtra("chargeLevel", fmtFixedDigits(chargeLevel, digits, indeterminate) + "%")
            .putExtra("chargingSince",
                when (val pluggedInAt = pluggedInAt) {
                    null -> indeterminate
                    else -> LocalDateTime
                        .ofInstant(pluggedInAt.toInstant(), pluggedInAt.zone)
                        .format(dateFmt)
                }
            )
            .putExtra("current", fmtFixedDigits(snapshot.amps, digits, indeterminate) + "A")
            .putExtra("energy",
                "${fmtFixedDigits(snapshot.energyWattHours, digits, indeterminate)}Wh (" +
                    "${fmtFixedDigits(snapshot.energyAmpHours, digits, indeterminate)}Ah)"
            )
            .putExtra("power", fmtFixedDigits(snapshot.watts, digits, indeterminate) + "W")
            .putExtra("temperature", fmtFixedDigits(snapshot.celsius, digits, indeterminate) + "°C")
            .putExtra("timeToFullCharge",
                when (val seconds = snapshot.secondsUntilCharged) {
                    null -> indeterminate
                    0.0 -> fullyCharged
                    else -> fmtSeconds(seconds)
                }
            )
            .putExtra("voltage", fmtFixedDigits(voltageCellV, digits, indeterminate) + "V")

        applicationContext.sendBroadcast(intent)
    }

    private fun determineCellsCount(voltage: Double?) {
        if (cellsCountDetermined) {
            return
        }
        cellsCount = if (voltage != null && voltage > 5.0) 2 else 1
        cellsCountDetermined = true
    }

    private fun clamp(value: Double, minValue: Double, maxValue: Double): Double {
        return value.coerceIn(minValue, maxValue)
    }

    private fun sanitize(value: Double, fallback: Double): Double {
        return if (value.isFinite()) value else fallback
    }

    private fun update() {
        debug("update()")

        snapshot = battery.snapshot()
        determineCellsCount(snapshot.volts)

        val voltageCellV = snapshot.volts?.div(cellsCount)
        val currentDischargemA = snapshot.milliamps
        val socBaseline = socPercent
            ?: snapshot.levelPercent
            ?: VoltageCurve.percentForVoltage(voltageCellV, voltageCurve)
            ?: 0.0
        val socPrev = sanitize(socBaseline, 0.0)
        socPercent = socPrev
        val dtHours = dtSeconds / 3600.0
        val deltaSoc = if (currentDischargemA != null && capacitymAh > 0.0) {
            (currentDischargemA * dtHours / capacitymAh) * 100.0
        } else {
            0.0
        }
        val socCc = clamp(socPrev - deltaSoc, 0.0, 100.0)

        if (currentDischargemA != null && abs(currentDischargemA) < restThresholdmA) {
            restAccumSeconds += dtSeconds
        } else {
            restAccumSeconds = 0.0
        }
        val isRest = restAccumSeconds >= restStableSeconds
        val socCandidate = if (isRest) {
            val socV = VoltageCurve.percentForVoltage(voltageCellV, voltageCurve)
            if (socV != null && socV.isFinite()) {
                socCc + pullToOcvK * (socV - socCc)
            } else {
                socCc
            }
        } else {
            socCc
        }
        val maxStepUp = rateLimitUpPerMin * (dtSeconds / 60.0)
        val maxStepDown = rateLimitDownPerMin * (dtSeconds / 60.0)
        var socLimited = clamp(socCandidate, socPrev - maxStepDown, socPrev + maxStepUp)
        if (!isRest && currentDischargemA != null && abs(currentDischargemA) > monotonicThresholdmA) {
            socLimited = when {
                currentDischargemA > 0.0 -> min(socLimited, socPrev)
                currentDischargemA < 0.0 -> max(socLimited, socPrev)
                else -> socLimited
            }
        }
        socPercent = clamp(sanitize(socLimited, socPrev), 0.0, 100.0)

        val txtLabel = when (indicatorUnits) {
            "A" -> getString(R.string.current)
            "Ah" -> getString(R.string.energy)
            "C" -> getString(R.string.temperature)
            "V" -> getString(R.string.voltage)
            "Wh" -> getString(R.string.energy)
            "%" -> getString(R.string.chargeLevel)
            "%V" -> getString(R.string.chargeLevelVoltage)
            else -> getString(R.string.power)
        }
        val digits = indicatorDigits
        val indeterminate = getString(R.string.indeterminate)
        val txtValue = fmtFixedDigits( when (indicatorUnits) {
            "A" -> snapshot.amps
            "Ah" -> snapshot.energyAmpHours
            "C" -> snapshot.celsius
            "V" -> voltageCellV
            "Wh" -> snapshot.energyWattHours
            "%" -> socPercent
            "%V" -> VoltageCurve.percentForVoltage(voltageCellV, voltageCurve)
            else -> snapshot.watts
        }, digits, indeterminate)
        val txtUnits = when (indicatorUnits) {
            "C" -> "°C"
            "%V" -> "%V"
            else -> indicatorUnits ?: "W"
        }
        val iconLines = splitFixedDigits(
            when (indicatorUnits) {
                "A" -> snapshot.amps
                "Ah" -> snapshot.energyAmpHours
                "C" -> snapshot.celsius
                "V" -> voltageCellV
                "Wh" -> snapshot.energyWattHours
                "%" -> socPercent
                "%V" -> VoltageCurve.percentForVoltage(voltageCellV, voltageCurve)
                else -> snapshot.watts
            },
            digits,
            indeterminate,
        )

        noteBuilder
            .setContentTitle("${getString(R.string.battery)} ${txtLabel}: ${txtValue}${txtUnits}")
            .setSmallIcon(renderIcon(iconLines.first, iconLines.second))

        noteBuilder.setContentText(
            when(val seconds = snapshot.secondsUntilCharged) {
                null -> ""
                0.0 -> "fully charged"
                else -> "${fmtSeconds(seconds)} until full charge"
            }
        )

        noteMgr.notify(noteId, noteBuilder.build())

        updateData()
    }
}
