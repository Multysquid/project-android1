package de.jw.einkaufsliste.feature.list

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.Text
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import de.jw.einkaufsliste.data.db.ItemEntity

/** "🥦 Obst & Gemüse   2 offen  ⌄"; tapping it collapses or expands the section. */
@Composable
internal fun SectionHeader(
    title: String,
    openCount: Int,
    collapsed: Boolean,
    onToggle: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClickLabel = if (collapsed) "Aufklappen" else "Zuklappen", role = Role.Button, onClick = onToggle)
            .semantics { stateDescription = if (collapsed) "zugeklappt" else "aufgeklappt" }
            .padding(start = 16.dp, end = 12.dp, top = 16.dp, bottom = 4.dp),
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.primary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        Text(
            text = if (openCount > 0) "$openCount offen" else "erledigt",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 8.dp),
        )
        Icon(
            imageVector = if (collapsed) Icons.Filled.ExpandMore else Icons.Filled.ExpandLess,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(20.dp),
        )
    }
}

/**
 * One item: checkbox, name, quantity/note and pinned shop. Swiping either way
 * deletes it ([onDelete] is called once).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun ItemRow(
    item: ItemEntity,
    pinnedShopName: String?,
    onCheckedChange: (Boolean) -> Unit,
    onClick: () -> Unit,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val currentOnDelete by rememberUpdatedState(onDelete)
    var deleted by remember { mutableStateOf(false) }
    val dismissState = rememberSwipeToDismissBoxState(
        confirmValueChange = { value ->
            if (value != SwipeToDismissBoxValue.Settled && !deleted) {
                deleted = true
                currentOnDelete()
            }
            true
        },
    )

    SwipeToDismissBox(
        state = dismissState,
        modifier = modifier,
        backgroundContent = {
            val alignment = if (dismissState.dismissDirection == SwipeToDismissBoxValue.StartToEnd) {
                Alignment.CenterStart
            } else {
                Alignment.CenterEnd
            }
            Box(
                contentAlignment = alignment,
                modifier = Modifier
                    .fillMaxSize()
                    .background(MaterialTheme.colorScheme.errorContainer)
                    .padding(horizontal = 24.dp),
            ) {
                Icon(
                    imageVector = Icons.Outlined.Delete,
                    contentDescription = "Löschen",
                    tint = MaterialTheme.colorScheme.onErrorContainer,
                )
            }
        },
    ) {
        ItemRowContent(item, pinnedShopName, onCheckedChange, onClick)
    }
}

@Composable
private fun ItemRowContent(
    item: ItemEntity,
    pinnedShopName: String?,
    onCheckedChange: (Boolean) -> Unit,
    onClick: () -> Unit,
) {
    val textAlpha = if (item.done) 0.55f else 1f
    val decoration = if (item.done) TextDecoration.LineThrough else null
    val details = listOfNotNull(item.quantity, item.note).joinToString(" · ")
    val hasSupporting = details.isNotEmpty() || pinnedShopName != null

    ListItem(
        modifier = Modifier.clickable(onClickLabel = "Bearbeiten", onClick = onClick),
        leadingContent = {
            Checkbox(checked = item.done, onCheckedChange = onCheckedChange)
        },
        headlineContent = {
            Text(
                text = item.name,
                textDecoration = decoration,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.alpha(textAlpha),
            )
        },
        supportingContent = if (hasSupporting) {
            {
                Column(Modifier.alpha(textAlpha)) {
                    if (details.isNotEmpty()) {
                        Text(text = details, textDecoration = decoration, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    }
                    if (pinnedShopName != null) {
                        Text(
                            text = "📍 $pinnedShopName",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.tertiary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
            }
        } else {
            null
        },
    )
}
