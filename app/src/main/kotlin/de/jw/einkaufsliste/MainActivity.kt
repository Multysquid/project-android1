package de.jw.einkaufsliste

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.navigation.compose.rememberNavController
import de.jw.einkaufsliste.ui.Notifications
import de.jw.einkaufsliste.ui.navigation.AppNavHost
import de.jw.einkaufsliste.ui.navigation.Routes
import de.jw.einkaufsliste.ui.theme.EinkaufTheme
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    /** Screen requested by a notification tap, consumed once navigation has happened. */
    private var pendingScreen by mutableStateOf<String?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        // Reopening from Recents repeats the task's first intent; its notification extra was used then.
        val fromRecents = (intent.flags and Intent.FLAG_ACTIVITY_LAUNCHED_FROM_HISTORY) != 0
        if (savedInstanceState == null && !fromRecents) {
            pendingScreen = intent.getStringExtra(Notifications.EXTRA_OPEN_SCREEN)
        }

        setContent {
            EinkaufTheme {
                // Opaque background behind all screens: NavHost crossfades them, and the
                // window background would show through.
                Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                    val navController = rememberNavController()
                    AppNavHost(container = appContainer, navController = navController)
                    LaunchedEffect(pendingScreen) {
                        if (pendingScreen == Notifications.SCREEN_NEARBY) {
                            navController.navigate(Routes.NEARBY) { launchSingleTop = true }
                        }
                        pendingScreen = null
                    }
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        pendingScreen = intent.getStringExtra(Notifications.EXTRA_OPEN_SCREEN)
    }

    override fun onStart() {
        super.onStart()
        // The app is in the foreground here, so the scheduler may (re)start the foreground
        // service for short intervals. Said explicitly: ProcessLifecycleOwner reaches STARTED
        // only after onStart returns.
        appContainer.appScope.launch { appContainer.checkScheduler.apply(appInForeground = true) }
    }
}
