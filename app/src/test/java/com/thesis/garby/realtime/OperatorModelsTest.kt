package com.thesis.garby.realtime

import com.thesis.garby.ui.StopButtonState
import com.thesis.garby.ui.stopButtonState
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class OperatorModelsTest {
    private val now = 1_800_000_000_000L

    private fun ready(
        stopped: Boolean? = false,
        canRelease: Boolean = false,
        canContinue: Boolean = false,
        canReverse: Boolean = false,
        canReset: Boolean = false,
        canConfigure: Boolean = true,
        readOnlyAllowed: Boolean = true,
        pending: Boolean = false,
        ageMs: Long = 2_000L
    ) = OperatorStatus(
        updatedAtMs = now - ageMs, supported = true, connected = true, pending = pending,
        emergencyStopped = stopped, ultrasonicEnabled = true, canConfigure = canConfigure,
        canRelease = canRelease, canContinue = canContinue, canReverse = canReverse,
        canReset = canReset, readOnlyAllowed = readOnlyAllowed
    )

    private fun block(action: OperatorAction, status: OperatorStatus?, online: Boolean = true, busy: Boolean = false) =
        operatorBlockReason(action, status, now, online, busy)

    @Test
    fun anEmergencyStopIsNeverHeldBackByTheRobotsState() {
        assertNull(block(OperatorAction.Estop, null))
        assertNull(block(OperatorAction.Estop, ready(ageMs = 600_000L)))
        assertNull(block(OperatorAction.Estop, ready().copy(connected = false, supported = false)))
        assertNotNull(block(OperatorAction.Estop, ready(), online = false))
    }

    @Test
    fun everythingElseNeedsAFreshConfirmedState() {
        val others = OperatorAction.entries.filter { it != OperatorAction.Estop }
        val all = ready(stopped = true, canRelease = true, canContinue = true, canReverse = true, canReset = true)
        for (action in others) {
            assertNull(action.name, block(action, all))
            assertNotNull(action.name, block(action, null))
            assertNotNull(action.name, block(action, all.copy(updatedAtMs = now - OperatorLimits.STATUS_STALE_AFTER_MS - 1)))
            assertNotNull(action.name, block(action, all.copy(connected = false)))
            assertNotNull(action.name, block(action, all.copy(supported = false)))
            assertNotNull(action.name, block(action, all.copy(pending = true)))
            assertNotNull(action.name, block(action, all, online = false))
            assertNotNull(action.name, block(action, all, busy = true))
        }
    }

    @Test
    fun releaseContinueAndReverseFollowTheControllersOwnPermissions() {
        val stopped = ready(stopped = true, canRelease = true)
        assertNull(block(OperatorAction.Release, stopped))
        assertEquals("No stopped route to continue.", block(OperatorAction.Continue, stopped))
        assertEquals("No stopped route to reverse.", block(OperatorAction.Reverse, stopped))
        assertNull(block(OperatorAction.Continue, stopped.copy(canContinue = true)))
        assertNull(block(OperatorAction.Reverse, stopped.copy(canReverse = true)))
        val running = ready(stopped = false)
        assertEquals("Only after a confirmed emergency stop.", block(OperatorAction.Release, running))
        assertEquals("Only after a confirmed emergency stop.", block(OperatorAction.Continue, running))
        assertNotNull(block(OperatorAction.Reboot, running))
        // Unknown stop state is not "stopped".
        assertNotNull(block(OperatorAction.Release, ready(stopped = null)))
    }

    @Test
    fun sensorSwitchesFollowIdleAndThePisPermission() {
        assertNull(block(OperatorAction.SonarOff, ready()))
        assertNotNull(block(OperatorAction.SonarOff, ready(canConfigure = false)))
        assertNull(block(OperatorAction.FrontReadOnlyOn, ready(canConfigure = false)))
        assertNotNull(block(OperatorAction.BackReadOnlyOn, ready(readOnlyAllowed = false)))
    }

    @Test
    fun aStatusFromTheFutureOrTheDistantPastIsNotFresh() {
        assertTrue(ready().isFresh(now))
        assertFalse(ready(ageMs = OperatorLimits.STATUS_STALE_AFTER_MS + 1).isFresh(now))
        assertFalse(ready(ageMs = -60_000L).isFresh(now))
        assertFalse(OperatorStatus().isFresh(now))
        assertFalse((null as OperatorStatus?).isFresh(now))
    }

    @Test
    fun aRequestReplacesOnlyAFinishedOrAbandonedOne() {
        fun replace(action: OperatorAction, status: String?, ageMs: Long? = 1_000L, exists: Boolean = true) =
            canReplaceOperatorCommand(action, status, ageMs?.let { now - it }, exists, now)
        assertTrue(replace(OperatorAction.Release, null, null, exists = false))
        for (finished in listOf("accepted", "failed", "unconfirmed")) assertTrue(replace(OperatorAction.Release, finished))
        for (open in listOf("pending", "claimed", "something-new")) {
            assertFalse(open, replace(OperatorAction.Release, open))
            assertTrue(open, replace(OperatorAction.Release, open, OperatorLimits.COMMAND_REPLACE_AFTER_MS + 1))
            // A stop goes through whatever is there.
            assertTrue(open, replace(OperatorAction.Estop, open))
        }
    }

    @Test
    fun outcomesAreToldInTheOperatorsWords() {
        fun command(status: String, reason: String? = null, ageMs: Long = 1_000L, action: String = "REVERSE") =
            OperatorCommand("a".repeat(32), now - ageMs, "uid", action, OperatorCommandStatus.fromWire(status), reason)
        assertNull(operatorOutcomeText(null, now))
        assertEquals("Reverse to start: sent, waiting for GARBY.", operatorOutcomeText(command("pending"), now))
        assertEquals("Reverse to start: GARBY did not pick it up.",
            operatorOutcomeText(command("pending", ageMs = OperatorLimits.COMMAND_LOST_AFTER_MS + 1), now))
        assertEquals("Reverse to start: confirmed by the controllers.", operatorOutcomeText(command("accepted"), now))
        assertEquals("Reverse to start: refused (no stopped route).", operatorOutcomeText(command("failed", "NO_ROUTE"), now))
        assertEquals("Reverse to start: refused (it arrived too late).",
            operatorOutcomeText(command("failed", "expired_request"), now))
        assertTrue(operatorOutcomeText(command("unconfirmed", "controller_result_unverified"), now)!!
            .endsWith("Check GARBY before trying again."))
        assertNull(operatorOutcomeText(command("mystery"), now))
        assertEquals("SELF_DESTRUCT: refused.", operatorOutcomeText(command("failed", action = "SELF_DESTRUCT"), now))
    }

    @Test
    fun theWireNamesMatchThePi() {
        assertEquals(
            listOf("ESTOP", "RELEASE", "CONTINUE", "REVERSE", "SONAR_ON", "SONAR_OFF", "FRONT_READONLY_ON",
                "FRONT_READONLY_OFF", "BACK_READONLY_ON", "BACK_READONLY_OFF", "REBOOT"),
            OperatorAction.entries.map { it.wire }
        )
        assertEquals(OperatorAction.Reverse, OperatorAction.fromWire("REVERSE"))
        assertNull(OperatorAction.fromWire("reverse"))
        assertEquals(RobotState.Reversing, RobotState.fromWire("REVERSING"))
        assertEquals(RobotState.Unknown, RobotState.fromWire("SOMETHING_NEWER"))
    }

    @Test
    fun theStopButtonSaysWhatWasConfirmed() {
        fun state(stopped: Boolean?, fresh: Boolean = true, sending: Boolean = false, open: Boolean = false, online: Boolean = true) =
            stopButtonState(stopped, fresh, sending, open, online)
        assertEquals(StopButtonState.Ready, state(false))
        assertEquals(StopButtonState.Sending, state(false, sending = true))
        assertEquals(StopButtonState.Requested, state(false, open = true))
        assertEquals(StopButtonState.Confirmed, state(true))
        // An old report of "stopped" is not a confirmation.
        assertEquals(StopButtonState.Ready, state(true, fresh = false))
        assertEquals(StopButtonState.Requested, state(null, fresh = false, open = true))
        assertEquals(StopButtonState.Offline, state(true, online = false))
    }

    // ---- map ------------------------------------------------------------------------------

    private fun golden(): String =
        checkNotNull(javaClass.classLoader?.getResource("map_frame_golden.json")).readText()

    private fun field(json: String, name: String): String =
        checkNotNull(Regex("\"$name\": \"([^\"]*)\"").find(json)).groupValues[1]

    @Test
    fun theDecoderReadsTheFrameThePisEncoderProduced() {
        // The same file is the Pi encoder's test (RasPi/map_frame_golden.json).
        val distances = checkNotNull(decodeDistances(field(golden(), "d")))
        assertEquals(360, distances.size)
        assertEquals(280, distances[0])     // straight ahead
        assertEquals(95, distances[90])     // to the left
        assertEquals(120, distances[270])   // to the right
        assertEquals(36, distances[180])    // behind
        assertEquals(300, distances[45])    // the nearer of two returns in one degree
        assertEquals(5, distances.count { it != 0 })
        assertEquals("OBJECT_AHEAD,SLOW", field(golden(), "z"))
    }

    @Test
    fun leftIsLeftAndForwardIsForward() {
        val ahead = mapPointCm(0, 100)
        assertEquals(100f, ahead.first, 0.01f)
        assertEquals(0f, ahead.second, 0.01f)
        val left = mapPointCm(90, 100)
        assertEquals(0f, left.first, 0.01f)
        assertEquals(100f, left.second, 0.01f)
        assertEquals(-100f, mapPointCm(270, 100).second, 0.01f)
        assertEquals(-100f, mapPointCm(180, 100).first, 0.01f)
    }

    @Test
    fun damagedDistancesAreNoFrameAtAll() {
        assertNull(decodeDistances(null))
        assertNull(decodeDistances(""))
        assertNull(decodeDistances("not base64 !!"))
        assertNull(decodeDistances(java.util.Base64.getEncoder().encodeToString(ByteArray(719))))
        val bytes = ByteArray(720).also { it[0] = 0xFF.toByte(); it[1] = 0xFF.toByte() }
        assertEquals(65535, decodeDistances(java.util.Base64.getEncoder().encodeToString(bytes))!![0])
    }

    @Test
    fun markersAreDecodedAndMalformedOnesDropped() {
        val markers = decodeMarkers(listOf(
            listOf(290L, 10L, 1L, -120L, 5L), listOf(150L, -60L, 2L, 0L, 70L), listOf(400L, 0L, 3L, 0L, 0L),
            listOf(1L, 2L, 9L, 0L, 0L), listOf(1L, 2L, 1L), "text", listOf(1L, 2L, 1L, null, 0L)
        ))
        assertEquals(
            listOf(MapMarker(290, 10, MarkerKind.Person, -120, 5), MapMarker(150, -60, MarkerKind.Moving, 0, 70),
                MapMarker(400, 0, MarkerKind.Object, 0, 0)),
            markers
        )
        assertTrue(decodeMarkers(null).isEmpty())
        assertTrue(decodeMarkers("m").isEmpty())
        assertEquals(16, decodeMarkers(List(40) { listOf(1L, 2L, 3L, 0L, 0L) }).size)
    }

    @Test
    fun aFrameGoesStaleAfterThreeSeconds() {
        fun frame(ageMs: Long) = MapFrame(1, now - ageMs, 200, IntArray(360), emptyList(), "", "", "", null, null, null)
        assertFalse(frame(1_000L).isStale(now))
        assertTrue(frame(OperatorLimits.MAP_STALE_AFTER_MS + 1).isStale(now))
        assertTrue(frame(-60_000L).isStale(now))
        assertArrayEquals(IntArray(360), frame(0).distancesCm)
    }

    @Test
    fun theSummaryCountsWhatIsMarked() {
        assertNull(markerSummary(0, 0, 0))
        assertEquals("1 likely person", markerSummary(1, 0, 0))
        assertEquals("2 likely people · 1 moving object · 3 objects", markerSummary(2, 1, 3))
    }
}
