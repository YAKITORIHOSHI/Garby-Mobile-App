package com.thesis.garby

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.firebase.auth.FirebaseAuth
import com.thesis.garby.network.NetworkConnectivityManager
import com.thesis.garby.performance.GarbyDispatchers
import com.thesis.garby.realtime.GarbyRealtimeDb
import com.thesis.garby.realtime.MapFrame
import com.thesis.garby.realtime.MapLease
import com.thesis.garby.realtime.OperatorAction
import com.thesis.garby.realtime.OperatorCommand
import com.thesis.garby.realtime.OperatorDb
import com.thesis.garby.realtime.OperatorLimits
import com.thesis.garby.realtime.OperatorStatus
import com.thesis.garby.realtime.OperatorSubmission
import com.thesis.garby.realtime.RtdbConstants
import com.thesis.garby.realtime.operatorBlockReason
import kotlin.time.Duration.Companion.milliseconds
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** Everything the bottom bar, the Controls tab and the Map tab draw. */
data class OperatorUiState(
    val status: OperatorStatus? = null,
    /** False until the first answer from the database, so "no data" is not shown as "not running". */
    val statusLoaded: Boolean = false,
    val command: OperatorCommand? = null,
    val frame: MapFrame? = null,
    val frameLoaded: Boolean = false,
    val nowMs: Long = System.currentTimeMillis(),
    val online: Boolean = true,
    val sending: OperatorAction? = null,
    val notice: String? = null,
    /** This phone's account ID: what the owner adds to the robot's operator list. */
    val uid: String = "",
    /** The database refused this phone's map lease: it is not on the operator list. */
    val mapRefused: Boolean = false
)

/**
 * Activity-scoped, so the stop button under every tab and the Controls tab share one state.
 * Listeners run only while [observeWhileVisible] / [observeMapWhileVisible] are being collected.
 */
class OperatorViewModel : ViewModel() {
    private val deviceId = RtdbConstants.DEFAULT_DEVICE_ID
    private val _uiState = MutableStateFlow(OperatorUiState())
    val uiState: StateFlow<OperatorUiState> = _uiState.asStateFlow()
    private var sendJob: Job? = null

    /** Status, last request and a clock, for as long as the app is on screen. */
    suspend fun observeWhileVisible() = coroutineScope {
        launch(GarbyDispatchers.background) {
            OperatorDb.operatorStatus(deviceId)
                .catch { error ->
                    if (error is CancellationException) throw error
                    Log.w(TAG, "Operator status stream failed", error)
                    emit(null)
                }
                .collect { status ->
                    _uiState.update { it.copy(status = status, statusLoaded = true, nowMs = now()) }
                }
        }
        launch(GarbyDispatchers.background) {
            OperatorDb.operatorCommand(deviceId)
                .catch { error ->
                    if (error is CancellationException) throw error
                    Log.w(TAG, "Operator command stream failed", error)
                    emit(null)
                }
                .collect { command -> _uiState.update { it.copy(command = command, nowMs = now()) } }
        }
        launch {
            while (true) {
                _uiState.update {
                    it.copy(
                        nowMs = now(), online = NetworkConnectivityManager.isNetworkConnected(),
                        uid = FirebaseAuth.getInstance().currentUser?.uid.orEmpty()
                    )
                }
                delay(1_000L.milliseconds)
            }
        }
    }

    /** The map: hold a lease so the Pi publishes, and collect its frames. */
    suspend fun observeMapWhileVisible() = coroutineScope {
        launch(GarbyDispatchers.background) {
            while (true) {
                val uid = FirebaseAuth.getInstance().currentUser?.uid
                val lease = if (uid.isNullOrBlank()) MapLease.Failed else OperatorDb.renewMapLease(deviceId, uid)
                // A failed write says nothing about the list; keep the last answer.
                if (lease != MapLease.Failed) {
                    _uiState.update { it.copy(mapRefused = lease == MapLease.NotAnOperator) }
                }
                delay(OperatorLimits.MAP_LEASE_RENEW_MS.milliseconds)
            }
        }
        launch(GarbyDispatchers.background) {
            OperatorDb.mapFrame(deviceId)
                .catch { error ->
                    if (error is CancellationException) throw error
                    Log.w(TAG, "Map stream failed", error)
                    emit(null)
                }
                .collect { frame ->
                    _uiState.update { it.copy(frame = frame ?: it.frame, frameLoaded = true, nowMs = now()) }
                }
        }
    }

    /** Send [action] after rechecking the gate with the newest state. */
    fun send(action: OperatorAction) {
        if (sendJob?.isActive == true && action != OperatorAction.Estop) return
        val state = _uiState.value
        val blocked = operatorBlockReason(
            action, state.status, now(), NetworkConnectivityManager.isNetworkConnected(),
            busy = state.sending != null && action != OperatorAction.Estop
        )
        val uid = FirebaseAuth.getInstance().currentUser?.uid?.takeIf { it.isNotBlank() }
        if (blocked != null || uid == null) {
            _uiState.update { it.copy(notice = blocked ?: "Your session ended. Connect again.") }
            return
        }
        _uiState.update { it.copy(sending = action, notice = null) }
        sendJob = viewModelScope.launch(GarbyDispatchers.background) {
            try {
                val notice = when (val result = OperatorDb.send(deviceId, uid, action)) {
                    is OperatorSubmission.Sent -> null
                    is OperatorSubmission.NotSent -> result.message
                    is OperatorSubmission.Unconfirmed -> result.message
                }
                _uiState.update { it.copy(notice = notice) }
            } finally {
                _uiState.update { if (it.sending == action) it.copy(sending = null) else it }
            }
        }
    }

    fun clearNotice() {
        _uiState.update { it.copy(notice = null) }
    }

    fun onSignedOut() {
        sendJob?.cancel()
        _uiState.value = OperatorUiState()
    }

    private fun now(): Long = GarbyRealtimeDb.serverNowMs()

    private companion object {
        const val TAG = "GarbyOperatorVM"
    }
}
