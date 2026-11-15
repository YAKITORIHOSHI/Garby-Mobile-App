package com.thesis.garby.realtime

import android.util.Log
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.MutableData
import com.google.firebase.database.ServerValue
import com.google.firebase.database.Transaction
import com.google.firebase.database.ValueEventListener
import com.thesis.garby.network.NetworkConnectivityManager
import com.thesis.garby.performance.GarbyDispatchers
import java.util.UUID
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlin.time.Duration.Companion.milliseconds
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.conflate
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withTimeout

/** What became of a request this phone tried to write. */
sealed interface OperatorSubmission {
    data class Sent(val requestId: String) : OperatorSubmission
    data class NotSent(val message: String) : OperatorSubmission
    data class Unconfirmed(val message: String) : OperatorSubmission
}

/**
 * Realtime Database access for phone control and the LiDAR map. Listeners live only while a
 * screen collects them; the map lease is written only while the map is on screen.
 */
object OperatorDb {
    private const val TAG = "GarbyOperator"
    private val validDeviceId = Regex("^[A-Za-z0-9_-]{1,64}$")

    private val database by lazy {
        try {
            FirebaseDatabase.getInstance(RtdbConstants.DATABASE_URL)
        } catch (_: Exception) {
            FirebaseDatabase.getInstance()
        }
    }

    private fun DataSnapshot.string(key: String): String? =
        (child(key).value as? String)?.takeIf { it.isNotBlank() }

    private fun DataSnapshot.bool(key: String): Boolean? = child(key).value as? Boolean

    private fun DataSnapshot.long(key: String): Long? = (child(key).value as? Number)?.toLong()

    private fun DataSnapshot.double(key: String): Double? = (child(key).value as? Number)?.toDouble()

    private fun snapshots(path: String): Flow<DataSnapshot> = callbackFlow {
        val ref = database.getReference(path)
        val listener = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                trySend(snapshot)
            }

            override fun onCancelled(error: DatabaseError) {
                close(error.toException())
            }
        }
        ref.addValueEventListener(listener)
        awaitClose { ref.removeEventListener(listener) }
    }.conflate()

    private fun requireValid(deviceId: String) {
        require(validDeviceId.matches(deviceId)) { "Invalid device id" }
    }

    /** The Pi's mirror of its Controls tab; null until the Pi has written one. */
    fun operatorStatus(deviceId: String = RtdbConstants.DEFAULT_DEVICE_ID): Flow<OperatorStatus?> {
        requireValid(deviceId)
        return snapshots("devices/$deviceId/operator").map { extractStatus(it) }
            .conflate().flowOn(GarbyDispatchers.background)
    }

    internal fun extractStatus(snapshot: DataSnapshot): OperatorStatus? {
        if (!snapshot.exists()) return null
        val launcher = snapshot.child("launcher")
        return OperatorStatus(
            updatedAtMs = snapshot.long("updatedAt") ?: 0L,
            supported = snapshot.bool("supported") ?: false,
            connected = snapshot.bool("connected") ?: false,
            pending = snapshot.bool("pending") ?: false,
            emergencyStopped = snapshot.bool("emergencyStopped"),
            ultrasonicEnabled = snapshot.bool("ultrasonicEnabled"),
            canConfigure = snapshot.bool("canConfigure") ?: false,
            canRelease = snapshot.bool("canRelease") ?: false,
            canContinue = snapshot.bool("canContinue") ?: false,
            canReverse = snapshot.bool("canReverse") ?: false,
            canReset = snapshot.bool("canReset") ?: false,
            message = snapshot.string("message").orEmpty(),
            robotState = snapshot.string("robotState").orEmpty(),
            frontReadOnly = snapshot.bool("frontReadOnly") ?: false,
            backReadOnly = snapshot.bool("backReadOnly") ?: false,
            readOnlyAllowed = snapshot.bool("readOnlyAllowed") ?: false,
            motionHold = snapshot.string("motionHold").orEmpty(),
            zones = snapshot.string("zones").orEmpty(),
            routeLeg = snapshot.string("routeLeg").orEmpty(),
            people = (snapshot.long("people") ?: 0L).toInt(),
            objects = (snapshot.long("objects") ?: 0L).toInt(),
            launcherMode = launcher.string("mode").orEmpty(),
            launcherStartedAtMs = launcher.long("startedAt") ?: 0L,
            software = launcher.string("software").orEmpty()
        )
    }

    /** The one operator request and what the Pi made of it. */
    fun operatorCommand(deviceId: String = RtdbConstants.DEFAULT_DEVICE_ID): Flow<OperatorCommand?> {
        requireValid(deviceId)
        return snapshots("devices/$deviceId/commands/operator").map { extractCommand(it) }
            .conflate().flowOn(GarbyDispatchers.background)
    }

    private fun extractCommand(snapshot: DataSnapshot): OperatorCommand? {
        if (!snapshot.exists()) return null
        return OperatorCommand(
            requestId = snapshot.string("requestId").orEmpty(),
            requestedAtMs = snapshot.long("requestedAt") ?: 0L,
            requestedBy = snapshot.string("requestedBy").orEmpty(),
            action = snapshot.string("action").orEmpty(),
            status = OperatorCommandStatus.fromWire(snapshot.string("status")),
            reason = snapshot.string("reason")
        )
    }

    /** The newest published scan; null until one arrives or when it cannot be decoded. */
    fun mapFrame(deviceId: String = RtdbConstants.DEFAULT_DEVICE_ID): Flow<MapFrame?> {
        requireValid(deviceId)
        return snapshots("devices/$deviceId/map/frame").map { extractFrame(it) }
            .conflate().flowOn(GarbyDispatchers.background)
    }

    private fun extractFrame(snapshot: DataSnapshot): MapFrame? {
        if (!snapshot.exists() || snapshot.long("v") != 1L) return null
        val distances = decodeDistances(snapshot.string("d")) ?: return null
        return MapFrame(
            seq = snapshot.long("seq") ?: 0L,
            atMs = snapshot.long("at") ?: 0L,
            ageMs = (snapshot.long("ageMs") ?: 0L).toInt(),
            distancesCm = distances,
            markers = decodeMarkers(snapshot.child("m").value),
            zones = snapshot.string("z").orEmpty(),
            path = snapshot.string("p").orEmpty(),
            hold = snapshot.string("hold").orEmpty(),
            wallLeftCm = snapshot.double("wl"),
            wallRightCm = snapshot.double("wr"),
            headingDeg = snapshot.double("h")
        )
    }

    /** Tell the Pi somebody is watching the map. Failure only means no frames arrive. */
    suspend fun renewMapLease(deviceId: String, uid: String) {
        requireValid(deviceId)
        try {
            withTimeout(RtdbConstants.WRITE_TIMEOUT_MS.milliseconds) {
                database.getReference("devices/$deviceId/commands/map")
                    .setValue(mapOf("leaseAt" to ServerValue.TIMESTAMP, "by" to uid)).await()
            }
        } catch (error: CancellationException) {
            throw error
        } catch (error: Exception) {
            Log.w(TAG, "Map lease not renewed", error)
        }
    }

    /**
     * Write one operator request. The transaction refuses to overwrite a request the Pi is still
     * working on, except for an emergency stop, which always goes through.
     */
    suspend fun send(deviceId: String, uid: String, action: OperatorAction): OperatorSubmission {
        requireValid(deviceId)
        require(uid.isNotBlank())
        if (!NetworkConnectivityManager.isNetworkConnected()) {
            return OperatorSubmission.NotSent("This phone is offline. Nothing was sent.")
        }
        val requestId = UUID.randomUUID().toString().replace("-", "")
        var attempted = false
        try {
            return withTimeout(RtdbConstants.WRITE_TIMEOUT_MS.milliseconds) {
                val ref = database.getReference("devices/$deviceId/commands/operator")
                attempted = true
                suspendCancellableCoroutine<OperatorSubmission> { continuation ->
                    ref.runTransaction(object : Transaction.Handler {
                        override fun doTransaction(data: MutableData): Transaction.Result {
                            val replaceable = canReplaceOperatorCommand(
                                action = action,
                                existingStatus = data.child("status").value as? String,
                                existingRequestedAtMs = (data.child("requestedAt").value as? Number)?.toLong(),
                                exists = data.value != null,
                                nowMs = GarbyRealtimeDb.serverNowMs()
                            )
                            if (!continuation.isActive || !replaceable) return Transaction.abort()
                            data.value = mapOf(
                                "version" to 1,
                                "requestId" to requestId,
                                "requestedAt" to ServerValue.TIMESTAMP,
                                "requestedBy" to uid,
                                "action" to action.wire,
                                "status" to "pending"
                            )
                            return Transaction.success(data)
                        }

                        override fun onComplete(error: DatabaseError?, committed: Boolean, snapshot: DataSnapshot?) {
                            if (!continuation.isActive) return
                            when {
                                error != null -> continuation.resumeWithException(error.toException())
                                !committed -> continuation.resume(OperatorSubmission.NotSent(
                                    "GARBY is still handling the previous request. Nothing was sent."))
                                snapshot?.string("requestId") == requestId ->
                                    continuation.resume(OperatorSubmission.Sent(requestId))
                                else -> continuation.resume(OperatorSubmission.Unconfirmed(
                                    "The request was not confirmed. Check GARBY before trying again."))
                            }
                        }
                    }, false) // Never show a local, unsent request as written.
                }
            }
        } catch (error: CancellationException) {
            if (error !is kotlinx.coroutines.TimeoutCancellationException) throw error
        } catch (error: Exception) {
            Log.w(TAG, "Operator request failed", error)
        }
        return if (attempted) {
            OperatorSubmission.Unconfirmed("Delivery was not confirmed. Check GARBY before trying again.")
        } else {
            OperatorSubmission.NotSent("Nothing was sent.")
        }
    }
}
