package org.pilou.betterbose.data

import org.pilou.betterbose.bmap.BmapFrame
import org.pilou.betterbose.bmap.u8

/** Addresses ([block.function]) used by the app. See docs/PROTOCOL.md. */
object Addr {
    val FIRMWARE = 0 to 5
    val NAME = 1 to 2
    val EQ = 1 to 7
    val SHORTCUT = 1 to 9
    val MULTIPOINT = 1 to 10
    val AUTO_PAUSE = 1 to 24
    val BATTERY = 2 to 2
    val SOURCES = 4 to 4
    val NOTIFY = 9 to 2
    val CURRENT_MODE = 31 to 3
    val MODE_RECORD = 31 to 6
    val LIVE = 31 to 10
}

/** Payload the official app sends to [9.2] to subscribe to pushes from blocks 0,1,2,4,5,9,18,31. */
val NOTIFY_SUBSCRIBE = byteArrayOf(0x01, 0x80.toByte(), 0x04, 0x02, 0x37)

const val CNC_MAX = 10
const val EQ_MIN = -10
const val EQ_MAX = 10
private const val PRESET_COUNT = 4
private const val MODE_NAME_START = 6
private const val MODE_NAME_END = 38
private const val MODE_CONFIGURED = 4
private const val EQ_ENTRY = 4
private const val MAC_LEN = 6
private const val LIVE_LEN = 5

private val SHORTCUT_ACTIONS = mapOf(
    1 to "Voice assistant",
    2 to "Noise cancel",
    3 to "Battery",
    4 to "Play / pause",
    8 to "Switch device",
    9 to "Conversation",
    13 to "Wind block",
    14 to "Off",
    17 to "Modes",
    19 to "Immersive audio",
)

fun BmapFrame.address(): Pair<Int, Int> = block to function

fun parseName(p: ByteArray): String? =
    if (p.size > 1) String(p, 1, p.size - 1, Charsets.UTF_8).trimEnd('\u0000') else null

fun parseBattery(p: ByteArray): Int? = p.firstOrNull()?.u8?.takeIf { it in 0..100 }

fun parseFlag(p: ByteArray): Boolean? = p.firstOrNull()?.let { it.u8 and 1 == 1 }

fun parseShortcut(p: ByteArray): String? =
    if (p.size >= 3) SHORTCUT_ACTIONS[p[2].u8] ?: "Custom" else null

fun parseCurrentMode(p: ByteArray): Int? = p.firstOrNull()?.u8

fun parseEq(p: ByteArray): Equalizer? {
    if (p.size < EQ_ENTRY) return null
    var eq = Equalizer()
    for (i in 0 until p.size / EQ_ENTRY) {
        val value = p[i * EQ_ENTRY + 2].toInt()
        val band = EqBand.entries.firstOrNull { it.code == p[i * EQ_ENTRY + 3].u8 } ?: continue
        eq = eq.with(band, value)
    }
    return eq
}

fun parseLive(p: ByteArray): LiveSettings? {
    if (p.size < LIVE_LEN) return null
    return LiveSettings(
        cancellation = CNC_MAX - p[0].u8.coerceIn(0, CNC_MAX),
        autoCnc = p[1].u8,
        spatial = Spatial.of(p[2].u8),
        windBlock = p[3].u8 != 0,
        ancEnabled = p[4].u8 != 0,
    )
}

fun LiveSettings.encode(): ByteArray = byteArrayOf(
    (CNC_MAX - cancellation).toByte(),
    0,
    spatial.code.toByte(),
    (if (windBlock) 1 else 0).toByte(),
    (if (ancEnabled) 1 else 0).toByte(),
)

/** [4.4] reply: mask byte (bit i = entry i connected) followed by 6-byte MACs. */
fun parseSources(p: ByteArray): Pair<Int, Int>? {
    if (p.isEmpty()) return null
    val paired = (p.size - 1) / MAC_LEN
    val connected = (0 until paired).count { p[0].u8 shr it and 1 == 1 }
    return connected to paired
}

fun parseModeRecord(index: Int, p: ByteArray): Mode? {
    if (p.size < MODE_NAME_END) return null
    val name = String(p.copyOfRange(MODE_NAME_START, MODE_NAME_END), Charsets.UTF_8).trimEnd('\u0000').trim()
    val preset = index < PRESET_COUNT
    val configured = p[MODE_CONFIGURED].u8 != 0
    if (!preset && (!configured || name.isEmpty() || name.equals("None", ignoreCase = true))) return null
    return Mode(index, name.ifEmpty { "Mode ${index + 1}" }, preset)
}

/** Applies a pushed or echoed frame to the current state; unknown frames leave it unchanged. */
fun Headset.apply(frame: BmapFrame): Headset {
    if (frame.isError) return this
    val p = frame.payload
    return when (frame.address()) {
        Addr.BATTERY -> copy(battery = parseBattery(p) ?: battery)
        Addr.NAME -> copy(name = parseName(p) ?: name)
        Addr.EQ -> copy(eq = parseEq(p) ?: eq)
        Addr.MULTIPOINT -> copy(multipoint = parseFlag(p) ?: multipoint)
        Addr.AUTO_PAUSE -> copy(autoPause = parseFlag(p) ?: autoPause)
        Addr.SHORTCUT -> copy(shortcut = parseShortcut(p) ?: shortcut)
        Addr.CURRENT_MODE -> copy(currentMode = parseCurrentMode(p) ?: currentMode)
        Addr.LIVE -> copy(live = parseLive(p) ?: live)
        Addr.SOURCES -> parseSources(p)?.let { (c, n) -> copy(sourcesConnected = c, sourcesPaired = n) } ?: this
        else -> this
    }
}
