package org.pilou.betterbose.data

import android.content.Context
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.pilou.betterbose.alerts.BatteryAlerts

/** Keeps the link open a while after the last use, so reopening the app or tapping a widget is instant. */
private const val GRACE_MS = 90_000L

/**
 * One headset link for the whole process, shared by the app and the widgets.
 * The app holds it while visible; widget taps borrow it; it closes [GRACE_MS] after the last use.
 */
object Headsets {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    @Volatile private var repo: HeadsetRepository? = null
    private var foreground = false
    private var stopJob: Job? = null

    fun repo(context: Context): HeadsetRepository = repo ?: synchronized(this) {
        repo ?: context.applicationContext.let { app ->
            HeadsetRepository(BoseDevices(app), AppPrefs.get(app), scope) { BatteryAlerts.onLevel(app, it) }
        }.also { repo = it }
    }

    fun onForeground(context: Context) {
        foreground = true
        stopJob?.cancel()
        repo(context).start()
    }

    fun onBackground(context: Context) {
        foreground = false
        scheduleStop(context)
    }

    /** Widget mode button: reuse the open link, or open one and keep it for the grace period. */
    suspend fun setModeFromWidget(context: Context, index: Int): Boolean = withContext(Dispatchers.Main.immediate) {
        val r = repo(context)
        r.start()
        if (!foreground) scheduleStop(context)
        if (!r.ensureLinked()) return@withContext false
        r.setMode(index)
        true
    }

    private fun scheduleStop(context: Context) {
        stopJob?.cancel()
        stopJob = scope.launch {
            delay(GRACE_MS)
            if (!foreground) repo(context).stop()
        }
    }
}
