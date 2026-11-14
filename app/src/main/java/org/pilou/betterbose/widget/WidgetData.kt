package org.pilou.betterbose.widget

import android.Manifest
import android.annotation.SuppressLint
import android.bluetooth.BluetoothDevice
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.provider.Settings
import androidx.glance.appwidget.updateAll
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import org.pilou.betterbose.R
import org.pilou.betterbose.alerts.BatteryAlerts
import org.pilou.betterbose.data.AppPrefs
import org.pilou.betterbose.data.BoseDevices
import org.pilou.betterbose.data.Mode

private const val DEBOUNCE_MS = 400L
private const val QUICK_MODES = 4
private val FALLBACK_MODES = listOf(Mode(0, "Quiet", true), Mode(1, "Aware", true), Mode(2, "Immersion", true), Mode(3, "Cinema", true))

/** Everything a widget renders. Built from the last headset snapshot plus live system state. */
data class WidgetData(
    val connected: Boolean = false,
    val battery: Int? = null,
    val currentMode: Int? = null,
    val modes: List<Mode> = FALLBACK_MODES,
    val phone: String = "",
    val lowThreshold: Int = 20,
) {
    val low: Boolean get() = battery != null && battery <= lowThreshold
    val currentName: String? get() = modes.firstOrNull { it.index == currentMode }?.name
}

fun modeIcon(name: String): Int = when (name.lowercase()) {
    "quiet" -> R.drawable.wm_quiet
    "aware" -> R.drawable.wm_aware
    "immersion" -> R.drawable.wm_immersion
    "cinema" -> R.drawable.wm_cinema
    else -> R.drawable.wm_custom
}

/** Quiet, Aware, Immersion, then the first mode you made yourself (falls back to Cinema). */
private fun quickModes(all: List<Mode>): List<Mode> {
    if (all.isEmpty()) return FALLBACK_MODES
    val presets = all.filter { it.isPreset && it.index < 3 }
    val extra = all.firstOrNull { !it.isPreset } ?: all.firstOrNull { it.index == 3 }
    return (presets + listOfNotNull(extra)).take(QUICK_MODES)
}

/** Shared, observable widget state; widgets collect it so a refresh re-renders open sessions too. */
object WidgetStore {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val _state = MutableStateFlow(WidgetData())
    val state: StateFlow<WidgetData> = _state.asStateFlow()
    private var pending: Job? = null
    @Volatile var loaded = false
        private set

    /** Debug only: freezes the faked state so real refreshes don't overwrite it. */
    @Volatile var demo = false

    /** Optimistic local change (e.g. a mode tap) shown before the headset confirms. */
    fun patch(context: Context, transform: (WidgetData) -> WidgetData) {
        _state.value = transform(_state.value)
        push(context)
    }

    /** Re-reads the snapshot and Bluetooth state, then redraws every placed widget. Debounced. */
    fun refresh(context: Context) {
        val app = context.applicationContext
        pending?.cancel()
        pending = scope.launch {
            delay(DEBOUNCE_MS)
            _state.value = load(app)
            push(app)
        }
    }

    suspend fun load(context: Context): WidgetData {
        if (demo) return _state.value
        val prefs = AppPrefs.get(context)
        val snapshot = prefs.loadHeadset()
        val device = connectedDevice(context)
        val live = device?.takeIf { snapshot == null || it.address == snapshot.address }
        return WidgetData(
            connected = live != null,
            battery = live?.let(::systemBattery) ?: snapshot?.battery,
            currentMode = snapshot?.currentMode,
            modes = quickModes(snapshot?.modes.orEmpty()),
            phone = Settings.Global.getString(context.contentResolver, Settings.Global.DEVICE_NAME) ?: Build.MODEL,
            lowThreshold = prefs.alerts.value.threshold,
        ).also { _state.value = it; loaded = true }
    }

    private fun push(context: Context) {
        scope.launch { ALL_WIDGETS.forEach { runCatching { it.updateAll(context) } } }
    }

    private suspend fun connectedDevice(context: Context): BluetoothDevice? {
        if (context.checkSelfPermission(Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) return null
        return runCatching { BoseDevices(context).connected() }.getOrNull()
    }

    @SuppressLint("MissingPermission")
    private fun systemBattery(device: BluetoothDevice): Int? = BatteryAlerts.systemBatteryLevel(device)
}
