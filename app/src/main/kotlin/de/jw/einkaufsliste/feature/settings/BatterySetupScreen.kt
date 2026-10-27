package de.jw.einkaufsliste.feature.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import de.jw.einkaufsliste.AppContainer
import kotlinx.coroutines.launch

/** "Samsung-Akku": steps that keep Samsung from stopping the background check. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BatterySetupScreen(container: AppContainer, onBack: () -> Unit) {
    val context = LocalContext.current
    var ignoringOptimizations by remember { mutableStateOf(context.isIgnoringBatteryOptimizations()) }
    val scrollBehavior = TopAppBarDefaults.pinnedScrollBehavior()

    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) {
        ignoringOptimizations = context.isIgnoringBatteryOptimizations()
    }

    Scaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            TopAppBar(
                title = { Text("Samsung-Akku") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Zurück")
                    }
                },
                scrollBehavior = scrollBehavior,
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                "Samsung-Handys beenden Apps im Hintergrund oft nach kurzer Zeit, um Akku zu sparen. " +
                    "Dann merkt die App nicht mehr, wenn du an einem passenden Laden vorbeikommst. " +
                    "Mit diesen drei Schritten läuft die Prüfung zuverlässig weiter.",
                style = MaterialTheme.typography.bodyLarge,
            )

            SetupStep(
                number = 1,
                title = "Akku-Optimierung ausschalten",
                text = "Erlaubt der App, auch im Hintergrund nach Läden zu sehen.",
            ) {
                if (ignoringOptimizations) {
                    Text(
                        "Erledigt ✓",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.primary,
                    )
                } else {
                    Button(onClick = { context.requestIgnoreBatteryOptimizations() }) {
                        Text("Akku-Optimierung ausschalten")
                    }
                }
            }

            SetupStep(
                number = 2,
                title = "Nie in den Standby-Modus",
                text = "Dort unter Hintergrundnutzungslimits → Apps, die nie in den Standby-Modus wechseln → " +
                    "Demento hinzufügen.",
            ) {
                OutlinedButton(onClick = { context.openSamsungBatterySettings() }) {
                    Text("Samsung-Akkueinstellungen öffnen")
                }
            }

            SetupStep(
                number = 3,
                title = "Akku nicht einschränken",
                text = "In den App-Einstellungen: Akku → Nicht eingeschränkt.",
            ) {
                OutlinedButton(onClick = { context.openAppDetailsSettings() }) {
                    Text("App-Einstellungen öffnen")
                }
            }

            Spacer(Modifier.height(4.dp))
            Button(
                onClick = {
                    // App scope: the screen is left right away, the write must still happen.
                    container.appScope.launch {
                        runCatching { container.settingsRepository.update { it.copy(batterySetupDone = true) } }
                    }
                    onBack()
                },
                modifier = Modifier.fillMaxWidth(),
            ) { Text("Fertig") }
        }
    }
}

@Composable
private fun SetupStep(
    number: Int,
    title: String,
    text: String,
    action: @Composable () -> Unit,
) {
    OutlinedCard(Modifier.fillMaxWidth()) {
        Row(Modifier.padding(16.dp)) {
            Surface(
                shape = CircleShape,
                color = MaterialTheme.colorScheme.primaryContainer,
                contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                modifier = Modifier.size(28.dp),
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Text("$number", style = MaterialTheme.typography.labelLarge)
                }
            }
            Spacer(Modifier.width(16.dp))
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(title, style = MaterialTheme.typography.titleMedium)
                Text(
                    text,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                action()
            }
        }
    }
}
