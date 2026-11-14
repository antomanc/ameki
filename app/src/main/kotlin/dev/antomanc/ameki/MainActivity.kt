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
    private val refreshRequest = mutableStateOf<Long?>(null)
    /** Place requested by a widget tap; consumed by the home screen. */
    private val requestedPlace = mutableStateOf<Long?>(null)
    private val requestId = mutableStateOf(0L)
    private var requestedDay: Long? = null
    private var requestedHour: Long? = null
    private val requestedAction = mutableStateOf<String?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        val splash = installSplashScreen()
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        if (savedInstanceState == null) readRequest(intent) else {
            requestId.value = savedInstanceState.getLong("requestId")
            requestedPlace.value = savedInstanceState.getLong("pendingPlace", -1).takeIf { it >= 0 }
            requestedDay = savedInstanceState.getLong("pendingDay", Long.MIN_VALUE).takeIf { it != Long.MIN_VALUE }
            requestedHour = savedInstanceState.getLong("pendingHour", Long.MIN_VALUE).takeIf { it != Long.MIN_VALUE }
            requestedAction.value = savedInstanceState.getString("pendingAction")
            refreshRequest.value = savedInstanceState.getLong("pendingRefresh", -1).takeIf { it >= 0 }
        }
        var ready = false
        splash.setKeepOnScreenCondition { !ready }

        setContent {
            val settings by settingsRepo.settings.collectAsStateWithLifecycle<AppSettings?>(null)
            val s = settings ?: return@setContent
            ready = true
            AmekiTheme(themeMode = s.theme, dynamicColor = s.dynamicColor) {
                val nav = rememberNavController()
                val request by requestId
                LaunchedEffect(request, s.onboardingDone) {
                    if (!s.onboardingDone) {
                        requestedAction.value = null
                        clearLaunchExtras()
                        return@LaunchedEffect
                    }
                    when (requestedAction.value) {
                        "places" -> nav.navigate(PLACES) { launchSingleTop = true }
                        "widget" -> WidgetPins.pinCompact(this@MainActivity)?.invoke()
                        "refresh" -> {
                            refreshRequest.value = request
                            nav.popBackStack(HOME, false)
                        }
                        else -> if (requestedPlace.value != null) nav.popBackStack(HOME, false)
                    }
                    requestedAction.value = null
                    if (requestedPlace.value == null) clearLaunchExtras()
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
                        val requested by requestedPlace
                        HomeRoute(
                            onOpenPlaces = { nav.navigate(PLACES) },
                            onOpenSettings = { nav.navigate(SETTINGS) },
                            onFixProblem = { err ->
                                nav.navigate(if (err == SyncError.NO_LOCATION) PLACES else SETTINGS)
                            },
                            initialPlaceId = requested,
                            requestId = request,
                            requestedDay = requestedDay,
                            requestedHour = requestedHour,
                            refreshRequestId = refreshRequest.value,
                            onRefreshRequestHandled = { refreshRequest.value = null },
                            onRequestApplied = {
                                requestedPlace.value = null
                                requestedDay = null
                                requestedHour = null
                                clearLaunchExtras()
                            },
                        )
                    }
                    composable(PLACES) {
                        LocationsRoute(
                            onBack = { nav.popBackStack() },
                            onOpenPlace = { id ->
                                requestedDay = null
                                requestedHour = null
                                requestedPlace.value = id
                                requestedAction.value = null
                                requestId.value++
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
        outState.putLong("requestId", requestId.value)
        requestedPlace.value?.let { outState.putLong("pendingPlace", it) }
        requestedDay?.let { outState.putLong("pendingDay", it) }
        requestedHour?.let { outState.putLong("pendingHour", it) }
        requestedAction.value?.let { outState.putString("pendingAction", it) }
        refreshRequest.value?.let { outState.putLong("pendingRefresh", it) }
        super.onSaveInstanceState(outState)
    }

    override fun onStart() {
        super.onStart()
        lifecycleScope.launch(kotlinx.coroutines.Dispatchers.IO) {
            runCatching { (application as AmekiApp).publishShortcuts() }
        }
    }

    private fun clearLaunchExtras() {
        listOf(EXTRA_PLACE_ID, "dev.antomanc.ameki.DAY_EPOCH_DAY", "dev.antomanc.ameki.HOUR_EPOCH_SECOND",
            "dev.antomanc.ameki.REQUEST_ID", "dev.antomanc.ameki.OPEN_PLACES", "dev.antomanc.ameki.ACTION").forEach { intent?.removeExtra(it) }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        readRequest(intent)
    }

    private fun readRequest(intent: Intent?) {
        requestedPlace.value = placeFrom(intent)
        requestedDay = intent?.takeIf { it.hasExtra("dev.antomanc.ameki.DAY_EPOCH_DAY") }?.getLongExtra("dev.antomanc.ameki.DAY_EPOCH_DAY", 0)
        requestedHour = intent?.takeIf { it.hasExtra("dev.antomanc.ameki.HOUR_EPOCH_SECOND") }?.getLongExtra("dev.antomanc.ameki.HOUR_EPOCH_SECOND", 0)
        requestedAction.value = if (intent?.getBooleanExtra("dev.antomanc.ameki.OPEN_PLACES", false) == true) "places" else intent?.getStringExtra("dev.antomanc.ameki.ACTION")
        requestId.value++
    }

    private fun placeFrom(intent: Intent?): Long? =
        intent?.getLongExtra(EXTRA_PLACE_ID, -1L)?.takeIf { it >= 0 }

    companion object {
        const val EXTRA_PLACE_ID = "dev.antomanc.ameki.PLACE_ID"
        private const val ONBOARDING = "onboarding"
        private const val HOME = "home"
        private const val PLACES = "places"
        private const val SETTINGS = "settings"
    }
}
