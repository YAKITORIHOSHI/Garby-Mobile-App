package com.thesis.garby

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.PointMode
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.compose.currentStateAsState
import androidx.lifecycle.repeatOnLifecycle
import com.thesis.garby.realtime.MapFrame
import com.thesis.garby.realtime.MarkerKind
import com.thesis.garby.realtime.isFresh
import com.thesis.garby.realtime.mapPointCm
import com.thesis.garby.realtime.markerSummary
import com.thesis.garby.ui.glass.GlassPanel
import com.thesis.garby.ui.glass.GlassScaffold
import com.thesis.garby.ui.glass.GlassShapes
import com.thesis.garby.ui.glass.GlassTopBar
import com.thesis.garby.ui.glass.RadarLoader
import com.thesis.garby.ui.glass.RevealWhenLoaded
import com.thesis.garby.ui.glass.widthCap
import com.thesis.garby.ui.theme.GarbyColors
import androidx.compose.ui.platform.LocalConfiguration

private val RadarPoint = Color(0xFF3DF58A)
private val RadarRing = Color(0x5562B088)
private val PersonRed = Color(0xFFFF2D2D)
private val MovingAmber = Color(0xFFFFB020)
private val ObjectOrange = Color(0xFFFF8A3D)
private val RANGES_M = listOf(2, 4, 8)
private val MapDisplaySize = 320.dp

/**
 * The LiDAR map, as the Pi monitor draws it: GARBY in the middle, front at the top. While this
 * screen is visible it holds a lease so the Pi publishes frames; leaving it stops them.
 */
@Composable
fun MapScreen(viewModel: OperatorViewModel) {
    val lifecycleOwner = LocalLifecycleOwner.current
    val lifecycleState by lifecycleOwner.lifecycle.currentStateAsState()
    LaunchedEffect(lifecycleOwner, viewModel) {
        lifecycleOwner.lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) { viewModel.observeMapWhileVisible() }
    }
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    var rangeIndex by rememberSaveable { mutableIntStateOf(1) }
    val frame = state.frame
    // Observed, so a locale change redraws the numbers (lint NonObservableLocale).
    val locale = LocalConfiguration.current.locales[0]
    val stale = frame == null || frame.isStale(state.nowMs)
    val piReporting = state.status.isFresh(state.nowMs)
    val (subtitle, subtitleColor) = when {
        !state.online -> "This phone is offline" to GarbyColors.Warning
        state.mapRefused -> "Not on GARBY's operator list" to GarbyColors.Warning
        state.mapError != null -> state.mapError.orEmpty() to GarbyColors.Warning
        state.mapSleeping -> "Sleeping · no new scan for 10 seconds" to GarbyColors.TextMuted
        frame != null && !stale -> "Live · ${"%.1f".format(locale, (state.nowMs - frame.atMs + frame.ageMs).coerceAtLeast(0L) / 1000f)} s old" to GarbyColors.Ok
        frame != null -> "Stale: last scan ${((state.nowMs - frame.atMs) / 1000L).coerceAtLeast(0L)} s ago" to GarbyColors.Warning
        !piReporting && state.statusLoaded -> "GARBY's Pi is not reporting" to GarbyColors.Warning
        else -> "Waiting for the first scan" to GarbyColors.TextMuted
    }
    GlassScaffold(
        topBar = { GlassTopBar(title = "LiDAR map", subtitle = subtitle, subtitleColor = subtitleColor) }
    ) { padding ->
        Column(
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                // Keep bar clearance in the scroll content, so the viewport reaches
                // behind both floating bars rather than ending at an empty strip.
                .padding(padding)
                .padding(horizontal = 16.dp, vertical = 12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            GlassPanel(modifier = Modifier.widthCap().fillMaxWidth()) {
                RevealWhenLoaded(
                    loaded = frame != null || state.mapSleeping,
                    modifier = Modifier.fillMaxWidth(),
                    skeleton = {
                        Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                            Box(Modifier.widthIn(max = MapDisplaySize).fillMaxWidth().aspectRatio(1f), contentAlignment = Alignment.Center) {
                                RadarLoader(
                                    label = when {
                                        state.mapRefused -> "Not on GARBY's operator list: this phone's ID is on Controls"
                                        state.frameLoaded && !piReporting -> "GARBY is not sending scans"
                                        else -> "Waiting for GARBY's LiDAR"
                                    },
                                    modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp),
                                    animate = lifecycleState.isAtLeast(Lifecycle.State.RESUMED)
                                )
                            }
                        }
                    }
                ) {
                    Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                        if (frame != null) RadarView(frame, RANGES_M[rangeIndex], stale)
                        else if (state.mapSleeping) {
                            Box(
                                Modifier.widthIn(max = MapDisplaySize).fillMaxWidth().aspectRatio(1f),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    if (state.mapError != null) "Map unavailable\nReopen this tab to retry"
                                    else "Map sleeping\nWaiting for a new scan",
                                    style = MaterialTheme.typography.bodyLarge,
                                    color = GarbyColors.TextMuted,
                                    textAlign = TextAlign.Center
                                )
                            }
                        }
                    }
                }
                Spacer(Modifier.height(12.dp))
                FlowRow(
                    Modifier.fillMaxWidth().selectableGroup(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    itemVerticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Range", style = MaterialTheme.typography.labelLarge, color = GarbyColors.TextMuted)
                    RANGES_M.forEachIndexed { index, metres ->
                        RangeChip("$metres m", index == rangeIndex) { rangeIndex = index }
                    }
                }
            }
            GlassPanel(modifier = Modifier.widthCap().fillMaxWidth()) {
                Text("In view", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.height(6.dp))
                val seen = frame?.takeIf { !stale }?.let { markerSummary(it.people, it.movers, it.objects) }
                Text(
                    seen ?: if (stale) "No current scan." else "Nothing marked.",
                    style = MaterialTheme.typography.bodyLarge, color = GarbyColors.TextSecondary
                )
                if (frame != null && !stale) {
                    if (frame.hold.isNotBlank()) DetailLine("Held by", frame.hold, GarbyColors.Warning)
                    if (frame.zones.isNotBlank()) DetailLine("Zones", frame.zones.replace(",", ", "), GarbyColors.TextSecondary)
                    val walls = listOfNotNull(
                        frame.wallLeftCm?.let { "left ${"%.0f".format(locale, it)} cm" },
                        frame.wallRightCm?.let { "right ${"%.0f".format(locale, it)} cm" },
                        frame.headingDeg?.let { "heading ${"%+.1f".format(locale, it)}°" }
                    )
                    if (walls.isNotEmpty()) DetailLine("Walls", walls.joinToString(", "), GarbyColors.TextSecondary)
                }
                Spacer(Modifier.height(10.dp))
                LegendRow(PersonRed, "Likely person: person-sized and seen moving", round = true)
                LegendRow(MovingAmber, "Other moving object", round = true)
                LegendRow(ObjectOrange, "Object standing between the hallway walls", round = false)
                Spacer(Modifier.height(6.dp))
                Text(
                    "The LiDAR scans one plane at chest height. It cannot recognise people; red is a " +
                        "guess from size and movement, and it does not see what is below that plane.",
                    style = MaterialTheme.typography.bodySmall, color = GarbyColors.TextMuted
                )
            }
        }
    }
}

@Composable
private fun RangeChip(text: String, selected: Boolean, onClick: () -> Unit) {
    Box(
        Modifier
            .clip(GlassShapes.Pill)
            .background(if (selected) GarbyColors.Ember.copy(alpha = 0.4f) else GarbyColors.GlassStrokeFaint)
            .selectable(selected = selected, role = Role.RadioButton, onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 10.dp)
    ) {
        Text(
            text, style = MaterialTheme.typography.labelLarge,
            color = if (selected) GarbyColors.TextPrimary else GarbyColors.TextMuted
        )
    }
}

@Composable
private fun DetailLine(label: String, value: String, color: Color) {
    Row(Modifier.padding(top = 4.dp)) {
        Text("$label  ", style = MaterialTheme.typography.bodyMedium, color = GarbyColors.TextMuted)
        Text(value, style = MaterialTheme.typography.bodyMedium, color = color)
    }
}

@Composable
private fun LegendRow(color: Color, text: String, round: Boolean) {
    Row(Modifier.padding(vertical = 2.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(
            Modifier
                .size(12.dp)
                .clip(if (round) CircleShape else RoundedCornerShape(2.dp))
                .background(color)
        )
        Spacer(Modifier.width(10.dp))
        Text(text, style = MaterialTheme.typography.bodyMedium, color = GarbyColors.TextSecondary)
    }
}

@Composable
private fun RadarView(frame: MapFrame, rangeM: Int, stale: Boolean) {
    val summary = markerSummary(frame.people, frame.movers, frame.objects) ?: "nothing marked"
    Box(
        Modifier
            .widthIn(max = MapDisplaySize)
            .fillMaxWidth()
            .aspectRatio(1f)
            .semantics {
                contentDescription = "LiDAR map, $rangeM metre range, $summary" + if (stale) ", stale" else ""
            }
            .drawWithCache {
                val center = Offset(size.width / 2f, size.height / 2f)
                val radius = size.minDimension / 2f - 6.dp.toPx()
                val pxPerCm = radius / (rangeM * 100f)
                val fade = if (stale) 0.35f else 1f
                val dash = PathEffect.dashPathEffect(floatArrayOf(4.dp.toPx(), 6.dp.toPx()))
                val limit = rangeM * 100
                // Build scan geometry once per frame, range or display size; redraws reuse it.
                val points = buildList {
                    for (degree in 0 until 360) {
                        val cm = frame.distancesCm[degree]
                        if (cm <= 0 || cm > limit) continue
                        val (forward, left) = mapPointCm(degree, cm)
                        add(Offset(center.x - left * pxPerCm, center.y - forward * pxPerCm))
                    }
                }
                val arrowLength = 11.dp.toPx()
                val arrowHalfWidth = 6.5.dp.toPx()
                val arrowIndent = 2.5.dp.toPx()
                val arrowPath = Path().apply {
                    moveTo(center.x, center.y - arrowLength)
                    lineTo(center.x + arrowHalfWidth, center.y + arrowLength * 0.6f)
                    lineTo(center.x, center.y + arrowLength * 0.6f - arrowIndent)
                    lineTo(center.x - arrowHalfWidth, center.y + arrowLength * 0.6f)
                    close()
                }
                onDrawBehind {
                    drawCircle(Color(0xFF07130C), radius, center)
                    for (metre in 1..rangeM) {
                        if (rangeM > 4 && metre % 2 == 1) continue
                        drawCircle(
                            RadarRing, metre * 100f * pxPerCm, center,
                            style = Stroke(1.dp.toPx(), pathEffect = if (metre == rangeM) null else dash)
                        )
                    }
                    drawLine(RadarRing, Offset(center.x, center.y - radius), Offset(center.x, center.y + radius), 1.dp.toPx())
                    drawLine(RadarRing, Offset(center.x - radius, center.y), Offset(center.x + radius, center.y), 1.dp.toPx())
                    drawPoints(points, PointMode.Points, RadarPoint.copy(alpha = fade),
                        strokeWidth = 4.4.dp.toPx(), cap = StrokeCap.Round)
                    drawPath(arrowPath, GarbyColors.TextPrimary.copy(alpha = fade))
                    if (!stale) {
                        frame.markers.forEach { marker ->
                            if (marker.forwardCm.toLong() * marker.forwardCm + marker.leftCm.toLong() * marker.leftCm > limit.toLong() * limit) return@forEach
                            val at = toCanvas(marker.forwardCm.toFloat(), marker.leftCm.toFloat(), pxPerCm)
                            when (marker.kind) {
                                MarkerKind.Object -> {
                                    val half = 6.dp.toPx()
                                    drawRect(ObjectOrange, Offset(at.x - half, at.y - half), Size(2f * half, 2f * half))
                                    drawRect(
                                        Color.White, Offset(at.x - half, at.y - half), Size(2f * half, 2f * half),
                                        style = Stroke(1.dp.toPx())
                                    )
                                }
                                else -> {
                                    val color = if (marker.kind == MarkerKind.Person) PersonRed else MovingAmber
                                    val tip = toCanvas(
                                        (marker.forwardCm.toFloat() + marker.vxCmS),
                                        (marker.leftCm.toFloat() + marker.vyCmS), pxPerCm
                                    )
                                    drawLine(color, at, tip, strokeWidth = 2.5.dp.toPx(), cap = StrokeCap.Round)
                                    val r = (if (marker.kind == MarkerKind.Person) 8 else 6).dp.toPx()
                                    drawCircle(color, r, at)
                                    drawCircle(Color.White, r, at, style = Stroke(1.5.dp.toPx()))
                                }
                            }
                        }
                    }
                }
            }
    )
}

/** Robot frame to canvas: forward is up, the robot's left is the screen's left. */
private fun DrawScope.toCanvas(forwardCm: Float, leftCm: Float, pxPerCm: Float): Offset =
    Offset(center.x - leftCm * pxPerCm, center.y - forwardCm * pxPerCm)
