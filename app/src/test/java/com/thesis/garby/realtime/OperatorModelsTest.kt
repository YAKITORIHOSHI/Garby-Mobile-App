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
    fun emergencyStopNeedsBothPiAndMcuOnlineButPreemptsPendingActions() {
        assertNotNull(block(OperatorAction.Estop, null))
        assertNotNull(block(OperatorAction.Estop, ready(ageMs = 600_000L)))
        assertNotNull(block(OperatorAction.Estop, ready().copy(connected = false)))
        assertNotNull(block(OperatorAction.Estop, ready().copy(supported = false)))
        assertNotNull(block(OperatorAction.Estop, ready(), online = false))
        assertNull(block(OperatorAction.Estop, ready(pending = true), busy = true))
        assertNotNull(operatorBlockReason(OperatorAction.Estop, ready(), now,
            online = true, busy = false, cloudConnected = false))
    }

    @Test
    fun everythingElseNeedsAFreshConfirmedState() {
        val others = OperatorAction.entries.filter { it != OperatorAction.Estop && !it.isDeveloperAction }
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

    private val partway = DeveloperRun(known = true, place = RunPlace.Partway, step = 1, done = 420,
        steps = parseRunSteps("F4000,L980"), speed = 2351, accel = 4800)

    @Test
    fun benchTestsAndNudgesFollowThePisOwnDeveloperReadiness() {
        // 2026-11-18: the phone used to offer tests the Pi refuses (a route running, not idle).
        val starts = OperatorAction.entries.filter { it.isBenchTest || it.isNudge }
        val reason = "Emergency stop is on. Release it on the Controls tab first."
        for (action in starts) {
            assertNull(action.name, block(action, ready().copy(developerEnabled = true)))
            assertEquals(action.name, reason, block(action, ready().copy(developerBlocked = reason)))
            assertEquals(action.name, "Developer tests are not available right now.", block(action, ready()))
            assertEquals(action.name, "A test is running. Stop test ends it.",
                block(action, ready().copy(developerEnabled = true, developerPending = true)))
            assertNotNull(action.name, block(action, ready(pending = true).copy(developerEnabled = true)))
        }
    }

    @Test
    fun theDeveloperStopsAreNeverHeldBackByWhatTheyStop() {
        val running = ready(pending = true).copy(developerPending = true)
        assertNull(block(OperatorAction.DevCancel, running, busy = true))
        assertNull(block(OperatorAction.DevHoldStop, running, busy = true))
        assertNull(block(OperatorAction.DevHoldStop, ready()))
        assertEquals("No test is running.", block(OperatorAction.DevCancel, ready()))
        // They still need a reporting Pi and live controllers.
        assertNotNull(block(OperatorAction.DevCancel, running.copy(connected = false)))
        assertNotNull(block(OperatorAction.DevHoldStop, null))
    }

    @Test
    fun runStartFollowsThePisRunStartTab() {
        val idle = ready().copy(developerEnabled = true, developerRun = partway)
        for (action in listOf(OperatorAction.DevRunContinue, OperatorAction.DevRunUndo, OperatorAction.DevRunClear)) {
            assertNull(action.name, block(action, idle))
            assertEquals(action.name, "Update GARBY's robot software for RunStart from the phone.",
                block(action, idle.copy(developerRun = null)))
            assertEquals(action.name, "Waiting for the robot to report its saved list.",
                block(action, idle.copy(developerRun = DeveloperRun())))
            assertEquals(action.name, "No saved list. Lists are made in the Pi's Developer window.",
                block(action, idle.copy(developerRun = DeveloperRun(known = true, place = RunPlace.None))))
            assertEquals(action.name, "The list is moving. Stop test stops it.",
                block(action, idle.copy(developerRun = partway.copy(running = true))))
            assertNotNull(action.name, block(action, idle.copy(developerRun = partway.copy(waitingRelease = true))))
        }
        assertEquals("The list finished. Reverse to start can take it back.",
            block(OperatorAction.DevRunContinue, idle.copy(developerRun = partway.copy(place = RunPlace.End))))
        assertNull(block(OperatorAction.DevRunUndo, idle.copy(developerRun = partway.copy(place = RunPlace.End))))
        assertEquals("The list is at its start.",
            block(OperatorAction.DevRunUndo, idle.copy(developerRun = partway.copy(place = RunPlace.Start))))
        assertNull(block(OperatorAction.DevRunContinue, idle.copy(developerRun = partway.copy(place = RunPlace.Start))))
        // After an emergency stop the Pi releases it first, as its own buttons do.
        val stopped = ready(stopped = true, canRelease = true).copy(developerRun = partway,
            developerBlocked = "Emergency stop is on. Release it on the Controls tab first.")
        assertNull(block(OperatorAction.DevRunContinue, stopped))
        assertNull(block(OperatorAction.DevRunUndo, stopped))
        assertEquals("Emergency stop is on. Release it on the Controls tab first.",
            block(OperatorAction.DevRunContinue, stopped.copy(canRelease = false)))
        // Discard only forgets: allowed while stopped, never while the list moves.
        assertNull(block(OperatorAction.DevRunClear, stopped.copy(canRelease = false)))
    }

    @Test
    fun onlyBenchTestsCarrySensorsAndOnlyNudgesCarrySpeed() {
        // Requests with extra fields failed the database rules that predate developer controls.
        for (action in OperatorAction.entries) {
            val (sensors, fast) = requestFields(action, 5, true)
            assertEquals(action.name, if (action.wire.startsWith("DEV_TEST_")) 5 else null, sensors)
            assertEquals(action.name, if (action.wire.matches(Regex("DEV_HOLD_[WSAD]"))) true else null, fast)
        }
        assertEquals(listOf(OperatorAction.DevTestL, OperatorAction.DevTestR, OperatorAction.DevTestL90,
            OperatorAction.DevTestR90), OperatorAction.entries.filter { it.isTurnTest })
    }

    @Test
    fun runStepsAreReadExactlyOrNotAtAll() {
        assertEquals(listOf(RunStep('F', 4000), RunStep('L', 980), RunStep('C', 100_000)),
            parseRunSteps("F4000,L980,C100000"))
        assertEquals("Bump calibrate", RunStep('C', 1).name)
        for (bad in listOf(null, "", "X10", "F0", "F100001", "F10,,L2", "F", "F1.5",
            (1..21).joinToString(",") { "F1" })) {
            assertTrue(bad.toString(), parseRunSteps(bad).isEmpty())
        }
        assertEquals(20, parseRunSteps((1..20).joinToString(",") { "B$it" }).size)
        assertFalse(DeveloperRun(known = true, place = RunPlace.Partway).hasSaved)
        assertTrue(partway.hasSaved)
        assertEquals(RunPlace.Unknown, RunPlace.fromWire("sideways"))
    }

    @Test
    fun startRunFollowsThePisStartButton() {
        val idle = ready().copy(developerEnabled = true, developerRun = DeveloperRun(known = true, place = RunPlace.None))
        assertNull(block(OperatorAction.DevRunStart, idle))
        // A finished or unstarted list may be replaced; one standing part-way locks the rows.
        assertNull(block(OperatorAction.DevRunStart, idle.copy(developerRun = partway.copy(place = RunPlace.End))))
        assertNull(block(OperatorAction.DevRunStart, idle.copy(developerRun = DeveloperRun())))
        assertEquals("A list stands part-way. Continue run, Reverse to start or Discard saved first.",
            block(OperatorAction.DevRunStart, idle.copy(developerRun = partway)))
        assertEquals("The list is moving. Stop test stops it.",
            block(OperatorAction.DevRunStart, idle.copy(developerRun = partway.copy(running = true))))
        assertEquals("Update GARBY's robot software for RunStart from the phone.",
            block(OperatorAction.DevRunStart, idle.copy(developerRun = null)))
        assertEquals("Emergency stop is on. Release it on the Controls tab first.",
            block(OperatorAction.DevRunStart, idle.copy(developerEnabled = false,
                developerBlocked = "Emergency stop is on. Release it on the Controls tab first.")))
    }

    @Test
    fun theTypedListIsCheckedInThePiWindowsWords() {
        fun check(rows: List<RunDraftRow>, speed: String = "2351", accel: String = "4800") =
            checkRunDraft(rows, speed, accel)
        fun problem(rows: List<RunDraftRow>, speed: String = "2351", accel: String = "4800") =
            (check(rows, speed, accel) as RunDraftCheck.Problem).reason
        val ready = check(listOf(RunDraftRow('F', "10000"), RunDraftRow('L', "980"), RunDraftRow('C', "007")),
            speed = "8000", accel = "20000") as RunDraftCheck.Ready
        assertEquals("F10000,L980,C7", ready.program.wire)
        assertEquals(8000 to 20000, ready.program.speed to ready.program.accel)
        assertEquals("Add at least one step.", problem(emptyList()))
        assertEquals("At most 20 steps.", problem(List(21) { RunDraftRow('F', "1") }))
        assertTrue(check(List(20) { RunDraftRow('F', "1") }) is RunDraftCheck.Ready)
        assertEquals("Step 2: enter the number of steps (1–100000).",
            problem(listOf(RunDraftRow('F', "1"), RunDraftRow('B', ""))))
        assertEquals("Step 1: steps must be a whole number from 1 to 100000.", problem(listOf(RunDraftRow('F', "0"))))
        assertEquals("Step 1: steps must be a whole number from 1 to 100000.", problem(listOf(RunDraftRow('F', "100001"))))
        assertEquals("Step 1: choose what it does.", problem(listOf(RunDraftRow('X', "5"))))
        val one = listOf(RunDraftRow('F', "5"))
        assertEquals("Max speed: enter a whole number.", problem(one, speed = ""))
        assertEquals("Max speed must be a whole number from 2351 to 8000 steps/s.", problem(one, speed = "2350"))
        assertEquals("Max speed must be a whole number from 2351 to 8000 steps/s.", problem(one, speed = "99999999999"))
        assertEquals("Acceleration: enter a whole number.", problem(one, accel = "4.8"))
        assertEquals("Acceleration must be a whole number from 100 to 20000 steps/s².", problem(one, accel = "20001"))
        // The rows survive rotation as text, and anything foreign in it is dropped.
        val rows = listOf(RunDraftRow('F', "4000"), RunDraftRow('R', ""), RunDraftRow('C', "100000"))
        assertEquals(rows, decodeRunDraft(encodeRunDraft(rows)))
        assertEquals(listOf(RunDraftRow()), decodeRunDraft(""))
        assertEquals(listOf(RunDraftRow('B', "12")), decodeRunDraft("B1x2,Q9"))
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
        assertNotNull(block(OperatorAction.SideReadOnlyOn, ready(readOnlyAllowed = false)))
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
        // Stop test / Stop nudge overtake an unfinished developer request, and nothing else.
        fun overtake(action: OperatorAction, existing: String) =
            canReplaceOperatorCommand(action, "pending", now - 1_000L, true, now, existingAction = existing)
        assertTrue(overtake(OperatorAction.DevHoldStop, "DEV_HOLD_W"))
        assertTrue(overtake(OperatorAction.DevCancel, "DEV_RUN_CONTINUE"))
        assertFalse(overtake(OperatorAction.DevCancel, "RELEASE"))
        assertFalse(overtake(OperatorAction.DevHoldW, "DEV_TEST_F"))
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
        // A developer request is accepted when the Pi takes it, not when the robot confirms it.
        assertEquals("Nudge forward: taken by GARBY's Pi. Bench status shows how it goes.",
            operatorOutcomeText(command("accepted", action = "DEV_HOLD_W"), now))
        assertEquals("Turn left 10°: refused (Turns need the gyro (MPU) ticked).",
            operatorOutcomeText(command("failed", "Turns need the gyro (MPU) ticked.", action = "DEV_TEST_L"), now))
        assertEquals("SELF_DESTRUCT: refused.", operatorOutcomeText(command("failed", action = "SELF_DESTRUCT"), now))
    }

    @Test
    fun theWireNamesMatchThePi() {
        assertEquals(
            listOf("ESTOP", "RELEASE", "CONTINUE", "REVERSE", "SONAR_ON", "SONAR_OFF", "FRONT_READONLY_ON",
                "FRONT_READONLY_OFF", "BACK_READONLY_ON", "BACK_READONLY_OFF", "SIDE_READONLY_ON",
                "SIDE_READONLY_OFF", "REBOOT",
                "DEV_TEST_F", "DEV_TEST_B", "DEV_TEST_L", "DEV_TEST_R", "DEV_TEST_L90", "DEV_TEST_R90",
                "DEV_TEST_M1", "DEV_TEST_M2", "DEV_TEST_I", "DEV_CANCEL",
                "DEV_RUN_START", "DEV_RUN_CONTINUE", "DEV_RUN_UNDO", "DEV_RUN_CLEAR",
                "DEV_HOLD_W", "DEV_HOLD_S", "DEV_HOLD_A", "DEV_HOLD_D", "DEV_HOLD_STOP"),
            OperatorAction.entries.map { it.wire }
        )
        assertEquals(OperatorAction.Reverse, OperatorAction.fromWire("REVERSE"))
        assertNull(OperatorAction.fromWire("reverse"))
        assertEquals(RobotState.Reversing, RobotState.fromWire("REVERSING"))
        assertEquals(RobotState.Unknown, RobotState.fromWire("SOMETHING_NEWER"))
    }

    @Test
    fun theStopButtonSaysWhatWasConfirmed() {
        fun state(stopped: Boolean?, fresh: Boolean = true, sending: Boolean = false,
            open: Boolean = false, online: Boolean = true, mcu: Boolean = true, cloud: Boolean = true) =
            stopButtonState(stopped, fresh, sending, open, online, mcu, cloud)
        assertEquals(StopButtonState.Ready, state(false))
        assertEquals(StopButtonState.Sending, state(false, sending = true))
        assertEquals(StopButtonState.Requested, state(false, open = true))
        assertEquals(StopButtonState.Confirmed, state(true))
        assertEquals(StopButtonState.PiUnavailable, state(true, fresh = false))
        assertEquals(StopButtonState.PiUnavailable, state(null, fresh = false, open = true))
        assertEquals(StopButtonState.ControllersUnavailable, state(true, mcu = false))
        assertEquals(StopButtonState.Offline, state(true, online = false))
        assertEquals(StopButtonState.CloudUnavailable, state(true, cloud = false))
        assertFalse(state(true, fresh = false).available)
        assertFalse(state(true, mcu = false).available)
        assertFalse(state(true, cloud = false).available)
        assertTrue(state(false).available)
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
