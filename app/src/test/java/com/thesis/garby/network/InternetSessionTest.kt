package com.thesis.garby.network

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class InternetSessionTest {
    @Test
    fun offlineIsConfirmedOnceAndReconnectDoesNotAutomaticallyStartASession() = runTest {
        val internet = MutableStateFlow(true)
        var onlineEvents = 0
        var signOuts = 0
        val watcher = backgroundScope.launch {
            watchSessionInternet(internet, onOnline = { onlineEvents++ }, onOffline = { signOuts++ })
        }
        runCurrent()
        assertEquals(1, onlineEvents)
        internet.value = false
        runCurrent()
        advanceTimeBy(OFFLINE_CONFIRMATION_MS - 1)
        runCurrent()
        assertEquals(0, signOuts)
        advanceTimeBy(1)
        runCurrent()
        assertEquals(1, signOuts)
        advanceTimeBy(60_000)
        runCurrent()
        assertEquals(1, signOuts) // no repeated polling/sign-outs while offline
        internet.value = true
        runCurrent()
        assertEquals(2, onlineEvents)
        assertEquals(1, signOuts)
        watcher.cancelAndJoin()
    }

    @Test
    fun briefHandoffCancelsThePendingOfflineActionAndALaterOutageStartsAFreshWindow() = runTest {
        val internet = MutableStateFlow(true)
        var signOuts = 0
        val watcher = backgroundScope.launch {
            watchSessionInternet(internet, onOnline = {}, onOffline = { signOuts++ })
        }
        runCurrent()
        internet.value = false
        runCurrent()
        advanceTimeBy(OFFLINE_CONFIRMATION_MS - 1)
        internet.value = true
        runCurrent()
        advanceTimeBy(OFFLINE_CONFIRMATION_MS)
        runCurrent()
        assertEquals(0, signOuts)
        internet.value = false
        runCurrent()
        advanceTimeBy(OFFLINE_CONFIRMATION_MS)
        runCurrent()
        assertEquals(1, signOuts)
        watcher.cancelAndJoin()
    }

    @Test
    fun startupOfflineIsHandledAndCancellationDoesNotLeaveAnOfflineTimer() = runTest {
        val internet = MutableStateFlow(false)
        var signOuts = 0
        val watcher = launch {
            watchSessionInternet(internet, onOnline = {}, onOffline = { signOuts++ })
        }
        runCurrent()
        advanceTimeBy(OFFLINE_CONFIRMATION_MS)
        runCurrent()
        assertEquals(1, signOuts)
        internet.value = true
        runCurrent()
        internet.value = false
        runCurrent()
        watcher.cancelAndJoin()
        advanceTimeBy(OFFLINE_CONFIRMATION_MS)
        runCurrent()
        assertEquals(1, signOuts)
    }
}
