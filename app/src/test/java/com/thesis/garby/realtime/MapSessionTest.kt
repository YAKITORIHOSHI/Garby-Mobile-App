package com.thesis.garby.realtime

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class MapSessionTest {
    private val epoch = 1_800_000_000_000L
    private fun frame(seq: Long, at: Long) =
        MapFrame(seq, at, 100, IntArray(360), emptyList(), "", "", "", null, null, null)

    @Test
    fun idleMapDropsTheScanAndFullListenerAndWakesOnANewTimestamp() = runTest {
        val frames = MutableSharedFlow<MapFrame?>(replay = 1)
        val timestamps = MutableStateFlow(0L)
        var fullListeners = 0
        var timestampListeners = 0
        var displayed: MapFrame? = null
        var sleeping = false
        val job = launch {
            observeMapSession(
                frames = { flow {
                    fullListeners++
                    try { emitAll(frames) } finally { fullListeners-- }
                } },
                timestamps = { flow {
                    timestampListeners++
                    try { emitAll(timestamps) } finally { timestampListeners-- }
                } },
                nowMs = { epoch + testScheduler.currentTime },
                onFrame = { displayed = it; sleeping = false },
                onSleep = { displayed = null; sleeping = true }
            )
        }
        runCurrent()
        frames.emit(frame(1, epoch))
        runCurrent()
        advanceTimeBy(9_999)
        runCurrent()
        assertNotNull(displayed)
        assertFalse(sleeping)
        advanceTimeBy(1)
        runCurrent()
        assertTrue(sleeping)
        assertNull(displayed)
        assertEquals(0, fullListeners)
        assertEquals(1, timestampListeners)
        timestamps.value = epoch - 60_000 // cached old data cannot wake the map
        runCurrent()
        assertEquals(0, fullListeners)
        val recovered = frame(1, epoch + testScheduler.currentTime) // Pi seq may restart
        frames.emit(recovered)
        timestamps.value = recovered.atMs
        runCurrent()
        assertSame(recovered, displayed)
        assertFalse(sleeping)
        assertEquals(1, fullListeners)
        assertEquals(0, timestampListeners)
        job.cancelAndJoin()
        assertEquals(0, fullListeners)
        assertEquals(0, timestampListeners)
    }

    @Test
    fun duplicateInvalidAndStaleFramesDoNotKeepTheMapAwake() = runTest {
        val frames = MutableSharedFlow<MapFrame?>(replay = 1)
        var sleeps = 0
        var accepted = 0
        val job = launch {
            observeMapSession(
                frames = { frames }, timestamps = { MutableStateFlow(0L) },
                nowMs = { epoch + testScheduler.currentTime },
                onFrame = { accepted++ }, onSleep = { sleeps++ }
            )
        }
        runCurrent()
        val first = frame(1, epoch)
        frames.emit(first)
        runCurrent()
        advanceTimeBy(2_000)
        frames.emit(first) // a duplicate while still fresh must not reset the timeout
        runCurrent()
        advanceTimeBy(3_000)
        frames.emit(null)
        frames.emit(frame(2, epoch - 60_000))
        runCurrent()
        advanceTimeBy(5_000)
        runCurrent()
        assertEquals(1, accepted)
        assertEquals(1, sleeps)
        job.cancelAndJoin()
    }

    @Test
    fun tenSecondDeadlineRestartsOnlyForANewUsableScan() = runTest {
        val frames = MutableSharedFlow<MapFrame?>(replay = 1)
        var sleeping = false
        val job = launch {
            observeMapSession(
                frames = { frames }, timestamps = { MutableStateFlow(0L) },
                nowMs = { epoch + testScheduler.currentTime },
                onFrame = { sleeping = false }, onSleep = { sleeping = true }
            )
        }
        runCurrent()
        frames.emit(frame(1, epoch))
        runCurrent()
        advanceTimeBy(9_000)
        frames.emit(frame(2, epoch + testScheduler.currentTime))
        runCurrent()
        advanceTimeBy(9_999)
        runCurrent()
        assertFalse(sleeping)
        advanceTimeBy(1)
        runCurrent()
        assertTrue(sleeping)
        job.cancelAndJoin()
    }

    @Test
    fun aMapThatHasNeverReceivedAScanAlsoSleeps() = runTest {
        var sleeping = false
        val job = launch {
            observeMapSession(
                frames = { MutableSharedFlow() }, timestamps = { MutableStateFlow(0L) },
                nowMs = { epoch + testScheduler.currentTime },
                onFrame = { fail("No frame was published") }, onSleep = { sleeping = true }
            )
        }
        runCurrent()
        advanceTimeBy(10_000)
        runCurrent()
        assertTrue(sleeping)
        job.cancelAndJoin()
    }
}
