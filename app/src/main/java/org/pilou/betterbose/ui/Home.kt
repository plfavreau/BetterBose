package org.pilou.betterbose.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import org.pilou.betterbose.data.Headset
import org.pilou.betterbose.data.HeadsetRepository

private const val HEADER_TITLE = "QC Ultra"

@Composable
fun HomeContent(headset: Headset, syncing: Boolean, phone: String, repo: HeadsetRepository, onSettings: () -> Unit) {
    Column(
        Modifier
            .statusBarsPadding()
            .verticalScroll(rememberScrollState())
            .navigationBarsPadding(),
    ) {
        Header(HEADER_TITLE, phone, syncing, onSettings)
        Hero(headset.battery)
        headset.battery?.takeIf { it <= LOW_BATTERY }?.let { LowBatteryNotice(it) }
        if (headset.modes.isNotEmpty()) ModeCarousel(headset.modes, headset.currentMode, repo::setMode)
        headset.live?.let { live ->
            NoiseCard(live, repo::setLive)
            SpatialSection(live, repo::setLive)
        }
        headset.eq?.let { EqualizerCard(it, repo::setEq, repo::resetEq) }
        SettingsCard(headset, repo)
        Spacer(Modifier.height(40.dp))
    }
}

@Composable
private fun SettingsCard(headset: Headset, repo: HeadsetRepository) {
    Card(SectionPadding, verticalPadding = 4.dp) {
        Column {
            var first = true
            fun divider(): Boolean = (!first).also { first = false }
            headset.multipoint?.let { on ->
                SettingsRow("Multipoint", divider = divider()) { Toggle(on, repo::setMultipoint) }
            }
            headset.autoPause?.let { on ->
                SettingsRow("Pause when removed", divider = divider()) { Toggle(on, repo::setAutoPause) }
            }
            headset.live?.let { live ->
                SettingsRow("Wind block", divider = divider()) {
                    Toggle(live.windBlock, { repo.setLive(live.copy(windBlock = it)) })
                }
            }
            headset.shortcut?.let { SettingsRow("Shortcut button", divider = divider()) { Value(it) } }
            headset.sourcesConnected?.let { n ->
                SettingsRow("Devices", divider = divider()) { Value("$n connected") }
            }
        }
    }
}

@Composable
private fun Value(text: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        BasicText(text, style = Type.callout)
        Spacer(Modifier.width(2.dp))
    }
}
