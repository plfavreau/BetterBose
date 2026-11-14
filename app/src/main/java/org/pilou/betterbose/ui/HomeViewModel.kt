package org.pilou.betterbose.ui

import android.app.Application
import android.os.Build
import android.provider.Settings
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.pilou.betterbose.alerts.BatteryAlerts
import org.pilou.betterbose.alerts.BatteryCheckJob
import org.pilou.betterbose.data.AlertSettings
import org.pilou.betterbose.data.AppPrefs
import org.pilou.betterbose.data.BoseDevices
import org.pilou.betterbose.data.HeadsetRepository

/** Keeps the link open briefly after the app is backgrounded so quick app switches stay instant. */
private const val BACKGROUND_GRACE_MS = 30_000L

class HomeViewModel(app: Application) : AndroidViewModel(app) {
    val prefs = AppPrefs.get(app)
    val repo = HeadsetRepository(BoseDevices(app), prefs, viewModelScope) { BatteryAlerts.onLevel(app, it) }
    val alerts = prefs.alerts

    fun setAlerts(settings: AlertSettings) {
        prefs.setAlerts(settings)
        BatteryCheckJob.schedule(getApplication())
        repo.currentBattery()?.let { BatteryAlerts.onLevel(getApplication(), it) }
    }
    val state = repo.state
    val phoneName: String =
        Settings.Global.getString(app.contentResolver, Settings.Global.DEVICE_NAME) ?: Build.MODEL

    private var stopJob: Job? = null

    fun onForeground(hasPermission: Boolean) {
        stopJob?.cancel()
        if (hasPermission) repo.start() else repo.requirePermission()
    }

    fun onBackground() {
        stopJob = viewModelScope.launch {
            delay(BACKGROUND_GRACE_MS)
            repo.stop()
        }
    }

    override fun onCleared() = repo.stop()
}
