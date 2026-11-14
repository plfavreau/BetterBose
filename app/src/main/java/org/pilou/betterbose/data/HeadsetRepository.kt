package org.pilou.betterbose.data

import android.bluetooth.BluetoothDevice
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import org.pilou.betterbose.alerts.BatteryCheckJob
import org.pilou.betterbose.bmap.BmapFrame

private const val TAG = "BetterBose"
private const val PUMP_INTERVAL_MS = 400L
private const val BATTERY_POLL_MS = 60_000L
private const val A2DP_SETTLE_MS = 1200L
private const val RECONNECT_DELAY_MS = 2000L
private const val CONNECT_TIMEOUT_MS = 30_000L
private const val CONNECT_RETRIES = 3
private const val RETRY_DELAY_MS = 3000L

/** Owns the connection lifecycle and exposes one observable [ConnectionState]. */
@OptIn(ExperimentalCoroutinesApi::class)
class HeadsetRepository(
    private val devices: BoseDevices,
    private val prefs: AppPrefs,
    private val scope: CoroutineScope,
    private val onBattery: (Int) -> Unit,
) {
    private val _state = MutableStateFlow<ConnectionState>(ConnectionState.Disconnected(canConnect = false))
    val state: StateFlow<ConnectionState> = _state.asStateFlow()

    private var session: HeadsetSession? = null
    private var sessionJob: Job? = null
    private var eventsJob: Job? = null
    private var editsDuringSync = 0

    private fun log(msg: String) = Log.i(TAG, msg)

    fun start() {
        if (eventsJob == null) {
            eventsJob = scope.launch { devices.events().collect(::onEvent) }
        }
        if (session == null) scope.launch { autoConnect() }
    }

    fun stop() {
        eventsJob?.cancel()
        eventsJob = null
        dropSession()
    }

    /** Debug only: renders the connected screen with sample data, no headset needed. */
    fun showDemo(battery: Int) {
        eventsJob?.cancel()
        _state.value = ConnectionState.Connected(DEMO_HEADSET.copy(battery = battery))
    }

    fun currentBattery(): Int? = (_state.value as? ConnectionState.Connected)?.headset?.battery

    fun requirePermission() { _state.value = ConnectionState.NeedsPermission }

    private suspend fun autoConnect() {
        if (!devices.isEnabled) {
            _state.value = ConnectionState.BluetoothOff
            return
        }
        val device = devices.connected()
        if (device != null) connect(device) else idle()
    }

    private fun idle(error: String? = null) {
        _state.value = if (devices.isEnabled) {
            ConnectionState.Disconnected(canConnect = devices.bonded().isNotEmpty(), error = error)
        } else {
            ConnectionState.BluetoothOff
        }
    }

    /** Manual connect: opening RFCOMM pages the headset, which then brings its audio back too. */
    fun connectManually() {
        scope.launch {
            val device = devices.connected() ?: devices.bonded().firstOrNull()
            if (device == null) idle("No Bose headphones are paired with this phone.") else connect(device)
        }
    }

    private suspend fun onEvent(event: BtEvent) {
        when (event) {
            is BtEvent.AdapterChanged -> if (event.on) autoConnect() else { dropSession(); idle() }
            is BtEvent.HeadsetConnected -> if (session == null) {
                delay(A2DP_SETTLE_MS)
                if (session == null) connect(event.device)
            }
            is BtEvent.HeadsetDisconnected -> if (session?.device?.address == event.device.address) {
                dropSession()
                idle()
            }
        }
    }

    private suspend fun connect(device: BluetoothDevice, attempt: Int = 1) {
        if (session != null) return
        val cached = ((_state.value as? ConnectionState.Connected)?.headset ?: prefs.loadHeadset())
            ?.takeIf { it.address == device.address }
        _state.value = if (cached != null) ConnectionState.Connected(cached, syncing = true) else ConnectionState.Connecting("QC Ultra")
        // Own lane per session: a stuck old session must never block a new one.
        val s = HeadsetSession(device, Dispatchers.IO.limitedParallelism(1), ::log)
        session = s
        BatteryCheckJob.appConnected = true
        // Blocking socket calls ignore coroutine cancellation, so the timeout closes the session instead.
        val watchdog = scope.launch { delay(CONNECT_TIMEOUT_MS); log("connect timed out"); s.close() }
        try {
            s.open()
            if (session !== s) return s.close()
            s.onPush = { frame -> onPush(frame) }
            editsDuringSync = 0
            var headset = s.readCore(cached?.modes.orEmpty())
            // Taps made while syncing already reached the headset, but the reads above may predate them.
            if (editsDuringSync > 0) s.readEditable().forEach { headset = headset.apply(it) }
            watchdog.cancel()
            if (session !== s || !s.isOpen) throw java.io.IOException("link lost while reading")
            s.onClosed = { scope.launch { onSessionClosed(s) } }
            _state.value = ConnectionState.Connected(headset)
            prefs.saveHeadset(headset)
            headset.battery?.let(onBattery)
            sessionJob = scope.launch {
                refreshModes(s)
                maintain(s)
            }
        } catch (e: Exception) {
            watchdog.cancel()
            log("connect attempt $attempt failed: ${e.message}")
            s.close()
            if (session !== s) return
            session = null
            BatteryCheckJob.appConnected = false
            val stillThere = devices.connected()?.address == device.address
            if (stillThere && attempt < CONNECT_RETRIES) {
                delay(RETRY_DELAY_MS)
                if (session == null) connect(device, attempt + 1)
            } else {
                idle("Couldn't reach the headphones. Make sure they're on and nearby.")
            }
        }
    }

    /** Mode names rarely change, so the cached ones are shown first and refreshed in the background. */
    private suspend fun refreshModes(s: HeadsetSession) {
        val modes = runCatching { s.readModes() }.getOrNull()?.takeIf { it.isNotEmpty() } ?: return
        if (session !== s) return
        edit { it.copy(modes = modes) }
        (_state.value as? ConnectionState.Connected)?.let { prefs.saveHeadset(it.headset) }
    }

    private suspend fun maintain(s: HeadsetSession) {
        var sinceBattery = 0L
        while (scope.isActive && session === s) {
            delay(PUMP_INTERVAL_MS)
            s.pumpPushes()
            sinceBattery += PUMP_INTERVAL_MS
            if (sinceBattery >= BATTERY_POLL_MS) {
                sinceBattery = 0
                s.refreshBattery()?.let(::onPush)
            }
        }
    }

    private suspend fun onSessionClosed(s: HeadsetSession) {
        if (session !== s) return
        dropSession()
        delay(RECONNECT_DELAY_MS)
        if (session == null) autoConnect()
    }

    private fun dropSession() {
        sessionJob?.cancel()
        sessionJob = null
        session?.let { it.onClosed = null; it.close() }
        session = null
        BatteryCheckJob.appConnected = false
        prefs.touch()
    }

    private fun onPush(frame: BmapFrame) {
        _state.update { st -> if (st is ConnectionState.Connected) st.copy(headset = st.headset.apply(frame)) else st }
        if (frame.address() == Addr.BATTERY && !frame.isError) parseBattery(frame.payload)?.let(onBattery)
        (_state.value as? ConnectionState.Connected)?.takeUnless { it.syncing }?.let { prefs.saveHeadset(it.headset) }
    }

    private fun edit(transform: (Headset) -> Headset) {
        if ((_state.value as? ConnectionState.Connected)?.syncing == true) editsDuringSync++
        _state.update { st -> if (st is ConnectionState.Connected) st.copy(headset = transform(st.headset)) else st }
    }

    /** Optimistically applies [optimistic], then sends; the echo (or a re-read) settles the truth. */
    private fun write(optimistic: (Headset) -> Headset, send: suspend (HeadsetSession) -> BmapFrame?) {
        edit(optimistic)
        val s = session ?: return
        scope.launch {
            val reply = runCatching { send(s) }.getOrNull()
            if (reply != null) onPush(reply)
        }
    }

    /** Opens the link if needed (widget taps); commands sent right after queue behind the connect. */
    suspend fun ensureLinked(): Boolean {
        if (session != null) return true
        val device = devices.connected() ?: return false
        if (session == null) scope.launch(start = CoroutineStart.UNDISPATCHED) { connect(device) }
        return session != null
    }

    fun setMode(index: Int) {
        editsDuringSync++
        edit { it.copy(currentMode = index) }
        val s = session ?: return
        scope.launch {
            runCatching {
                s.setMode(index)
                s.refreshLive()?.let(::onPush)
            }
        }
    }

    fun setLive(live: LiveSettings) = write({ it.copy(live = live) }) { it.setLive(live) }

    fun setEq(band: EqBand, value: Int) =
        write({ h -> h.copy(eq = (h.eq ?: Equalizer()).with(band, value)) }) { it.setEq(band, value) }

    fun resetEq() {
        write({ it.copy(eq = Equalizer()) }) { s ->
            EqBand.entries.map { s.setEq(it, 0) }.lastOrNull()
        }
    }

    fun setMultipoint(on: Boolean) = write({ it.copy(multipoint = on) }) { it.setMultipoint(on) }

    fun setAutoPause(on: Boolean) = write({ it.copy(autoPause = on) }) { it.setAutoPause(on) }

    fun rename(name: String) = write({ it.copy(name = name) }) { it.rename(name) }
}
