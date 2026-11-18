package com.thesis.garby.ui

import com.thesis.garby.realtime.RobotLocation
import com.thesis.garby.realtime.RobotState
import com.thesis.garby.ui.glass.RoutePosition
import org.junit.Assert.*
import org.junit.Test

class RobotLocationTest {
    @Test
    fun genericIdleNeverClaimsStationArrival() {
        assertEquals(RoutePosition.Stopped, robotStateUi(RobotState.Idle, false).route)
        assertEquals(RoutePosition.Stopped,
            robotStateUi(RobotState.Idle, false, RobotLocation.Unknown).route)
        assertEquals(RoutePosition.AtBase,
            robotStateUi(RobotState.Idle, false, RobotLocation.PointA).route)
        assertEquals(RoutePosition.ToDestination,
            robotStateUi(RobotState.Outbound, false, RobotLocation.PointA).route)
    }

    @Test
    fun absentOrUnknownLocationCannotBecomePointA() {
        assertNull(RobotLocation.fromWire(null))
        assertEquals(RobotLocation.Unknown, RobotLocation.fromWire("OTHER"))
        assertEquals(RobotLocation.PointA, RobotLocation.fromWire("POINT_A"))
        assertEquals(RobotLocation.PointB, RobotLocation.fromWire("POINT_B"))
        assertEquals(RobotLocation.InTransit, RobotLocation.fromWire("IN_TRANSIT"))
    }
}
