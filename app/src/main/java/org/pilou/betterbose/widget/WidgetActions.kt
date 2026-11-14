package org.pilou.betterbose.widget

import android.annotation.SuppressLint
import android.bluetooth.BluetoothDevice
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.glance.GlanceId
import androidx.glance.action.ActionParameters
import androidx.glance.appwidget.action.ActionCallback
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.pilou.betterbose.alerts.BatteryAlerts
import org.pilou.betterbose.data.AppPrefs
import org.pilou.betterbose.data.BoseDevices
import org.pilou.betterbose.data.HeadsetRepository
import org.pilou.betterbose.data.HeadsetSession

private const val TAG = "BetterBose"
private const val BT_SETTLE_MS = 3000L

/** Mode button on a widget: reuses the app's link when it has one, otherwise opens a short one. */
class SetModeAction : ActionCallback {
    override suspend fun onAction(context: Context, glanceId: GlanceId, parameters: ActionParameters) {
        val index = parameters[MODE_KEY] ?: return
        val before = WidgetStore.state.value
        if (!before.connected) return
        WidgetStore.patch(context) { it.copy(currentMode = index) }

        HeadsetRepository.active?.takeIf { it.isLinked }?.let {
            it.setMode(index)
            return
        }
        val ok = runCatching { switchOverShortLink(context, index) }
            .onFailure { Log.w(TAG, "widget mode switch failed", it) }
            .getOrDefault(false)
        if (!ok) WidgetStore.patch(context) { it.copy(currentMode = before.currentMode) }
    }

    private suspend fun switchOverShortLink(context: Context, index: Int): Boolean {
        val device = BoseDevices(context).connected() ?: return false
        val session = HeadsetSession(device, Dispatchers.IO) { }
        try {
            session.open()
            if (!session.setMode(index)) return false
        } finally {
            session.close()
        }
        val prefs = AppPrefs.get(context)
        prefs.loadHeadset()?.takeIf { it.address == device.address }?.let { prefs.saveHeadset(it.copy(currentMode = index)) }
        Log.i(TAG, "widget switched mode to $index")
        return true
    }

    companion object {
        val MODE_KEY = ActionParameters.Key<Int>("mode")
    }
}

/** Headset link up or down (ACL broadcasts reach manifest receivers): redraw widgets, re-arm the low battery reminder. */
class BluetoothChangeReceiver : BroadcastReceiver() {
    @SuppressLint("MissingPermission")
    override fun onReceive(context: Context, intent: Intent) {
        val device = intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE, BluetoothDevice::class.java)
        val classic = intent.getIntExtra(BluetoothDevice.EXTRA_TRANSPORT, BluetoothDevice.TRANSPORT_BREDR) == BluetoothDevice.TRANSPORT_BREDR
        val headsetBack = intent.action == BluetoothDevice.ACTION_ACL_CONNECTED && classic &&
            device != null && runCatching { BoseDevices(context).isBose(device) }.getOrDefault(false)
        if (headsetBack) BatteryAlerts.rearm(context)
        val pending = goAsync()
        CoroutineScope(SupervisorJob() + Dispatchers.Default).launch {
            try {
                delay(BT_SETTLE_MS)
                WidgetStore.refresh(context)
                delay(BT_SETTLE_MS)
                // Android learns the battery level over HFP a few seconds after connecting.
                if (headsetBack) device?.let(BatteryAlerts::systemBatteryLevel)?.let { BatteryAlerts.onLevel(context, it) }
            } finally {
                pending.finish()
            }
        }
    }
}
