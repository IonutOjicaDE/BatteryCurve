package dubrowgn.wattz

import android.content.SharedPreferences
import org.json.JSONArray
import org.json.JSONObject

data class VoltagePoint(val v: Double, val p: Double)

object VoltageCurve {
    const val prefsKey = "voltageCurveJson"

    val defaultPoints = listOf(
        VoltagePoint(3.30, 0.0),
        VoltagePoint(3.40, 5.0),
        VoltagePoint(3.50, 15.0),
        VoltagePoint(3.60, 30.0),
        VoltagePoint(3.70, 50.0),
        VoltagePoint(3.80, 70.0),
        VoltagePoint(3.90, 85.0),
        VoltagePoint(4.00, 93.0),
        VoltagePoint(4.10, 97.0),
        VoltagePoint(4.20, 100.0),
    )

    fun parseJson(json: String): List<VoltagePoint> {
        val arr = JSONArray(json)
        val points = mutableListOf<VoltagePoint>()
        for (i in 0 until arr.length()) {
            val obj = arr.getJSONObject(i)
            val volts = obj.getDouble("v")
            val percent = obj.getDouble("p")
            if (!volts.isFinite() || !percent.isFinite()) {
                throw IllegalArgumentException("Invalid voltage curve point")
            }
            points.add(VoltagePoint(volts, percent))
        }
        if (points.isEmpty()) {
            throw IllegalArgumentException("Voltage curve is empty")
        }
        return points
    }

    fun toJson(points: List<VoltagePoint>): String {
        val arr = JSONArray()
        for (point in points) {
            arr.put(
                JSONObject()
                    .put("v", point.v)
                    .put("p", point.p)
            )
        }
        return arr.toString(2)
    }

    fun loadFromPrefs(settings: SharedPreferences): List<VoltagePoint> {
        val json = settings.getString(prefsKey, null) ?: return defaultPoints
        return runCatching { parseJson(json) }.getOrDefault(defaultPoints)
    }

    fun saveToPrefs(settings: SharedPreferences, points: List<VoltagePoint>) {
        settings.edit().putString(prefsKey, toJson(points)).apply()
    }

    fun percentForVoltage(volts: Double?, points: List<VoltagePoint>): Double? {
        val v = volts ?: return null
        if (points.isEmpty()) {
            return null
        }
        val sorted = points.sortedBy { it.v }
        if (v <= sorted.first().v) {
            return sorted.first().p
        }
        if (v >= sorted.last().v) {
            return sorted.last().p
        }
        for (i in 0 until sorted.size - 1) {
            val left = sorted[i]
            val right = sorted[i + 1]
            if (v <= right.v) {
                val span = right.v - left.v
                if (span == 0.0) {
                    return left.p
                }
                val ratio = (v - left.v) / span
                return left.p + (right.p - left.p) * ratio
            }
        }
        return sorted.last().p
    }
}
