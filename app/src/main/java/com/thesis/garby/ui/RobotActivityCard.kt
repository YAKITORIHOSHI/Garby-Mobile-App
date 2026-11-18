package com.thesis.garby.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Block
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material.icons.rounded.HourglassTop
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Report
import androidx.compose.material.icons.rounded.Sync
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.dp
import com.thesis.garby.realtime.RobotActivity
import com.thesis.garby.realtime.RobotActivityState
import com.thesis.garby.ui.glass.GlassPanel
import com.thesis.garby.ui.glass.IconOrb
import com.thesis.garby.ui.theme.GarbyColors

@Composable
fun RobotActivityCard(state: RobotActivityState) {
    val (icon, color) = when (state.activity) {
        RobotActivity.Running -> Icons.Rounded.PlayArrow to GarbyColors.Info
        RobotActivity.Blocked -> Icons.Rounded.Block to GarbyColors.Warning
        RobotActivity.Turning -> Icons.Rounded.Sync to GarbyColors.Rose
        RobotActivity.Idle, RobotActivity.Parked -> Icons.Rounded.Pause to GarbyColors.Ok
        RobotActivity.EmergencyStopped -> Icons.Rounded.Report to GarbyColors.Danger
        RobotActivity.Fault -> Icons.Rounded.ErrorOutline to GarbyColors.Danger
        RobotActivity.Unknown -> Icons.Rounded.HourglassTop to GarbyColors.Neutral
    }
    GlassPanel(modifier = Modifier.fillMaxWidth().semantics { stateDescription = state.activity.label }) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconOrb(icon, color, size = 42.dp)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text("CURRENT ACTIVITY", style = MaterialTheme.typography.labelSmall,
                    color = GarbyColors.TextMuted, modifier = Modifier.semantics { heading() })
                Text(state.activity.label, style = MaterialTheme.typography.headlineSmall, color = color)
            }
        }
        Spacer(Modifier.height(10.dp))
        Text(state.detail, style = MaterialTheme.typography.bodyMedium, color = GarbyColors.TextSecondary)
        if (state.routeLeg.isNotBlank()) {
            Spacer(Modifier.height(6.dp))
            Text(state.routeLeg, style = MaterialTheme.typography.bodySmall, color = GarbyColors.TextMuted)
        }
    }
}
