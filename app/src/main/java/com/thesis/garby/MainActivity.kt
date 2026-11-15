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
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.ui.Modifier
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
import com.thesis.garby.realtime.OperatorCommandStatus
import com.thesis.garby.realtime.isFresh
import com.thesis.garby.ui.GarbyBottomBar
import com.thesis.garby.ui.GarbyTab
import com.thesis.garby.ui.glass.tabEnter
import com.thesis.garby.ui.glass.tabExit
import com.thesis.garby.ui.hideSystemBars
import com.thesis.garby.ui.stopButtonState
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
    // Activity-scoped: a return request stays tracked while the operator moves between screens.
    val returnViewModel: ReturnViewModel = viewModel()
    // Activity-scoped too: the stop button under every tab and the Controls tab share it.
    val operatorViewModel: OperatorViewModel = viewModel()
    val authState by authViewModel.authState.collectAsStateWithLifecycle()
    val authError by authViewModel.signInError.collectAsStateWithLifecycle()
    val signingIn by authViewModel.isSigningIn.collectAsStateWithLifecycle()

    LaunchedEffect(notificationOpens) {
        if (notificationOpens > 0 && authViewModel.authState.value is AuthState.Unauthenticated) {
            authViewModel.signIn()
        }
    }

    AnimatedContent(
        targetState = authState is AuthState.Authenticated,
        transitionSpec = {
            if (reducedEffects) EnterTransition.None togetherWith ExitTransition.None
            // Signing in: the app grows into place while the start screen lets go.
            else (fadeIn(tween(320)) + scaleIn(tween(420), initialScale = 0.94f)) togetherWith
                (fadeOut(tween(200)) + scaleOut(tween(260), targetScale = 1.04f))
        },
        modifier = Modifier.fillMaxSize(),
        label = "authenticated"
    ) { authenticated ->
        if (authenticated) {
            GarbyNavHost(
                returnViewModel = returnViewModel,
                operatorViewModel = operatorViewModel,
                onSignOut = {
                    returnViewModel.onSignedOut()
                    operatorViewModel.onSignedOut()
                    authViewModel.signOut()
                }
            )
        } else {
            StartScreen(
                isAuthenticating = signingIn,
                errorMessage = authError,
                onConnect = authViewModel::signIn
            )
        }
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
    val operator by operatorViewModel.uiState.collectAsStateWithLifecycle()
    val backStackEntry by navController.currentBackStackEntryAsState()
    val selected = GarbyTab.fromRoute(backStackEntry?.destination?.route)

    fun open(tab: GarbyTab) {
        navController.navigate(tab.route) {
            popUpTo(navController.graph.findStartDestination().id) { saveState = true }
            launchSingleTop = true
            restoreState = true
        }
    }

    Column(Modifier.fillMaxSize()) {
        Box(
            Modifier
                .weight(1f)
                .fillMaxWidth()
                // The bar below owns the bottom inset; the screens must not add it again.
                .consumeWindowInsets(WindowInsets.safeDrawing.only(WindowInsetsSides.Bottom))
        ) {
            NavHost(
                navController = navController,
                startDestination = GarbyTab.Home.route,
                modifier = Modifier.fillMaxSize(),
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
                    MainDashboard(
                        returnViewModel = returnViewModel,
                        onOpenReturn = { open(GarbyTab.Activity) },
                        onSignOut = onSignOut
                    )
                }
                composable(GarbyTab.Map.route) { MapScreen(viewModel = operatorViewModel) }
                composable(GarbyTab.Controls.route) { ControlsScreen(viewModel = operatorViewModel) }
                composable(GarbyTab.Activity.route) {
                    ReturnScreen(viewModel = returnViewModel, onBack = { open(GarbyTab.Home) })
                }
            }
        }
        val stopCommand = operator.command?.takeIf { it.action == OperatorAction.Estop.wire }
        GarbyBottomBar(
            selected = selected,
            onSelect = ::open,
            stopState = stopButtonState(
                emergencyStopped = operator.status?.emergencyStopped,
                statusFresh = operator.status.isFresh(operator.nowMs),
                sendingStop = operator.sending == OperatorAction.Estop,
                stopRequestOpen = stopCommand != null && (
                    stopCommand.status == OperatorCommandStatus.Pending ||
                        stopCommand.status == OperatorCommandStatus.Claimed
                    ) && operator.nowMs - stopCommand.requestedAtMs < 30_000L,
                online = operator.online
            ),
            onStop = { operatorViewModel.send(OperatorAction.Estop) }
        )
    }
}

private fun tabIndex(route: String?): Int = GarbyTab.fromRoute(route).ordinal
