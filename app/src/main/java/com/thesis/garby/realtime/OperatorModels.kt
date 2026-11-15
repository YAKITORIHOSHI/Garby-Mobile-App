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
    Reboot("REBOOT", "Restart Pi and controllers");

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
    val readOnlyAllowed: Boolean = false,
    val motionHold: String = "",
    val zones: String = "",
    val routeLeg: String = "",
    val people: Int = 0,
    val objects: Int = 0,
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

    /** The Pi publishes while a lease is under 45 s old. */
    const val MAP_LEASE_RENEW_MS = 20_000L
}

fun OperatorStatus?.isFresh(nowMs: Long): Boolean =
    this != null && updatedAtMs > 0L &&
        nowMs - updatedAtMs in -FUTURE_TOLERANCE_MS..OperatorLimits.STATUS_STALE_AFTER_MS

private const val FUTURE_TOLERANCE_MS = 30_000L

/**
 * Why [action] cannot be sent now, or null when it can. An emergency stop is never held back by
 * the robot's reported state: with no fresh state it is still worth sending.
 */
fun operatorBlockReason(
    action: OperatorAction,
    status: OperatorStatus?,
    nowMs: Long,
    online: Boolean,
    busy: Boolean
): String? {
    if (!online) return "This phone is offline."
    if (busy) return "Another request is being sent."
    if (action == OperatorAction.Estop) return null
    if (!status.isFresh(nowMs)) return "GARBY's Pi is not reporting. Is the robot software running?"
    status!!
    if (!status.connected) return "The Pi has no link to the robot's controllers."
    if (!status.supported) return "Waiting for the controllers to report their state."
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
        OperatorAction.BackReadOnlyOn, OperatorAction.BackReadOnlyOff ->
            if (status.readOnlyAllowed) null else "Read-only is not permitted on this robot."
        OperatorAction.Estop -> null
    }
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
    nowMs: Long
): Boolean {
    if (action == OperatorAction.Estop || !exists) return true
    if (OperatorCommandStatus.fromWire(existingStatus).finished) return true
    val requestedAt = existingRequestedAtMs ?: return true
    return nowMs - requestedAt > OperatorLimits.COMMAND_REPLACE_AFTER_MS
}

/** What the last request came to, in the operator's words. */
fun operatorOutcomeText(command: OperatorCommand?, nowMs: Long): String? {
    if (command == null) return null
    val label = OperatorAction.fromWire(command.action)?.label ?: command.action
    val age = nowMs - command.requestedAtMs
    return when (command.status) {
        OperatorCommandStatus.Pending ->
            if (age > OperatorLimits.COMMAND_LOST_AFTER_MS) "$label: GARBY did not pick it up."
            else "$label: sent, waiting for GARBY."
        OperatorCommandStatus.Claimed -> "$label: received, waiting for the controllers."
        OperatorCommandStatus.Accepted -> "$label: confirmed by the controllers."
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
    else -> " (${reason!!.trim().take(80)})."
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
