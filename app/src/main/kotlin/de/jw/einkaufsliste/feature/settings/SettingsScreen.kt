package de.jw.einkaufsliste.feature.settings

import android.os.SystemClock
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.BatteryAlert
import androidx.compose.material.icons.filled.BatteryFull
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.layout.onPlaced
import androidx.compose.ui.layout.positionInParent
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import de.jw.einkaufsliste.AppContainer
import de.jw.einkaufsliste.data.settings.AppSettings
import de.jw.einkaufsliste.ui.navigation.Routes
import kotlin.math.roundToInt

/**
 * "Einstellungen": shop check and its permissions, notification rules, Gemini key, links,
 * list backup, learned purchases and about. [initialSection] ([Routes.SECTION_GEMINI]) is
 * scrolled to once, when the screen is first shown.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    container: AppContainer,
    onBack: () -> Unit,
    onOpenCategories: () -> Unit,
    onOpenBatterySetup: () -> Unit,
    onOpenNearby: () -> Unit = {},
    initialSection: String? = null,
) {
    val viewModel: SettingsViewModel = viewModel { SettingsViewModel(container) }
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val scrollBehavior = TopAppBarDefaults.pinnedScrollBehavior()
    val context = LocalContext.current
    val permissions = rememberCheckPermissionsState(onChanged = viewModel::reschedule)
    // Saveable: coming back from another screen or rotating must not scroll again.
    var pendingSection by rememberSaveable { mutableStateOf(initialSection) }

    // "Erlauben" after "Jetzt prüfen" without location: ask, then check again.
    var locationAskedAt by remember { mutableLongStateOf(0L) }
    val checkLocationLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { result ->
        // Empty: the request was interrupted, not answered.
        if (result.isEmpty()) return@rememberLauncherForActivityResult
        val instant = SystemClock.elapsedRealtime() - locationAskedAt < INSTANT_REFUSAL_MILLIS
        when {
            permissions.refresh().location -> viewModel.checkNow()
            instant -> {
                viewModel.showMessage("Bitte in den App-Einstellungen unter „Berechtigungen → Standort“ den Standort erlauben.")
                context.openAppDetailsSettings()
            }
            else -> viewModel.showMessage("Ohne Standort kann die App keine Läden in der Nähe finden.")
        }
    }

    val latestOnOpenNearby by rememberUpdatedState(onOpenNearby)
    LaunchedEffect(viewModel) {
        viewModel.messages.collect { message ->
            val action = message.action
            val result = snackbarHostState.showSnackbar(
                message = message.text,
                actionLabel = action?.label,
                duration = if (action == null) SnackbarDuration.Short else SnackbarDuration.Long,
            )
            if (result != SnackbarResult.ActionPerformed) return@collect
            when (action) {
                SettingsMessageAction.SHOW_NEARBY -> latestOnOpenNearby()
                SettingsMessageAction.GRANT_LOCATION -> {
                    locationAskedAt = SystemClock.elapsedRealtime()
                    checkLocationLauncher.launch(LOCATION_PERMISSIONS)
                }
                null -> Unit
            }
        }
    }

    Scaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            TopAppBar(
                title = { Text("Einstellungen") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Zurück")
                    }
                },
                scrollBehavior = scrollBehavior,
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { padding ->
        val contentModifier = Modifier
            .fillMaxSize()
            .padding(padding)
            .consumeWindowInsets(padding)
            .imePadding()
        val current = settings
        if (current == null) {
            Box(contentModifier)
        } else {
            SettingsContent(
                settings = current,
                viewModel = viewModel,
                permissions = permissions,
                scrollToGemini = pendingSection == Routes.SECTION_GEMINI,
                onScrolledToSection = { pendingSection = null },
                onOpenCategories = onOpenCategories,
                onOpenBatterySetup = onOpenBatterySetup,
                modifier = contentModifier,
            )
        }
    }
}

@Composable
private fun SettingsContent(
    settings: AppSettings,
    viewModel: SettingsViewModel,
    permissions: CheckPermissionsState,
    scrollToGemini: Boolean,
    onScrolledToSection: () -> Unit,
    onOpenCategories: () -> Unit,
    onOpenBatterySetup: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val hasApiKey by viewModel.hasApiKey.collectAsStateWithLifecycle()
    val checkRunning by viewModel.checkRunning.collectAsStateWithLifecycle()
    val keyTestRunning by viewModel.keyTestRunning.collectAsStateWithLifecycle()
    val backupRunning by viewModel.backupRunning.collectAsStateWithLifecycle()
    val purchaseCount by viewModel.purchaseCount.collectAsStateWithLifecycle()
    val purchaseHistory by viewModel.purchaseHistory.collectAsStateWithLifecycle()
    val contentResolver = LocalContext.current.applicationContext.contentResolver
    val scrollState = rememberScrollState()

    // Top of the Gemini section in the scrolled content; known after the first layout.
    var geminiTop by remember { mutableStateOf<Int?>(null) }
    if (scrollToGemini) {
        LaunchedEffect(geminiTop) {
            val top = geminiTop ?: return@LaunchedEffect
            // A jump, not an animation: the screen is still sliding in.
            scrollState.scrollTo(top)
            onScrolledToSection()
        }
    }

    Column(modifier.verticalScroll(scrollState)) {
        NearbyCheckSection(
            checkEnabled = settings.checkEnabled,
            batterySetupDone = settings.batterySetupDone,
            intervalMinutes = settings.intervalMinutes,
            permissionsState = permissions,
            onCheckEnabledChange = viewModel::setCheckEnabled,
            onOpenBatterySetup = onOpenBatterySetup,
            onMessage = viewModel::showMessage,
        )
        RadiusSection(radiusMeters = settings.radiusMeters, onRadiusChange = viewModel::setRadius)
        IntervalSection(
            intervalMinutes = settings.intervalMinutes,
            backgroundLocation = permissions.current.backgroundLocation,
            onIntervalChange = viewModel::setInterval,
        )
        QuietHoursSection(
            enabled = settings.quietHoursEnabled,
            startMinute = settings.quietStartMinute,
            endMinute = settings.quietEndMinute,
            onEnabledChange = viewModel::setQuietHoursEnabled,
            onStartChange = viewModel::setQuietStart,
            onEndChange = viewModel::setQuietEnd,
        )
        NotificationRulesSection(
            respectOpeningHours = settings.respectOpeningHours,
            cooldownHours = settings.cooldownHours,
            onRespectOpeningHoursChange = viewModel::setRespectOpeningHours,
            onCooldownChange = viewModel::setCooldownHours,
        )
        CheckNowRow(running = checkRunning, onCheckNow = viewModel::checkNow)
        GeminiSection(
            hasKey = hasApiKey,
            storedModel = settings.geminiModel,
            keyTestRunning = keyTestRunning,
            onSaveKey = viewModel::saveApiKey,
            onTestKey = viewModel::testApiKey,
            onDeleteKey = viewModel::deleteApiKey,
            onSaveModel = viewModel::setGeminiModel,
            onMessage = viewModel::showMessage,
            modifier = Modifier.onPlaced { geminiTop = it.positionInParent().y.roundToInt() },
        )

        SectionHeader("Allgemein")
        NavigationRow(
            title = "Unterlisten bearbeiten",
            subtitle = "Reihenfolge, Namen und passende Ladentypen",
            icon = Icons.AutoMirrored.Filled.List,
            onClick = onOpenCategories,
        )
        NavigationRow(
            title = "Samsung-Akku einrichten",
            subtitle = if (settings.batterySetupDone) {
                "Erledigt"
            } else {
                "Empfohlen, damit die Prüfung im Hintergrund nicht gestoppt wird"
            },
            icon = if (settings.batterySetupDone) Icons.Filled.BatteryFull else Icons.Filled.BatteryAlert,
            onClick = onOpenBatterySetup,
        )

        BackupSection(
            running = backupRunning,
            onExport = { uri -> viewModel.exportList(contentResolver, uri) },
            onImport = { uri -> viewModel.importList(contentResolver, uri) },
            onMessage = viewModel::showMessage,
        )

        PurchaseHistorySection(
            count = purchaseCount,
            history = purchaseHistory,
            onRefreshCount = viewModel::refreshPurchaseCount,
            onLoadHistory = viewModel::loadPurchaseHistory,
            onDeleteAll = viewModel::deletePurchaseHistory,
        )

        AboutSection()
        Spacer(Modifier.height(24.dp))
    }
}

@Composable
private fun AboutSection() {
    val context = LocalContext.current
    val version = remember { context.appVersionName() }
    SectionHeader("Über")
    ListItem(
        headlineContent = { Text("Einkaufsliste") },
        supportingContent = { Text(if (version != null) "Version $version" else "Version unbekannt") },
    )
    SectionText("Kartendaten © OpenStreetMap-Mitwirkende (ODbL)")
    SectionText("Produktdaten: Open Food Facts und Schwesterprojekte (ODbL)")
}
