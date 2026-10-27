package de.jw.einkaufsliste.feature.shops

import android.Manifest
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings as SystemSettings
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.outlined.CloudOff
import androidx.compose.material.icons.outlined.Directions
import androidx.compose.material.icons.outlined.GpsNotFixed
import androidx.compose.material.icons.outlined.LocationOff
import androidx.compose.material.icons.outlined.LocationOn
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.ShoppingCart
import androidx.compose.material.icons.outlined.Storefront
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import de.jw.einkaufsliste.AppContainer
import de.jw.einkaufsliste.core.geo.GeoMath
import kotlinx.coroutines.launch

private val LOCATION_PERMISSIONS = arrayOf(
    Manifest.permission.ACCESS_FINE_LOCATION,
    Manifest.permission.ACCESS_COARSE_LOCATION,
)

/** "In der Nähe": which shops around the current location probably have something from the list. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NearbyScreen(container: AppContainer, onBack: () -> Unit, onOpenSettings: () -> Unit) {
    val viewModel: NearbyViewModel = viewModel { NearbyViewModel(container) }
    val state by viewModel.state.collectAsStateWithLifecycle()
    val locationProvider = container.locationProvider
    val context = LocalContext.current

    var hasPermission by remember { mutableStateOf(locationProvider.hasForegroundPermission()) }
    var hasPrecise by remember { mutableStateOf(locationProvider.hasFinePermission()) }
    // Android stops asking after refusals: a second tap on the hint leads to the app settings.
    var preciseRequested by remember { mutableStateOf(false) }
    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
        hasPermission = locationProvider.hasForegroundPermission()
        hasPrecise = locationProvider.hasFinePermission()
        // The dialog does not restart the activity, so a schedule cancelled for the missing permission
        // (on app start) would stay off until the app is opened again.
        if (hasPermission) container.appScope.launch { container.checkScheduler.apply(appInForeground = true) }
    }
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) {
        hasPermission = locationProvider.hasForegroundPermission()
        hasPrecise = locationProvider.hasFinePermission()
        viewModel.onResume()
    }
    LaunchedEffect(hasPermission) {
        if (hasPermission) viewModel.start()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("In der Nähe") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Zurück")
                    }
                },
                actions = {
                    if (hasPermission) {
                        IconButton(onClick = { viewModel.refresh() }, enabled = state.progress == null) {
                            Icon(Icons.Filled.Refresh, contentDescription = "Aktualisieren")
                        }
                    }
                    IconButton(onClick = onOpenSettings) {
                        Icon(Icons.Outlined.Settings, contentDescription = "Einstellungen")
                    }
                },
            )
        },
    ) { padding ->
        val modifier = Modifier
            .fillMaxSize()
            .padding(padding)
        when {
            !hasPermission -> LocationPermissionMissing(
                onRequest = { permissionLauncher.launch(LOCATION_PERMISSIONS) },
                modifier = modifier,
            )
            else -> NearbyContent(
                state = state,
                approximateOnly = !hasPrecise,
                preciseRequested = preciseRequested,
                onAllowPrecise = {
                    if (preciseRequested) {
                        context.openAppDetails()
                    } else {
                        // Android offers the switch to precise location.
                        preciseRequested = true
                        permissionLauncher.launch(LOCATION_PERMISSIONS)
                    }
                },
                onRetry = { viewModel.retry() },
                onWidenedChange = { viewModel.setWidened(it) },
                modifier = modifier,
            )
        }
    }
}

@Composable
private fun NearbyContent(
    state: NearbyUiState,
    approximateOnly: Boolean,
    preciseRequested: Boolean,
    onAllowPrecise: () -> Unit,
    onRetry: () -> Unit,
    onWidenedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val result = state.result
    when {
        state.listEmpty -> CenteredMessage(
            icon = Icons.Outlined.ShoppingCart,
            title = "Deine Liste ist leer.",
            text = "Sobald etwas auf der Liste steht, zeigt dir die App hier Läden in der Nähe, die es wahrscheinlich haben.",
            modifier = modifier,
        ) {
            EmptyListShops(state = state, onRetry = onRetry)
        }
        state.noLocation -> CenteredMessage(
            icon = Icons.Outlined.LocationOff,
            title = "Kein Standort verfügbar. Ist die Ortung an?",
            text = "Schalte die Standortermittlung ein und versuche es noch einmal. Draußen klappt es meist schneller.",
            modifier = modifier,
        ) {
            Button(onClick = onRetry) { Text("Erneut versuchen") }
            Spacer(Modifier.height(8.dp))
            OutlinedButton(onClick = { context.openLocationSettings() }) { Text("Ortung einschalten") }
        }
        result == null && state.progress == null && state.warning != null -> CenteredMessage(
            icon = Icons.Outlined.CloudOff,
            title = state.warning,
            text = "Bitte versuche es noch einmal.",
            modifier = modifier,
        ) {
            Button(onClick = onRetry) { Text("Erneut versuchen") }
        }
        result == null -> CenteredProgress(text = state.progress.label(), modifier = modifier)
        else -> LazyColumn(
            modifier = modifier,
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            if (state.progress != null) {
                item(key = "progress") { ProgressRow(state.progress.label()) }
            }
            state.warning?.let { warning ->
                item(key = "warning") { WarningBanner(warning) }
            }
            if (approximateOnly) {
                // Approximate location can be off by kilometres; distances and the radius look exact.
                item(key = "approximate") {
                    WarningBanner(
                        text = "Nur ungefährer Standort erlaubt – Entfernungen können stark abweichen.",
                        icon = Icons.Outlined.GpsNotFixed,
                    ) {
                        TextButton(onClick = onAllowPrecise) {
                            Text(if (preciseRequested) "App-Einstellungen öffnen" else "Genauen Standort erlauben")
                        }
                    }
                }
            }
            item(key = "header") {
                RadiusHeader(
                    radiusMeters = result.radiusMeters,
                    showWiden = state.canWiden,
                    widened = state.widened,
                    onWidenedChange = onWidenedChange,
                )
            }
            if (result.rows.isEmpty()) {
                item(key = "empty") {
                    NoMatches(
                        text = if (result.noShopsInRadius) {
                            "Keine Läden im Umkreis von ${GeoMath.formatDistance(result.radiusMeters.toDouble())} gefunden."
                        } else {
                            "Kein Laden im Umkreis hat wahrscheinlich etwas von deiner Liste."
                        },
                        onWiden = if (state.canWiden && !state.widened) ({ onWidenedChange(true) }) else null,
                    )
                }
            } else if (result.hasLearnedItems) {
                item(key = "legend") {
                    Text(
                        "★ = hier schon mal gekauft",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            items(result.rows, key = { it.match.shop.id }) { row ->
                ShopCard(row = row, onRoute = { context.openRoute(row) })
            }
            item(key = "attribution") {
                Text(
                    "Kartendaten © OpenStreetMap-Mitwirkende (ODbL)",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 8.dp),
                )
            }
        }
    }
}

/** With an empty list: whether the shops around are loaded, so an item can be pinned to one of them. */
@Composable
private fun EmptyListShops(state: NearbyUiState, onRetry: () -> Unit) {
    val context = LocalContext.current
    val shopCount = state.shopsInRadius
    val radius = GeoMath.formatDistance(state.searchRadiusMeters.toDouble())
    when {
        state.progress != null -> Row(verticalAlignment = Alignment.CenterVertically) {
            CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
            Spacer(Modifier.width(12.dp))
            Text(state.progress.label(), style = MaterialTheme.typography.bodyMedium)
        }
        state.noLocation -> {
            StatusText("Ohne Standort konnten keine Läden geladen werden. Ist die Ortung an?")
            Spacer(Modifier.height(12.dp))
            Button(onClick = onRetry) { Text("Erneut versuchen") }
            Spacer(Modifier.height(8.dp))
            OutlinedButton(onClick = { context.openLocationSettings() }) { Text("Ortung einschalten") }
        }
        state.warning != null -> {
            StatusText(state.warning)
            Spacer(Modifier.height(12.dp))
            Button(onClick = onRetry) { Text("Erneut versuchen") }
        }
        shopCount == 0 -> StatusText("Keine Läden im Umkreis von $radius gefunden.")
        shopCount != null -> StatusText(
            "${if (shopCount == 1) "1 Laden" else "$shopCount Läden"} im Umkreis von $radius geladen. " +
                "Beim Bearbeiten eines Artikels kannst du ihn unter „Nur in diesem Laden“ einem Laden zuordnen.",
        )
    }
}

@Composable
private fun StatusText(text: String) {
    Text(text, style = MaterialTheme.typography.bodyMedium, textAlign = TextAlign.Center)
}

private fun NearbyProgress?.label(): String = when (this) {
    NearbyProgress.LOCATING, null -> "Standort wird bestimmt …"
    NearbyProgress.LOADING_SHOPS -> "Läden werden geladen …"
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun RadiusHeader(
    radiusMeters: Int,
    showWiden: Boolean,
    widened: Boolean,
    onWidenedChange: (Boolean) -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            "Umkreis: ${GeoMath.formatDistance(radiusMeters.toDouble())}",
            style = MaterialTheme.typography.titleSmall,
            modifier = Modifier.weight(1f),
        )
        if (showWiden || widened) {
            FilterChip(
                selected = widened,
                onClick = { onWidenedChange(!widened) },
                label = { Text("Weiter weg suchen") },
                leadingIcon = if (widened) {
                    { Icon(Icons.Filled.Check, contentDescription = null, modifier = Modifier.size(FilterChipDefaults.IconSize)) }
                } else {
                    null
                },
            )
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ShopCard(row: NearbyShopRow, onRoute: () -> Unit) {
    val match = row.match
    ElevatedCard(modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(start = 16.dp, end = 8.dp, top = 12.dp, bottom = 4.dp)) {
            Row(verticalAlignment = Alignment.Top) {
                Column(Modifier.weight(1f)) {
                    Text(
                        row.title,
                        style = MaterialTheme.typography.titleMedium,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        listOfNotNull(row.typeLabel, GeoMath.formatDistance(match.distanceMeters)).joinToString(" · "),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    row.openNow?.let { open ->
                        Text(
                            if (open) "Jetzt geöffnet" else "Jetzt geschlossen",
                            style = MaterialTheme.typography.labelLarge,
                            color = if (open) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error,
                        )
                    }
                }
                TextButton(onClick = onRoute) {
                    Icon(Icons.Outlined.Directions, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Route")
                }
            }
            Spacer(Modifier.height(8.dp))
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
                modifier = Modifier.padding(end = 8.dp, bottom = 12.dp),
            ) {
                match.items.forEach { item ->
                    ItemTag(name = item.name, learned = item.id in match.learnedItemIds)
                }
            }
        }
    }
}

/** A matched item; not clickable, so a plain surface rather than a chip. */
@Composable
private fun ItemTag(name: String, learned: Boolean) {
    Surface(
        shape = MaterialTheme.shapes.small,
        color = if (learned) MaterialTheme.colorScheme.tertiaryContainer else MaterialTheme.colorScheme.secondaryContainer,
        contentColor = if (learned) {
            MaterialTheme.colorScheme.onTertiaryContainer
        } else {
            MaterialTheme.colorScheme.onSecondaryContainer
        },
    ) {
        Text(
            if (learned) "★ $name" else name,
            style = MaterialTheme.typography.labelLarge,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
        )
    }
}

@Composable
private fun NoMatches(text: String, onWiden: (() -> Unit)?) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(
            Icons.Outlined.Storefront,
            contentDescription = null,
            modifier = Modifier.size(40.dp),
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(12.dp))
        Text(text, style = MaterialTheme.typography.bodyLarge, textAlign = TextAlign.Center)
        if (onWiden != null) {
            Spacer(Modifier.height(12.dp))
            OutlinedButton(onClick = onWiden) { Text("Weiter weg suchen") }
        }
    }
}

@Composable
private fun WarningBanner(
    text: String,
    icon: ImageVector = Icons.Outlined.CloudOff,
    action: (@Composable () -> Unit)? = null,
) {
    Card(
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.errorContainer,
            contentColor = MaterialTheme.colorScheme.onErrorContainer,
        ),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            Modifier.padding(start = 16.dp, end = 16.dp, top = 16.dp, bottom = if (action != null) 4.dp else 16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(icon, contentDescription = null)
            Spacer(Modifier.width(12.dp))
            Text(text, style = MaterialTheme.typography.bodyMedium)
        }
        if (action != null) {
            Row(
                horizontalArrangement = Arrangement.End,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 8.dp, end = 8.dp, bottom = 4.dp),
            ) {
                action()
            }
        }
    }
}

@Composable
private fun ProgressRow(text: String) {
    Column(Modifier.fillMaxWidth()) {
        LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
        Spacer(Modifier.height(6.dp))
        Text(text, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun CenteredProgress(text: String, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier.padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        CircularProgressIndicator()
        Spacer(Modifier.height(16.dp))
        Text(text, style = MaterialTheme.typography.bodyLarge, textAlign = TextAlign.Center)
    }
}

@Composable
private fun CenteredMessage(
    icon: ImageVector,
    title: String,
    text: String,
    modifier: Modifier = Modifier,
    actions: @Composable () -> Unit = {},
) {
    Column(
        modifier = modifier
            .verticalScroll(rememberScrollState())
            .padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(icon, contentDescription = null, modifier = Modifier.size(48.dp), tint = MaterialTheme.colorScheme.primary)
        Spacer(Modifier.height(16.dp))
        Text(title, style = MaterialTheme.typography.titleMedium, textAlign = TextAlign.Center)
        Spacer(Modifier.height(8.dp))
        Text(
            text,
            style = MaterialTheme.typography.bodyMedium,
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(24.dp))
        actions()
    }
}

@Composable
private fun LocationPermissionMissing(onRequest: () -> Unit, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    CenteredMessage(
        icon = Icons.Outlined.LocationOn,
        title = "Standort benötigt",
        text = "Um Läden in deiner Nähe zu finden, braucht die App Zugriff auf deinen Standort. " +
            "Zum Herunterladen der Läden wird der ungefähre Bereich um deinen Standort (auf etwa 100 m " +
            "gerundet) an einen OpenStreetMap-Kartenserver (Overpass) geschickt.",
        modifier = modifier,
    ) {
        Button(onClick = onRequest) { Text("Standort erlauben") }
        Spacer(Modifier.height(8.dp))
        OutlinedButton(onClick = { context.openAppDetails() }) { Text("App-Einstellungen öffnen") }
        Spacer(Modifier.height(12.dp))
        Text(
            "Falls keine Abfrage erscheint: In den App-Einstellungen unter „Berechtigungen“ den Standort erlauben.",
            style = MaterialTheme.typography.bodySmall,
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/** Opens a map app at the shop; a toast when there is none. */
private fun Context.openRoute(row: NearbyShopRow) {
    val intent = Intent(Intent.ACTION_VIEW, Uri.parse(routeUri(row.match.shop, row.title)))
    try {
        startActivity(intent)
    } catch (e: ActivityNotFoundException) {
        Toast.makeText(this, "Keine Karten-App gefunden.", Toast.LENGTH_SHORT).show()
    }
}

private fun Context.openLocationSettings() {
    try {
        startActivity(Intent(SystemSettings.ACTION_LOCATION_SOURCE_SETTINGS))
    } catch (e: ActivityNotFoundException) {
        Toast.makeText(this, "Die Standort-Einstellungen ließen sich nicht öffnen.", Toast.LENGTH_SHORT).show()
    }
}

/** This app's page in the system settings, where permissions can be granted after "Nicht mehr fragen". */
private fun Context.openAppDetails() {
    val intent = Intent(SystemSettings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", packageName, null))
    try {
        startActivity(intent)
    } catch (e: ActivityNotFoundException) {
        Toast.makeText(this, "Die App-Einstellungen ließen sich nicht öffnen.", Toast.LENGTH_SHORT).show()
    }
}
