package org.pilou.betterbose.data

import android.annotation.SuppressLint
import android.bluetooth.BluetoothA2dp
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothProfile
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.coroutines.resume

private const val PROXY_TIMEOUT_MS = 3000L

sealed interface BtEvent {
    data class HeadsetConnected(val device: BluetoothDevice) : BtEvent
    data class HeadsetDisconnected(val device: BluetoothDevice) : BtEvent
    data class AdapterChanged(val on: Boolean) : BtEvent
}

/** Finds Bose headsets among bonded / audio-connected devices and watches their link state. */
@SuppressLint("MissingPermission")
class BoseDevices(private val context: Context) {
    private val adapter: BluetoothAdapter? =
        context.getSystemService(BluetoothManager::class.java)?.adapter

    val isEnabled: Boolean get() = adapter?.isEnabled == true

    fun isBose(device: BluetoothDevice): Boolean =
        device.name?.contains("Bose", ignoreCase = true) == true

    fun bonded(): List<BluetoothDevice> = adapter?.bondedDevices.orEmpty().filter(::isBose)

    /** The Bose headset currently connected for audio, if any. */
    suspend fun connected(): BluetoothDevice? {
        val a = adapter ?: return null
        if (!a.isEnabled) return null
        return withTimeoutOrNull(PROXY_TIMEOUT_MS) {
            suspendCancellableCoroutine { cont ->
                val listener = object : BluetoothProfile.ServiceListener {
                    override fun onServiceConnected(profile: Int, proxy: BluetoothProfile) {
                        val device = proxy.connectedDevices.firstOrNull(::isBose)
                        a.closeProfileProxy(BluetoothProfile.A2DP, proxy)
                        if (cont.isActive) cont.resume(device)
                    }
                    override fun onServiceDisconnected(profile: Int) = Unit
                }
                if (!a.getProfileProxy(context, listener, BluetoothProfile.A2DP)) cont.resume(null)
            }
        }
    }

    fun events(): Flow<BtEvent> = callbackFlow {
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(ctx: Context, intent: Intent) {
                when (intent.action) {
                    BluetoothAdapter.ACTION_STATE_CHANGED -> {
                        val s = intent.getIntExtra(BluetoothAdapter.EXTRA_STATE, BluetoothAdapter.STATE_OFF)
                        if (s == BluetoothAdapter.STATE_ON || s == BluetoothAdapter.STATE_OFF) {
                            trySend(BtEvent.AdapterChanged(s == BluetoothAdapter.STATE_ON))
                        }
                    }
                    BluetoothA2dp.ACTION_CONNECTION_STATE_CHANGED -> {
                        val device = intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE, BluetoothDevice::class.java)
                            ?: return
                        if (!isBose(device)) return
                        when (intent.getIntExtra(BluetoothProfile.EXTRA_STATE, -1)) {
                            BluetoothProfile.STATE_CONNECTED -> trySend(BtEvent.HeadsetConnected(device))
                            BluetoothProfile.STATE_DISCONNECTED -> trySend(BtEvent.HeadsetDisconnected(device))
                        }
                    }
                }
            }
        }
        val filter = IntentFilter().apply {
            addAction(BluetoothAdapter.ACTION_STATE_CHANGED)
            addAction(BluetoothA2dp.ACTION_CONNECTION_STATE_CHANGED)
        }
        context.registerReceiver(receiver, filter, Context.RECEIVER_EXPORTED)
        awaitClose { context.unregisterReceiver(receiver) }
    }
}
