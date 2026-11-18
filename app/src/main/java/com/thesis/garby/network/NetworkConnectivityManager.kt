package com.thesis.garby.network

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Process-wide monitor for validated internet access.
 *
 * A network being present is not enough for control operations: it must expose
 * NET_CAPABILITY_INTERNET and be validated by Android. StateFlow is thread-safe,
 * so callbacks update it directly without a leaked process coroutine scope.
 */
object NetworkConnectivityManager {

    private const val TAG = "NetworkConnectivity"
    private val lock = Any()

    @Volatile
    private var connectivityManager: ConnectivityManager? = null

    @Volatile
    private var networkCallback: ConnectivityManager.NetworkCallback? = null

    @Volatile
    private var isInitialized = false

    private val _isInternetAvailable = MutableStateFlow(false)
    val isInternetAvailable: StateFlow<Boolean> = _isInternetAvailable.asStateFlow()

    fun initialize(context: Context) = synchronized(lock) {
        if (isInitialized) return@synchronized

        val manager = context.applicationContext
            .getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager

        if (manager == null) {
            Log.e(TAG, "ConnectivityManager unavailable")
            _isInternetAvailable.value = false
            return@synchronized
        }

        connectivityManager = manager
        _isInternetAvailable.value = hasValidatedInternet(manager)

        val callback = object : ConnectivityManager.NetworkCallback() {
            private var currentNetwork: Network? = manager.activeNetwork

            override fun onAvailable(network: Network) {
                currentNetwork = network
                // Android 8+ follows with capabilities; an available Wi-Fi link alone
                // must not announce internet access (for example a captive portal).
            }

            override fun onLost(network: Network) {
                if (network == currentNetwork) {
                    currentNetwork = null
                    _isInternetAvailable.value = false
                }
            }

            override fun onCapabilitiesChanged(
                network: Network,
                networkCapabilities: NetworkCapabilities
            ) {
                if (network == currentNetwork) {
                    _isInternetAvailable.value = hasValidatedInternet(networkCapabilities)
                }
            }

            override fun onUnavailable() {
                currentNetwork = null
                _isInternetAvailable.value = false
            }
        }

        try {
            manager.registerDefaultNetworkCallback(callback)
            networkCallback = callback
            isInitialized = true
        } catch (e: SecurityException) {
            Log.e(TAG, "Missing permission to monitor connectivity", e)
            networkCallback = null
            connectivityManager = null
            _isInternetAvailable.value = false
        } catch (e: RuntimeException) {
            Log.e(TAG, "Unable to register network callback", e)
            networkCallback = null
            connectivityManager = null
            _isInternetAvailable.value = false
        }
    }

    fun isNetworkConnected(): Boolean {
        val manager = connectivityManager ?: return false
        return hasValidatedInternet(manager)
    }

    private fun hasValidatedInternet(capabilities: NetworkCapabilities): Boolean =
        capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) &&
            capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)

    private fun hasValidatedInternet(manager: ConnectivityManager): Boolean {
        return try {
            val network = manager.activeNetwork ?: return false
            val capabilities = manager.getNetworkCapabilities(network) ?: return false
            hasValidatedInternet(capabilities)
        } catch (e: SecurityException) {
            Log.w(TAG, "Connectivity check denied", e)
            false
        }
    }

    fun cleanup() = synchronized(lock) {
        val manager = connectivityManager
        val callback = networkCallback
        if (manager != null && callback != null) {
            try {
                manager.unregisterNetworkCallback(callback)
            } catch (_: IllegalArgumentException) {
                // Callback was already unregistered.
            } catch (e: RuntimeException) {
                Log.w(TAG, "Unable to unregister network callback", e)
            }
        }
        networkCallback = null
        connectivityManager = null
        isInitialized = false
        _isInternetAvailable.value = false
    }
}
