package com.thesis.garby.network

import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChanged

internal const val OFFLINE_CONFIRMATION_MS = 3_000L

/** A network handoff may briefly lose validation. Recovery cancels a pending sign-out. */
internal suspend fun watchSessionInternet(
    internet: Flow<Boolean>,
    confirmationMs: Long = OFFLINE_CONFIRMATION_MS,
    onOnline: () -> Unit,
    onOffline: () -> Unit
) {
    internet.distinctUntilChanged().collectLatest { online ->
        if (online) onOnline()
        else {
            delay(confirmationMs)
            onOffline()
        }
    }
}
