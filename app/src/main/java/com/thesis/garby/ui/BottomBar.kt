package com.thesis.garby.ui

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.Radar
import androidx.compose.material.icons.rounded.Report
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.thesis.garby.performance.LocalPerformanceProfile
import com.thesis.garby.ui.glass.GlassBorderBrush
import com.thesis.garby.ui.glass.GlassTier
import com.thesis.garby.ui.glass.glassFill
import com.thesis.garby.ui.glass.glassSheen
import com.thesis.garby.ui.theme.GarbyColors

/** The four places of the app, in bar order. */
enum class GarbyTab(val route: String, val label: String, val icon: ImageVector) {
    Home("dashboard", "Home", Icons.Rounded.Home),
    Map("map", "Map", Icons.Rounded.Radar),
    Controls("controls", "Controls", Icons.Rounded.Tune),
    Activity("return", "Activity", Icons.Rounded.History);

    companion object {
        fun fromRoute(route: String?): GarbyTab = entries.firstOrNull { it.route == route } ?: Home
    }
}

/** What the stop button says. It reports what the controllers confirmed, never just a tap. */
enum class StopButtonState(val text: String, val detail: String) {
    Ready("EMERGENCY STOP", "Stops GARBY's wheels"),
    Sending("SENDING STOP…", "Waiting for GARBY"),
    Requested("STOP SENT", "Waiting for the controllers to confirm"),
    Confirmed("STOPPED", "Confirmed by the controllers. Release it in Controls"),
    Offline("EMERGENCY STOP", "This phone is offline: use the robot's own stop")
}

/**
 * The bar under every screen: an emergency stop that is always one tap away, and the four tabs.
 * The phone's stop travels over the internet. It is a convenience, not a safety device.
 */
@Composable
fun GarbyBottomBar(
    selected: GarbyTab,
    onSelect: (GarbyTab) -> Unit,
    stopState: StopButtonState,
    onStop: () -> Unit,
    modifier: Modifier = Modifier
) {
    val shape = RoundedCornerShape(28.dp)
    Box(
        modifier
            .fillMaxWidth()
            .background(GarbyColors.Night)
            .windowInsetsPadding(
                WindowInsets.safeDrawing.only(WindowInsetsSides.Bottom + WindowInsetsSides.Horizontal)
            )
            .padding(start = 10.dp, end = 10.dp, top = 6.dp, bottom = 8.dp)
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .clip(shape)
                .glassFill(GlassTier.Thick)
                .glassSheen(GlassTier.Thick)
                .border(1.dp, GlassBorderBrush, shape)
                .padding(8.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            StopButton(stopState, onStop)
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                GarbyTab.entries.forEach { tab ->
                    TabItem(tab, tab == selected, { onSelect(tab) }, Modifier.weight(1f))
                }
            }
        }
    }
}

@Composable
private fun StopButton(state: StopButtonState, onStop: () -> Unit) {
    val confirmed = state == StopButtonState.Confirmed
    val fill by animateColorAsState(
        if (confirmed) GarbyColors.CrimsonDeep else GarbyColors.Ember, label = "stopFill"
    )
    val shape = RoundedCornerShape(20.dp)
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = 52.dp)
            .clip(shape)
            .background(fill)
            .border(1.dp, Color.White.copy(alpha = 0.35f), shape)
            .clickable(role = Role.Button, onClickLabel = "Emergency stop", onClick = onStop)
            .semantics { contentDescription = "${state.text}. ${state.detail}" }
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(Icons.Rounded.Report, contentDescription = null, tint = Color.White, modifier = Modifier.size(26.dp))
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(
                state.text, style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold, color = Color.White
            )
            Text(state.detail, style = MaterialTheme.typography.bodySmall, color = Color.White.copy(alpha = 0.9f))
        }
    }
}

@Composable
private fun TabItem(tab: GarbyTab, selected: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val reducedEffects = LocalPerformanceProfile.current.reducedEffects
    val tint by animateColorAsState(
        if (selected) GarbyColors.TextPrimary else GarbyColors.TextMuted, label = "tabTint"
    )
    val pill by animateColorAsState(
        if (selected) GarbyColors.Ember.copy(alpha = 0.32f) else Color.Transparent, label = "tabPill"
    )
    val lift by animateFloatAsState(
        targetValue = if (selected && !reducedEffects) 1.08f else 1f,
        animationSpec = spring(dampingRatio = 0.55f, stiffness = Spring.StiffnessMedium),
        label = "tabLift"
    )
    val shape = RoundedCornerShape(18.dp)
    Column(
        modifier
            .heightIn(min = 52.dp)
            .clip(shape)
            .background(pill)
            .clickable(role = Role.Tab, onClickLabel = tab.label, onClick = onClick)
            .semantics { this.selected = selected }
            .padding(vertical = 6.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            tab.icon, contentDescription = null, tint = tint,
            modifier = Modifier.size(24.dp).graphicsLayer { scaleX = lift; scaleY = lift }
        )
        Spacer(Modifier.height(2.dp))
        Text(
            tab.label, style = MaterialTheme.typography.labelMedium, color = tint,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal, maxLines = 1
        )
    }
}

/** The stop button's state from what the Pi mirrors and what this phone is doing. */
fun stopButtonState(
    emergencyStopped: Boolean?,
    statusFresh: Boolean,
    sendingStop: Boolean,
    stopRequestOpen: Boolean,
    online: Boolean
): StopButtonState = when {
    !online -> StopButtonState.Offline
    sendingStop -> StopButtonState.Sending
    statusFresh && emergencyStopped == true -> StopButtonState.Confirmed
    stopRequestOpen -> StopButtonState.Requested
    else -> StopButtonState.Ready
}
