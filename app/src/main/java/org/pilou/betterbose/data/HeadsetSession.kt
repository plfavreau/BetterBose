package org.pilou.betterbose.data

import android.annotation.SuppressLint
import android.bluetooth.BluetoothDevice
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import org.pilou.betterbose.bmap.BmapClient
import org.pilou.betterbose.bmap.BmapFrame
import org.pilou.betterbose.bmap.Op
import org.pilou.betterbose.bmap.bytesOf

private const val MAX_MODE_SLOT = 10
private const val MODE_SWITCH_TIMEOUT_MS = 4000L

/**
 * One open BMAP connection. Every call runs on [io], a single-lane dispatcher, because
 * the underlying client is blocking and handles one request at a time.
 */
@SuppressLint("MissingPermission")
class HeadsetSession(
    val device: BluetoothDevice,
    private val io: CoroutineDispatcher,
    log: (String) -> Unit,
) {
    private val client = BmapClient(device, log)

    var onPush: ((BmapFrame) -> Unit)?
        get() = client.onUnsolicited
        set(value) { client.onUnsolicited = value }

    var onClosed: (() -> Unit)?
        get() = client.onClosed
        set(value) { client.onClosed = value }

    suspend fun open() = withContext(io) { client.connect() }

    fun close() = client.close()

    val isOpen: Boolean get() = client.isConnected

    private suspend fun get(addr: Pair<Int, Int>, payload: ByteArray = ByteArray(0)): BmapFrame? =
        withContext(io) { client.request(addr.first, addr.second, Op.GET, payload).lastOrNull() }

    private suspend fun setGet(addr: Pair<Int, Int>, payload: ByteArray): BmapFrame? =
        withContext(io) { client.request(addr.first, addr.second, Op.SET_GET, payload).lastOrNull() }

    /** Reads everything the home screen needs. Missing features stay null. */
    suspend fun readAll(): Headset {
        setGet(Addr.NOTIFY, NOTIFY_SUBSCRIBE)
        var h = Headset(name = device.name ?: "QC Ultra", address = device.address)
        h = get(Addr.FIRMWARE)?.takeUnless { it.isError }?.let { h.copy(firmware = it.ascii().substringBefore("+")) } ?: h
        for (addr in listOf(
            Addr.NAME, Addr.BATTERY, Addr.CURRENT_MODE, Addr.LIVE, Addr.EQ,
            Addr.MULTIPOINT, Addr.AUTO_PAUSE, Addr.SHORTCUT, Addr.SOURCES,
        )) {
            get(addr)?.let { h = h.apply(it) }
        }
        return h.copy(modes = readModes())
    }

    private suspend fun readModes(): List<Mode> {
        val modes = mutableListOf<Mode>()
        for (idx in 0..MAX_MODE_SLOT) {
            val r = get(Addr.MODE_RECORD, bytesOf(idx)) ?: break
            if (r.isError) break
            parseModeRecord(idx, r.payload)?.let(modes::add)
        }
        return modes
    }

    suspend fun refreshBattery(): BmapFrame? = get(Addr.BATTERY)

    suspend fun refreshLive(): BmapFrame? = get(Addr.LIVE)

    /** Switches mode; the reply is RESULT [index] once the headset has applied it. */
    suspend fun setMode(index: Int): Boolean = withContext(io) {
        val replies = client.request(
            Addr.CURRENT_MODE.first, Addr.CURRENT_MODE.second, Op.START,
            bytesOf(index, 0), timeoutMs = MODE_SWITCH_TIMEOUT_MS,
        )
        replies.any { it.op == Op.RESULT || it.op == Op.STATUS }
    }

    suspend fun setLive(live: LiveSettings): BmapFrame? = setGet(Addr.LIVE, live.encode())

    suspend fun setEq(band: EqBand, value: Int): BmapFrame? =
        setGet(Addr.EQ, bytesOf(value.coerceIn(EQ_MIN, EQ_MAX), band.code))

    suspend fun setMultipoint(on: Boolean): BmapFrame? = setGet(Addr.MULTIPOINT, bytesOf(if (on) 1 else 0))

    suspend fun setAutoPause(on: Boolean): BmapFrame? = setGet(Addr.AUTO_PAUSE, bytesOf(if (on) 1 else 0))

    suspend fun rename(name: String): BmapFrame? = setGet(Addr.NAME, name.toByteArray(Charsets.UTF_8))

    suspend fun pumpPushes() = withContext(io) { client.drainUnsolicited() }
}
