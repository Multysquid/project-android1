package de.jw.einkaufsliste.feature.categories

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.outlined.Restore
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import de.jw.einkaufsliste.AppContainer
import de.jw.einkaufsliste.core.catalog.CategoryCatalog
import de.jw.einkaufsliste.data.db.CategoryEntity
import de.jw.einkaufsliste.data.db.osmTagList
import kotlinx.coroutines.launch

/** Sentinel for "the new-category dialog is open" in the saveable dialog state. */
private const val NEW_CATEGORY = 0L

/** "Unterlisten bearbeiten": order, names, emoji and shop types of the categories. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CategoriesScreen(container: AppContainer, onBack: () -> Unit) {
    val viewModel: CategoriesViewModel = viewModel { CategoriesViewModel(container) }
    val state by viewModel.state.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val scrollBehavior = TopAppBarDefaults.pinnedScrollBehavior()

    // Id of the category in the edit dialog, NEW_CATEGORY for a new one, null when closed.
    var editingId by rememberSaveable { mutableStateOf<Long?>(null) }
    var deletingId by rememberSaveable { mutableStateOf<Long?>(null) }
    var menuOpen by remember { mutableStateOf(false) }

    LaunchedEffect(viewModel) {
        viewModel.messages.collect { snackbarHostState.showSnackbar(it) }
    }

    Scaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            TopAppBar(
                title = { Text("Unterlisten bearbeiten") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Zurück")
                    }
                },
                actions = {
                    Box {
                        IconButton(onClick = { menuOpen = true }) {
                            Icon(Icons.Filled.MoreVert, contentDescription = "Weitere Optionen")
                        }
                        DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                            DropdownMenuItem(
                                text = { Text("Standard-Unterlisten wiederherstellen") },
                                leadingIcon = { Icon(Icons.Outlined.Restore, contentDescription = null) },
                                onClick = {
                                    menuOpen = false
                                    viewModel.restoreDefaults()
                                },
                            )
                        }
                    }
                },
                scrollBehavior = scrollBehavior,
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = { editingId = NEW_CATEGORY },
                icon = { Icon(Icons.Filled.Add, contentDescription = null) },
                text = { Text("Neue Unterliste") },
            )
        },
    ) { padding ->
        val categories = state?.categories
        if (categories == null) {
            Box(Modifier.fillMaxSize().padding(padding))
        } else {
            LazyColumn(
                contentPadding = PaddingValues(bottom = 96.dp),
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
            ) {
                item(key = "intro", contentType = "intro") {
                    Text(
                        text = "In dieser Reihenfolge erscheinen die Unterlisten auf der Einkaufsliste. " +
                            "Die Ladentypen bestimmen, bei welchen Läden in der Nähe sich die App meldet.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                    )
                }
                itemsIndexed(categories, key = { _, category -> category.id }, contentType = { _, _ -> "category" }) { index, category ->
                    CategoryRow(
                        category = category,
                        canMoveUp = index > 0,
                        canMoveDown = index < categories.lastIndex,
                        onMoveUp = { viewModel.move(category.id, -1) },
                        onMoveDown = { viewModel.move(category.id, +1) },
                        onClick = { editingId = category.id },
                        modifier = Modifier.animateItem(),
                    )
                }
            }
        }
    }

    val loaded = state
    val currentEditId = editingId
    if (loaded != null && currentEditId != null) {
        val category = loaded.categories.firstOrNull { it.id == currentEditId }
        if (currentEditId == NEW_CATEGORY || category != null) {
            key(currentEditId) {
                CategoryEditDialog(
                    category = category,
                    onDismiss = { editingId = null },
                    onSave = { draft ->
                        editingId = null
                        viewModel.save(category?.id, draft)
                    },
                    onReset = if (category != null && CategoryCatalog.bySlug(category.slug) != null) {
                        {
                            editingId = null
                            viewModel.resetToDefault(category.id)
                            scope.launch { snackbarHostState.showSnackbar("„${category.name}“ zurückgesetzt") }
                        }
                    } else {
                        null
                    },
                    onDelete = if (category != null && category.slug != CategoryCatalog.FALLBACK_SLUG) {
                        {
                            editingId = null
                            deletingId = category.id
                        }
                    } else {
                        null
                    },
                )
            }
        } else {
            LaunchedEffect(Unit) { editingId = null }
        }
    }

    val currentDeleteId = deletingId
    if (loaded != null && currentDeleteId != null) {
        val category = loaded.categories.firstOrNull { it.id == currentDeleteId }
        if (category != null) {
            val fallbackName = loaded.categories.firstOrNull { it.slug == CategoryCatalog.FALLBACK_SLUG }?.name ?: "Sonstiges"
            val count = loaded.itemCounts[category.id] ?: 0
            AlertDialog(
                onDismissRequest = { deletingId = null },
                title = { Text("„${category.name}“ löschen?") },
                text = {
                    val moved = when (count) {
                        0 -> "Die Unterliste ist leer."
                        1 -> "1 Artikel kommt nach „$fallbackName“."
                        else -> "$count Artikel kommen nach „$fallbackName“."
                    }
                    val isDefault = CategoryCatalog.bySlug(category.slug) != null
                    Text(
                        if (isDefault) {
                            "$moved Über „Standard-Unterlisten wiederherstellen“ im Menü kommt sie wieder."
                        } else {
                            moved
                        },
                    )
                },
                confirmButton = {
                    TextButton(
                        onClick = {
                            deletingId = null
                            viewModel.delete(category.id)
                            scope.launch { snackbarHostState.showSnackbar("„${category.name}“ gelöscht") }
                        },
                    ) { Text("Löschen") }
                },
                dismissButton = {
                    TextButton(onClick = { deletingId = null }) { Text("Abbrechen") }
                },
            )
        } else {
            LaunchedEffect(Unit) { deletingId = null }
        }
    }
}

@Composable
private fun CategoryRow(
    category: CategoryEntity,
    canMoveUp: Boolean,
    canMoveDown: Boolean,
    onMoveUp: () -> Unit,
    onMoveDown: () -> Unit,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    ListItem(
        modifier = modifier.clickable(onClickLabel = "Bearbeiten", onClick = onClick),
        leadingContent = { Text(category.emoji, fontSize = 24.sp) },
        headlineContent = { Text(category.name, maxLines = 1, overflow = TextOverflow.Ellipsis) },
        supportingContent = {
            Text(shopTypesSummary(category), maxLines = 1, overflow = TextOverflow.Ellipsis)
        },
        trailingContent = {
            Row {
                IconButton(onClick = onMoveUp, enabled = canMoveUp) {
                    Icon(Icons.Filled.KeyboardArrowUp, contentDescription = "Nach oben")
                }
                IconButton(onClick = onMoveDown, enabled = canMoveDown) {
                    Icon(Icons.Filled.KeyboardArrowDown, contentDescription = "Nach unten")
                }
            }
        },
    )
}

/** "Supermarkt, Bäckerei, Lebensmittelladen …" (at most three labels). */
private fun shopTypesSummary(category: CategoryEntity): String {
    val labels = category.osmTagList().map { CategoryCatalog.shopTagLabel(it) }.distinct()
    return when {
        labels.isEmpty() -> "Keine Ladentypen"
        labels.size <= 3 -> labels.joinToString(", ")
        else -> labels.take(3).joinToString(", ") + " …"
    }
}
