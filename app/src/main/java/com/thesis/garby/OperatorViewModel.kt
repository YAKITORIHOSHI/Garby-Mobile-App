package com.thesis.garby

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.firebase.auth.FirebaseAuth
import com.thesis.garby.network.NetworkConnectivityManager
import com.thesis.garby.performance.GarbyDispatchers
import com.thesis.garby.realtime.ConnectionState
import com.thesis.garby.realtime.GarbyRealtimeDb
import com.thesis.garby.realtime.MapFrame
import com.thesis.garby.realtime.MapLease
import com.thesis.garby.realtime.OperatorAction
import com.thesis.garby.realtime.OperatorCommand
import com.thesis.garby.realtime.OperatorCommandStatus
import com.thesis.garby.realtime.OperatorDb
import com.thesis.garby.realtime.OperatorLimits
import com.thesis.garby.realtime.OperatorStatus
import com.thesis.garby.realtime.OperatorSubmission
import com.thesis.garby.realtime.RtdbConstants
import com.thesis.garby.realtime.RunProgram
import com.thesis.garby.realtime.RobotActivityState
import com.thesis.garby.realtime.resolveRobotActivity
import com.thesis.garby.realtime.observeMapSession
import com.thesis.garby.realtime.operatorBlockReason
import com.thesis.garby.realtime.isFresh
import com.thesis.garby.ui.StopButtonState
import com.thesis.garby.ui.stopButtonState
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.conflate
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlin.time.Duration.Companion.milliseconds

/** Everything the bottom bar, the Controls tab and the Map tab draw. */
data class OperatorUiState(
    val status: OperatorStatus? = null,
    /** False until the first answer from the database, so "no data" is not shown as "not running". */
    val statusLoaded: Boolean = false,
    val command: OperatorCommand? = null,
    val frame: MapFrame? = null,
    val frameLoaded: Boolean = false,
    val mapSleeping: Boolean = false,
    val mapError: String? = null,
    val nowMs: Long = System.currentTimeMillis(),
    val online: Boolean = true,
    val cloudConnected: Boolean = false,
    val sending: OperatorAction? = null,
    val notice: String? = null,
    /** This phone's account ID: what the owner adds to the robot's operator list. */
    val uid: String = "",
    /** The database refused this phone's map lease: it is not on the operator list. */
    val mapRefused: Boolean = false
)

/** Map frames and clock ticks cannot redraw navigation unless the stop button actually changes. */
internal fun Flow<OperatorUiState>.stopButtonStates(): Flow<StopButtonState> = map { state ->
    val command = state.command?.takeIf { it.action == OperatorAction.Estop.wire }
    stopButtonState(
        emergencyStopped = state.status?.emergencyStopped,
        statusFresh = state.status.isFresh(state.nowMs),
        sendingStop = state.sending == OperatorAction.Estop,
        stopRequestOpen = command != null &&
            (command.status == OperatorCommandStatus.Pending || command.status == OperatorCommandStatus.Claimed) &&
            state.nowMs - command.requestedAtMs < 30_000L,
        online = state.online,
        controllersOnline = state.status?.let { it.connected && it.supported } == true,
        cloudConnected = state.cloudConnected
    )
}.distinctUntilChanged().conflate()

/**
 * Activity-scoped, so the stop button under every tab and the Controls tab share one state.
 * Listeners run only while [observeWhileVisible] / [observeMapWhileVisible] are being collected.
 */
class OperatorViewModel : ViewModel() {
    private val deviceId = RtdbConstants.DEFAULT_DEVICE_ID
    private val _uiState = MutableStateFlow(OperatorUiState())
    val uiState: StateFlow<OperatorUiState> = _uiState.asStateFlow()
    val stopState: StateFlow<StopButtonState> = uiState.stopButtonStates()
        .flowOn(GarbyDispatchers.background)
        .stateIn(viewModelScope,
            SharingStarted.WhileSubscribed(stopTimeoutMillis = 0, replayExpirationMillis = 0),
            StopButtonState.PiUnavailable)
    val activityState: StateFlow<RobotActivityState> = uiState.map { state ->
        resolveRobotActivity(state.status, state.statusLoaded, state.nowMs, state.online, state.cloudConnected)
    }.distinctUntilChanged().conflate().flowOn(GarbyDispatchers.background)
        .stateIn(viewModelScope,
            SharingStarted.WhileSubscribed(stopTimeoutMillis = 0, replayExpirationMillis = 0),
            RobotActivityState())
    private var sendJob: Job? = null

    /** Status, last request and a clock, for as long as the app is on screen. */
    suspend fun observeWhileVisible() {
        coroutineScope {
            _uiState.update { it.copy(cloudConnected = false, nowMs = now()) }
            launch(GarbyDispatchers.background) {
                GarbyRealtimeDb.connectionState.collect { connection: ConnectionState ->
                    _uiState.update { it.copy(cloudConnected = connection is ConnectionState.Connected) }
                }
            }
            launch(GarbyDispatchers.background) {
                OperatorDb.operatorStatus(deviceId)
                    .catch { error ->
                        if (error is CancellationException) throw error
                        Log.w(TAG, "Operator status stream failed", error)
                        emit(null)
                    }
                    .collect { status: OperatorStatus? ->
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
                    .collect { command: OperatorCommand? ->
                        _uiState.update { it.copy(command = command, nowMs = now()) }
                    }
            }
            launch(GarbyDispatchers.background) {
                NetworkConnectivityManager.isInternetAvailable.collect { available ->
                    _uiState.update { it.copy(online = available, nowMs = now()) }
                }
            }
            launch(GarbyDispatchers.background) {
                userIds().collect { uid -> _uiState.update { it.copy(uid = uid, nowMs = now()) } }
            }
            launch(GarbyDispatchers.background) {
                GarbyRealtimeDb.monitoringTimeMs.collect {
                    _uiState.update { it.copy(nowMs = now()) }
                }
            }
        }
    }

    /** Full scans are retained only while visible and receiving updates. */
    suspend fun observeMapWhileVisible() {
        try {
            coroutineScope {
                launch(GarbyDispatchers.background) {
                    GarbyRealtimeDb.connectionState.collectLatest { connection: ConnectionState ->
                        if (connection is ConnectionState.Connected) {
                            while (true) {
                                val uid = FirebaseAuth.getInstance().currentUser?.uid
                                val lease = if (uid.isNullOrBlank()) MapLease.Failed
                                    else OperatorDb.renewMapLease(deviceId, uid)
                                // A failed write says nothing about the list; keep the last answer.
                                if (lease != MapLease.Failed) {
                                    _uiState.update { it.copy(mapRefused = lease == MapLease.NotAnOperator) }
                                }
                                delay(OperatorLimits.MAP_LEASE_RENEW_MS.milliseconds)
                            }
                        }
                    }
                }
                launch(GarbyDispatchers.background) {
                    observeMapSession(
                        frames = { OperatorDb.mapFrame(deviceId) },
                        timestamps = { OperatorDb.mapTimestamp(deviceId) },
                        nowMs = ::now,
                        onFrame = { frame: MapFrame ->
                            _uiState.update {
                                it.copy(frame = frame, frameLoaded = true, mapSleeping = false,
                                    mapError = null, nowMs = now())
                            }
                        },
                        onSleep = {
                            _uiState.update { it.copy(frame = null, frameLoaded = true, mapSleeping = true) }
                        }
                    )
                }
            }
        } catch (error: CancellationException) {
            throw error
        } catch (error: Exception) {
            Log.w(TAG, "Map stream failed", error)
            _uiState.update {
                it.copy(frame = null, frameLoaded = true, mapSleeping = true,
                    mapError = "Map connection unavailable")
            }
            awaitCancellation() // Leave a static placeholder until the tab is opened again.
        } finally {
            _uiState.update {
                it.copy(frame = null, frameLoaded = false, mapSleeping = false, mapError = null)
            }
        }
    }

    /** Send [action] after rechecking the gate with the newest state. */
    fun send(action: OperatorAction, sensors: Int? = null, fast: Boolean? = null) {
        submit(action, sensors, fast, run = null)
    }

    /** Start run of the RunStart list typed on the phone. */
    fun startRun(program: RunProgram) {
        submit(OperatorAction.DevRunStart, sensors = null, fast = null, run = program)
    }

    private fun submit(action: OperatorAction, sensors: Int?, fast: Boolean?, run: RunProgram?) {
        if (action == OperatorAction.DevRunStart && run == null) return  // only with its list
        // A stop never waits for the request still being written: it may overtake it.
        val preempts = action == OperatorAction.Estop || action.stopsDeveloperMotion
        if (sendJob?.isActive == true && !preempts) return
        val state = _uiState.value
        val blocked = operatorBlockReason(
            action, state.status, now(), NetworkConnectivityManager.isNetworkConnected(),
            busy = state.sending != null && !preempts,
            cloudConnected = state.cloudConnected
        )
        val uid = FirebaseAuth.getInstance().currentUser?.uid?.takeIf { it.isNotBlank() }
        if (blocked != null || uid == null) {
            _uiState.update { it.copy(notice = blocked ?: "Your session ended. Connect again.") }
            return
        }
        _uiState.update { it.copy(sending = action, notice = null) }
        sendJob = viewModelScope.launch(GarbyDispatchers.background) {
            try {
                val result = if (sensors == null && fast == null && run == null) {
                    OperatorDb.send(deviceId, uid, action)
                } else {
                    OperatorDb.send(deviceId, uid, action, sensors, fast, run)
                }
                val notice = when (result) {
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

    private fun userIds(): Flow<String> = callbackFlow {
        val auth = FirebaseAuth.getInstance()
        val listener = FirebaseAuth.AuthStateListener { current ->
            trySend(current.currentUser?.uid.orEmpty())
        }
        auth.addAuthStateListener(listener)
        awaitClose { auth.removeAuthStateListener(listener) }
    }.distinctUntilChanged().conflate()

    private fun now(): Long = GarbyRealtimeDb.serverNowMs()

    private companion object {
        const val TAG = "GarbyOperatorVM"
    }
}
