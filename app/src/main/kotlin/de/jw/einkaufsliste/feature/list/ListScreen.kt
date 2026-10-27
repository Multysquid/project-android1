package de.jw.einkaufsliste.feature.list

import android.Manifest
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.outlined.DeleteSweep
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.Storefront
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.TopAppBarScrollBehavior
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import de.jw.einkaufsliste.AppContainer
import de.jw.einkaufsliste.data.db.ItemEntity
import de.jw.einkaufsliste.data.repo.ListSection
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/** Collapse key for the section of items whose category is missing. */
private const val NO_CATEGORY_KEY = -1L

private val LOCATION_PERMISSIONS = arrayOf(
    Manifest.permission.ACCESS_FINE_LOCATION,
    Manifest.permission.ACCESS_COARSE_LOCATION,
)

/** The shopping list, grouped into "Unterlisten"; the app's start screen. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ListScreen(
    container: AppContainer,
    onAddByBarcode: () -> Unit,
    onAddByPhoto: () -> Unit,
    onOpenNearby: () -> Unit,
    onOpenSettings: () -> Unit,
) {
    val viewModel: ListViewModel = viewModel { ListViewModel(container) }
    val state by viewModel.state.collectAsStateWithLifecycle()
    val here by viewModel.here.collectAsStateWithLifecycle()
    val shopLoad by viewModel.shopLoad.collectAsStateWithLifecycle()

    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val scrollBehavior = TopAppBarDefaults.pinnedScrollBehavior()

    var collapsed by rememberSaveable { mutableStateOf(emptySet<Long>()) }
    var showAddSheet by rememberSaveable { mutableStateOf(false) }
    var showAddDialog by rememberSaveable { mutableStateOf(false) }
    var editingId by rememberSaveable { mutableStateOf<Long?>(null) }
    var confirmClearDone by rememberSaveable { mutableStateOf(false) }
    // How often each item was restored. "Rückgängig" brings an item back with its old id; its
    // row needs a new list key, or the lazy list restores the old row's swiped-away state.
    val restores = remember { mutableStateMapOf<Long, Int>() }

    // "Läden laden" in the shop picker: the picker explains why the location is needed, then
    // Android asks for it.
    val locationPermissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
        if (container.locationProvider.hasForegroundPermission()) {
            // The dialog does not restart the activity; a check schedule cancelled for the
            // missing permission would otherwise stay off until the app is opened again.
            container.appScope.launch { container.checkScheduler.apply(appInForeground = true) }
        }
        viewModel.loadShops()
    }
    val loadShops: () -> Unit = {
        if (container.locationProvider.hasForegroundPermission()) {
            viewModel.loadShops()
        } else {
            locationPermissionLauncher.launch(LOCATION_PERMISSIONS)
        }
    }

    val deleteItem: (ItemEntity) -> Unit = { item ->
        viewModel.delete(item.id)
        scope.showMessage(snackbarHostState, "„${item.name}“ gelöscht", actionLabel = "Rückgängig") {
            restores[item.id] = (restores[item.id] ?: 0) + 1
            viewModel.restore(item)
        }
    }

    Scaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            ListTopBar(
                subtitle = when {
                    !state.loaded || state.isEmpty -> null
                    state.openCount == 0 -> "Alles erledigt"
                    else -> "${state.openCount} Artikel offen"
                },
                canClearDone = state.doneCount > 0,
                scrollBehavior = scrollBehavior,
                onOpenNearby = onOpenNearby,
                onOpenSettings = onOpenSettings,
                onClearDone = { confirmClearDone = true },
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
        floatingActionButton = {
            FloatingActionButton(onClick = { showAddSheet = true }) {
                Icon(Icons.Filled.Add, contentDescription = "Hinzufügen")
            }
        },
    ) { padding ->
        when {
            !state.loaded -> Box(Modifier.fillMaxSize().padding(padding))
            state.isEmpty -> Column(Modifier.fillMaxSize().padding(padding)) {
                // A new user must learn early that the shop check is off; with the card only in
                // the list, someone who just looks around without adding anything never sees it.
                if (state.showCheckBanner) {
                    CheckOffBanner(onOpenSettings = onOpenSettings, onHide = viewModel::hideCheckBanner)
                }
                EmptyList(Modifier.fillMaxWidth().weight(1f))
            }
            else -> LazyColumn(
                contentPadding = PaddingValues(bottom = 96.dp),
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
            ) {
                if (state.showCheckBanner) {
                    item(key = "banner", contentType = "banner") {
                        CheckOffBanner(
                            onOpenSettings = onOpenSettings,
                            onHide = viewModel::hideCheckBanner,
                            modifier = Modifier.animateItem(),
                        )
                    }
                }
                state.sections.forEach { section ->
                    val sectionKey = section.category?.id ?: NO_CATEGORY_KEY
                    val isCollapsed = sectionKey in collapsed
                    item(key = "section-$sectionKey", contentType = "header") {
                        SectionHeader(
                            title = section.title(),
                            openCount = section.openCount,
                            collapsed = isCollapsed,
                            onToggle = { collapsed = if (isCollapsed) collapsed - sectionKey else collapsed + sectionKey },
                            modifier = Modifier.animateItem(),
                        )
                    }
                    if (!isCollapsed) {
                        items(
                            section.items,
                            key = { "item-${it.id}-${restores[it.id] ?: 0}" },
                            contentType = { "item" },
                        ) { item ->
                            ItemRow(
                                item = item,
                                pinnedShopName = item.pinnedShopId?.let { id ->
                                    state.shops[id]?.displayName() ?: if (state.shopsLoaded) MISSING_SHOP_LABEL else "…"
                                },
                                onCheckedChange = { done -> viewModel.setDone(item.id, done) },
                                onClick = { editingId = item.id },
                                onDelete = { deleteItem(item) },
                                modifier = Modifier.animateItem(),
                            )
                        }
                    }
                }
            }
        }
    }

    if (showAddSheet) {
        AddOptionsSheet(
            onDismiss = { showAddSheet = false },
            onType = { showAddDialog = true },
            onPhoto = onAddByPhoto,
            onBarcode = onAddByBarcode,
        )
    }

    if (showAddDialog && state.loaded) {
        ItemEditDialog(
            item = null,
            categories = state.categories,
            existingItems = state.allItems,
            shops = state.shops,
            here = here,
            shopLoad = shopLoad,
            onRequestLocation = viewModel::locate,
            onLoadShops = loadShops,
            onDismiss = { showAddDialog = false },
            onSave = { draft ->
                showAddDialog = false
                viewModel.add(draft)
                scope.showMessage(snackbarHostState, "„${draft.name}“ hinzugefügt")
            },
            onDelete = {},
        )
    }

    val editing = editingId?.let { id -> state.allItems.firstOrNull { it.id == id } }
    if (editing != null) {
        key(editing.id) {
            ItemEditDialog(
                item = editing,
                categories = state.categories,
                existingItems = state.allItems,
                shops = state.shops,
                here = here,
                shopLoad = shopLoad,
                onRequestLocation = viewModel::locate,
                onLoadShops = loadShops,
                onDismiss = { editingId = null },
                onSave = { draft ->
                    editingId = null
                    viewModel.save(editing.id, draft)
                },
                onDelete = {
                    editingId = null
                    deleteItem(editing)
                },
            )
        }
    } else if (editingId != null && state.loaded) {
        // The item is gone (deleted elsewhere); close the dialog.
        LaunchedEffect(Unit) { editingId = null }
    }

    if (confirmClearDone) {
        val count = state.doneCount
        AlertDialog(
            onDismissRequest = { confirmClearDone = false },
            title = { Text("Erledigte löschen?") },
            text = {
                Text(
                    if (count == 1) {
                        "1 erledigter Artikel wird von der Liste gelöscht."
                    } else {
                        "$count erledigte Artikel werden von der Liste gelöscht."
                    },
                )
            },
            confirmButton = {
                TextButton(
                    enabled = count > 0,
                    onClick = {
                        confirmClearDone = false
                        viewModel.clearDone()
                        scope.showMessage(snackbarHostState, if (count == 1) "1 Artikel gelöscht" else "$count Artikel gelöscht")
                    },
                ) { Text("Löschen") }
            },
            dismissButton = {
                TextButton(onClick = { confirmClearDone = false }) { Text("Abbrechen") }
            },
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ListTopBar(
    subtitle: String?,
    canClearDone: Boolean,
    scrollBehavior: TopAppBarScrollBehavior,
    onOpenNearby: () -> Unit,
    onOpenSettings: () -> Unit,
    onClearDone: () -> Unit,
) {
    var menuOpen by remember { mutableStateOf(false) }
    TopAppBar(
        title = {
            Column {
                Text("Einkaufsliste")
                if (subtitle != null) {
                    Text(
                        text = subtitle,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        },
        actions = {
            IconButton(onClick = onOpenNearby) {
                Icon(Icons.Outlined.Storefront, contentDescription = "In der Nähe")
            }
            Box {
                IconButton(onClick = { menuOpen = true }) {
                    Icon(Icons.Filled.MoreVert, contentDescription = "Weitere Optionen")
                }
                DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                    DropdownMenuItem(
                        text = { Text("Erledigte löschen") },
                        leadingIcon = { Icon(Icons.Outlined.DeleteSweep, contentDescription = null) },
                        enabled = canClearDone,
                        onClick = {
                            menuOpen = false
                            onClearDone()
                        },
                    )
                    DropdownMenuItem(
                        text = { Text("Einstellungen") },
                        leadingIcon = { Icon(Icons.Outlined.Settings, contentDescription = null) },
                        onClick = {
                            menuOpen = false
                            onOpenSettings()
                        },
                    )
                }
            }
        },
        scrollBehavior = scrollBehavior,
    )
}

@Composable
private fun CheckOffBanner(onOpenSettings: () -> Unit, onHide: () -> Unit, modifier: Modifier = Modifier) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer),
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
    ) {
        Column(Modifier.padding(start = 16.dp, end = 8.dp, top = 16.dp, bottom = 4.dp)) {
            Text(
                text = "„Läden in der Nähe prüfen“ ist aus. Schalte es in den Einstellungen ein, " +
                    "damit die App dir Läden in der Nähe meldet.",
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(end = 8.dp),
            )
            Row(Modifier.align(Alignment.End)) {
                TextButton(onClick = onHide) { Text("Ausblenden") }
                TextButton(onClick = onOpenSettings) { Text("Zu den Einstellungen") }
            }
        }
    }
}

@Composable
private fun EmptyList(modifier: Modifier = Modifier) {
    Column(
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = modifier.padding(32.dp),
    ) {
        Text("🛒", style = MaterialTheme.typography.displayMedium)
        Spacer(Modifier.height(16.dp))
        Text(
            text = "Deine Liste ist leer.",
            style = MaterialTheme.typography.titleMedium,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(4.dp))
        Text(
            text = "Tippe auf +, um etwas hinzuzufügen.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
    }
}

private fun ListSection.title(): String =
    category?.let { "${it.emoji} ${it.name}" } ?: "🛒 Ohne Unterliste"

/** Replaces any visible snackbar with [message]; runs [onAction] when its action is tapped. */
private fun CoroutineScope.showMessage(
    host: SnackbarHostState,
    message: String,
    actionLabel: String? = null,
    onAction: () -> Unit = {},
) {
    launch {
        host.currentSnackbarData?.dismiss()
        val result = host.showSnackbar(
            message = message,
            actionLabel = actionLabel,
            duration = if (actionLabel != null) SnackbarDuration.Long else SnackbarDuration.Short,
        )
        if (result == SnackbarResult.ActionPerformed) onAction()
    }
}
