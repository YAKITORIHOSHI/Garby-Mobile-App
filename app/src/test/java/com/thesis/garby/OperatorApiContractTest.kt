package com.thesis.garby

import com.thesis.garby.realtime.MapFrame
import com.thesis.garby.realtime.OperatorAction
import com.thesis.garby.realtime.OperatorCommand
import com.thesis.garby.realtime.OperatorCommandStatus
import com.thesis.garby.realtime.OperatorDb
import com.thesis.garby.realtime.OperatorLimits
import com.thesis.garby.realtime.OperatorStatus
import com.thesis.garby.realtime.OperatorSubmission
import com.thesis.garby.realtime.RunProgram
import com.thesis.garby.realtime.observeMapSession
import java.lang.reflect.ParameterizedType
import java.lang.reflect.WildcardType
import kotlin.coroutines.Continuation
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import com.thesis.garby.ui.StopButtonState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test

/** Compile the actual consumer signatures together; constructing a robot/Firebase client is unnecessary. */
@OptIn(ExperimentalCoroutinesApi::class)
class OperatorApiContractTest {
    private val now = 1_800_000_000_000L

    private fun live() = OperatorUiState(
        status = OperatorStatus(updatedAtMs = now, connected = true, supported = true,
            emergencyStopped = false),
        statusLoaded = true, online = true, cloudConnected = true, nowMs = now
    )

    @Test
    fun mapAndClockUpdatesDoNotPublishUnchangedDockStateButStaleAndStopStillDo() = runTest {
        val states = MutableSharedFlow<OperatorUiState>()
        val seen = mutableListOf<StopButtonState>()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            states.stopButtonStates().collect { seen.add(it) }
        }
        runCurrent()
        val base = live()
        states.emit(base)
        runCurrent()
        repeat(60) { index ->
            states.emit(base.copy(nowMs = now + index,
                frame = MapFrame(index.toLong(), now, 100, IntArray(360), emptyList(),
                    "", "", "", null, null, null)))
            runCurrent()
        }
        assertEquals(listOf(StopButtonState.Ready), seen)
        val expiredAt = now + OperatorLimits.STATUS_STALE_AFTER_MS + 1
        states.emit(base.copy(nowMs = expiredAt))
        runCurrent()
        assertEquals(StopButtonState.PiUnavailable, seen.last())
        val stopped = base.copy(nowMs = expiredAt,
            status = base.status!!.copy(updatedAtMs = expiredAt, emergencyStopped = true))
        states.emit(stopped)
        runCurrent()
        assertEquals(StopButtonState.Confirmed, seen.last())
        states.emit(stopped.copy(online = false))
        runCurrent()
        assertEquals(listOf(StopButtonState.Ready, StopButtonState.PiUnavailable,
            StopButtonState.Confirmed, StopButtonState.Offline), seen)
    }

    @Test
    fun pendingStopExpiryIsRecheckedWithoutADatabaseCallback() = runTest {
        val states = MutableSharedFlow<OperatorUiState>()
        val seen = mutableListOf<StopButtonState>()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            states.stopButtonStates().collect { seen.add(it) }
        }
        runCurrent()
        val base = live().copy(
            status = live().status!!.copy(updatedAtMs = now + 6_000), nowMs = now + 6_000,
            command = OperatorCommand("a".repeat(32), now, "uid", "ESTOP",
                OperatorCommandStatus.Pending, null))
        states.emit(base)
        runCurrent()
        states.emit(base.copy(nowMs = now + 29_999))
        runCurrent()
        states.emit(base.copy(nowMs = now + 30_000))
        runCurrent()
        assertEquals(listOf(StopButtonState.Requested, StopButtonState.Ready), seen)
    }

    @Test
    fun lifecycleObserversReturnUnitRatherThanJob() {
        for (name in listOf("observeWhileVisible", "observeMapWhileVisible")) {
            val method = OperatorViewModel::class.java.getDeclaredMethod(name, Continuation::class.java)
            val continuation = method.genericParameterTypes.last() as ParameterizedType
            val result = continuation.actualTypeArguments.single() as WildcardType
            assertEquals(name, Unit::class.java, result.lowerBounds.single())
        }
    }

    @Test
    fun databaseAndMapHelpersMatchTheViewModelsCalls() {
        val ordinary: suspend (String, String, OperatorAction) -> OperatorSubmission = OperatorDb::send
        val configured: suspend (String, String, OperatorAction, Int?, Boolean?) -> OperatorSubmission =
            OperatorDb::send
        val withList: suspend (String, String, OperatorAction, Int?, Boolean?, RunProgram?) -> OperatorSubmission =
            OperatorDb::send
        val startRun: (OperatorViewModel, RunProgram) -> Unit = OperatorViewModel::startRun
        assertNotNull(withList)
        assertNotNull(startRun)
        val timestamps: (String) -> Flow<Long> = OperatorDb::mapTimestamp
        val session: suspend (
            () -> Flow<MapFrame?>, () -> Flow<Long>, () -> Long, (MapFrame) -> Unit, () -> Unit
        ) -> Unit = ::observeMapSession
        assertNotNull(ordinary)
        assertNotNull(configured)
        assertNotNull(timestamps)
        assertNotNull(session)
    }
}
