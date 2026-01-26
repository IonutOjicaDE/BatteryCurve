package dubrowgn.wattz

import android.os.Handler

class PeriodicTask(callback: () -> Unit, intervalMs: Long) {
    private val ticker = Handler()
    private val callback = callback
    private val runnable = Runnable { start() }
    private var intervalMs = intervalMs

    fun updateInterval(newIntervalMs: Long) {
        intervalMs = newIntervalMs
    }

    fun stop() {
        ticker.removeCallbacks(runnable)
    }

    private fun tick() {
        callback()
        ticker.postDelayed(runnable, intervalMs)
    }

    fun start() {
        stop()
        tick()
    }
}
