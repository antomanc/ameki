package dev.antomanc.ameki

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.getValue
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import dev.antomanc.ameki.core.data.settings.SettingsRepository
import dev.antomanc.ameki.core.data.sync.SyncError
import dev.antomanc.ameki.core.model.AppSettings
import dev.antomanc.ameki.core.ui.AppRequest
import dev.antomanc.ameki.core.ui.PendingRequest
import dev.antomanc.ameki.core.ui.theme.AmekiTheme
import dev.antomanc.ameki.feature.home.HomeRoute
import dev.antomanc.ameki.feature.locations.LocationsRoute
import dev.antomanc.ameki.feature.onboarding.OnboardingRoute
import dev.antomanc.ameki.feature.settings.SettingsRoute
import dev.antomanc.ameki.widget.WidgetPins
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch
import org.koin.android.ext.android.inject

class MainActivity : ComponentActivity() {
    private val settingsRepo: SettingsRepository by inject()
    /** The request from the launching intent or the places list, until a screen has handled it. */
    private val pending = mutableStateOf<PendingRequest?>(null)
    private var nextRequestId = 0L

    override fun onCreate(savedInstanceState: Bundle?) {
        val splash = installSplashScreen()
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        if (savedInstanceState == null) receive(intent) else {
            nextRequestId = savedInstanceState.getLong(KEY_NEXT_ID)
            pending.value = AppRequest.from(savedInstanceState.getBundle(KEY_PENDING))
                ?.let { PendingRequest(savedInstanceState.getLong(KEY_PENDING_ID), it) }
        }
        var ready = false
        splash.setKeepOnScreenCondition { !ready }

        setContent {
            val settings by settingsRepo.settings.collectAsStateWithLifecycle<AppSettings?>(null)
            val s = settings ?: return@setContent
            ready = true
            AmekiTheme(themeMode = s.theme, dynamicColor = s.dynamicColor) {
                val nav = rememberNavController()
                val request = pending.value
                LaunchedEffect(request?.id, s.onboardingDone) {
                    request ?: return@LaunchedEffect
                    if (!s.onboardingDone) return@LaunchedEffect handled(request.id)
                    when (request.request) {
                        AppRequest.OpenPlaces -> {
                            nav.navigate(PLACES) { launchSingleTop = true }
                            handled(request.id)
                        }
                        AppRequest.PinWidget -> {
                            WidgetPins.pinCompact(this@MainActivity)?.invoke()
                            handled(request.id)
                        }
                        // The home screen handles these and reports back.
                        is AppRequest.Show, AppRequest.Refresh -> nav.popBackStack(HOME, false)
                    }
                }
                NavHost(nav, startDestination = if (s.onboardingDone) HOME else ONBOARDING) {
                    composable(ONBOARDING) {
                        OnboardingRoute(
                            onDone = {
                                lifecycleScope.launch { settingsRepo.setOnboardingDone(true) }
                                nav.navigate(HOME) { popUpTo(ONBOARDING) { inclusive = true } }
                            },
                            onPinWidget = WidgetPins.pinCompact(this@MainActivity),
                        )
                    }
                    composable(HOME) {
                        HomeRoute(
                            onOpenPlaces = { nav.navigate(PLACES) },
                            onOpenSettings = { nav.navigate(SETTINGS) },
                            onFixProblem = { err ->
                                nav.navigate(if (err == SyncError.NO_LOCATION) PLACES else SETTINGS)
                            },
                            request = pending.value?.takeIf { it.request is AppRequest.Show || it.request is AppRequest.Refresh },
                            onRequestHandled = ::handled,
                        )
                    }
                    composable(PLACES) {
                        LocationsRoute(
                            onBack = { nav.popBackStack() },
                            onOpenPlace = { id ->
                                submit(AppRequest.Show(id))
                                nav.popBackStack(HOME, inclusive = false)
                            },
                        )
                    }
                    composable(SETTINGS) { SettingsRoute(onBack = { nav.popBackStack() }) }
                }
            }
        }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        outState.putLong(KEY_NEXT_ID, nextRequestId)
        pending.value?.let {
            outState.putBundle(KEY_PENDING, it.request.toBundle())
            outState.putLong(KEY_PENDING_ID, it.id)
        }
        super.onSaveInstanceState(outState)
    }

    override fun onStart() {
        super.onStart()
        lifecycleScope.launch(kotlinx.coroutines.Dispatchers.IO) {
            runCatching { (application as AmekiApp).publishShortcuts() }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        receive(intent)
    }

    private fun receive(intent: Intent?) {
        AppRequest.from(intent?.extras)?.let(::submit)
    }

    private fun submit(request: AppRequest) {
        pending.value = PendingRequest(++nextRequestId, request)
    }

    private fun handled(id: Long) {
        if (pending.value?.id != id) return
        pending.value = null
        // Recreating the activity from its intent must not replay a request already handled.
        AppRequest.clear(intent)
    }

    companion object {
        private const val ONBOARDING = "onboarding"
        private const val HOME = "home"
        private const val PLACES = "places"
        private const val SETTINGS = "settings"
        private const val KEY_PENDING = "pendingRequest"
        private const val KEY_PENDING_ID = "pendingRequestId"
        private const val KEY_NEXT_ID = "nextRequestId"
    }
}
