package org.pilou.betterbose.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import org.pilou.betterbose.data.AlertSettings
import org.pilou.betterbose.data.Headset

private const val MAX_NAME = 30
private val SCRIM = Color(0x66000000)

@Composable
fun SettingsSheet(
    visible: Boolean,
    headset: Headset,
    alerts: AlertSettings,
    canNotify: Boolean,
    onAlerts: (AlertSettings) -> Unit,
    onAllowNotifications: () -> Unit,
    onRename: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    BackHandler(visible, onDismiss)
    AnimatedVisibility(visible, enter = fadeIn(), exit = fadeOut()) {
        Box(Modifier.fillMaxSize().background(SCRIM).clickableNoRipple(onDismiss))
    }
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.BottomCenter) {
        AnimatedVisibility(visible, enter = slideInVertically { it }, exit = slideOutVertically { it }) {
            Column(
                Modifier
                    .statusBarsPadding()
                    .fillMaxWidth()
                    .background(Palette.background, RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp))
                    .clickableNoRipple {}
                    .verticalScroll(rememberScrollState())
                    .navigationBarsPadding()
                    .imePadding()
                    .padding(start = 18.dp, end = 18.dp, top = 10.dp, bottom = 24.dp),
            ) {
                Box(Modifier.align(Alignment.CenterHorizontally).width(36.dp).height(5.dp).background(Palette.track, CircleShape))
                Row(
                    Modifier.fillMaxWidth().padding(top = 14.dp, bottom = 16.dp, start = 4.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    BasicText("Headphones", style = Type.title)
                    Box(
                        Modifier.size(30.dp).background(Palette.track, CircleShape).clickableNoRipple(onDismiss),
                        contentAlignment = Alignment.Center,
                    ) { Icon(Icons.close, Palette.body, size = 14.dp) }
                }
                SectionLabel("Name")
                NameField(headset.name, onRename)
                Spacer(Modifier.height(22.dp))
                AlertsSection(alerts, canNotify, onAlerts, onAllowNotifications)
                Spacer(Modifier.height(22.dp))
                SectionLabel("About")
                Card(verticalPadding = 4.dp) {
                    Column {
                        SettingsRow("Model", divider = false) { BasicText("QC Ultra (2nd gen)", style = Type.callout) }
                        if (headset.firmware.isNotEmpty()) {
                            SettingsRow("Firmware") { BasicText(headset.firmware, style = Type.callout) }
                        }
                        headset.sourcesPaired?.let { SettingsRow("Paired devices") { BasicText("$it", style = Type.callout) } }
                        SettingsRow("Address") { BasicText(headset.address, style = Type.callout) }
                    }
                }
            }
        }
    }
}

@Composable
private fun NameField(current: String, onRename: (String) -> Unit) {
    var text by remember(current) { mutableStateOf(current) }
    val focus = LocalFocusManager.current
    val dirty = text.isNotBlank() && text.trim() != current
    fun save() {
        if (dirty) onRename(text.trim())
        focus.clearFocus()
    }
    Card {
        Row(verticalAlignment = Alignment.CenterVertically) {
            BasicTextField(
                value = text,
                onValueChange = { text = it.take(MAX_NAME) },
                singleLine = true,
                textStyle = Type.body,
                cursorBrush = SolidColor(Palette.blue),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = { save() }),
                modifier = Modifier.weight(1f).padding(vertical = 4.dp),
            )
            if (dirty) {
                BasicText("Save", style = Type.bodyStrong.copy(color = Palette.blue), modifier = Modifier.clickableNoRipple { save() })
            }
        }
    }
}
