package com.thesis.garby

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.repeatOnLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.compose.NavHost
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.thesis.garby.firestore.AuthState
import com.thesis.garby.firestore.AuthViewModel
import com.thesis.garby.notifications.GarbyNotificationHelper
import com.thesis.garby.performance.LocalPerformanceProfile
import com.thesis.garby.realtime.OperatorAction
import com.thesis.garby.ui.GarbyBottomBar
import com.thesis.garby.ui.GarbyTab
import com.thesis.garby.ui.LocalFloatingDockInset
import com.thesis.garby.ui.glass.tabEnter
import com.thesis.garby.ui.glass.tabExit
import com.thesis.garby.ui.hideSystemBars
import com.thesis.garby.ui.theme.GarbyTheme

class MainActivity : ComponentActivity() {

    /** Bumped for every launch from a notification, so each tap can trigger sign-in once. */
    private val notificationOpens = mutableIntStateOf(0)

    private val notificationPermission =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) {
            // Notifications are optional; the dashboard works either way.
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // The glass UI is always dark, so the system bars use light icons whatever the theme.
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(Color.TRANSPARENT)
        )
        window.hideSystemBars()
        if (savedInstanceState == null) {
            requestNotificationPermission()
            handleNotificationIntent(intent)
        }

        setContent {
            GarbyTheme {
                GarbyApp(notificationOpens = notificationOpens.intValue)
            }
        }
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        // Re-enter immersive mode after Home/resume, permission prompts and modal windows.
        if (hasFocus) window.hideSystemBars()
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleNotificationIntent(intent)
    }

    private fun handleNotificationIntent(intent: Intent?) {
        if (intent == null) return
        val fromNotification = intent.getBooleanExtra(GarbyNotificationHelper.EXTRA_OPEN_DASHBOARD, false) ||
            intent.getStringExtra(GarbyNotificationHelper.EXTRA_NAVIGATE_TO) == GarbyNotificationHelper.DESTINATION_DASHBOARD ||
            intent.hasExtra("google.message_id")
        if (fromNotification) notificationOpens.intValue += 1
    }

    private fun requestNotificationPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }
}

@Composable
fun GarbyApp(notificationOpens: Int) {
    val reducedEffects = LocalPerformanceProfile.current.reducedEffects
    val authViewModel: AuthViewModel = viewModel()
    val authState by authViewModel.authState.collectAsStateWithLifecycle()
    val authError by authViewModel.signInError.collectAsStateWithLifecycle()
    val signingIn by authViewModel.isSigningIn.collectAsStateWithLifecycle()

    LaunchedEffect(notificationOpens) {
        if (notificationOpens > 0 && authViewModel.authState.value is AuthState.Unauthenticated) {
            authViewModel.signIn()
        }
    }

    val authenticated = authState is AuthState.Authenticated
    if (reducedEffects) {
        // A transition with None still creates AnimatedContent's measurement/subcomposition
        // machinery. The light profile renders a single screen directly.
        Box(Modifier.fillMaxSize()) {
            GarbyAppContent(authenticated, signingIn, authError, authViewModel)
        }
        return
    }

    AnimatedContent(
        targetState = authenticated,
        transitionSpec = {
            // Signing in: the app grows into place while the start screen lets go.
            (fadeIn(tween(320)) + scaleIn(tween(420), initialScale = 0.94f)) togetherWith
                (fadeOut(tween(200)) + scaleOut(tween(260), targetScale = 1.04f))
        },
        modifier = Modifier.fillMaxSize(),
        label = "authenticated"
    ) { connected ->
        GarbyAppContent(connected, signingIn, authError, authViewModel)
    }
}

@Composable
private fun GarbyAppContent(
    authenticated: Boolean,
    signingIn: Boolean,
    authError: String?,
    authViewModel: AuthViewModel
) {
    if (authenticated) {
        // Activity-scoped across tabs, but created only after sign-in. The Connect screen
        // needs neither monitoring state machines nor their coroutine subscriptions.
        val returnViewModel: ReturnViewModel = viewModel()
        val operatorViewModel: OperatorViewModel = viewModel()
        DisposableEffect(authViewModel, returnViewModel, operatorViewModel) {
            val cleanup: () -> Unit = {
                returnViewModel.onSignedOut()
                operatorViewModel.onSignedOut()
            }
            authViewModel.addSessionEndListener(cleanup)
            onDispose { authViewModel.removeSessionEndListener(cleanup) }
        }
        GarbyNavHost(
            returnViewModel = returnViewModel,
            operatorViewModel = operatorViewModel,
            onSignOut = authViewModel::signOut
        )
    } else {
        val internetAvailable by authViewModel.internetAvailable.collectAsStateWithLifecycle()
        StartScreen(
            isAuthenticating = signingIn,
            errorMessage = authError,
            onConnect = authViewModel::signIn,
            internetAvailable = internetAvailable
        )
    }
}

/**
 * The four tabs above one bottom bar. A tab change slides the way the bar does (GlassMotion.kt);
 * each tab keeps its own state, and Back from any tab returns to Home.
 */
@Composable
private fun GarbyNavHost(
    returnViewModel: ReturnViewModel,
    operatorViewModel: OperatorViewModel,
    onSignOut: () -> Unit
) {
    val navController = rememberNavController()
    val reducedEffects = LocalPerformanceProfile.current.reducedEffects
    val lifecycleOwner = LocalLifecycleOwner.current
    LaunchedEffect(lifecycleOwner, operatorViewModel) {
        lifecycleOwner.lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
            operatorViewModel.observeWhileVisible()
        }
    }
    val backStackEntry by navController.currentBackStackEntryAsState()
    val selected = GarbyTab.fromRoute(backStackEntry?.destination?.route)

    fun open(tab: GarbyTab) {
        navController.navigate(tab.route) {
            popUpTo(navController.graph.findStartDestination().id) { saveState = true }
            launchSingleTop = true
            restoreState = true
        }
    }

    var dockHeightPx by remember { mutableIntStateOf(0) }
    val dockInset = with(LocalDensity.current) { dockHeightPx.toDp() }
    CompositionLocalProvider(LocalFloatingDockInset provides dockInset) {
        Box(Modifier.fillMaxSize()) {
            NavHost(
                navController = navController,
                startDestination = GarbyTab.Home.route,
                modifier = Modifier.fillMaxSize()
                    .consumeWindowInsets(WindowInsets.safeDrawing.only(WindowInsetsSides.Bottom)),
                enterTransition = {
                    tabEnter(tabIndex(initialState.destination.route), tabIndex(targetState.destination.route), reducedEffects)
                },
                exitTransition = {
                    tabExit(tabIndex(initialState.destination.route), tabIndex(targetState.destination.route), reducedEffects)
                },
                popEnterTransition = {
                    tabEnter(tabIndex(initialState.destination.route), tabIndex(targetState.destination.route), reducedEffects)
                },
                popExitTransition = {
                    tabExit(tabIndex(initialState.destination.route), tabIndex(targetState.destination.route), reducedEffects)
                }
            ) {
                composable(GarbyTab.Home.route) {
                    MainDashboard(returnViewModel = returnViewModel,
                        operatorViewModel = operatorViewModel, onSignOut = onSignOut)
                }
                composable(GarbyTab.Map.route) { MapScreen(viewModel = operatorViewModel) }
                composable(GarbyTab.Controls.route) { ControlsScreen(viewModel = operatorViewModel) }
                composable(GarbyTab.Activity.route) {
                    ReturnScreen(viewModel = returnViewModel, onBack = { open(GarbyTab.Home) })
                }
            }
            FloatingOperatorDock(
                viewModel = operatorViewModel,
                selected = selected,
                onSelect = ::open,
                modifier = Modifier.align(Alignment.BottomCenter).onSizeChanged { size ->
                    if (dockHeightPx != size.height) dockHeightPx = size.height
                }
            )
        }
    }
}

/** Clock and map updates stay outside navigation; only a changed stop state redraws this dock. */
@Composable
private fun FloatingOperatorDock(
    viewModel: OperatorViewModel,
    selected: GarbyTab,
    onSelect: (GarbyTab) -> Unit,
    modifier: Modifier = Modifier
) {
    val stopState by viewModel.stopState.collectAsStateWithLifecycle()
    GarbyBottomBar(selected = selected, onSelect = onSelect, stopState = stopState,
        onStop = { viewModel.send(OperatorAction.Estop) }, modifier = modifier)
}

private fun tabIndex(route: String?): Int = GarbyTab.fromRoute(route).ordinal
