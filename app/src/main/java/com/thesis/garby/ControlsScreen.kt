package com.thesis.garby

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.ArrowDropDown
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Build
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.PanTool
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Replay
import androidx.compose.material.icons.rounded.Sensors
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.thesis.garby.performance.LocalPerformanceProfile
import com.thesis.garby.realtime.DeveloperRun
import com.thesis.garby.realtime.OperatorAction
import com.thesis.garby.realtime.OperatorStatus
import com.thesis.garby.realtime.RunDraftCheck
import com.thesis.garby.realtime.RunDraftRow
import com.thesis.garby.realtime.RunLimits
import com.thesis.garby.realtime.RunPlace
import com.thesis.garby.realtime.RunProgram
import com.thesis.garby.realtime.RunStep
import com.thesis.garby.realtime.checkRunDraft
import com.thesis.garby.realtime.decodeRunDraft
import com.thesis.garby.realtime.encodeRunDraft
import com.thesis.garby.realtime.isFresh
import com.thesis.garby.realtime.markerSummary
import com.thesis.garby.realtime.operatorBlockReason
import com.thesis.garby.realtime.operatorOutcomeText
import com.thesis.garby.realtime.requestFields
import com.thesis.garby.ui.glass.GlassButtonStyle
import com.thesis.garby.ui.glass.GlassDialog
import com.thesis.garby.ui.glass.RevealWhenLoaded
import com.thesis.garby.ui.glass.SkeletonCard
import com.thesis.garby.ui.glass.widthCap
import com.thesis.garby.ui.theme.GarbyColors
import com.thesis.garby.ui.theme.GarbyTheme
import com.thesis.garby.ui.withFloatingDockInset

/** Two sections for robot controls: Standard Operational Controls and Developer Bench Tests. */
private enum class ControlsSection(val label: String, val accent: Color, val icon: ImageVector) {
    Control("Controls", GarbyColors.EmberGlow, Icons.Rounded.Tune),
    Developer("Developer", GarbyColors.Rose, Icons.Rounded.Build)
}

/** Opaque text and substantial glass fills keep controls legible over either backdrop glow. */
private object ControlGlass {
    val Background = GarbyColors.Night
    val SurfaceTop = Color(0xFF41212B)
    val SurfaceBottom = Color(0xFF241116)
    val Inset = Color(0xFF211016)
    val ButtonTop = Color(0xFF63303D)
    val ButtonBottom = Color(0xFF402029)
    val Text = Color(0xFFF8FAFC)
    val Secondary = Color(0xFFF1D8DE)
    val Muted = Color(0xFFD8BAC2)
    val OnAccent = GarbyColors.Night
    val DisabledFill = Color(0xFF3A272E)
    val DisabledText = Color(0xFFDECAD0)
    val Enabled = GarbyColors.Ok
    val Attention = GarbyColors.Caution
    val Stop = GarbyColors.Crimson
}

private val LocalControlsSection = staticCompositionLocalOf { ControlsSection.Control }

/** What the operator is asked before an action that lets the robot move or restarts it. */
private data class Confirmation(val title: String, val body: String, val confirm: String)

/** The developer sensor selection, as the Pi's Developer window names it. */
private val SENSOR_GATES = listOf(1 to "LiDAR", 2 to "Gyro (MPU)", 4 to "Sonar")

/**
 * The question before an action that moves GARBY, restarts it or weakens a check: the same
 * questions as the Pi's own windows, with the current sensor selection and RunStart place.
 */
private fun confirmationFor(
    action: OperatorAction,
    sensorMask: Int,
    status: OperatorStatus?,
    runProgram: RunProgram? = null
): Confirmation? =
    when (action) {
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
                "wheels repeat their steps. Obstacle detection, the heading check and the emergency " +
                "stop remain active.",
            "Reverse"
        )
        OperatorAction.Reboot -> Confirmation(
            "Restart GARBY?",
            "Restarts the Raspberry Pi, the BLE bridge and the motor controller. The emergency stop " +
                "stays latched; afterwards release it and start again. This app loses GARBY for about a " +
                "minute. The separate bin/gas sensor board is not restarted.",
            "Restart"
        )
        OperatorAction.SonarOff -> Confirmation(
            "Disable the obstacle sonar?",
            "The low front sonar is the only sensor that sees chair legs, boxes and feet. With it off, " +
                "GARBY relies on the LiDAR at chest height. The servo stays at its default view.",
            "Disable"
        )
        OperatorAction.FrontReadOnlyOn, OperatorAction.BackReadOnlyOn -> Confirmation(
            "Set these LiDAR checks to read-only?",
            "The distances are still shown, but they no longer stop GARBY in that direction. Side " +
                "clearance, missing scans and the emergency stop still do. It is switched back on when " +
                "the robot software restarts.\n\n$READ_ONLY_ROUTE_NOTE",
            "Set read-only"
        )
        OperatorAction.SideReadOnlyOn -> Confirmation(
            "Set side LiDAR checks to read-only?",
            "The distances are still shown, but they no longer stop GARBY beside its body. Front and rear " +
                "clearance, the spin check, missing scans and the emergency stop still do. It is " +
                "switched back on when the robot software restarts.\n\n$READ_ONLY_ROUTE_NOTE",
            "Set read-only"
        )
        OperatorAction.DevRunStart -> runProgram?.let { program ->
            Confirmation(
                "Start this list?",
                program.steps.mapIndexed { index, step -> "${index + 1}. ${step.name} ${step.steps} steps" }
                    .joinToString("\n") +
                    "\n\nMax speed ${program.speed} steps/s, acceleration ${program.accel} steps/s².\n\n" +
                    "No LiDAR, sonar or gyro checks: GARBY drives exactly these steps. Keep its way " +
                    "clear. EMERGENCY STOP stops it at once.",
                "Start run"
            )
        }
        OperatorAction.DevRunContinue, OperatorAction.DevRunUndo -> runConfirmation(action, status)
        OperatorAction.DevRunClear -> Confirmation(
            "Discard the saved list?",
            "GARBY forgets where the list stopped, so Continue run and Reverse to start are no longer " +
                "possible for it. Nothing moves.",
            "Discard"
        )
        // Check sensors does not move; the Pi's window asks nothing for it either.
        OperatorAction.DevTestSensors -> null
        else -> if (action.isBenchTest) benchTestConfirmation(action, sensorMask) else null
    }

private const val READ_ONLY_ROUTE_NOTE =
    "Changing it while a route runs stops GARBY until you choose Continue run, Reverse to start " +
        "or Release stop."

private fun benchTestConfirmation(action: OperatorAction, sensorMask: Int): Confirmation {
    val obeys = SENSOR_GATES.filter { (sensorMask and it.first) != 0 }.joinToString { it.second }
    val ignores = SENSOR_GATES.filter { (sensorMask and it.first) == 0 }.joinToString { it.second }
    val floor = if (action.isTurnTest) {
        "GARBY must be on the floor with both wheels touching the ground. Do not lift the motors or " +
            "wheels. The MPU detects the robot turning; spinning lifted wheels cannot show rotation.\n\n"
    } else ""
    return Confirmation(
        "Start this test?",
        "${action.label}\n\n${floor}Obeys: ${obeys.ifEmpty { "nothing" }}\n" +
            "Ignores: ${ignores.ifEmpty { "nothing" }}\n\nGARBY will move briefly. Keep hands clear of " +
            "the wheels. EMERGENCY STOP still works.",
        "Start test"
    )
}

private fun runConfirmation(action: OperatorAction, status: OperatorStatus?): Confirmation {
    val run = status?.developerRun
    val continuing = action == OperatorAction.DevRunContinue
    val where = run?.takeIf { it.steps.isNotEmpty() }?.let { " (step ${it.step + 1} of ${it.steps.size})" }.orEmpty()
    return Confirmation(
        if (continuing) "Continue run?" else "Reverse to start?",
        (if (continuing) "Finish the RunStart list from where it stopped" else
            "Drive the RunStart list back to where it started") + "$where." +
            (if (status?.emergencyStopped == true) "\n\nThis releases the emergency stop." else "") +
            "\n\nNo LiDAR, sonar or gyro checks: GARBY drives exactly these steps, so keep its way " +
            "clear. EMERGENCY STOP stops it at once.",
        if (continuing) "Continue" else "Reverse"
    )
}

/**
 * The Controls screen divided into two sections:
 * - Controls: Operation, stop recovery, obstacle sensing (front/side/rear/sonar), system restart.
 * - Developer: sensor gates, bench tests, remote nudges and the RunStart list saved on the robot.
 */
@Composable
fun ControlsScreen(viewModel: OperatorViewModel) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    ControlsContent(state, viewModel::send, viewModel::clearNotice, onStartRun = viewModel::startRun)
}

@Composable
private fun ControlsContent(
    state: OperatorUiState,
    onSend: (OperatorAction, Int?, Boolean?) -> Unit,
    onDismissNotice: () -> Unit,
    initialSection: ControlsSection = ControlsSection.Control,
    onStartRun: (RunProgram) -> Unit = {}
) {
    var section by rememberSaveable { mutableStateOf(initialSection) }
    var confirming by rememberSaveable { mutableStateOf<OperatorAction?>(null) }
    var gateLidar by rememberSaveable { mutableStateOf(true) }
    var gateMpu by rememberSaveable { mutableStateOf(true) }
    var gateSonar by rememberSaveable { mutableStateOf(true) }
    var fastSpeed by rememberSaveable { mutableStateOf(false) }
    // The RunStart rows as typed ("F4000,L,R90"), max speed and acceleration: the Pi window's
    // fields, kept across rotation.
    var runDraft by rememberSaveable { mutableStateOf(encodeRunDraft(listOf(RunDraftRow()))) }
    var runSpeed by rememberSaveable { mutableStateOf(RunLimits.DEFAULT_SPEED.toString()) }
    var runAccel by rememberSaveable { mutableStateOf(RunLimits.DEFAULT_ACCEL.toString()) }
    val runRows = decodeRunDraft(runDraft)
    val runCheck = checkRunDraft(runRows, runSpeed, runAccel)
    val runProgram = (runCheck as? RunDraftCheck.Ready)?.program

    val sensorMask = (if (gateLidar) 1 else 0) or (if (gateMpu) 2 else 0) or (if (gateSonar) 4 else 0)
    val status = state.status
    val fresh = status.isFresh(state.nowMs)
    val (subtitle, subtitleColor) = when {
        !state.online -> "This phone is offline" to GarbyColors.Warning
        !state.statusLoaded -> "Connecting" to ControlGlass.Muted
        !state.cloudConnected -> "Cloud link unavailable" to ControlGlass.Attention
        !fresh -> "GARBY's Pi is not reporting" to GarbyColors.Warning
        status?.connected != true -> "Pi running · no link to the controllers" to GarbyColors.Warning
        !status.supported -> "Waiting for MCU status" to ControlGlass.Attention
        else -> "Pi and controllers connected" to GarbyColors.Ok
    }

    // Only bench tests carry the sensor selection and only nudges Normal/Fast.
    fun send(action: OperatorAction) {
        val (sensors, fast) = requestFields(action, sensorMask, fastSpeed)
        onSend(action, sensors, fast)
    }

    fun request(action: OperatorAction) {
        if (confirmationFor(action, sensorMask, status, runProgram) != null) confirming = action
        else if (action != OperatorAction.DevRunStart) send(action)
    }

    CompositionLocalProvider(LocalControlsSection provides section) {
        ControlsGlassFrame(
            section = section, subtitle = subtitle, subtitleColor = subtitleColor
        ) { padding ->
            Column(
                Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    // Scroll beneath the floating header/dock; their clearance
                    // belongs to the content rather than the viewport bounds.
                    .padding(padding)
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                ControlsSectionPicker(section) { section = it }
                SectionIntro(section)

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
                        val outcome = state.notice ?: operatorOutcomeText(state.command, state.nowMs)
                        if (outcome != null) {
                            ControlPanel(onClick = onDismissNotice, onClickLabel = "Dismiss") {
                                Text("Last request", style = MaterialTheme.typography.labelLarge, color = ControlGlass.Muted)
                                Spacer(Modifier.height(4.dp))
                                Text(outcome, style = MaterialTheme.typography.bodyLarge, color = ControlGlass.Text)
                            }
                        }

                        if (section == ControlsSection.Control) {
                            // Operational Controls Section
                            StatePanel(status, fresh, state.nowMs)

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
                                    title = "Side LiDAR checks",
                                    value = status?.sideReadOnly?.not().takeIf { fresh },
                                    onText = "Stopping for obstacles beside body",
                                    offText = "Read-only: shown, not stopping",
                                    turnOn = OperatorAction.SideReadOnlyOff, turnOff = OperatorAction.SideReadOnlyOn,
                                    state = state, onRequest = ::request
                                )
                                ToggleRow(
                                    title = "Rear LiDAR checks",
                                    value = status?.backReadOnly?.not().takeIf { fresh },
                                    onText = "Stopping for what is behind when backing and turning",
                                    offText = "Read-only: shown, not stopping",
                                    turnOn = OperatorAction.BackReadOnlyOff, turnOff = OperatorAction.BackReadOnlyOn,
                                    state = state, onRequest = ::request
                                )
                                Hint("Read-only keeps the distances visible and stops that group holding GARBY. " +
                                    "Missing or stale scans still stop it. $READ_ONLY_ROUTE_NOTE")
                            }

                            ControlGroup("System") {
                                ActionRow(OperatorAction.Reboot, state, ::request, primary = false)
                                Hint("Manual disposal is on Home; the return to base is on Activity.")
                                if (fresh && status != null && status.software.isNotBlank()) {
                                    Hint("Robot software ${status.software}, running ${status.launcherMode}.")
                                }
                                if (state.uid.isNotBlank()) {
                                    Hint("This phone's ID, for GARBY's operator list: ${state.uid}")
                                }
                            }
                        } else {
                            // Developer: the Pi's Developer window (bench tests, remote control,
                            // RunStart), behind the same Developer readiness it uses.
                            val developerPending = fresh && status?.developerPending == true
                            DeveloperStatusPanel(status, fresh)

                            ControlGroup("1. Sensors GARBY obeys during a test") {
                                FlowRow(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    verticalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    SensorGateChip("LiDAR", gateLidar, enabled = !developerPending) { gateLidar = !gateLidar }
                                    SensorGateChip("Gyro (MPU)", gateMpu, enabled = !developerPending) { gateMpu = !gateMpu }
                                    SensorGateChip("Sonar", gateSonar, enabled = !developerPending) { gateSonar = !gateSonar }
                                }
                                Hint("LiDAR stops it if something is close. The gyro holds heading; turns need it. " +
                                    "The sonar stops it if something is in front. Untick one to test without it; " +
                                    "all are back on after the test.")
                            }

                            ControlGroup("2. Bench tests (90°: 30 s; others: 8 s)") {
                                val turnBlock = if (gateMpu) null else "Turns need the gyro (MPU) ticked."
                                listOf(
                                    OperatorAction.DevTestF to OperatorAction.DevTestB,
                                    OperatorAction.DevTestM1 to OperatorAction.DevTestM2,
                                    OperatorAction.DevTestL to OperatorAction.DevTestR,
                                    OperatorAction.DevTestL90 to OperatorAction.DevTestR90
                                ).forEach { (left, right) ->
                                    ActionButtons(listOf(left, right), state, ::request,
                                        extraBlocks = { action -> turnBlock.takeIf { action.isTurnTest } },
                                        showReasons = false, minHeight = 68.dp)
                                }
                                ActionRow(OperatorAction.DevTestSensors, state, ::request, primary = false,
                                    showReason = false)
                                val testBlock = blockReason(OperatorAction.DevTestF, state)
                                if (testBlock != null) Hint("Not available: $testBlock")
                                Hint(turnBlock ?: "Turns: GARBY must be on the floor, wheels down. The MPU " +
                                    "measures the robot turning.")
                                ActionRow(OperatorAction.DevCancel, state, ::request, primary = false)
                            }

                            ControlGroup("3. Remote nudge") {
                                Text("Speed", style = MaterialTheme.typography.labelLarge, color = ControlGlass.Secondary)
                                FlowRow(
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    verticalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    SensorGateChip("Normal · 30 cm/s", !fastSpeed, choice = true) { fastSpeed = false }
                                    SensorGateChip("Fast · 60 cm/s", fastSpeed, choice = true) { fastSpeed = true }
                                }
                                val nudgeBlock = blockReason(OperatorAction.DevHoldW, state)
                                val stopBlock = blockReason(OperatorAction.DevHoldStop, state)
                                // Five identical cells, as the Pi window's pad: the same width and
                                // height whatever the label or lock icon.
                                val pad = listOf(
                                    listOf(null, OperatorAction.DevHoldW, null),
                                    listOf(OperatorAction.DevHoldA, OperatorAction.DevHoldStop, OperatorAction.DevHoldD),
                                    listOf(null, OperatorAction.DevHoldS, null)
                                )
                                Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                    pad.forEach { cells ->
                                        Row(
                                            Modifier.fillMaxWidth().height(IntrinsicSize.Min),
                                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                                        ) {
                                            cells.forEach { action ->
                                                if (action == null) {
                                                    Spacer(Modifier.weight(1f))
                                                } else {
                                                    val stop = action == OperatorAction.DevHoldStop
                                                    ControlButton(
                                                        text = NUDGE_LABELS.getValue(action).second,
                                                        glyph = NUDGE_LABELS.getValue(action).first,
                                                        onClick = { request(action) },
                                                        enabled = (if (stop) stopBlock else nudgeBlock) == null,
                                                        loading = state.sending == action,
                                                        style = GlassButtonStyle.Secondary,
                                                        danger = stop,
                                                        minHeight = 76.dp,
                                                        modifier = Modifier.weight(1f).fillMaxHeight()
                                                    )
                                                }
                                            }
                                        }
                                    }
                                }
                                if (nudgeBlock != null) Hint("Not available: $nudgeBlock")
                                Hint("Each tap drives about 0.6 s, then GARBY stops by itself: about 18 cm at " +
                                    "Normal, 36 cm at Fast. Left and right spin in place. No LiDAR, sonar or " +
                                    "gyro checks: GARBY goes where you point it, so watch it. A tap reaches " +
                                    "GARBY a second or two later. Stop or EMERGENCY STOP ends a nudge early.")
                            }

                            ControlGroup("4. RunStart list") {
                                val run = status?.developerRun.takeIf { fresh }
                                RunStartSummary(run, stopped = fresh && status?.emergencyStopped == true, fresh = fresh)
                                ActionButtons(listOf(OperatorAction.DevRunContinue, OperatorAction.DevRunUndo),
                                    state, ::request, primary = OperatorAction.DevRunContinue, labels = RUN_LABELS)
                                ActionRow(OperatorAction.DevRunClear, state, ::request, primary = false,
                                    text = RUN_LABELS.getValue(OperatorAction.DevRunClear))
                                if (run != null && (run.running || run.waitingRelease)) {
                                    ActionRow(OperatorAction.DevCancel, state, ::request, primary = false)
                                }

                                // The Pi window's rows: what each step does and how many steps.
                                val locked = run != null && (run.running || run.waitingRelease || run.place == RunPlace.Partway)
                                val editable = !locked && !developerPending
                                Spacer(Modifier.height(2.dp))
                                Text("New list", style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.SemiBold, color = ControlGlass.Text)
                                runRows.forEachIndexed { index, row ->
                                    RunStepRow(
                                        number = index + 1,
                                        row = row,
                                        enabled = editable,
                                        canDelete = editable && runRows.size > 1,
                                        onChange = { changed ->
                                            runDraft = encodeRunDraft(runRows.toMutableList().also { it[index] = changed })
                                        },
                                        onDelete = {
                                            runDraft = encodeRunDraft(runRows.toMutableList().also { it.removeAt(index) })
                                        }
                                    )
                                }
                                Row(
                                    Modifier.fillMaxWidth().height(IntrinsicSize.Min),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    ControlButton(
                                        text = "Add step",
                                        icon = Icons.Rounded.Add,
                                        onClick = { runDraft = encodeRunDraft(runRows + RunDraftRow()) },
                                        enabled = editable && runRows.size < RunLimits.MAX_STEPS,
                                        style = GlassButtonStyle.Secondary,
                                        modifier = Modifier.weight(1f).fillMaxHeight()
                                    )
                                    if (run != null && run.hasSaved) {
                                        ControlButton(
                                            text = "Copy saved list",
                                            onClick = {
                                                runDraft = encodeRunDraft(run.steps.map { RunDraftRow(it.op, it.steps.toString()) })
                                                run.speed?.let { runSpeed = it.toString() }
                                                run.accel?.let { runAccel = it.toString() }
                                            },
                                            enabled = editable,
                                            style = GlassButtonStyle.Secondary,
                                            modifier = Modifier.weight(1f).fillMaxHeight()
                                        )
                                    }
                                }
                                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    NumberField(runSpeed, { runSpeed = it }, "Max speed", editable,
                                        Modifier.weight(1f), maxDigits = 4)
                                    NumberField(runAccel, { runAccel = it }, "Acceleration", editable,
                                        Modifier.weight(1f), maxDigits = 5, last = true)
                                }
                                Hint("Max speed ${RunLimits.MIN_SPEED}–${RunLimits.MAX_SPEED} steps/s, acceleration " +
                                    "${RunLimits.MIN_ACCEL}–${RunLimits.MAX_ACCEL} steps/s². Minimum setting: about 30 cm/s; " +
                                    "ramps and turn braking can be slower. Up to ${RunLimits.MAX_STEPS} steps of " +
                                    "1–${RunLimits.MAX_STEP}.")
                                if (locked) {
                                    Hint("The rows are locked while the saved list moves or stands part-way: " +
                                        "Continue run and Reverse to start act on exactly that list. Discard saved unlocks them.")
                                }
                                ActionRow(OperatorAction.DevRunStart, state, ::request, primary = true,
                                    text = RUN_LABELS.getValue(OperatorAction.DevRunStart),
                                    extraBlock = (runCheck as? RunDraftCheck.Problem)?.reason)
                                Hint("No LiDAR, sonar or gyro checks: GARBY drives exactly these steps, so keep its " +
                                    "way clear. EMERGENCY STOP stops it at once; then Continue run finishes the list " +
                                    "or Reverse to start takes it back. Stop test also stops a moving list, which " +
                                    "keeps its place. Turns spin in place; Bump calibrate goes forward in 100-step bumps.")
                            }
                        }
                    }
                }
            }
        }

        confirming?.let { action ->
            val text = confirmationFor(action, sensorMask, status, runProgram)
            if (text == null) {
                confirming = null
            } else {
                GlassDialog(onDismissRequest = { confirming = null }) {
                    Text(text.title, style = MaterialTheme.typography.headlineSmall, color = ControlGlass.Text)
                    Spacer(Modifier.height(10.dp))
                    // A 20-step list is long: the body scrolls, the buttons stay in view.
                    Text(text.body, style = MaterialTheme.typography.bodyMedium, color = ControlGlass.Secondary,
                        modifier = Modifier.heightIn(max = 380.dp).verticalScroll(rememberScrollState()))
                    Spacer(Modifier.height(22.dp))
                    Row(Modifier.height(IntrinsicSize.Min), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        ControlButton(
                            text = "Cancel", onClick = { confirming = null },
                            style = GlassButtonStyle.Secondary, modifier = Modifier.weight(1f).fillMaxHeight()
                        )
                        ControlButton(
                            text = text.confirm,
                            onClick = {
                                confirming = null
                                if (action == OperatorAction.DevRunStart) runProgram?.let(onStartRun)
                                else send(action)
                            },
                            modifier = Modifier.weight(1f).fillMaxHeight()
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun SensorGateChip(
    label: String,
    checked: Boolean,
    choice: Boolean = false,
    enabled: Boolean = true,
    onToggle: () -> Unit
) {
    val accent = LocalControlsSection.current.accent
    val color = if (choice) accent else if (checked) ControlGlass.Enabled else ControlGlass.Attention
    val shape = RoundedCornerShape(16.dp)
    val selection = if (choice) Modifier.selectable(
        selected = checked, enabled = enabled, role = Role.RadioButton, onClick = onToggle
    ) else Modifier.toggleable(value = checked, enabled = enabled, role = Role.Checkbox,
        onValueChange = { onToggle() })
    Row(
        modifier = Modifier
            .heightIn(min = 50.dp)
            .clip(shape)
            .graphicsLayer { alpha = if (enabled) 1f else 0.6f }
            .background(if (checked) color.copy(alpha = 0.16f) else ControlGlass.Inset)
            .border(1.5.dp, color.copy(alpha = if (checked) 0.9f else 0.65f), shape)
            .then(selection)
            .semantics { stateDescription = if (choice) {
                if (checked) "Selected" else "Not selected"
            } else if (checked) "Sensor check on" else "Sensor check off" }
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Icon(
            if (checked) Icons.Rounded.Check else Icons.Rounded.Close,
            contentDescription = null, tint = color, modifier = Modifier.size(18.dp)
        )
        Text(label, style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.SemiBold, color = ControlGlass.Text)
        if (!choice) {
            Text(if (checked) "ON" else "OFF", style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Bold, color = color)
        }
    }
}

@Composable
private fun DeveloperStatusPanel(status: OperatorStatus?, fresh: Boolean) {
    ControlPanel {
        ControlPanelHeading("Bench status", Icons.Rounded.Build)
        Spacer(Modifier.height(6.dp))
        val controllersOnline = fresh && status?.connected == true && status.supported
        StateBadge(if (controllersOnline) "Controllers online" else "Waiting for controllers",
            if (controllersOnline) ControlGlass.Enabled else ControlGlass.Attention)
        Spacer(Modifier.height(8.dp))
        if (status == null || !fresh) {
            Text(
                "Waiting for robot connection. Developer tests require a live link to GARBY.",
                style = MaterialTheme.typography.bodyMedium, color = GarbyColors.Warning
            )
        } else {
            // The same first line as the Pi's Developer window: can a test start, and if not, why.
            val (banner, color) = when {
                status.developerPending ->
                    "▶ " + status.developerMessage.ifBlank { "Test running…" } to GarbyColors.Warning
                status.developerEnabled ->
                    "✔ Ready. Choose the sensors, then press a test." to GarbyColors.Ok
                else -> "Not available: " +
                    status.developerBlocked.ifBlank { "waiting for the robot…" } to GarbyColors.Warning
            }
            Text(banner, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.SemiBold, color = color)
            Spacer(Modifier.height(6.dp))
            if (!status.developerPending && status.developerMessage.isNotBlank()) {
                InfoLine("Last test", status.developerMessage, ControlGlass.Text)
            }
            if (status.developerResult.isNotBlank()) InfoLine("Result", status.developerResult, ControlGlass.Secondary)
            val stateText = status.robotState.replace('_', ' ').lowercase()
                .replaceFirstChar { it.uppercase() }.ifBlank { "Unknown" }
            InfoLine("Robot state", stateText, ControlGlass.Text)
            Spacer(Modifier.height(4.dp))
            if (status.developerRun == null) {
                Hint("GARBY's robot software predates phone developer status (PHONEDEV-20261118): " +
                    "reasons, results and RunStart are missing until it is updated.")
            }
            Hint("Developer tests leave out the route rules for bench checks. EMERGENCY STOP, under " +
                "every screen, stops any test at once.")
        }
    }
}

/** The saved RunStart list as the Pi's RunStart tab shows it: steps, marks and place. */
@Composable
private fun RunStartSummary(run: DeveloperRun?, stopped: Boolean, fresh: Boolean) {
    if (run == null) {
        // Only a reporting Pi without the field means old robot software.
        Text(if (!fresh) "Waiting for GARBY's Pi to report its saved list."
            else "GARBY's robot software does not report RunStart. Update it to PHONEDEV-20261118 or newer.",
            style = MaterialTheme.typography.bodyMedium, color = GarbyColors.Warning)
        return
    }
    val total = run.steps.size
    val (text, color) = when {
        !run.known -> "Waiting for the robot to report its saved list…" to ControlGlass.Muted
        run.running -> "▶ " + (if (run.mode == "U") "Reversing" else "Running") +
            (run.runningStep?.let { ": step ${it + 1} of $total" } ?: "…") +
            ". EMERGENCY STOP stops it at once." to GarbyColors.Warning
        run.waitingRelease -> "▶ " + run.message.ifBlank { "Releasing the emergency stop first…" } to
            GarbyColors.Warning
        !run.hasSaved -> "No saved list." to ControlGlass.Muted
        run.place == RunPlace.Partway -> {
            val of = run.steps.getOrNull(run.step)?.steps
            "Stopped at step ${run.step + 1} of $total" +
                (if (run.done != null && of != null) " (${run.done} of $of steps)" else "") +
                ". Continue run finishes it; Reverse to start takes it back." +
                (if (stopped) " Either one releases the emergency stop first." else "") to GarbyColors.Warning
        }
        run.place == RunPlace.End -> "The list finished. Reverse to start can take it back." to GarbyColors.Ok
        else -> "Saved list, at its start. Continue run drives it." to GarbyColors.Ok
    }
    Text(text, style = MaterialTheme.typography.bodyMedium, color = color)
    if (run.message.isNotBlank() && !run.running && !run.waitingRelease) {
        InfoLine("Last", run.message, ControlGlass.Secondary)
    }
    if (!run.hasSaved && !run.running) return
    Column(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(ControlGlass.Inset)
            .padding(horizontal = 12.dp, vertical = 8.dp)
    ) {
        run.steps.forEachIndexed { index, step ->
            val now = run.runningStep
            val mark = when {
                run.running && now != null -> when {
                    index < now -> "✔ done"
                    index == now -> "▶ now"
                    run.mode == "U" -> "↩ undone"
                    else -> ""
                }
                run.running -> ""
                run.place == RunPlace.End -> "✔ done"
                run.place == RunPlace.Partway -> when {
                    index < run.step -> "✔ done"
                    index == run.step -> "■ ${run.done ?: 0}/${step.steps}"
                    else -> ""
                }
                else -> ""
            }
            Row(Modifier.fillMaxWidth().padding(vertical = 3.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("${index + 1}. ${step.name} ${step.steps} steps", style = MaterialTheme.typography.bodyMedium,
                    color = ControlGlass.Text, modifier = Modifier.weight(1f))
                if (mark.isNotEmpty()) {
                    Text(mark, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold,
                        color = if (mark.startsWith("✔")) GarbyColors.Ok else GarbyColors.Warning)
                }
            }
        }
    }
    if (run.speed != null && run.accel != null) {
        Hint("Max speed ${run.speed} steps/s · acceleration ${run.accel} steps/s²")
    }
}

@Composable
private fun StatePanel(status: OperatorStatus?, fresh: Boolean, nowMs: Long) {
    ControlPanel {
        ControlPanelHeading("GARBY now", Icons.Rounded.Sensors)
        Spacer(Modifier.height(6.dp))
        val controllersOnline = fresh && status?.connected == true && status.supported
        StateBadge(if (controllersOnline) "Controllers online" else "Waiting for controllers",
            if (controllersOnline) ControlGlass.Enabled else ControlGlass.Attention)
        Spacer(Modifier.height(8.dp))
        if (status == null || !fresh) {
            Text(
                if (status == null) "GARBY has not reported yet. Check the Raspberry Pi's power and internet connection."
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
            InfoLine("State", stateText, ControlGlass.Text)
            InfoLine("Emergency stop", stop.first, stop.second)
            if (status.routeLeg.isNotBlank()) InfoLine("Route step", status.routeLeg, ControlGlass.Secondary)
            if (status.motionHold.isNotBlank()) InfoLine("Held by", status.motionHold, GarbyColors.Warning)
            if (status.zones.isNotBlank()) InfoLine("Zones", status.zones.replace(",", ", "), ControlGlass.Secondary)
            val seen = markerSummary(status.people, 0, status.objects)
            if (seen != null) InfoLine("In view", seen, ControlGlass.Secondary)
            if (status.message.isNotBlank()) InfoLine("Controllers", status.message, ControlGlass.Muted)
        }
    }
}

@Composable
private fun InfoLine(label: String, value: String, color: Color) {
    Row(Modifier.fillMaxWidth().padding(vertical = 3.dp)) {
        Text(label, style = MaterialTheme.typography.bodyMedium, color = ControlGlass.Muted, modifier = Modifier.weight(0.38f))
        Text(value, style = MaterialTheme.typography.bodyMedium, color = color, modifier = Modifier.weight(0.62f))
    }
}

@Composable
private fun ControlGroup(title: String, content: @Composable () -> Unit) {
    ControlPanel {
        ControlPanelHeading(title, when {
            title.contains("sens", ignoreCase = true) -> Icons.Rounded.Sensors
            title.startsWith("System") -> Icons.Rounded.Refresh
            else -> LocalControlsSection.current.icon
        })
        Spacer(Modifier.height(14.dp))
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) { content() }
    }
}

@Composable
private fun Hint(text: String) {
    Text(text, style = MaterialTheme.typography.bodySmall, color = ControlGlass.Muted)
}

/** One action button with the reason underneath when it is not available. */
@Composable
private fun ActionRow(
    action: OperatorAction,
    state: OperatorUiState,
    onRequest: (OperatorAction) -> Unit,
    primary: Boolean,
    /** A reason from this screen's own choices, e.g. a turn without the gyro selected. */
    extraBlock: String? = null,
    /** False where one shared reason is shown for a whole grid of buttons instead. */
    showReason: Boolean = true,
    text: String = action.label
) {
    val blocked = blockReason(action, state) ?: extraBlock
    Column {
        ActionButton(action, state, onRequest, primary, blocked, text, Modifier.fillMaxWidth())
        if (blocked != null && state.sending != action && showReason) {
            Spacer(Modifier.height(4.dp))
            Hint(blocked)
        }
    }
}

/**
 * Buttons side by side, all the same height whatever their labels wrap to, with the reasons
 * they are unavailable shown once underneath (once when they share it).
 */
@Composable
private fun ActionButtons(
    actions: List<OperatorAction>,
    state: OperatorUiState,
    onRequest: (OperatorAction) -> Unit,
    primary: OperatorAction? = null,
    labels: Map<OperatorAction, String> = emptyMap(),
    extraBlocks: (OperatorAction) -> String? = { null },
    showReasons: Boolean = true,
    minHeight: Dp = 56.dp
) {
    val reasons = actions.map { blockReason(it, state) ?: extraBlocks(it) }
    Column {
        Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            actions.forEachIndexed { index, action ->
                ActionButton(action, state, onRequest, action == primary, reasons[index],
                    labels[action] ?: action.label, Modifier.weight(1f).fillMaxHeight(), minHeight)
            }
        }
        if (showReasons) {
            val shown = actions.indices.filter { reasons[it] != null && state.sending != actions[it] }
            val distinct = shown.mapNotNull { reasons[it] }.distinct()
            if (distinct.size == 1) {
                Spacer(Modifier.height(4.dp))
                Hint(distinct.single())
            } else if (distinct.size > 1) {
                shown.forEach { index ->
                    Spacer(Modifier.height(4.dp))
                    Hint("${labels[actions[index]] ?: actions[index].label}: ${reasons[index]}")
                }
            }
        }
    }
}

@Composable
private fun ActionButton(
    action: OperatorAction,
    state: OperatorUiState,
    onRequest: (OperatorAction) -> Unit,
    primary: Boolean,
    blocked: String?,
    text: String,
    modifier: Modifier,
    minHeight: Dp = 56.dp
) {
    ControlButton(
        text = text,
        onClick = { onRequest(action) },
        enabled = blocked == null,
        loading = state.sending == action,
        style = if (primary) GlassButtonStyle.Primary else GlassButtonStyle.Secondary,
        danger = action == OperatorAction.DevCancel,
        icon = when (action) {
            OperatorAction.Reboot -> Icons.Rounded.Refresh
            OperatorAction.Release -> Icons.Rounded.PanTool
            OperatorAction.Reverse, OperatorAction.DevRunUndo -> Icons.Rounded.Replay
            OperatorAction.DevRunClear -> Icons.Rounded.Delete
            OperatorAction.SonarOn, OperatorAction.SonarOff, OperatorAction.DevTestSensors -> Icons.Rounded.Sensors
            else -> if (action.isDeveloperAction) Icons.Rounded.Build else Icons.Rounded.PlayArrow
        },
        minHeight = minHeight,
        modifier = modifier
    )
}

private fun blockReason(action: OperatorAction, state: OperatorUiState): String? =
    operatorBlockReason(action, state.status, state.nowMs, state.online, state.sending != null,
        cloudConnected = state.cloudConnected)

/** The Pi window's own names for the RunStart buttons. */
private val RUN_LABELS = mapOf(
    OperatorAction.DevRunStart to "Start run",
    OperatorAction.DevRunContinue to "Continue run",
    OperatorAction.DevRunUndo to "Reverse to start",
    OperatorAction.DevRunClear to "Discard saved"
)

/** The pad's glyph and word for each nudge, as on the Pi window's arrows. */
private val NUDGE_LABELS = mapOf(
    OperatorAction.DevHoldW to ("▲" to "Forward"),
    OperatorAction.DevHoldA to ("◀" to "Left"),
    OperatorAction.DevHoldStop to ("■" to "Stop"),
    OperatorAction.DevHoldD to ("▶" to "Right"),
    OperatorAction.DevHoldS to ("▼" to "Backward")
)

/** One RunStart row: number, what it does, how many steps, and the bin. */
@Composable
private fun RunStepRow(
    number: Int,
    row: RunDraftRow,
    enabled: Boolean,
    canDelete: Boolean,
    onChange: (RunDraftRow) -> Unit,
    onDelete: () -> Unit
) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        Text("$number.", style = MaterialTheme.typography.labelLarge, color = ControlGlass.Muted,
            modifier = Modifier.width(26.dp))
        RunOpPicker(row.op, enabled, { onChange(row.copy(op = it)) }, Modifier.weight(1f))
        NumberField(row.steps, { onChange(row.copy(steps = it)) }, "Steps", enabled,
            Modifier.width(112.dp), maxDigits = 6)
        IconButton(onClick = onDelete, enabled = canDelete) {
            Icon(Icons.Rounded.Delete, contentDescription = "Delete step $number",
                tint = if (canDelete) GarbyColors.Danger else ControlGlass.DisabledText.copy(alpha = 0.5f))
        }
    }
}

@Composable
private fun RunOpPicker(op: Char, enabled: Boolean, onPick: (Char) -> Unit, modifier: Modifier) {
    var open by remember { mutableStateOf(false) }
    val shape = RoundedCornerShape(14.dp)
    Box(modifier) {
        Row(
            Modifier.fillMaxWidth().heightIn(min = 56.dp).clip(shape)
                .background(if (enabled) ControlGlass.Inset else ControlGlass.DisabledFill)
                .border(1.dp, if (enabled) Color(0xFF986372) else Color(0xFF5A3A44), shape)
                .clickable(enabled = enabled, role = Role.DropdownList, onClickLabel = "Choose what this step does") {
                    open = true
                }
                .padding(start = 12.dp, end = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(RunStep(op, 1).name, style = MaterialTheme.typography.bodyMedium,
                color = if (enabled) ControlGlass.Text else ControlGlass.DisabledText, modifier = Modifier.weight(1f))
            Icon(Icons.Rounded.ArrowDropDown, contentDescription = null,
                tint = if (enabled) ControlGlass.Secondary else ControlGlass.DisabledText)
        }
        DropdownMenu(expanded = open, onDismissRequest = { open = false },
            containerColor = ControlGlass.SurfaceBottom) {
            RunLimits.OPS.forEach { choice ->
                DropdownMenuItem(
                    text = { Text(RunStep(choice, 1).name, color = ControlGlass.Text) },
                    onClick = { onPick(choice); open = false }
                )
            }
        }
    }
}

/** A digits-only field in the controls' glass colours. */
@Composable
private fun NumberField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    enabled: Boolean,
    modifier: Modifier,
    maxDigits: Int,
    last: Boolean = false
) {
    val accent = LocalControlsSection.current.accent
    OutlinedTextField(
        value = value,
        onValueChange = { typed -> onValueChange(typed.filter { it in '0'..'9' }.take(maxDigits)) },
        modifier = modifier,
        enabled = enabled,
        singleLine = true,
        label = { Text(label, maxLines = 1) },
        textStyle = MaterialTheme.typography.bodyLarge,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number,
            imeAction = if (last) ImeAction.Done else ImeAction.Next),
        shape = RoundedCornerShape(14.dp),
        colors = OutlinedTextFieldDefaults.colors(
            focusedTextColor = ControlGlass.Text,
            unfocusedTextColor = ControlGlass.Text,
            disabledTextColor = ControlGlass.DisabledText,
            focusedContainerColor = ControlGlass.Inset,
            unfocusedContainerColor = ControlGlass.Inset,
            disabledContainerColor = ControlGlass.DisabledFill,
            cursorColor = accent,
            focusedBorderColor = accent,
            unfocusedBorderColor = Color(0xFF986372),
            disabledBorderColor = Color(0xFF5A3A44),
            focusedLabelColor = accent,
            unfocusedLabelColor = ControlGlass.Muted,
            disabledLabelColor = ControlGlass.DisabledText
        )
    )
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
    Column(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp))
            .background(ControlGlass.Inset)
            .border(1.dp, Color(0xFF986372), RoundedCornerShape(20.dp))
            .padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Text(title, style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold, color = ControlGlass.Text)
            StateBadge(
                when (value) { true -> "CHECKS ON"; false -> if (turnOn == OperatorAction.SonarOn) "OFF" else "READ ONLY"; null -> "UNKNOWN" },
                when (value) { true -> ControlGlass.Enabled; else -> ControlGlass.Attention }
            )
        }
        Text(
            when (value) { true -> onText; false -> offText; null -> "Waiting for controller confirmation" },
            style = MaterialTheme.typography.bodySmall, color = ControlGlass.Secondary
        )
        if (value != null) {
            ActionRow(if (value) turnOff else turnOn, state, onRequest, primary = false)
        }
    }
}

/** Original GARBY red/rose glass, with cached light and no bitmap or live-blur allocation. */
@Composable
private fun ControlsGlassFrame(
    section: ControlsSection,
    subtitle: String,
    subtitleColor: Color,
    content: @Composable (PaddingValues) -> Unit
) {
    Box(Modifier.fillMaxSize().background(ControlGlass.Background)) {
        Box(Modifier.fillMaxSize().drawWithCache {
            val upper = Brush.radialGradient(
                listOf(GarbyColors.Crimson.copy(alpha = 0.75f), Color.Transparent),
                center = Offset(size.width * 0.08f, size.height * 0.12f),
                radius = size.maxDimension * 0.65f
            )
            val lower = Brush.radialGradient(
                listOf(GarbyColors.Rose.copy(alpha = 0.14f), Color.Transparent),
                center = Offset(size.width * 0.96f, size.height * 0.70f),
                radius = size.maxDimension * 0.6f
            )
            onDrawBehind { drawRect(upper); drawRect(lower) }
        })
        Scaffold(
            containerColor = Color.Transparent,
            contentColor = ControlGlass.Text,
            contentWindowInsets = WindowInsets.safeDrawing,
            topBar = {
                Box(Modifier.fillMaxWidth()
                    .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Top + WindowInsetsSides.Horizontal))
                    .padding(horizontal = 12.dp, vertical = 8.dp)) {
                    ControlPanel(modifier = Modifier.fillMaxWidth(), contentPadding = PaddingValues(16.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            Icon(section.icon, contentDescription = null, tint = section.accent,
                                modifier = Modifier.size(26.dp))
                            Column(Modifier.weight(1f)) {
                                Text(section.label, style = MaterialTheme.typography.titleLarge,
                                    fontWeight = FontWeight.Bold, color = ControlGlass.Text,
                                    modifier = Modifier.semantics { heading() })
                                Text(subtitle, style = MaterialTheme.typography.labelMedium, color = subtitleColor)
                            }
                        }
                    }
                }
            },
            content = { padding -> content(padding.withFloatingDockInset()) }
        )
    }
}

@Composable
private fun ControlsSectionPicker(selected: ControlsSection, onSelect: (ControlsSection) -> Unit) {
    Row(Modifier.widthCap().fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        ControlsSection.entries.forEach { section ->
            val active = selected == section
            val shape = RoundedCornerShape(22.dp)
            val ink = if (active) ControlGlass.OnAccent else ControlGlass.Text
            Column(
                Modifier.weight(1f).heightIn(min = 82.dp)
                    .liquidSurface(shape, if (active) listOf(section.accent, section.accent)
                        else listOf(ControlGlass.SurfaceTop, ControlGlass.SurfaceBottom))
                    .selectable(selected = active, role = Role.Tab, onClick = { onSelect(section) })
                    .padding(14.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Icon(section.icon, contentDescription = null, tint = ink, modifier = Modifier.size(22.dp))
                    Spacer(Modifier.weight(1f))
                    if (active) Icon(Icons.Rounded.Check, contentDescription = "Selected", tint = ink,
                        modifier = Modifier.size(18.dp))
                }
                Text(section.label, style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold, color = ink)
            }
        }
    }
}

@Composable
private fun SectionIntro(section: ControlsSection) {
    Column(Modifier.widthCap().fillMaxWidth().padding(horizontal = 4.dp, vertical = 4.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(if (section == ControlsSection.Control) "Robot controls" else "Developer tools",
            style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold,
            color = ControlGlass.Text)
        Text(if (section == ControlsSection.Control) "Recovery, obstacle sensing and system settings"
            else "Bench tests, remote nudges and the RunStart list, as in the Pi's Developer window",
            style = MaterialTheme.typography.bodyMedium, color = ControlGlass.Secondary)
    }
}

/** Bright rim and a fixed specular reflection suggest liquid glass without an animated shader. */
private fun Modifier.liquidSurface(shape: Shape, colors: List<Color>): Modifier =
    clip(shape)
        .background(Brush.linearGradient(colors))
        .drawWithCache {
            val reflection = Brush.linearGradient(
                0f to Color.White.copy(alpha = 0.12f),
                0.38f to Color.White.copy(alpha = 0.025f),
                0.60f to Color.Transparent,
                1f to Color.White.copy(alpha = 0.025f),
                start = Offset.Zero, end = Offset(size.width, size.height)
            )
            onDrawBehind { drawRect(reflection) }
        }
        .border(1.2.dp, Brush.linearGradient(listOf(
            Color.White.copy(alpha = 0.65f), Color.White.copy(alpha = 0.18f), Color.White.copy(alpha = 0.32f)
        )), shape)

@Composable
private fun ControlPanel(
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues(18.dp),
    onClick: (() -> Unit)? = null,
    onClickLabel: String? = null,
    content: @Composable ColumnScope.() -> Unit
) {
    val shape = RoundedCornerShape(26.dp)
    val reduced = LocalPerformanceProfile.current.reducedEffects
    val interaction = if (onClick != null) Modifier.clickable(
        role = Role.Button, onClickLabel = onClickLabel, onClick = onClick
    ) else Modifier
    Column(
        modifier.then(if (reduced) Modifier else Modifier.shadow(10.dp, shape))
            .liquidSurface(shape, listOf(ControlGlass.SurfaceTop, ControlGlass.SurfaceBottom))
            .then(interaction).padding(contentPadding),
        content = content
    )
}

@Composable
private fun ControlPanelHeading(title: String, icon: ImageVector) {
    val accent = LocalControlsSection.current.accent
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp),
        modifier = Modifier.semantics { heading() }) {
        Box(Modifier.size(34.dp).clip(RoundedCornerShape(11.dp))
            .background(accent.copy(alpha = 0.14f)).border(1.dp, accent.copy(alpha = 0.6f), RoundedCornerShape(11.dp)),
            contentAlignment = Alignment.Center) {
            Icon(icon, contentDescription = null, tint = accent, modifier = Modifier.size(19.dp))
        }
        Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold,
            color = ControlGlass.Text)
    }
}

@Composable
private fun StateBadge(text: String, color: Color) {
    Text(text, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold,
        color = color, modifier = Modifier.clip(RoundedCornerShape(8.dp))
            .background(color.copy(alpha = 0.10f)).border(1.dp, color.copy(alpha = 0.55f), RoundedCornerShape(8.dp))
            .padding(horizontal = 8.dp, vertical = 5.dp))
}

@Composable
private fun ControlButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    style: GlassButtonStyle = GlassButtonStyle.Primary,
    enabled: Boolean = true,
    loading: Boolean = false,
    danger: Boolean = false,
    icon: ImageVector? = null,
    minHeight: Dp = 56.dp,
    /** The nudge pad's arrow, drawn above the word. */
    glyph: String? = null
) {
    val section = LocalControlsSection.current
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val reduced = LocalPerformanceProfile.current.reducedEffects
    val scale = if (reduced) rememberUpdatedState(1f) else animateFloatAsState(
        targetValue = if (pressed && enabled && !loading) 0.98f else 1f,
        animationSpec = tween(120), label = "controlPress"
    )
    val primary = style == GlassButtonStyle.Primary
    val fill = when {
        !enabled -> listOf(ControlGlass.DisabledFill, ControlGlass.DisabledFill)
        danger -> listOf(ControlGlass.Stop, GarbyColors.CrimsonDeep)
        primary -> listOf(section.accent, if (section == ControlsSection.Control) GarbyColors.Rose else Color(0xFFFFE5E9))
        else -> listOf(ControlGlass.ButtonTop, ControlGlass.ButtonBottom)
    }
    val ink = when {
        !enabled -> ControlGlass.DisabledText
        danger -> Color.White
        primary -> ControlGlass.OnAccent
        else -> ControlGlass.Text
    }
    val leading = if (!enabled) Icons.Rounded.Lock else icon
    val surface = modifier.then(if (reduced) Modifier else Modifier.graphicsLayer { scaleX = scale.value; scaleY = scale.value })
        .heightIn(min = minHeight).liquidSurface(RoundedCornerShape(18.dp), fill)
        .clickable(interactionSource = interaction, indication = ripple(color = ink),
            enabled = enabled && !loading, role = Role.Button, onClick = onClick)
    if (glyph != null) {
        // The pad: glyph (or lock / progress) over the word, centred in a fixed cell.
        Column(
            surface.padding(horizontal = 6.dp, vertical = 10.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            when {
                loading -> CircularProgressIndicator(color = ink, strokeWidth = 2.dp, modifier = Modifier.size(18.dp))
                !enabled -> Icon(Icons.Rounded.Lock, contentDescription = null, tint = ink, modifier = Modifier.size(18.dp))
                else -> Text(glyph, style = MaterialTheme.typography.labelLarge, color = ink)
            }
            Spacer(Modifier.height(4.dp))
            Text(text, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold,
                color = ink, textAlign = TextAlign.Center)
        }
        return
    }
    Row(
        surface.padding(horizontal = 14.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center
    ) {
        if (loading) {
            CircularProgressIndicator(color = ink, strokeWidth = 2.dp, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp))
        } else if (leading != null) {
            Icon(leading, contentDescription = null, tint = ink, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp))
        }
        Text(text, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold,
            color = ink, textAlign = TextAlign.Center, modifier = Modifier.weight(1f))
    }
}

private const val CONTROLS_PREVIEW_NOW = 1_800_000_000_000L

private fun previewControlsState(developer: Boolean) = OperatorUiState(
    status = OperatorStatus(updatedAtMs = CONTROLS_PREVIEW_NOW, supported = true, connected = true,
        emergencyStopped = !developer, ultrasonicEnabled = true, canConfigure = true,
        canRelease = true, canContinue = true, canReverse = true, canReset = true,
        readOnlyAllowed = true, robotState = "IDLE", developerEnabled = developer,
        developerBlocked = if (developer) "" else "Emergency stop is on. Release it on the Controls tab first.",
        developerMessage = "“Forward” finished. Normal safety checks are back on.",
        developerResult = "Motor 1 +400 steps (≈+5.1 cm), motor 2 +400 steps (≈+5.1 cm); gyro turned +0.12°.",
        developerRun = DeveloperRun(known = true, place = RunPlace.Partway, step = 1, done = 420,
            steps = listOf(com.thesis.garby.realtime.RunStep('F', 4000), com.thesis.garby.realtime.RunStep('L', 980)),
            speed = 2351, accel = 4800, message = "“RunStart list” stopped by the emergency stop."),
        message = "Controller settings confirmed"),
    statusLoaded = true, nowMs = CONTROLS_PREVIEW_NOW, online = true, cloudConnected = true
)

@Preview(name = "Controls · red glass", widthDp = 400, heightDp = 1500, showBackground = true)
@Composable
private fun ControlsGlassPreview() {
    GarbyTheme { ControlsContent(previewControlsState(false), { _, _, _ -> }, {}) }
}

@Preview(name = "Developer · rose glass", widthDp = 400, heightDp = 2100, showBackground = true)
@Composable
private fun DeveloperGlassPreview() {
    GarbyTheme { ControlsContent(previewControlsState(true), { _, _, _ -> }, {}, ControlsSection.Developer) }
}

@Preview(name = "Controls · large text", widthDp = 360, heightDp = 1600, fontScale = 1.5f)
@Composable
private fun ControlsLargeTextPreview() {
    GarbyTheme { ControlsContent(previewControlsState(false), { _, _, _ -> }, {}) }
}
