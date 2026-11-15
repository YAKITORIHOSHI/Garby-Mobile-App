package com.thesis.garby

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.thesis.garby.realtime.OperatorAction
import com.thesis.garby.realtime.OperatorStatus
import com.thesis.garby.realtime.isFresh
import com.thesis.garby.realtime.markerSummary
import com.thesis.garby.realtime.operatorBlockReason
import com.thesis.garby.realtime.operatorOutcomeText
import com.thesis.garby.ui.glass.GlassButton
import com.thesis.garby.ui.glass.GlassButtonStyle
import com.thesis.garby.ui.glass.GlassDialog
import com.thesis.garby.ui.glass.GlassPanel
import com.thesis.garby.ui.glass.GlassScaffold
import com.thesis.garby.ui.glass.GlassTopBar
import com.thesis.garby.ui.glass.RevealWhenLoaded
import com.thesis.garby.ui.glass.SkeletonCard
import com.thesis.garby.ui.glass.widthCap
import com.thesis.garby.ui.theme.GarbyColors

/** What the operator is asked before an action that lets the robot move or restarts it. */
private data class Confirmation(val title: String, val body: String, val confirm: String)

private fun confirmationFor(action: OperatorAction): Confirmation? = when (action) {
    OperatorAction.Release -> Confirmation(
        "Release the stop?",
        "The stopped route ends. GARBY stays at idle and needs a new deliberate start.",
        "Release"
    )
    OperatorAction.Continue -> Confirmation(
        "Continue run?",
        "Release the emergency stop and let GARBY finish the stopped route from where it stopped.\n\n" +
            "Only continue if GARBY has not been moved by hand. Obstacle detection, the heading " +
            "check and the emergency stop remain active.",
        "Continue"
    )
    OperatorAction.Reverse -> Confirmation(
        "Reverse to start?",
        "Release the emergency stop and let GARBY drive back to where this route started. It turns " +
            "around, drives forward along the way it came and faces its starting direction again.\n\n" +
            "Only do this if GARBY has not been moved by hand. It returns only as exactly as its " +
            "wheels repeat their steps.",
        "Reverse"
    )
    OperatorAction.Reboot -> Confirmation(
        "Restart GARBY?",
        "Restarts the Raspberry Pi, the BLE bridge and the motor controller. The emergency stop " +
            "stays latched; afterwards release it and start again. This app loses GARBY for about a " +
            "minute.",
        "Restart"
    )
    OperatorAction.SonarOff -> Confirmation(
        "Disable the obstacle sonar?",
        "The low front sonar is the only sensor that sees chair legs, boxes and feet. With it off, " +
            "GARBY relies on the LiDAR at chest height.",
        "Disable"
    )
    OperatorAction.FrontReadOnlyOn, OperatorAction.BackReadOnlyOn -> Confirmation(
        "Set these LiDAR checks to read-only?",
        "The distances are still shown, but they no longer stop GARBY in that direction. Side " +
            "clearance, missing scans and the emergency stop still do. It is switched back on when " +
            "the robot software restarts.",
        "Set read-only"
    )
    else -> null
}

/**
 * The Pi monitor's Controls tab on the phone. Every button is enabled from what the controllers
 * confirmed to the Pi, and every request goes through the gate the Pi's own buttons use.
 */
@Composable
fun ControlsScreen(viewModel: OperatorViewModel) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    var confirming by rememberSaveable { mutableStateOf<OperatorAction?>(null) }
    val status = state.status
    val fresh = status.isFresh(state.nowMs)
    val (subtitle, subtitleColor) = when {
        !state.online -> "This phone is offline" to GarbyColors.Warning
        !state.statusLoaded -> "Connecting" to GarbyColors.TextMuted
        !fresh -> "GARBY's Pi is not reporting" to GarbyColors.Warning
        status?.connected != true -> "Pi running · no link to the controllers" to GarbyColors.Warning
        else -> "Pi and controllers connected" to GarbyColors.Ok
    }

    fun request(action: OperatorAction) {
        if (confirmationFor(action) != null) confirming = action else viewModel.send(action)
    }

    GlassScaffold(
        topBar = { GlassTopBar(title = "Controls", subtitle = subtitle, subtitleColor = subtitleColor) }
    ) { padding ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            RevealWhenLoaded(
                loaded = state.statusLoaded,
                modifier = Modifier.widthCap().fillMaxWidth(),
                skeleton = {
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        SkeletonCard("Loading GARBY's state", lines = 3)
                        SkeletonCard("Loading stop controls", lines = 2)
                        SkeletonCard("Loading sensor controls", lines = 3)
                    }
                }
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    StatePanel(status, fresh, state.nowMs)
                    val outcome = state.notice ?: operatorOutcomeText(state.command, state.nowMs)
                    if (outcome != null) {
                        GlassPanel(onClick = viewModel::clearNotice, onClickLabel = "Dismiss") {
                            Text("Last request", style = MaterialTheme.typography.labelLarge, color = GarbyColors.TextMuted)
                            Spacer(Modifier.height(4.dp))
                            Text(outcome, style = MaterialTheme.typography.bodyLarge, color = GarbyColors.TextPrimary)
                        }
                    }
                    ControlGroup("After an emergency stop") {
                        listOf(OperatorAction.Continue, OperatorAction.Reverse, OperatorAction.Release).forEach { action ->
                            ActionRow(action, state, ::request, primary = action == OperatorAction.Continue)
                        }
                        Hint("Continue finishes the stopped route. Reverse drives back to its start. " +
                            "Release ends it at idle. The stop button is under every screen.")
                    }
                    ControlGroup("Obstacle sensing") {
                        ToggleRow(
                            title = "Obstacle sonar",
                            value = status?.ultrasonicEnabled.takeIf { fresh },
                            onText = "Enabled (controller confirmed)",
                            offText = "Disabled; servo at its default view (confirmed)",
                            turnOn = OperatorAction.SonarOn, turnOff = OperatorAction.SonarOff,
                            state = state, onRequest = ::request
                        )
                        ToggleRow(
                            title = "Front LiDAR checks",
                            value = status?.frontReadOnly?.not().takeIf { fresh },
                            onText = "Stopping for what is ahead",
                            offText = "Read-only: shown, not stopping",
                            turnOn = OperatorAction.FrontReadOnlyOff, turnOff = OperatorAction.FrontReadOnlyOn,
                            state = state, onRequest = ::request
                        )
                        ToggleRow(
                            title = "Rear LiDAR checks",
                            value = status?.backReadOnly?.not().takeIf { fresh },
                            onText = "Enabled",
                            offText = "Read-only: shown, not stopping",
                            turnOn = OperatorAction.BackReadOnlyOff, turnOff = OperatorAction.BackReadOnlyOn,
                            state = state, onRequest = ::request
                        )
                    }
                    ControlGroup("System") {
                        ActionRow(OperatorAction.Reboot, state, ::request, primary = false)
                        Hint("Manual disposal is on Home; the return to base is on Activity.")
                        if (fresh && status != null && status.software.isNotBlank()) {
                            Hint("Robot software ${status.software}, running ${status.launcherMode}.")
                        }
                    }
                }
            }
        }
    }

    confirming?.let { action ->
        val text = confirmationFor(action)
        if (text == null) {
            confirming = null
        } else {
            GlassDialog(onDismissRequest = { confirming = null }) {
                Text(text.title, style = MaterialTheme.typography.headlineSmall, color = GarbyColors.TextPrimary)
                Spacer(Modifier.height(10.dp))
                Text(text.body, style = MaterialTheme.typography.bodyMedium, color = GarbyColors.TextSecondary)
                Spacer(Modifier.height(22.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    GlassButton(
                        text = "Cancel", onClick = { confirming = null },
                        style = GlassButtonStyle.Secondary, modifier = Modifier.weight(1f)
                    )
                    GlassButton(
                        text = text.confirm,
                        onClick = {
                            confirming = null
                            // The view model rechecks the gate with the state of this moment.
                            viewModel.send(action)
                        },
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }
    }
}

@Composable
private fun StatePanel(status: OperatorStatus?, fresh: Boolean, nowMs: Long) {
    GlassPanel {
        Text("GARBY now", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.height(8.dp))
        if (status == null || !fresh) {
            Text(
                if (status == null) "The robot software has not reported yet. Start it on the Pi " +
                    "(run_headless.sh start) and check its internet link."
                else "Last report ${((nowMs - status.updatedAtMs) / 1000L).coerceAtLeast(0L)} s ago. " +
                    "The robot software is stopped or has no internet.",
                style = MaterialTheme.typography.bodyMedium, color = GarbyColors.Warning
            )
        } else {
            val stop = when (status.emergencyStopped) {
                true -> "Latched (confirmed)" to GarbyColors.Danger
                false -> "Released (confirmed)" to GarbyColors.Ok
                null -> "Unknown / unconfirmed" to GarbyColors.Warning
            }
            val stateText = status.robotState.replace('_', ' ').lowercase()
                .replaceFirstChar { it.uppercase() }.ifBlank { "Unknown" }
            InfoLine("State", stateText, GarbyColors.TextPrimary)
            InfoLine("Emergency stop", stop.first, stop.second)
            if (status.routeLeg.isNotBlank()) InfoLine("Route step", status.routeLeg, GarbyColors.TextSecondary)
            if (status.motionHold.isNotBlank()) InfoLine("Held by", status.motionHold, GarbyColors.Warning)
            if (status.zones.isNotBlank()) InfoLine("Zones", status.zones.replace(",", ", "), GarbyColors.TextSecondary)
            val seen = markerSummary(status.people, 0, status.objects)
            if (seen != null) InfoLine("In view", seen, GarbyColors.TextSecondary)
            if (status.message.isNotBlank()) InfoLine("Controllers", status.message, GarbyColors.TextMuted)
        }
    }
}

@Composable
private fun InfoLine(label: String, value: String, color: Color) {
    Row(Modifier.fillMaxWidth().padding(vertical = 3.dp)) {
        Text(label, style = MaterialTheme.typography.bodyMedium, color = GarbyColors.TextMuted, modifier = Modifier.weight(0.38f))
        Text(value, style = MaterialTheme.typography.bodyMedium, color = color, modifier = Modifier.weight(0.62f))
    }
}

@Composable
private fun ControlGroup(title: String, content: @Composable () -> Unit) {
    GlassPanel {
        Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.height(10.dp))
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) { content() }
    }
}

@Composable
private fun Hint(text: String) {
    Text(text, style = MaterialTheme.typography.bodySmall, color = GarbyColors.TextMuted)
}

/** One action button with the reason underneath when it is not available. */
@Composable
private fun ActionRow(
    action: OperatorAction,
    state: OperatorUiState,
    onRequest: (OperatorAction) -> Unit,
    primary: Boolean
) {
    val blocked = operatorBlockReason(action, state.status, state.nowMs, state.online, state.sending != null)
    Column {
        GlassButton(
            text = action.label,
            onClick = { onRequest(action) },
            enabled = blocked == null,
            loading = state.sending == action,
            style = if (primary) GlassButtonStyle.Primary else GlassButtonStyle.Secondary,
            modifier = Modifier.fillMaxWidth()
        )
        if (blocked != null && state.sending != action) {
            Spacer(Modifier.height(4.dp))
            Hint(blocked)
        }
    }
}

/** A confirmed on/off value with the one action that changes it. Unknown offers nothing. */
@Composable
private fun ToggleRow(
    title: String,
    value: Boolean?,
    onText: String,
    offText: String,
    turnOn: OperatorAction,
    turnOff: OperatorAction,
    state: OperatorUiState,
    onRequest: (OperatorAction) -> Unit
) {
    Column {
        Text(title, style = MaterialTheme.typography.bodyLarge, color = GarbyColors.TextPrimary)
        Text(
            when (value) {
                true -> onText
                false -> offText
                null -> "Unknown: waiting for confirmation"
            },
            style = MaterialTheme.typography.bodySmall,
            color = if (value == false) GarbyColors.Warning else GarbyColors.TextMuted
        )
        if (value != null) {
            Spacer(Modifier.height(6.dp))
            ActionRow(if (value) turnOff else turnOn, state, onRequest, primary = false)
        }
    }
}
