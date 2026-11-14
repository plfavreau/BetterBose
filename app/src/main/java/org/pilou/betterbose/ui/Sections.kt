package org.pilou.betterbose.ui

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import org.pilou.betterbose.data.CNC_MAX
import org.pilou.betterbose.data.EQ_MAX
import org.pilou.betterbose.data.EQ_MIN
import org.pilou.betterbose.data.EqBand
import org.pilou.betterbose.data.Equalizer
import org.pilou.betterbose.data.LiveSettings
import org.pilou.betterbose.data.Mode
import org.pilou.betterbose.data.Spatial

val SectionPadding = Modifier.padding(start = 18.dp, end = 18.dp, top = 22.dp)

@Composable
fun ModeCarousel(modes: List<Mode>, current: Int?, onSelect: (Int) -> Unit) {
    Column(Modifier.padding(top = 22.dp)) {
        SectionLabel("Mode", Modifier.padding(horizontal = 18.dp))
        LazyRow(
            contentPadding = PaddingValues(horizontal = 18.dp, vertical = 2.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            items(modes, key = { it.index }) { mode -> ModeCard(mode, mode.index == current) { onSelect(mode.index) } }
        }
    }
}

@Composable
private fun ModeCard(mode: Mode, selected: Boolean, onClick: () -> Unit) {
    val bg by animateColorAsState(if (selected) Palette.ink else Color.White, label = "modeBg")
    val fg by animateColorAsState(if (selected) Color.White else Palette.ink, label = "modeFg")
    Column(
        Modifier
            .widthIn(min = 92.dp)
            .shadow(1.dp, RoundedCornerShape(18.dp), ambientColor = Color(0x0A000000), spotColor = Color(0x0A000000))
            .background(bg, RoundedCornerShape(18.dp))
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                role = Role.RadioButton,
                onClick = onClick,
            )
            .padding(start = 14.dp, end = 14.dp, top = 14.dp, bottom = 12.dp),
    ) {
        Icon(Icons.forMode(mode.name), fg)
        Spacer(Modifier.height(18.dp))
        BasicText(mode.name, style = Type.bodyStrong.copy(fontSize = Type.callout.fontSize, color = fg), maxLines = 1)
    }
}

private fun cancellationLabel(v: Int): String = when (v) {
    CNC_MAX -> "Max"
    0 -> "Min"
    else -> "$v"
}

@Composable
fun NoiseCard(live: LiveSettings, onCommit: (LiveSettings) -> Unit) {
    var drag by remember(live.cancellation) { mutableStateOf(live.cancellation) }
    Card(SectionPadding) {
        Column {
            Row(Modifier.fillMaxWidth().padding(bottom = 4.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                BasicText("Noise cancellation", style = Type.body)
                BasicText(
                    if (live.windBlock) "Wind block" else cancellationLabel(drag),
                    style = Type.label,
                )
            }
            StepSlider(
                value = drag,
                range = 0..CNC_MAX,
                enabled = !live.windBlock,
                onChange = { drag = it },
                onCommit = { onCommit(live.copy(cancellation = it, ancEnabled = true)) },
            )
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                BasicText("Aware", style = Type.caption)
                BasicText("Quiet", style = Type.caption)
            }
        }
    }
}

@Composable
fun SpatialSection(live: LiveSettings, onCommit: (LiveSettings) -> Unit) {
    Column(SectionPadding) {
        SectionLabel("Immersive audio")
        Segmented(
            options = listOf(Spatial.OFF to "Off", Spatial.STILL to "Still", Spatial.MOTION to "Motion"),
            selected = live.spatial,
            onSelect = { onCommit(live.copy(spatial = it)) },
        )
    }
}

@Composable
fun EqualizerCard(eq: Equalizer, onBand: (EqBand, Int) -> Unit, onReset: () -> Unit) {
    Card(SectionPadding) {
        Column {
            Row(Modifier.fillMaxWidth().padding(bottom = 6.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                BasicText("Equalizer", style = Type.body)
                if (!eq.isFlat) {
                    BasicText(
                        "Reset",
                        style = Type.label.copy(color = Palette.blue),
                        modifier = Modifier.clickable(role = Role.Button, onClick = onReset),
                    )
                }
            }
            EqBand.entries.forEach { band -> EqRow(band, eq.band(band)) { onBand(band, it) } }
        }
    }
}

@Composable
private fun EqRow(band: EqBand, value: Int, onCommit: (Int) -> Unit) {
    var drag by remember(value) { mutableStateOf(value) }
    Row(verticalAlignment = Alignment.CenterVertically) {
        BasicText(band.label, style = Type.callout.copy(color = Palette.body), modifier = Modifier.width(56.dp))
        StepSlider(drag, EQ_MIN..EQ_MAX, onChange = { drag = it }, onCommit = onCommit, modifier = Modifier.weight(1f), origin = 0)
        BasicText(
            if (drag > 0) "+$drag" else "$drag",
            style = Type.bodyStrong.copy(fontSize = Type.callout.fontSize, textAlign = TextAlign.End),
            modifier = Modifier.width(34.dp),
        )
    }
}
