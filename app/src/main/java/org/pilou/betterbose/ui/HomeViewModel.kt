package org.pilou.betterbose.ui

import android.app.Application
import android.os.Build
import android.provider.Settings
import androidx.lifecycle.AndroidViewModel
import org.pilou.betterbose.alerts.BatteryAlerts
import org.pilou.betterbose.alerts.BatteryCheckJob
import org.pilou.betterbose.data.AlertSettings
import org.pilou.betterbose.data.AppPrefs
import org.pilou.betterbose.data.HeadsetRepository
import org.pilou.betterbose.data.Headsets

class HomeViewModel(app: Application) : AndroidViewModel(app) {
    val prefs = AppPrefs.get(app)
    val repo: HeadsetRepository = Headsets.repo(app)
    val alerts = prefs.alerts

    fun setAlerts(settings: AlertSettings) {
        prefs.setAlerts(settings)
        BatteryCheckJob.schedule(getApplication())
        repo.currentBattery()?.let { BatteryAlerts.onLevel(getApplication(), it) }
    }
    val state = repo.state
    val phoneName: String =
        Settings.Global.getString(app.contentResolver, Settings.Global.DEVICE_NAME) ?: Build.MODEL

    fun onForeground(hasPermission: Boolean) {
        if (hasPermission) Headsets.onForeground(getApplication()) else repo.requirePermission()
    }

    /** The link outlives the screen: [Headsets] closes it after a grace period. */
    fun onBackground() = Headsets.onBackground(getApplication())
}
