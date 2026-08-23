package com.music.bitchord.ui.screens

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AddCircleOutline
import androidx.compose.material.icons.rounded.Autorenew
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Cloud
import androidx.compose.material.icons.rounded.GraphicEq
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Sync
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.compose.AsyncImage
import com.music.bitchord.data.model.Account
import com.music.bitchord.data.settings.AppSettings
import com.music.bitchord.ui.components.thumbnailBorder
import kotlin.math.roundToInt

import androidx.compose.material.icons.rounded.BookmarkBorder
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import com.music.bitchord.auth.SavedAccount

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AccountAndScrobblingScreen(
    signedIn: Boolean,
    account: Account?,
    savedAccounts: List<SavedAccount> = emptyList(),
    currentCookie: String? = null,
    onSignIn: () -> Unit,
    onSignOut: () -> Unit,
    onSyncNow: () -> Unit,
    onSaveCurrentAccount: () -> Unit = {},
    onSwitchAccount: (SavedAccount) -> Unit = {},
    onRemoveSavedAccount: (String) -> Unit = {},
    onAddAnotherAccount: () -> Unit = {},
    onOpenListenBrainzLogin: () -> Unit,
    onOpenLastfmLogin: () -> Unit,
    contentPadding: PaddingValues,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current

    // Account & General Settings
    val moreContent by AppSettings.accountMoreContent.collectAsStateWithLifecycle()
    val autoSync by AppSettings.accountAutoSync.collectAsStateWithLifecycle()
    val forceSyncOnSwitch by AppSettings.accountForceSyncOnSwitch.collectAsStateWithLifecycle()

    // Scrobbling states
    val lastfmEnabled by AppSettings.lastfmEnabled.collectAsStateWithLifecycle()
    val lastfmUsername by AppSettings.lastfmUsername.collectAsStateWithLifecycle()
    val lastfmSessionKey by AppSettings.lastfmSessionKey.collectAsStateWithLifecycle()
    val lastfmScrobbleEnabled by AppSettings.lastfmScrobbleEnabled.collectAsStateWithLifecycle()
    val lastfmNowPlayingEnabled by AppSettings.lastfmNowPlaying.collectAsStateWithLifecycle()
    val scrobbleMinDuration by AppSettings.scrobbleMinDuration.collectAsStateWithLifecycle()
    val scrobbleDelayPercent by AppSettings.scrobbleDelayPercent.collectAsStateWithLifecycle()
    val scrobbleDelaySeconds by AppSettings.scrobbleDelaySeconds.collectAsStateWithLifecycle()
    val listenBrainzEnabled by AppSettings.listenBrainzEnabled.collectAsStateWithLifecycle()
    val listenBrainzToken by AppSettings.listenBrainzToken.collectAsStateWithLifecycle()

    var showSignOutConfirm by remember { mutableStateOf(false) }
    var showSavedAccountsSheet by remember { mutableStateOf(false) }

    if (showSavedAccountsSheet) {
        SavedAccountsBottomSheet(
            currentAccount = account,
            currentCookie = currentCookie,
            savedAccounts = savedAccounts,
            onDismiss = { showSavedAccountsSheet = false },
            onSaveCurrent = {
                onSaveCurrentAccount()
                Toast.makeText(context, "Account saved", Toast.LENGTH_SHORT).show()
            },
            onSelect = { saved ->
                showSavedAccountsSheet = false
                onSwitchAccount(saved)
            },
            onDelete = { id ->
                onRemoveSavedAccount(id)
            },
            onAddAnother = {
                showSavedAccountsSheet = false
                onAddAnotherAccount()
            },
        )
    }

    if (showSignOutConfirm) {
        AlertDialog(
            onDismissRequest = { showSignOutConfirm = false },
            title = { Text("Log out from account?") },
            text = {
                Text("You will be signed out of YouTube Music. Your local playlists and playback cache will remain on this device.")
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        showSignOutConfirm = false
                        onSignOut()
                    },
                ) {
                    Text("Log out", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { showSignOutConfirm = false }) {
                    Text("Cancel")
                }
            },
        )
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(contentPadding),
    ) {
        Text(
            text = "Account",
            style = MaterialTheme.typography.displayLarge,
            color = MaterialTheme.colorScheme.onBackground,
            modifier = Modifier.padding(start = 20.dp, end = 20.dp, top = 8.dp, bottom = 14.dp),
        )

        // ── Main Account Card (YouTube Music Parity) ────────────────────────
        ProfileAccountCard(
            signedIn = signedIn,
            account = account,
            onSignIn = onSignIn,
            onAccountClick = {
                if (signedIn) {
                    showSavedAccountsSheet = true
                } else {
                    onSignIn()
                }
            },
            onSignOutClick = { showSignOutConfirm = true },
        )

        // ── General Section ─────────────────────────────────────────────────
        SettingsGroup(
            header = "General",
        ) {
            SettingsRow(
                icon = Icons.Rounded.AddCircleOutline,
                title = "More content",
                subtitle = "This can influence what content you see and for example shows premium-only albums if you are signed in",
                trailing = {
                    Switch(
                        checked = moreContent,
                        onCheckedChange = AppSettings::setAccountMoreContent,
                        colors = SwitchDefaults.colors(
                            checkedTrackColor = MaterialTheme.colorScheme.primary,
                            checkedBorderColor = MaterialTheme.colorScheme.primary,
                        ),
                    )
                },
            )
            RowDivider()
            SettingsRow(
                icon = Icons.Rounded.Sync,
                title = "Auto sync with account",
                subtitle = "Automatically sync listening history, playlists, and liked songs with your YouTube Music account",
                trailing = {
                    Switch(
                        checked = autoSync,
                        onCheckedChange = AppSettings::setAccountAutoSync,
                        colors = SwitchDefaults.colors(
                            checkedTrackColor = MaterialTheme.colorScheme.primary,
                            checkedBorderColor = MaterialTheme.colorScheme.primary,
                        ),
                    )
                },
            )
            RowDivider()
            SettingsRow(
                icon = Icons.Rounded.Autorenew,
                title = "Force Sync on Switch Account",
                subtitle = "Re-sync playlists, liked songs, artists, and albums from the selected account after switching",
                trailing = {
                    Switch(
                        checked = forceSyncOnSwitch,
                        onCheckedChange = AppSettings::setAccountForceSyncOnSwitch,
                        colors = SwitchDefaults.colors(
                            checkedTrackColor = MaterialTheme.colorScheme.primary,
                            checkedBorderColor = MaterialTheme.colorScheme.primary,
                        ),
                    )
                },
            )
            if (signedIn) {
                RowDivider()
                SettingsRow(
                    icon = Icons.Rounded.Refresh,
                    title = "Sync with account now",
                    subtitle = "Pull latest cloud history and library updates immediately",
                    onClick = {
                        onSyncNow()
                        Toast.makeText(context, "Syncing account data...", Toast.LENGTH_SHORT).show()
                    },
                )
            }
        }

        // ── Integration / Scrobbling Section ────────────────────────────────
        SettingsGroup(
            header = "Integration",
            footer = "Scrobble your listens to Last.fm and ListenBrainz.",
        ) {
            SettingsRow(
                icon = Icons.Rounded.Cloud,
                title = "ListenBrainz",
                subtitle = if (listenBrainzEnabled && listenBrainzToken.isNotBlank()) "Connected" else "Enter a token to enable",
                trailing = {
                    Switch(
                        checked = listenBrainzEnabled,
                        onCheckedChange = AppSettings::setListenBrainzEnabled,
                        colors = SwitchDefaults.colors(
                            checkedTrackColor = MaterialTheme.colorScheme.primary,
                            checkedBorderColor = MaterialTheme.colorScheme.primary,
                        ),
                    )
                },
                onClick = onOpenListenBrainzLogin,
            )
            RowDivider()
            SettingsRow(
                icon = Icons.Rounded.History,
                title = "Last.fm",
                subtitle = if (lastfmSessionKey.isNotBlank()) "Signed in as $lastfmUsername" else "Tap to sign in",
                trailing = {
                    Switch(
                        checked = lastfmEnabled,
                        onCheckedChange = AppSettings::setLastfmEnabled,
                        colors = SwitchDefaults.colors(
                            checkedTrackColor = MaterialTheme.colorScheme.primary,
                            checkedBorderColor = MaterialTheme.colorScheme.primary,
                        ),
                    )
                },
                onClick = {
                    if (lastfmSessionKey.isNotBlank()) {
                        AppSettings.setLastfmSessionKey("")
                        AppSettings.setLastfmUsername("")
                        AppSettings.setLastfmEnabled(false)
                        AppSettings.setLastfmScrobbleEnabled(false)
                        AppSettings.setLastfmNowPlaying(false)
                    } else {
                        onOpenLastfmLogin()
                    }
                },
            )
            if (lastfmEnabled && lastfmSessionKey.isNotBlank()) {
                RowDivider()
                SettingsRow(
                    icon = Icons.Rounded.GraphicEq,
                    title = "Scrobble tracks",
                    subtitle = "Log plays to your Last.fm timeline",
                    trailing = {
                        Switch(
                            checked = lastfmScrobbleEnabled,
                            onCheckedChange = AppSettings::setLastfmScrobbleEnabled,
                            colors = SwitchDefaults.colors(
                                checkedTrackColor = MaterialTheme.colorScheme.primary,
                                checkedBorderColor = MaterialTheme.colorScheme.primary,
                            ),
                        )
                    },
                    onClick = { AppSettings.setLastfmScrobbleEnabled(!lastfmScrobbleEnabled) },
                )
                RowDivider()
                SettingsRow(
                    icon = Icons.Rounded.GraphicEq,
                    title = "Now playing",
                    subtitle = "Update Last.fm with what you're listening to",
                    trailing = {
                        Switch(
                            checked = lastfmNowPlayingEnabled,
                            onCheckedChange = AppSettings::setLastfmNowPlaying,
                            colors = SwitchDefaults.colors(
                                checkedTrackColor = MaterialTheme.colorScheme.primary,
                                checkedBorderColor = MaterialTheme.colorScheme.primary,
                            ),
                        )
                    },
                    onClick = { AppSettings.setLastfmNowPlaying(!lastfmNowPlayingEnabled) },
                )
            }
        }

        if (lastfmEnabled && lastfmSessionKey.isNotBlank()) {
            SettingsGroup(header = "Scrobble timing") {
                SliderRow(
                    icon = Icons.Rounded.Tune,
                    title = "Min song duration",
                    subtitle = "Songs shorter than this won't scrobble",
                    value = "${scrobbleMinDuration}s",
                    sliderValue = scrobbleMinDuration.toFloat(),
                    onSliderValue = { AppSettings.setScrobbleMinDuration(it.roundToInt()) },
                    valueRange = 15f..120f,
                    steps = 20,
                )
                RowDivider()
                SliderRow(
                    icon = Icons.Rounded.Tune,
                    title = "Scrobble delay",
                    subtitle = "How far into a song before scrobbling",
                    value = "${(scrobbleDelayPercent * 100).roundToInt()}%",
                    sliderValue = scrobbleDelayPercent,
                    onSliderValue = { AppSettings.setScrobbleDelayPercent(it) },
                    valueRange = 0.1f..1.0f,
                    steps = 8,
                )
                RowDivider()
                SliderRow(
                    icon = Icons.Rounded.Tune,
                    title = "Max delay",
                    subtitle = "Cap on scrobble delay in seconds",
                    value = "${scrobbleDelaySeconds}s",
                    sliderValue = scrobbleDelaySeconds.toFloat(),
                    onSliderValue = { AppSettings.setScrobbleDelaySeconds(it.roundToInt()) },
                    valueRange = 30f..300f,
                    steps = 26,
                )
            }
        }

        Spacer(Modifier.height(32.dp))
    }
}

/**
 * Large, premium account profile card mirroring YouTube Music's Account menu.
 * Features verified avatar badge, display name, handle, account switch button,
 * and distinct logout action.
 */
@Composable
private fun ProfileAccountCard(
    signedIn: Boolean,
    account: Account?,
    onSignIn: () -> Unit,
    onAccountClick: () -> Unit,
    onSignOutClick: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = GROUP_INSET)
            .clip(RoundedCornerShape(22.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .then(if (signedIn) Modifier else Modifier.clickable(onClick = onSignIn))
            .padding(horizontal = 20.dp, vertical = 20.dp),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth(),
        ) {
            // Profile photo with verified badge
            Box(
                modifier = Modifier.size(68.dp),
                contentAlignment = Alignment.BottomEnd,
            ) {
                if (account?.thumbnailUrl != null) {
                    AsyncImage(
                        model = account.thumbnailUrl,
                        contentDescription = "Profile Photo",
                        contentScale = ContentScale.Crop,
                        modifier = Modifier
                            .size(68.dp)
                            .clip(CircleShape)
                            .thumbnailBorder(CircleShape),
                    )
                } else {
                    Box(
                        modifier = Modifier
                            .size(68.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.surfaceContainerHighest),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Person,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(36.dp),
                        )
                    }
                }

                if (signedIn) {
                    // Blue verified checkmark circle
                    Box(
                        modifier = Modifier
                            .size(22.dp)
                            .clip(CircleShape)
                            .background(Color(0xFF3B82F6)),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Check,
                            contentDescription = "Verified Account",
                            tint = Color.White,
                            modifier = Modifier.size(14.dp),
                        )
                    }
                }
            }

            Spacer(Modifier.width(16.dp))

            Column(Modifier.weight(1f)) {
                Text(
                    text = account?.name ?: if (signedIn) "YouTube User" else "Not signed in",
                    style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                    color = MaterialTheme.colorScheme.onBackground,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(Modifier.height(3.dp))
                Text(
                    text = account?.email?.takeIf { it.isNotBlank() }
                        ?: if (signedIn) "YouTube Music connected" else "Tap to sign in with Google",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }

        Spacer(Modifier.height(18.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // Pill button [ 👤 Account ▾ ]
            Surface(
                shape = RoundedCornerShape(14.dp),
                color = MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.75f),
                modifier = Modifier
                    .clip(RoundedCornerShape(14.dp))
                    .clickable(onClick = onAccountClick),
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 9.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Person,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.size(17.dp),
                    )
                    Spacer(Modifier.width(6.dp))
                    Text(
                        text = if (signedIn) "Account" else "Sign in",
                        style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.SemiBold),
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    Spacer(Modifier.width(4.dp))
                    Icon(
                        imageVector = Icons.Rounded.KeyboardArrowDown,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(18.dp),
                    )
                }
            }

            Spacer(Modifier.weight(1f))

            if (signedIn) {
                // Log out button
                Text(
                    text = "Log out",
                    style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Medium),
                    color = Color(0xFFFF8B8B),
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .clickable(onClick = onSignOutClick)
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                )
            }
        }
    }
}

/**
 * Saved accounts bottom sheet with options to switch, save current, or add another account.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SavedAccountsBottomSheet(
    currentAccount: Account?,
    currentCookie: String?,
    savedAccounts: List<SavedAccount>,
    onDismiss: () -> Unit,
    onSaveCurrent: () -> Unit,
    onSelect: (SavedAccount) -> Unit,
    onDelete: (String) -> Unit,
    onAddAnother: () -> Unit,
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = MaterialTheme.colorScheme.surface,
        dragHandle = {
            Surface(
                modifier = Modifier.padding(top = 12.dp, bottom = 8.dp),
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f),
                shape = RoundedCornerShape(2.dp),
            ) {
                Box(Modifier.size(width = 36.dp, height = 4.dp))
            }
        },
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .padding(bottom = 32.dp),
        ) {
            Text(
                text = "Saved accounts",
                style = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.Bold),
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.padding(top = 4.dp, bottom = 18.dp),
            )

            Text(
                text = "Saved accounts",
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(bottom = 10.dp),
            )

            // List of saved accounts
            if (savedAccounts.isEmpty() && currentAccount != null) {
                SavedAccountRow(
                    name = currentAccount.name,
                    handle = currentAccount.email.takeIf { it.isNotBlank() },
                    thumbnailUrl = currentAccount.thumbnailUrl,
                    isActive = true,
                    onSelect = {},
                    onDelete = null,
                )
                Spacer(Modifier.height(14.dp))
            } else {
                savedAccounts.forEach { saved ->
                    val isActive = (currentCookie != null && saved.cookie == currentCookie) ||
                        (currentAccount != null && saved.name == currentAccount.name)
                    SavedAccountRow(
                        name = saved.name,
                        handle = saved.handle,
                        thumbnailUrl = saved.thumbnailUrl,
                        isActive = isActive,
                        onSelect = { onSelect(saved) },
                        onDelete = { onDelete(saved.id) },
                    )
                    Spacer(Modifier.height(14.dp))
                }
            }

            Spacer(Modifier.height(6.dp))

            // Button 1: Save current account
            Surface(
                shape = RoundedCornerShape(18.dp),
                color = MaterialTheme.colorScheme.surfaceVariant,
                modifier = Modifier
                    .clip(RoundedCornerShape(18.dp))
                    .clickable(onClick = onSaveCurrent),
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(horizontal = 18.dp, vertical = 13.dp),
                ) {
                    Icon(
                        imageVector = Icons.Rounded.BookmarkBorder,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(22.dp),
                    )
                    Spacer(Modifier.width(12.dp))
                    Text(
                        text = "Save current account",
                        style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.SemiBold),
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                }
            }

            Spacer(Modifier.height(12.dp))

            // Button 2: Add another account
            Surface(
                shape = RoundedCornerShape(18.dp),
                color = MaterialTheme.colorScheme.surfaceVariant,
                modifier = Modifier
                    .clip(RoundedCornerShape(18.dp))
                    .clickable(onClick = onAddAnother),
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(horizontal = 18.dp, vertical = 13.dp),
                ) {
                    Icon(
                        imageVector = Icons.Rounded.AddCircleOutline,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(22.dp),
                    )
                    Spacer(Modifier.width(12.dp))
                    Text(
                        text = "Add another account",
                        style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.SemiBold),
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                }
            }
        }
    }
}

@Composable
private fun SavedAccountRow(
    name: String,
    handle: String?,
    thumbnailUrl: String?,
    isActive: Boolean,
    onSelect: () -> Unit,
    onDelete: (() -> Unit)?,
) {
    Surface(
        shape = RoundedCornerShape(18.dp),
        color = MaterialTheme.colorScheme.surfaceVariant,
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .clickable(onClick = onSelect),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
        ) {
            // Profile icon or avatar
            if (thumbnailUrl != null) {
                AsyncImage(
                    model = thumbnailUrl,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .size(34.dp)
                        .clip(CircleShape),
                )
            } else {
                Box(
                    modifier = Modifier
                        .size(34.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.surfaceContainerHighest),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Person,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(20.dp),
                    )
                }
            }

            Spacer(Modifier.width(16.dp))

            Column(Modifier.weight(1f)) {
                Text(
                    text = name,
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                if (!handle.isNullOrBlank()) {
                    Text(
                        text = if (handle.startsWith("@")) handle else "@$handle",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }

            if (isActive) {
                Icon(
                    imageVector = Icons.Rounded.Check,
                    contentDescription = "Active account",
                    tint = Color(0xFFA5B4FC),
                    modifier = Modifier.size(22.dp),
                )
                Spacer(Modifier.width(16.dp))
            }

            if (onDelete != null) {
                Box(
                    modifier = Modifier
                        .size(32.dp)
                        .clip(CircleShape)
                        .clickable(onClick = onDelete),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = Icons.Rounded.DeleteOutline,
                        contentDescription = "Delete account",
                        tint = Color(0xFFFCA5A5),
                        modifier = Modifier.size(22.dp),
                    )
                }
            }
        }
    }
}
