package com.ggym.app.ui.common

import androidx.compose.foundation.layout.Box
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import com.ggym.app.R

/** One entry of [RowMenu]: a label, an optional icon, and whether it can be chosen right now. */
data class RowMenuAction(
    val label: String,
    val icon: ImageVector? = null,
    val enabled: Boolean = true,
    val onClick: () -> Unit,
)

/**
 * The overflow menu every list row uses.
 *
 * Reordering lives here as plain "Move up" / "Move down" entries rather than drag-and-drop: a
 * gesture with no keyboard or screen-reader equivalent is not the only way to arrange a list, and
 * this mapping is exactly what the repositories' reorder calls take. A disabled entry (moving the
 * first row up) is shown greyed out, so the list does not appear to lose a menu item at its ends.
 */
@Composable
fun RowMenu(actions: List<RowMenuAction>, modifier: Modifier = Modifier) {
    var expanded by remember { mutableStateOf(false) }

    Box(modifier = modifier) {
        IconButton(onClick = { expanded = true }) {
            Icon(Icons.Filled.MoreVert, contentDescription = stringResource(R.string.action_more))
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            actions.forEach { action ->
                DropdownMenuItem(
                    text = { Text(action.label) },
                    leadingIcon = action.icon?.let { icon -> { Icon(icon, contentDescription = null) } },
                    enabled = action.enabled,
                    onClick = {
                        expanded = false
                        action.onClick()
                    },
                )
            }
        }
    }
}
