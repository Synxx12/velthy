package com.velthy.client.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.ManageAccounts
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.compose.AsyncImage
import com.velthy.client.auth.SavedAccount
import com.velthy.client.data.settings.AppSettings
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.hazeEffect
import dev.chrisbanes.haze.materials.ExperimentalHazeMaterialsApi
import dev.chrisbanes.haze.materials.HazeMaterials

/**
 * The account switcher the top bar's avatar opens.
 *
 * A compact overlay rather than a page: picking between two signed-in accounts
 * is a two-tap errand, and the player, the queue or a page behind it should not
 * be torn down to answer it. The list is the saved sessions — one row each —
 * with the management actions under them.
 */
@OptIn(ExperimentalHazeMaterialsApi::class)
@Composable
fun AccountProfileSelector(
    accounts: List<SavedAccount>,
    activeAccountId: String?,
    hazeState: HazeState,
    onSelect: (SavedAccount) -> Unit,
    onAddAccount: () -> Unit,
    onRemoveAccount: (SavedAccount) -> Unit,
    onOpenSettings: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var managing by remember { mutableStateOf(false) }
    val reduceDynamicBlur by AppSettings.reduceDynamicBlur.collectAsStateWithLifecycle()
    val shape = MaterialTheme.shapes.extraLarge
    Column(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.scrim.copy(alpha = 0.48f))
            .clickable(onClick = onDismiss),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Surface(
            color = Color.Transparent,
            contentColor = MaterialTheme.colorScheme.onSurface,
            shape = shape,
            modifier = Modifier
                .padding(top = 56.dp, start = 20.dp, end = 20.dp)
                .fillMaxWidth()
                .clip(shape)
                .then(
                    if (reduceDynamicBlur) {
                        Modifier.background(MaterialTheme.colorScheme.surface)
                    } else {
                        Modifier.hazeEffect(
                            state = hazeState,
                            style = HazeMaterials.thin(MaterialTheme.colorScheme.surface),
                        )
                    },
                )
                // Absorbs taps on the panel itself, so only the scrim around it
                // dismisses.
                .clickable(onClick = {}),
        ) {
            LazyColumn {
                item {
                    Text(
                        text = "Switch account",
                        style = MaterialTheme.typography.titleLarge,
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.padding(20.dp, 18.dp, 20.dp, 8.dp),
                    )
                }
                if (accounts.isEmpty()) {
                    item {
                        Text(
                            text = "No accounts saved yet",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(20.dp, 4.dp, 20.dp, 12.dp),
                        )
                    }
                }
                items(accounts.size, key = { accounts[it].id }) { index ->
                    val account = accounts[index]
                    AccountRow(
                        account = account,
                        selected = account.id == activeAccountId,
                        managing = managing,
                        onClick = {
                            onSelect(account)
                            onDismiss()
                        },
                        onRemove = { onRemoveAccount(account) },
                    )
                }
                item { SelectorAction(Icons.Rounded.Add, "Add account", onAddAccount) }
                item {
                    SelectorAction(Icons.Rounded.ManageAccounts, "Manage accounts") {
                        managing = !managing
                    }
                }
                item { SelectorAction(Icons.Rounded.Settings, "Settings", onOpenSettings) }
            }
        }
    }
}

@Composable
private fun AccountRow(
    account: SavedAccount,
    selected: Boolean,
    managing: Boolean,
    onClick: () -> Unit,
    onRemove: () -> Unit,
) {
    val description = if (selected) "Selected account" else "Switch account"
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 56.dp)
            .clickable(role = Role.RadioButton, onClick = if (managing) onRemove else onClick)
            .semantics { contentDescription = description }
            .padding(horizontal = 20.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (account.thumbnailUrl != null) {
            AsyncImage(
                model = account.thumbnailUrl,
                contentDescription = null,
                modifier = Modifier
                    .size(38.dp)
                    .clip(MaterialTheme.shapes.extraLarge),
            )
        } else {
            Icon(
                Icons.Rounded.Person,
                contentDescription = null,
                modifier = Modifier.size(38.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(account.name, maxLines = 1, overflow = TextOverflow.Ellipsis)
            // The channel the session was left acting as, when it has one —
            // that, not the login's handle, is what the listener is choosing
            // between when two of these point at the same Google account.
            Text(
                text = account.channelName?.takeIf { it.isNotBlank() }
                    ?: account.handle?.takeIf { it.isNotBlank() }
                    ?: "Personal",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        if (selected && !managing) {
            Icon(
                Icons.Rounded.Check,
                contentDescription = "Selected account",
                tint = MaterialTheme.colorScheme.primary,
            )
        }
        if (managing) {
            Text("Sign out", color = MaterialTheme.colorScheme.error)
        }
    }
}

@Composable
private fun SelectorAction(icon: ImageVector, label: String, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 52.dp)
            .clickable(role = Role.Button, onClick = onClick)
            .semantics { contentDescription = label }
            .padding(horizontal = 20.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, contentDescription = null)
        Spacer(Modifier.width(16.dp))
        Text(label)
    }
}
