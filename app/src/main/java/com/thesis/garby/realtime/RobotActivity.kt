package com.thesis.garby.realtime

/** Reported execution activity, distinct from the route's last confirmed endpoint. */
enum class RobotActivity(val label: String) {
    Running("Running"), Blocked("Blocked"), Turning("Turning"), Idle("Idle"),
    Parked("Parked"), EmergencyStopped("Emergency stopped"), Fault("Route fault"),
    Unknown("Activity unavailable")
}

data class RobotActivityState(
    val activity: RobotActivity = RobotActivity.Unknown,
    val detail: String = "Waiting for GARBY's activity report.",
    val routeLeg: String = ""
)

// station_mode.RouteLeg.describe() supplies this field; missing/expired legs are empty.
private val turnLeg = Regex("^(?:Outbound|Return) step [1-9][0-9]* of [1-9][0-9]*: (left|right) turn(?: \\(way back\\))?(?: · station step, station walls ignored)?$")

/** Display only. Unknown/stale telemetry cannot become a live running or idle indication. */
fun resolveRobotActivity(
    status: OperatorStatus?,
    statusLoaded: Boolean,
    nowMs: Long,
    online: Boolean,
    cloudConnected: Boolean
): RobotActivityState {
    fun unavailable(detail: String) = RobotActivityState(detail = detail)
    if (!online) return unavailable("This phone is offline.")
    if (!cloudConnected) return unavailable("The cloud link is unavailable.")
    if (!statusLoaded) return unavailable("Waiting for GARBY's activity report.")
    if (status == null) return unavailable("GARBY has not published an activity report.")
    if (!status.isFresh(nowMs)) return unavailable("GARBY's activity report is stale.")
    if (!status.connected || !status.supported) return unavailable("Waiting for the MCU to report online.")
    if (status.emergencyStopped == true) {
        return RobotActivityState(RobotActivity.EmergencyStopped, "The controllers confirmed the emergency stop.")
    }
    val robotState = RobotState.fromWire(status.robotState)
    return when (robotState) {
        RobotState.Idle -> RobotActivityState(RobotActivity.Idle, "GARBY reports idle; its location is shown separately.")
        RobotState.AtDestination -> RobotActivityState(RobotActivity.Parked, "The outbound route is complete at the disposal area.")
        RobotState.Fault -> RobotActivityState(RobotActivity.Fault, "GARBY reports a route fault. Check the robot.")
        RobotState.Outbound, RobotState.Returning, RobotState.Reversing -> {
            val leg = status.routeLeg.trim()
            val hold = status.motionHold.trim()
            when {
                // This particular Pi message reports a parking correction, not a hold.
                hold == "Parking: aligning square to the rear wall" ->
                    RobotActivityState(RobotActivity.Turning, hold, leg)
                hold.isNotEmpty() -> RobotActivityState(RobotActivity.Blocked, hold, leg)
                turnLeg.matches(leg) ->
                    RobotActivityState(RobotActivity.Turning, "Executing the reported turn step.", leg)
                else -> RobotActivityState(RobotActivity.Running, when (robotState) {
                    RobotState.Returning -> "Executing the return route."
                    RobotState.Reversing -> "Executing reverse to start."
                    else -> "Executing the outbound route."
                }, leg)
            }
        }
        RobotState.Unknown, null -> unavailable("The controllers have not confirmed the current activity.")
    }
}
