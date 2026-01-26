package dubrowgn.wattz

import java.util.Locale
import kotlin.math.abs

fun fmtFixedDigits(v: Double?, digits: Int, indeterminate: String = "-"): String {
    if (v == null || !v.isFinite()) {
        return indeterminate
    }

    val clampedDigits = digits.coerceIn(1, 9)
    val absValue = abs(v)
    val integerDigits = if (absValue < 1.0) 0 else absValue.toLong().toString().length
    val decimalPlaces = (clampedDigits - integerDigits).coerceAtLeast(0)
    val formatted = String.format(Locale.US, "%.${decimalPlaces}f", absValue)

    return if (integerDigits == 0 && formatted.startsWith("0.")) {
        formatted.drop(1)
    } else {
        formatted
    }
}

fun splitFixedDigits(v: Double?, digits: Int, indeterminate: String = "-"): Pair<String, String> {
    val formatted = fmtFixedDigits(v, digits, indeterminate)
    if (formatted == indeterminate) {
        return Pair(formatted, "")
    }

    val splitDigits = digits.coerceIn(1, 9) / 2
    var digitIndex = 0
    val top = StringBuilder()
    val bottom = StringBuilder()

    for (char in formatted) {
        when {
            char.isDigit() -> {
                if (digitIndex < splitDigits) {
                    top.append(char)
                } else {
                    bottom.append(char)
                }
                digitIndex++
            }
            char == '.' -> {
                if (digitIndex < splitDigits) {
                    top.append(char)
                } else {
                    bottom.append(char)
                }
            }
        }
    }

    return Pair(top.toString(), bottom.toString())
}

fun fmtSeconds(seconds: Double?): String {
    if (seconds == null)
        return ""

    var secs = seconds.toInt()
    if (secs < 60)
        return "${secs}s"

    var mins = secs / 60
    secs %= 60
    if (mins < 60)
        return "${mins}m ${secs}s"

    val hrs = mins / 60
    mins %= 60
    return "${hrs}h ${mins}m ${secs}s"
}
