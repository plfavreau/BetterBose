package org.pilou.betterbose

import android.Manifest
import android.annotation.SuppressLint
import android.app.Activity
import android.bluetooth.BluetoothA2dp
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothProfile
import android.content.pm.PackageManager
import android.graphics.Typeface
import android.os.Bundle
import android.util.Log
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import org.pilou.betterbose.bmap.BmapClient
import org.pilou.betterbose.bmap.BmapFrame
import org.pilou.betterbose.bmap.Op
import org.pilou.betterbose.bmap.bytesOf
import org.pilou.betterbose.bmap.hex
import org.pilou.betterbose.bmap.u8
import java.io.File

/**
 * Step-1 feasibility probe: connect to the headset over RFCOMM/SPP and run read-only
 * BMAP GETs. Only writes when launched with `--ei setMode <idx>` (mode switch).
 */
@SuppressLint("MissingPermission", "SetTextI18n")
class ProbeActivity : Activity() {
    private lateinit var output: TextView
    private lateinit var scroll: ScrollView
    private val transcript = StringBuilder()
    @Volatile private var running = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        output = TextView(this).apply {
            typeface = Typeface.MONOSPACE
            textSize = 11f
            setPadding(32, 32, 32, 32)
            setTextIsSelectable(true)
        }
        scroll = ScrollView(this).apply { addView(output) }
        val rerun = Button(this).apply { text = "Run probe"; setOnClickListener { start() } }
        setContentView(LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            fitsSystemWindows = true
            addView(rerun)
            addView(scroll, LinearLayout.LayoutParams(-1, 0, 1f))
        })

        if (checkSelfPermission(Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(arrayOf(Manifest.permission.BLUETOOTH_CONNECT), PERMISSION_REQUEST)
        } else {
            start()
        }
    }

    override fun onRequestPermissionsResult(code: Int, perms: Array<out String>, results: IntArray) {
        if (results.firstOrNull() == PackageManager.PERMISSION_GRANTED) start() else log("permission denied")
    }

    private fun log(line: String) {
        Log.i(TAG, line)
        synchronized(transcript) { transcript.append(line).append('\n') }
        runOnUiThread {
            output.append(line + "\n")
            scroll.post { scroll.fullScroll(ScrollView.FOCUS_DOWN) }
        }
    }

    private fun start() {
        if (running) return
        running = true
        output.text = ""
        synchronized(transcript) { transcript.clear() }
        val setMode = intent.getIntExtra(EXTRA_SET_MODE, -1)
        findConnectedBose { device ->
            Thread({
                try {
                    if (device == null) log("no connected Bose headset found") else runProbe(device, setMode)
                } catch (e: Exception) {
                    log("FAILED: ${e.javaClass.simpleName}: ${e.message} / cause=${e.cause?.message}")
                } finally {
                    saveTranscript()
                    running = false
                }
            }, "probe").start()
        }
    }

    /** Uses the A2DP proxy so we pick the headset that is actually connected right now. */
    private fun findConnectedBose(onFound: (BluetoothDevice?) -> Unit) {
        val adapter = getSystemService(BluetoothManager::class.java).adapter
        adapter.getProfileProxy(this, object : BluetoothProfile.ServiceListener {
            override fun onServiceConnected(profile: Int, proxy: BluetoothProfile) {
                val connected = (proxy as BluetoothA2dp).connectedDevices
                adapter.closeProfileProxy(BluetoothProfile.A2DP, proxy)
                log("A2DP connected: ${connected.joinToString { "${it.name} (${it.address})" }}")
                onFound(connected.firstOrNull { it.name?.contains("Bose", ignoreCase = true) == true })
            }
            override fun onServiceDisconnected(profile: Int) = Unit
        }, BluetoothProfile.A2DP)
    }

    private fun runProbe(device: BluetoothDevice, setMode: Int) {
        log("target: ${device.name} ${device.address}")
        log("sdp uuids: ${device.uuids?.joinToString { it.toString().take(8) }}")
        val t0 = System.currentTimeMillis()
        val client = BmapClient(device, ::log)
        client.onUnsolicited = { log("   (unsolicited) $it") }
        val via = client.connect()
        log("connect took ${System.currentTimeMillis() - t0} ms via $via")

        fun get(label: String, block: Int, fn: Int, payload: ByteArray = ByteArray(0)): BmapFrame? {
            val r = client.request(block, fn, Op.GET, payload)
            val last = r.lastOrNull()
            log("== $label: ${describe(last)}")
            return last
        }

        get("BMAP version", 0, 1)?.let { log("   = ${it.ascii()}") }
        get("Fblock bitmask", 0, 2)
        get("Product id", 0, 3)
        get("Firmware", 0, 5)?.let { log("   = ${it.ascii()}") }
        get("Product name", 1, 2)?.let {
            log("   = ${String(it.payload.drop(1).toByteArray(), Charsets.UTF_8)}")
        }
        get("Battery", 2, 2)?.let { if (!it.isError) log("   = ${it.payload[0].u8}%") }
        get("CNC level", 1, 5)
        get("EQ", 1, 7)
        get("Multipoint", 1, 10)
        get("Wear detect", 1, 24)
        get("Shortcut btn", 1, 9)
        get("Volume", 5, 5)
        get("Spatial", 5, 15)
        get("Live settings 31.10", 31, 10)
        get("Current mode", 31, 3)?.let { if (!it.isError) log("   = mode #${it.payload[0].u8}") }

        for (idx in 0..MAX_MODE_SLOT) {
            val r = get("Mode slot $idx", 31, 6, bytesOf(idx)) ?: break
            if (r.isError) break
            if (r.payload.size >= MODE_NAME_END) {
                val name = String(r.payload.copyOfRange(MODE_NAME_START, MODE_NAME_END), Charsets.UTF_8)
                    .trimEnd('\u0000')
                log("   = \"$name\" configured=${r.payload[4].u8}")
            }
        }

        if (setMode >= 0) {
            val r = client.request(31, 3, Op.START, bytesOf(setMode, 0), timeoutMs = 4000)
            log("== SET mode $setMode: ${r.joinToString { describe(it) }}")
        }

        client.close()
        log("done in ${System.currentTimeMillis() - t0} ms")
    }

    private fun describe(f: BmapFrame?): String = when {
        f == null -> "TIMEOUT"
        f.isError -> "ERROR ${f.errorCode}"
        else -> "${Op.name(f.op)} ${f.payload.hex()}"
    }

    private fun saveTranscript() {
        val dir = getExternalFilesDir(null) ?: return
        File(dir, "probe.txt").writeText(synchronized(transcript) { transcript.toString() })
    }

    companion object {
        private const val TAG = "BMAP"
        private const val PERMISSION_REQUEST = 1
        private const val EXTRA_SET_MODE = "setMode"
        private const val MAX_MODE_SLOT = 10
        private const val MODE_NAME_START = 6
        private const val MODE_NAME_END = 38
    }
}
