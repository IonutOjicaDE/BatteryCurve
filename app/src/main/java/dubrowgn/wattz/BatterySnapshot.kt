package dubrowgn.wattz


class BatterySnapshot(
    // configuration
    private val currentScalar: Double,
    private val invertCurrent: Boolean,

    // data
    val chargeTimeRemainingRaw: Long,
    val currentRaw: Long?,
    val energyRaw: Long?,
    val level: Double?,
    val isChargingRaw: Boolean,
    val plugType: PlugType?,
    val tempRaw: Long?,
    val voltsRaw: Long?,
) {
    private fun fromMicros(v: Double?) : Double? {
        return v?.div(1_000_000.0)
    }

    private fun fromMillis(v: Double?) : Double? {
        return v?.div(1_000.0)
    }

    private fun normalizeCurrent(currentNowmA: Double?, chargingWorkaroundSwitch: Boolean): Double? {
        val current = currentNowmA ?: return null
        return if (chargingWorkaroundSwitch) current else -current
    }

    private val milliampsRaw : Double? get() = currentRaw?.times(currentScalar)?.div(1_000.0)

    val milliamps : Double? get() = normalizeCurrent(milliampsRaw, invertCurrent)
    val microamps : Double? get() = milliamps?.times(1_000.0)
    val amps : Double? get() = milliamps?.div(1_000.0)

    val millivolts : Double? get() = voltsRaw?.toDouble()
    val volts : Double? get() = fromMillis(millivolts)

    val milliwatts : Double? get() = milliamps.times(volts)
    val watts : Double? get() = amps.times(volts)

    val energyAmpHours : Double? get() = fromMicros(energyRaw?.toDouble())
    val energyWattHours : Double? get() = volts?.times(energyAmpHours)

    val levelPercent : Double? get() = level?.times(100.0)

    val celsius : Double? get() = tempRaw?.toDouble()?.div(10.0)

    // Some devices always report false for isCharging, so fall back to battery current detection
    val charging : Boolean get() {
        if (isChargingRaw)
            return true

        val ma = milliamps
        return ma != null && ma < 1.0
    }

    val secondsUntilCharged: Double? get() {
        // Some devices incorrectly report "0 seconds to full" when not charging,
        // so ensure we are actually charging first.
        if (!charging)
            return null

        val ms = chargeTimeRemainingRaw
        if (ms == -1L)
            return null

        return fromMillis(ms.toDouble())
    }
}
