package com.thesis.garby.realtime

import java.util.Base64
import kotlin.math.cos
import kotlin.math.sin

/**
 * Phone control of the robot (2026-11-14). The Pi mirrors what its monitor's Controls tab knows
 * to `devices/<id>/operator`, takes one request at a time from `devices/<id>/commands/operator`
 * through the same gate its own buttons use, and publishes a compact LiDAR frame to
 * `devices/<id>/map/frame` while an app holds a lease in `devices/<id>/commands/map`.
 *
 * Everything in this file is plain Kotlin so the rules can be unit-tested on the JVM.
 */

/** An operator action as the Pi names it. */
enum class OperatorAction(val wire: String, val label: String) {
    Estop("ESTOP", "Emergency stop"),
    Release("RELEASE", "Release stop"),
    Continue("CONTINUE", "Continue run"),
    Reverse("REVERSE", "Reverse to start"),
    SonarOn("SONAR_ON", "Enable ultrasonic"),
    SonarOff("SONAR_OFF", "Disable ultrasonic"),
    FrontReadOnlyOn("FRONT_READONLY_ON", "Front LiDAR read-only"),
    FrontReadOnlyOff("FRONT_READONLY_OFF", "Enable front LiDAR checks"),
    BackReadOnlyOn("BACK_READONLY_ON", "Rear LiDAR read-only"),
    BackReadOnlyOff("BACK_READONLY_OFF", "Enable rear LiDAR checks"),
    SideReadOnlyOn("SIDE_READONLY_ON", "Side LiDAR read-only"),
    SideReadOnlyOff("SIDE_READONLY_OFF", "Enable side LiDAR checks"),
    Reboot("REBOOT", "Restart Pi and controllers"),
    // Developer actions:
    DevTestF("DEV_TEST_F", "Forward ~5 cm"),
    DevTestB("DEV_TEST_B", "Backward ~5 cm"),
    DevTestL("DEV_TEST_L", "Turn left 10°"),
    DevTestR("DEV_TEST_R", "Turn right 10°"),
    DevTestL90("DEV_TEST_L90", "Turn left 90°"),
    DevTestR90("DEV_TEST_R90", "Turn right 90°"),
    DevTestM1("DEV_TEST_M1", "Motor 1 only"),
    DevTestM2("DEV_TEST_M2", "Motor 2 only"),
    DevTestSensors("DEV_TEST_I", "Check sensors (no movement)"),
    DevCancel("DEV_CANCEL", "Stop test"),
    DevRunStart("DEV_RUN_START", "Start RunStart list"),
    DevRunContinue("DEV_RUN_CONTINUE", "Continue RunStart list"),
    DevRunUndo("DEV_RUN_UNDO", "Reverse RunStart to start"),
    DevRunClear("DEV_RUN_CLEAR", "Discard saved list"),
    // Remote drive from the phone (owner, 2026-11-18: "Tap = short nudge"): each tap drives
    // for a fixed time the Pi itself measures, then stops.
    DevHoldW("DEV_HOLD_W", "Nudge forward"),
    DevHoldS("DEV_HOLD_S", "Nudge backward"),
    DevHoldA("DEV_HOLD_A", "Nudge spin left"),
    DevHoldD("DEV_HOLD_D", "Nudge spin right"),
    DevHoldStop("DEV_HOLD_STOP", "Stop nudge");

    val isDeveloperAction: Boolean get() = wire.startsWith("DEV_")

    /** A finite bench test: the only requests that carry the sensor selection. */
    val isBenchTest: Boolean get() = wire.startsWith("DEV_TEST_")

    /** A remote-drive nudge: the only requests that carry Normal/Fast. */
    val isNudge: Boolean get() = this == DevHoldW || this == DevHoldS || this == DevHoldA || this == DevHoldD

    /** The Pi refuses a turn test unless the gyro (MPU) is selected. */
    val isTurnTest: Boolean get() = this == DevTestL || this == DevTestR || this == DevTestL90 || this == DevTestR90

    /** Stops a developer motion: never held back by an unfinished developer request. */
    val stopsDeveloperMotion: Boolean get() = this == DevCancel || this == DevHoldStop

    companion object {
        fun fromWire(value: String?): OperatorAction? = entries.firstOrNull { it.wire == value }
    }
}

enum class OperatorCommandStatus(val wire: String) {
    Pending("pending"),
    Claimed("claimed"),
    Accepted("accepted"),
    Failed("failed"),
    Unconfirmed("unconfirmed"),
    Unknown("");

    val finished: Boolean get() = this == Accepted || this == Failed || this == Unconfirmed

    companion object {
        fun fromWire(value: String?): OperatorCommandStatus =
            entries.firstOrNull { it.wire.isNotEmpty() && it.wire == value } ?: Unknown
    }
}

/** The extra fields a request carries: the sensor selection (LiDAR 1, MPU 2, sonar 4) for bench
 *  tests only, Normal/Fast for nudges only. Every other request sends neither, so it still
 *  passes database rules that predate the developer controls. */
fun requestFields(action: OperatorAction, sensorMask: Int, fast: Boolean): Pair<Int?, Boolean?> =
    Pair(if (action.isBenchTest) sensorMask else null, if (action.isNudge) fast else null)

/** Where the robot's saved RunStart list stands, as the Pi's RunStart tab reports it. */
enum class RunPlace(val wire: String) {
    Unknown("unknown"), None("none"), Start("start"), Partway("partway"), End("end");

    companion object {
        fun fromWire(value: String?): RunPlace = entries.firstOrNull { it.wire == value } ?: Unknown
    }
}

/** One step of a RunStart list: F/B/L/R/C and its step count. */
data class RunStep(val op: Char, val steps: Int) {
    val name: String get() = when (op) {
        'F' -> "Forward"
        'B' -> "Backward"
        'L' -> "Turn left"
        'R' -> "Turn right"
        'C' -> "Bump calibrate"
        else -> "Step"
    }
}

/** "F4000,L980" from the Pi; anything malformed gives no steps rather than a guess. */
fun parseRunSteps(text: String?): List<RunStep> {
    if (text.isNullOrBlank()) return emptyList()
    val items = text.split(',')
    if (items.size > 20) return emptyList()
    return items.map { item ->
        val count = item.drop(1).toIntOrNull()
        if (item.isEmpty() || item[0] !in "FBLRC" || count == null || count !in 1..100_000) return emptyList()
        RunStep(item[0], count)
    }
}

/** The Pi's RunStart limits (`RasPi/developer_support.py`, owner 2026-11-16). */
object RunLimits {
    const val MAX_STEPS = 20
    const val MAX_STEP = 100_000
    const val MIN_SPEED = 2351
    const val MAX_SPEED = 8000
    const val MIN_ACCEL = 100
    const val MAX_ACCEL = 20_000
    const val DEFAULT_SPEED = 2351
    const val DEFAULT_ACCEL = 4800

    /** In the Pi window's order: Forward, Backward, Turn left, Turn right, Bump calibrate. */
    val OPS = listOf('F', 'B', 'L', 'R', 'C')
}

/** A list ready for Start run: what the Pi's RunStart tab sends the robot. */
data class RunProgram(val steps: List<RunStep>, val speed: Int, val accel: Int) {
    /** "F4000,L980", as the Pi reads it. */
    val wire: String get() = steps.joinToString(",") { "${it.op}${it.steps}" }
}

/** One row as typed: what it does and its step count (digits only). */
data class RunDraftRow(val op: Char = 'F', val steps: String = "")

/** The typed rows as a list to send, or the Pi window's words for what is wrong. */
sealed interface RunDraftCheck {
    data class Ready(val program: RunProgram) : RunDraftCheck
    data class Problem(val reason: String) : RunDraftCheck
}

/** The Pi window's own checks (`_run_collect`, `run_program`), in its words. */
fun checkRunDraft(rows: List<RunDraftRow>, speedText: String, accelText: String): RunDraftCheck {
    if (rows.isEmpty()) return RunDraftCheck.Problem("Add at least one step.")
    if (rows.size > RunLimits.MAX_STEPS) return RunDraftCheck.Problem("At most ${RunLimits.MAX_STEPS} steps.")
    val steps = rows.mapIndexed { index, row ->
        val number = index + 1
        val text = row.steps.trim()
        if (row.op !in RunLimits.OPS) return RunDraftCheck.Problem("Step $number: choose what it does.")
        if (text.isEmpty() || !text.all { it in '0'..'9' }) {
            return RunDraftCheck.Problem("Step $number: enter the number of steps (1–${RunLimits.MAX_STEP}).")
        }
        val count = text.toIntOrNull()
        if (count == null || count !in 1..RunLimits.MAX_STEP) {
            return RunDraftCheck.Problem("Step $number: steps must be a whole number from 1 to ${RunLimits.MAX_STEP}.")
        }
        RunStep(row.op, count)
    }
    val values = listOf("Max speed" to speedText, "Acceleration" to accelText).map { (name, raw) ->
        val text = raw.trim()
        if (text.isEmpty() || !text.all { it in '0'..'9' }) return RunDraftCheck.Problem("$name: enter a whole number.")
        text.toIntOrNull() ?: -1
    }
    val (speed, accel) = values
    if (speed !in RunLimits.MIN_SPEED..RunLimits.MAX_SPEED) {
        return RunDraftCheck.Problem("Max speed must be a whole number from ${RunLimits.MIN_SPEED} to " +
            "${RunLimits.MAX_SPEED} steps/s.")
    }
    if (accel !in RunLimits.MIN_ACCEL..RunLimits.MAX_ACCEL) {
        return RunDraftCheck.Problem("Acceleration must be a whole number from ${RunLimits.MIN_ACCEL} to " +
            "${RunLimits.MAX_ACCEL} steps/s².")
    }
    return RunDraftCheck.Ready(RunProgram(steps, speed, accel))
}

/** The rows kept across rotation as "F4000,L,R90"; the step field takes digits only. */
fun encodeRunDraft(rows: List<RunDraftRow>): String = rows.joinToString(",") { "${it.op}${it.steps}" }

fun decodeRunDraft(text: String): List<RunDraftRow> =
    text.split(',').mapNotNull { item ->
        item.firstOrNull()?.takeIf { it in RunLimits.OPS }?.let { op ->
            RunDraftRow(op, item.drop(1).filter { it in '0'..'9' }.take(6))
        }
    }.take(RunLimits.MAX_STEPS).ifEmpty { listOf(RunDraftRow()) }

/** The saved RunStart list and its place (Pi `PHONEDEV-20261118`). Lists are made and started
 *  in the Pi's Developer window; the phone can continue, reverse or discard one. */
data class DeveloperRun(
    val known: Boolean = false,
    val place: RunPlace = RunPlace.Unknown,
    val running: Boolean = false,
    val waitingRelease: Boolean = false,
    /** "P" started, "C" continuing, "U" reversing; empty when not moving. */
    val mode: String = "",
    val message: String = "",
    val steps: List<RunStep> = emptyList(),
    /** Zero-based step the saved list stands at, with [done] steps of it driven. */
    val step: Int = 0,
    val done: Int? = null,
    val speed: Int? = null,
    val accel: Int? = null,
    /** Zero-based step a moving list is driving. */
    val runningStep: Int? = null
) {
    val hasSaved: Boolean
        get() = steps.isNotEmpty() &&
            (place == RunPlace.Start || place == RunPlace.Partway || place == RunPlace.End)
}

/** The single operator request in `commands/operator`. */
data class OperatorCommand(
    val requestId: String,
    val requestedAtMs: Long,
    val requestedBy: String,
    val action: String,
    val status: OperatorCommandStatus,
    val reason: String?
)

/**
 * The Pi's mirror of its Controls tab. Null booleans mean the controller has not confirmed the
 * value: unknown is never shown as released, enabled or allowed.
 */
data class OperatorStatus(
    val updatedAtMs: Long = 0L,
    val supported: Boolean = false,
    val connected: Boolean = false,
    val pending: Boolean = false,
    val emergencyStopped: Boolean? = null,
    val ultrasonicEnabled: Boolean? = null,
    val canConfigure: Boolean = false,
    val canRelease: Boolean = false,
    val canContinue: Boolean = false,
    val canReverse: Boolean = false,
    val canReset: Boolean = false,
    val message: String = "",
    val robotState: String = "",
    val frontReadOnly: Boolean = false,
    val backReadOnly: Boolean = false,
    val sideReadOnly: Boolean = false,
    val readOnlyAllowed: Boolean = false,
    val motionHold: String = "",
    val zones: String = "",
    val routeLeg: String = "",
    val people: Int = 0,
    val objects: Int = 0,
    val developerEnabled: Boolean = false,
    val developerPending: Boolean = false,
    val developerResult: String = "",
    /** The Developer window's own words: the running or last test, and why none can start. */
    val developerMessage: String = "",
    val developerBlocked: String = "",
    /** Null from Pi software older than `PHONEDEV-20261118`. */
    val developerRun: DeveloperRun? = null,
    val launcherMode: String = "",
    val launcherStartedAtMs: Long = 0L,
    val software: String = ""
)

object OperatorLimits {
    /** The Pi rewrites its mirror at least every 10 s; three misses make it stale. */
    const val STATUS_STALE_AFTER_MS = 30_000L

    /** A request the Pi has not finished after this long may be replaced. */
    const val COMMAND_REPLACE_AFTER_MS = 30_000L

    /** The Pi refuses anything but a stop older than 20 s; show it as lost a little later. */
    const val COMMAND_LOST_AFTER_MS = 25_000L

    /** A map frame older than this is drawn greyed and labelled stale. */
    const val MAP_STALE_AFTER_MS = 3_000L

    /** Release scan data and its listener after ten seconds without a new valid scan. */
    const val MAP_SLEEP_AFTER_MS = 10_000L

    /** The Pi publishes while a lease is under 45 s old. */
    const val MAP_LEASE_RENEW_MS = 20_000L
}

fun OperatorStatus?.isFresh(nowMs: Long): Boolean =
    this != null && updatedAtMs > 0L &&
        nowMs - updatedAtMs in -FUTURE_TOLERANCE_MS..OperatorLimits.STATUS_STALE_AFTER_MS

private const val FUTURE_TOLERANCE_MS = 30_000L

/**
 * Why [action] cannot be sent now, or null when it can. The phone's stop requires a reporting
 * Pi and a live controller link; once online it can preempt any other pending action.
 */
fun operatorBlockReason(
    action: OperatorAction,
    status: OperatorStatus?,
    nowMs: Long,
    online: Boolean,
    busy: Boolean,
    cloudConnected: Boolean = true
): String? {
    if (!online) return "This phone is offline."
    if (!cloudConnected) return "This phone is not connected to GARBY's database."
    if (!status.isFresh(nowMs)) return "GARBY's Pi is not reporting. Is the robot software running?"
    status!!
    if (!status.connected) return "The Pi has no link to the robot's controllers."
    if (!status.supported) return "Waiting for the controllers to report their state."
    if (action == OperatorAction.Estop) return null
    // A developer stop is never held back by the request it stops.
    if (action == OperatorAction.DevHoldStop) return null
    if (action == OperatorAction.DevCancel) return if (status.developerPending) null else "No test is running."
    if (busy) return "Another request is being sent."
    if (status.pending) return "The controllers are still confirming the last action."
    return when (action) {
        OperatorAction.Release ->
            if (status.canRelease) null else stopNeeded(status)
        OperatorAction.Continue ->
            if (status.canContinue) null
            else if (status.emergencyStopped == true) "No stopped route to continue."
            else stopNeeded(status)
        OperatorAction.Reverse ->
            if (status.canReverse) null
            else if (status.emergencyStopped == true) "No stopped route to reverse."
            else stopNeeded(status)
        OperatorAction.Reboot ->
            if (status.canReset) null else "Apply the emergency stop first; it must be confirmed."
        OperatorAction.SonarOn, OperatorAction.SonarOff ->
            if (status.canConfigure) null else "Only while GARBY is idle."
        OperatorAction.FrontReadOnlyOn, OperatorAction.FrontReadOnlyOff,
        OperatorAction.BackReadOnlyOn, OperatorAction.BackReadOnlyOff,
        OperatorAction.SideReadOnlyOn, OperatorAction.SideReadOnlyOff ->
            if (status.readOnlyAllowed) null else "Read-only is not permitted on this robot."
        OperatorAction.Estop -> null
        else -> if (action.isDeveloperAction) developerBlockReason(action, status) else null
    }
}

/**
 * The Pi's Developer window rules. Tests and nudges follow its own readiness (idle, no
 * emergency stop, controllers ready for tests). RunStart follows its RunStart tab: Continue
 * while the list is at its start or part-way, Reverse while part-way or at its end, either one
 * also while the emergency stop is latched and releasable (the Pi releases it first), Discard
 * whenever a list is saved and not moving.
 */
private fun developerBlockReason(action: OperatorAction, status: OperatorStatus): String? {
    val notReady = status.developerBlocked.ifBlank { "Developer tests are not available right now." }
    if (action == OperatorAction.DevRunStart) {
        // The Pi window's Start run: ready, and the rows not locked by a moving or part-way list.
        val run = status.developerRun ?: return "Update GARBY's robot software for RunStart from the phone."
        if (run.running || run.waitingRelease) return "The list is moving. Stop test stops it."
        if (run.place == RunPlace.Partway) {
            return "A list stands part-way. Continue run, Reverse to start or Discard saved first."
        }
        if (status.developerPending) return "A test is running. Stop test ends it."
        return if (status.developerEnabled) null else notReady
    }
    if (action == OperatorAction.DevRunContinue || action == OperatorAction.DevRunUndo ||
        action == OperatorAction.DevRunClear
    ) {
        val run = status.developerRun ?: return "Update GARBY's robot software for RunStart from the phone."
        if (!run.known) return "Waiting for the robot to report its saved list."
        if (run.running || run.waitingRelease) return "The list is moving. Stop test stops it."
        if (!run.hasSaved) return "No saved list. Lists are made in the Pi's Developer window."
        if (action == OperatorAction.DevRunClear) return null
        if (action == OperatorAction.DevRunContinue && run.place == RunPlace.End) {
            return "The list finished. Reverse to start can take it back."
        }
        if (action == OperatorAction.DevRunUndo && run.place == RunPlace.Start) return "The list is at its start."
        if (status.developerPending) return "A test is running."
        if (status.developerEnabled) return null
        if (status.emergencyStopped == true && status.canRelease) return null
        return notReady
    }
    if (status.developerPending) return "A test is running. Stop test ends it."
    return if (status.developerEnabled) null else notReady
}

private fun stopNeeded(status: OperatorStatus): String =
    if (status.emergencyStopped == true) "GARBY must be stopped at idle, confirmed by its controllers."
    else "Only after a confirmed emergency stop."

/**
 * Whether a new request may replace what is in `commands/operator`. A stop always may. Anything
 * else waits for the previous request to finish, unless the Pi has clearly not picked it up.
 */
fun canReplaceOperatorCommand(
    action: OperatorAction,
    existingStatus: String?,
    existingRequestedAtMs: Long?,
    exists: Boolean,
    nowMs: Long,
    existingAction: String? = null
): Boolean {
    if (action == OperatorAction.Estop || !exists) return true
    if (OperatorCommandStatus.fromWire(existingStatus).finished) return true
    // Stop test / Stop nudge may overtake the developer request it stops: unclaimed, that
    // request never runs; claimed, the Pi has already finished acting on it.
    if (action.stopsDeveloperMotion && existingAction?.startsWith("DEV_") == true) return true
    val requestedAt = existingRequestedAtMs ?: return true
    return nowMs - requestedAt > OperatorLimits.COMMAND_REPLACE_AFTER_MS
}

/** What the last request came to, in the operator's words. */
fun operatorOutcomeText(command: OperatorCommand?, nowMs: Long): String? {
    if (command == null) return null
    val action = OperatorAction.fromWire(command.action)
    val label = action?.label ?: command.action
    val age = nowMs - command.requestedAtMs
    return when (command.status) {
        OperatorCommandStatus.Pending ->
            if (age > OperatorLimits.COMMAND_LOST_AFTER_MS) "$label: GARBY did not pick it up."
            else "$label: sent, waiting for GARBY."
        OperatorCommandStatus.Claimed -> "$label: received, waiting for the controllers."
        OperatorCommandStatus.Accepted ->
            // A developer request is accepted when the Pi takes it; how it went comes after.
            if (action?.isDeveloperAction == true) "$label: taken by GARBY's Pi. Bench status shows how it goes."
            else "$label: confirmed by the controllers."
        OperatorCommandStatus.Failed -> "$label: refused" + reasonText(command.reason)
        OperatorCommandStatus.Unconfirmed -> "$label: not confirmed" + reasonText(command.reason) +
            " Check GARBY before trying again."
        OperatorCommandStatus.Unknown -> null
    }
}

private fun reasonText(reason: String?): String = when (reason?.trim().orEmpty()) {
    "" -> "."
    "expired_request" -> " (it arrived too late)."
    "invalid_request", "unknown_action" -> " (this app and the robot software do not match)."
    "not_permitted" -> " (not permitted on this robot)."
    "NO_ROUTE" -> " (no stopped route)."
    "NOT_IDLE" -> " (GARBY is not idle)."
    "NOT_STOPPED" -> " (the emergency stop is not latched)."
    "stop_requested_controller_unconfirmed" -> " (the Pi asked for the stop; the controllers did not answer)."
    "controller_result_unverified" -> " (the controllers did not answer)."
    "developer_tests_unavailable" -> " (this robot software takes no developer tests from the phone)."
    else -> " (${reason!!.trim().take(120).trimEnd('.')})."
}

// ---------------------------------------------------------------------------------------------
// LiDAR map frame
// ---------------------------------------------------------------------------------------------

enum class MarkerKind { Person, Moving, Object }

/** A marked thing in the robot's frame: centimetres forward and to the left, cm/s velocity. */
data class MapMarker(
    val forwardCm: Int,
    val leftCm: Int,
    val kind: MarkerKind,
    val vxCmS: Int,
    val vyCmS: Int
)

/** One published scan. [distancesCm] holds 360 values, 0 = no return; index 0 is straight ahead
 *  and the index grows counter-clockwise (to the robot's left). */
class MapFrame(
    val seq: Long,
    val atMs: Long,
    val ageMs: Int,
    val distancesCm: IntArray,
    val markers: List<MapMarker>,
    val zones: String,
    val path: String,
    val hold: String,
    val wallLeftCm: Double?,
    val wallRightCm: Double?,
    val headingDeg: Double?
) {
    fun isStale(nowMs: Long): Boolean =
        nowMs - atMs !in -FUTURE_TOLERANCE_MS..OperatorLimits.MAP_STALE_AFTER_MS

    val people: Int get() = markers.count { it.kind == MarkerKind.Person }
    val movers: Int get() = markers.count { it.kind == MarkerKind.Moving }
    val objects: Int get() = markers.count { it.kind == MarkerKind.Object }
}

/** 360 little-endian unsigned 16-bit centimetres, base64. Null for anything else. */
fun decodeDistances(encoded: String?): IntArray? {
    if (encoded.isNullOrBlank()) return null
    val bytes = try {
        Base64.getDecoder().decode(encoded)
    } catch (_: IllegalArgumentException) {
        return null
    }
    if (bytes.size != 720) return null
    return IntArray(360) { index ->
        (bytes[2 * index].toInt() and 0xFF) or ((bytes[2 * index + 1].toInt() and 0xFF) shl 8)
    }
}

/** `[[forward cm, left cm, kind, vx, vy], ...]`; malformed entries are dropped, not guessed. */
fun decodeMarkers(raw: Any?): List<MapMarker> {
    val list = raw as? List<*> ?: return emptyList()
    return list.mapNotNull { entry ->
        val fields = (entry as? List<*>)?.map { (it as? Number)?.toInt() } ?: return@mapNotNull null
        if (fields.size != 5 || fields.any { it == null }) return@mapNotNull null
        val kind = when (fields[2]) {
            1 -> MarkerKind.Person
            2 -> MarkerKind.Moving
            3 -> MarkerKind.Object
            else -> return@mapNotNull null
        }
        MapMarker(fields[0]!!, fields[1]!!, kind, fields[3]!!, fields[4]!!)
    }.take(16)
}

/** Robot-frame position of the return at [degree]: (forward cm, left cm). */
fun mapPointCm(degree: Int, distanceCm: Int): Pair<Float, Float> {
    val radians = Math.toRadians(degree.toDouble())
    return Pair((distanceCm * cos(radians)).toFloat(), (distanceCm * sin(radians)).toFloat())
}

/** "1 likely person · 2 objects", or null when nothing is marked. */
fun markerSummary(people: Int, movers: Int, objects: Int): String? {
    val parts = buildList {
        if (people > 0) add(if (people == 1) "1 likely person" else "$people likely people")
        if (movers > 0) add(if (movers == 1) "1 moving object" else "$movers moving objects")
        if (objects > 0) add(if (objects == 1) "1 object" else "$objects objects")
    }
    return parts.takeIf { it.isNotEmpty() }?.joinToString(" · ")
}
