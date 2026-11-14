package dev.antomanc.ameki

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.getValue
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
    /** Place requested by a widget tap; consumed by the home screen. */
    private val requestedPlace = mutableStateOf<Long?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        val splash = installSplashScreen()
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        requestedPlace.value = placeFrom(intent)
        var ready = false
        splash.setKeepOnScreenCondition { !ready }

        setContent {
            val settings by settingsRepo.settings.collectAsStateWithLifecycle<AppSettings?>(null)
            val s = settings ?: return@setContent
            ready = true
            AmekiTheme(themeMode = s.theme, dynamicColor = s.dynamicColor) {
                val nav = rememberNavController()
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
                        )
                    }
                    composable(PLACES) {
                        LocationsRoute(
                            onBack = { nav.popBackStack() },
                            onOpenPlace = { id ->
                                requestedPlace.value = id
                                nav.popBackStack(HOME, inclusive = false)
                            },
                        )
                    }
                    composable(SETTINGS) { SettingsRoute(onBack = { nav.popBackStack() }) }
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        placeFrom(intent)?.let { requestedPlace.value = it }
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
