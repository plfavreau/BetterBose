package org.pilou.betterbose.data

enum class Spatial(val code: Int) {
    OFF(0), STILL(1), MOTION(2);

    companion object {
        fun of(code: Int): Spatial = entries.firstOrNull { it.code == code } ?: OFF
    }
}

data class Mode(val index: Int, val name: String, val isPreset: Boolean)

/**
 * Live listening settings from [31.10]. [cancellation] is in UI terms (10 = maximum),
 * the wire byte is inverted (0 = maximum).
 */
data class LiveSettings(
    val cancellation: Int,
    val autoCnc: Int,
    val spatial: Spatial,
    val windBlock: Boolean,
    val ancEnabled: Boolean,
)

data class Equalizer(val bass: Int = 0, val mid: Int = 0, val treble: Int = 0) {
    fun band(band: EqBand): Int = when (band) {
        EqBand.BASS -> bass
        EqBand.MID -> mid
        EqBand.TREBLE -> treble
    }

    fun with(band: EqBand, value: Int): Equalizer = when (band) {
        EqBand.BASS -> copy(bass = value)
        EqBand.MID -> copy(mid = value)
        EqBand.TREBLE -> copy(treble = value)
    }

    val isFlat: Boolean get() = bass == 0 && mid == 0 && treble == 0
}

enum class EqBand(val code: Int, val label: String) {
    BASS(0, "Bass"), MID(1, "Mid"), TREBLE(2, "Treble")
}

data class Headset(
    val name: String = "QC Ultra",
    val address: String = "",
    val firmware: String = "",
    val battery: Int? = null,
    val modes: List<Mode> = emptyList(),
    val currentMode: Int? = null,
    val live: LiveSettings? = null,
    val eq: Equalizer? = null,
    val multipoint: Boolean? = null,
    val autoPause: Boolean? = null,
    val shortcut: String? = null,
    val sourcesConnected: Int? = null,
    val sourcesPaired: Int? = null,
)

sealed interface ConnectionState {
    data object NeedsPermission : ConnectionState
    data object BluetoothOff : ConnectionState
    data class Disconnected(val canConnect: Boolean, val error: String? = null) : ConnectionState
    data class Connecting(val name: String) : ConnectionState
    /** [syncing] = showing the last known state while the link is (re)established. */
    data class Connected(val headset: Headset, val syncing: Boolean = false) : ConnectionState
}

val DEMO_HEADSET = Headset(
    name = "My QC Ultra",
    address = "00:0C:8A:12:34:56",
    firmware = "10.12.14",
    battery = 72,
    modes = listOf(
        Mode(0, "Quiet", true), Mode(1, "Aware", true), Mode(2, "Immersion", true),
        Mode(3, "Cinema", true), Mode(4, "Focus", false),
    ),
    currentMode = 0,
    live = LiveSettings(cancellation = 10, autoCnc = 0, spatial = Spatial.OFF, windBlock = false, ancEnabled = true),
    eq = Equalizer(),
    multipoint = true,
    autoPause = true,
    shortcut = "Modes",
    sourcesConnected = 2,
    sourcesPaired = 4,
)
