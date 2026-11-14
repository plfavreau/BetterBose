package org.pilou.betterbose.bmap

import android.annotation.SuppressLint
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothSocket
import java.io.IOException
import java.util.Timer
import java.util.UUID
import kotlin.concurrent.timerTask
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.TimeUnit

/**
 * Minimal blocking BMAP client over one long-lived RFCOMM socket.
 * Responses are matched by (block, function), not by arrival order, because the
 * headset pushes unsolicited STATUS frames at any time.
 */
@SuppressLint("MissingPermission")
class BmapClient(
    private val device: BluetoothDevice,
    private val log: (String) -> Unit,
) {
    @Volatile private var socket: BluetoothSocket? = null
    @Volatile private var pending: BluetoothSocket? = null
    @Volatile private var closed = false
    private val inbox = LinkedBlockingQueue<BmapFrame>()
    private var reader: Thread? = null

    var onUnsolicited: ((BmapFrame) -> Unit)? = null
    var onClosed: (() -> Unit)? = null

    val isConnected: Boolean get() = socket?.isConnected == true

    /** Tries each strategy in order; returns the label of the one that worked. */
    fun connect(): String {
        val strategies: List<Pair<String, () -> BluetoothSocket>> = listOf(
            "secure SPP uuid" to { device.createRfcommSocketToServiceRecord(SPP_UUID) },
            "insecure SPP uuid" to { device.createInsecureRfcommSocketToServiceRecord(SPP_UUID) },
            "channel 2 (reflection)" to { rfcommOnChannel(2) },
            "channel 1 (reflection)" to { rfcommOnChannel(1) },
        )
        var lastError: Exception? = null
        // The official Bose app usually holds one SPP channel; start with whatever worked last time.
        val ordered = strategies.sortedByDescending { it.first == preferred }
        for ((label, factory) in ordered) {
            for (attempt in 1..CONNECT_ATTEMPTS) {
                if (closed) throw IOException("closed while connecting")
                try {
                    val s = factory()
                    pending = s
                    // BluetoothSocket.connect has no timeout of its own and can hang ~20s on a busy channel.
                    val watchdog = Timer(true).apply { schedule(timerTask { runCatching { s.close() } }, ATTEMPT_TIMEOUT_MS) }
                    try { s.connect() } finally { watchdog.cancel() }
                    pending = null
                    if (closed) {
                        runCatching { s.close() }
                        throw IOException("closed while connecting")
                    }
                    socket = s
                    startReader(s)
                    preferred = label
                    log("connected via $label (attempt $attempt)")
                    return label
                } catch (e: Exception) {
                    if (closed) throw IOException("closed while connecting")
                    lastError = e
                    log("connect $label #$attempt failed: ${e.javaClass.simpleName}: ${e.message}")
                    Thread.sleep(BACKOFF_MS * attempt)
                }
            }
        }
        throw IOException("all connect strategies failed", lastError)
    }

    private fun rfcommOnChannel(channel: Int): BluetoothSocket {
        val m = device.javaClass.getMethod("createRfcommSocket", Int::class.javaPrimitiveType)
        return m.invoke(device, channel) as BluetoothSocket
    }

    private fun startReader(s: BluetoothSocket) {
        reader = Thread({
            val parser = BmapStreamParser()
            val buf = ByteArray(READ_BUFFER)
            try {
                val input = s.inputStream
                while (true) {
                    val n = input.read(buf)
                    if (n < 0) break
                    for (frame in parser.feed(buf, n)) {
                        log("<- $frame")
                        inbox.put(frame)
                    }
                }
            } catch (e: IOException) {
                log("reader stopped: ${e.message}")
            }
            onClosed?.invoke()
        }, "bmap-reader").apply { isDaemon = true; start() }
    }

    fun send(frame: BmapFrame) {
        val s = socket ?: throw IOException("not connected")
        log("-> $frame")
        s.outputStream.write(frame.encode())
        s.outputStream.flush()
    }

    /**
     * Sends a request and waits for the terminal reply on the same (block, function).
     * STATUS/RESULT/ERROR end the exchange; PROCESSING extends the wait.
     * Enumerations (START -> n x STATUS -> RESULT) are collected when [collectUntilResult].
     */
    fun request(
        block: Int,
        function: Int,
        op: Int,
        payload: ByteArray = ByteArray(0),
        timeoutMs: Long = DEFAULT_TIMEOUT_MS,
        collectUntilResult: Boolean = false,
    ): List<BmapFrame> {
        drainUnsolicited()
        send(BmapFrame(block, function, op, payload))
        val replies = mutableListOf<BmapFrame>()
        var deadline = System.currentTimeMillis() + timeoutMs
        while (true) {
            val remaining = deadline - System.currentTimeMillis()
            if (remaining <= 0) break
            val f = inbox.poll(remaining, TimeUnit.MILLISECONDS) ?: break
            if (f.block != block || f.function != function) {
                onUnsolicited?.invoke(f)
                continue
            }
            replies += f
            when (f.op) {
                Op.PROCESSING -> deadline = System.currentTimeMillis() + timeoutMs
                Op.ERROR, Op.RESULT -> return replies
                Op.STATUS -> if (!collectUntilResult) return replies
            }
        }
        return replies
    }

    /** Delivers frames that arrived while no request was waiting. */
    fun drainUnsolicited() {
        while (true) {
            val f = inbox.poll() ?: return
            onUnsolicited?.invoke(f)
        }
    }

    /** Closes the link, and aborts a connect still in progress so it can't steal the channel later. */
    fun close() {
        closed = true
        try { pending?.close() } catch (_: IOException) {}
        try { socket?.close() } catch (_: IOException) {}
        socket = null
    }

    companion object {
        val SPP_UUID: UUID = UUID.fromString("00001101-0000-1000-8000-00805f9b34fb")
        private const val CONNECT_ATTEMPTS = 2
        private const val ATTEMPT_TIMEOUT_MS = 6000L

        /** Strategy that last connected, tried first next time (process-wide). */
        @Volatile var preferred: String? = null
        private const val BACKOFF_MS = 600L
        private const val READ_BUFFER = 1024
        const val DEFAULT_TIMEOUT_MS = 1500L
    }
}
