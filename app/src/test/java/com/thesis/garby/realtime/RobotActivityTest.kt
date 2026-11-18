package com.thesis.garby.realtime

import org.junit.Assert.assertEquals
import org.junit.Test

class RobotActivityTest {
    private val now = 1_800_000_000_000L
    private fun status(state: String = "OUTBOUND", hold: String = "", leg: String = "") =
        OperatorStatus(updatedAtMs = now, connected = true, supported = true,
            emergencyStopped = false, robotState = state, motionHold = hold, routeLeg = leg)

    private fun activity(report: OperatorStatus?, loaded: Boolean = true,
        online: Boolean = true, cloud: Boolean = true) =
        resolveRobotActivity(report, loaded, now, online, cloud)

    @Test
    fun distinguishesRouteExecutionIdleAndParkedWithoutGuessingLocation() {
        for (route in listOf("OUTBOUND", "RETURNING", "REVERSING")) {
            assertEquals(RobotActivity.Running, activity(status(route)).activity)
        }
        assertEquals(RobotActivity.Idle, activity(status("IDLE")).activity)
        assertEquals(RobotActivity.Parked, activity(status("AT_DESTINATION")).activity)
        assertEquals(RobotActivity.Fault, activity(status("FAULT")).activity)
        assertEquals(RobotActivity.Unknown, activity(status("UNKNOWN")).activity)
        assertEquals(RobotActivity.Unknown, activity(status("NEW_STATE")).activity)
    }

    @Test
    fun onlyReportedTurnStepsOrTheExplicitParkingAlignmentShowTurning() {
        for (leg in listOf("Outbound step 2 of 9: left turn", "Return step 3 of 9: right turn",
            "Return step 3 of 9: right turn (way back) · station step, station walls ignored")) {
            val result = activity(status(leg = leg))
            assertEquals(RobotActivity.Turning, result.activity)
            assertEquals(leg, result.routeLeg)
        }
        for (leg in listOf("", "Outbound step 2 of 9: long hallway", "left turn",
            "Unconfirmed left turn", "Outbound step 0 of 9: left turn")) {
            assertEquals(RobotActivity.Running, activity(status(leg = leg)).activity)
        }
        assertEquals(RobotActivity.Turning,
            activity(status(hold = "Parking: aligning square to the rear wall")).activity)
    }

    @Test
    fun holdsOverrideTurnLegsAndConfirmedEmergencyStopOverridesAllActivity() {
        val turning = status(leg = "Outbound step 2 of 9: left turn")
        for (hold in listOf("LiDAR obstacle", "Turn sweep blocked", "Local sonar obstacle",
            "Waiting for LiDAR", "Confirming clearance", "MPU not ready")) {
            val result = activity(turning.copy(motionHold = hold))
            assertEquals(RobotActivity.Blocked, result.activity)
            assertEquals(hold, result.detail)
        }
        assertEquals(RobotActivity.EmergencyStopped,
            activity(turning.copy(emergencyStopped = true, motionHold = "LiDAR obstacle")).activity)
        // A completed/idle route must not inherit an old hold or turn label.
        assertEquals(RobotActivity.Idle, activity(turning.copy(robotState = "IDLE")).activity)
        assertEquals(RobotActivity.Parked,
            activity(turning.copy(robotState = "AT_DESTINATION", motionHold = "LiDAR obstacle")).activity)
    }

    @Test
    fun unavailableAndExpiredEvidenceCannotShowLiveActivity() {
        val ready = status()
        val unknown = RobotActivity.Unknown
        assertEquals(unknown, activity(null).activity)
        assertEquals(unknown, activity(ready, loaded = false).activity)
        assertEquals(unknown, activity(ready, online = false).activity)
        assertEquals(unknown, activity(ready, cloud = false).activity)
        assertEquals(unknown, activity(ready.copy(connected = false)).activity)
        assertEquals(unknown, activity(ready.copy(supported = false)).activity)
        assertEquals(unknown, activity(ready.copy(updatedAtMs = 0)).activity)
        assertEquals(unknown, activity(ready.copy(updatedAtMs = now - OperatorLimits.STATUS_STALE_AFTER_MS - 1)).activity)
        assertEquals(unknown, activity(ready.copy(updatedAtMs = now + 60_000)).activity)
        assertEquals(unknown, activity(ready.copy(emergencyStopped = true,
            updatedAtMs = now - OperatorLimits.STATUS_STALE_AFTER_MS - 1)).activity)
    }
}
