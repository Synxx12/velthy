package com.velthy.client.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.PlaylistPlay
import androidx.compose.material.icons.automirrored.rounded.QueueMusic
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.DeleteForever
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.velthy.client.data.model.BrowseType
import com.velthy.client.data.model.ROW_ART_PX
import com.velthy.client.data.model.Song
import com.velthy.client.data.model.UserPlaylist
import com.velthy.client.data.model.artworkAt
import com.velthy.client.ui.icons.VelthyIcons
import java.util.Locale

data class BrowseTarget(
    val browseId: String?,
    val title: String,
    val subtitle: String,
    val thumbnailUrl: String? = null,
    val type: BrowseType = BrowseType.OTHER,
    val songs: List<Song> = emptyList(),
    val playlist: UserPlaylist? = null,
    val fromCard: Boolean = true,
    val downloadId: String? = null,
    val highlightDeleteDownload: Boolean = false,
)

@Composable
fun BrowseActionsSheet(
    target: BrowseTarget,
    onPlayNext: () -> Unit,
    onAddToQueue: () -> Unit,
    modifier: Modifier = Modifier,
    onPlay: (() -> Unit)? = null,
    onShuffle: (() -> Unit)? = null,
    onOpen: (() -> Unit)? = null,
    onDownloadAll: (() -> Unit)? = null,
    onRename: ((String) -> Unit)? = null,
    onDelete: (() -> Unit)? = null,
    onDeleteDownload: (() -> Unit)? = null,
) {
    var renaming by remember { mutableStateOf(false) }
    var confirmingDelete by remember { mutableStateOf(false) }
    var confirmingDeleteDownload by remember { mutableStateOf(target.highlightDeleteDownload) }

    val playlist = target.playlist
    if (renaming && playlist != null && onRename != null) {
        RenamePlaylistForm(
            playlist = playlist,
            onBack = { renaming = false },
            onRename = onRename,
            modifier = modifier,
        )
        return
    }

    Column(modifier.fillMaxWidth()) {
        BrowseSheetHeader(target)
        HorizontalDivider(thickness = 0.5.dp, color = MaterialTheme.colorScheme.outline)

        onPlay?.let { ActionRow(Icons.Rounded.PlayArrow, "Play", onClick = it) }
        onShuffle?.let { ActionRow(VelthyIcons.Shuffle, "Shuffle", onClick = it) }
        ActionRow(Icons.AutoMirrored.Rounded.PlaylistPlay, "Play next", onClick = onPlayNext)
        ActionRow(Icons.AutoMirrored.Rounded.QueueMusic, "Add to queue", onClick = onAddToQueue)
        onDownloadAll?.let { ActionRow(VelthyIcons.Download, "Download all", onClick = it) }
        onOpen?.let {
            ActionRow(VelthyIcons.ChevronRight, "Open ${target.type.noun}".trim(), onClick = it)
        }
        if (onRename != null) {
            ActionRow(Icons.Rounded.Edit, "Rename") { renaming = true }
        }
        if (onDelete != null) {
            if (confirmingDelete) {
                ActionRow(
                    icon = Icons.Rounded.DeleteForever,
                    label = "Delete \"${target.title}\" — tap to confirm",
                    tint = MaterialTheme.colorScheme.error,
                    onClick = onDelete,
                )
            } else {
                ActionRow(Icons.Rounded.Delete, "Delete playlist") { confirmingDelete = true }
            }
        }
        if (onDeleteDownload != null) {
            if (confirmingDeleteDownload) {
                ActionRow(
                    icon = Icons.Rounded.DeleteForever,
                    label = "Remove \"${target.title}\" from this device — tap to confirm",
                    tint = MaterialTheme.colorScheme.error,
                    onClick = onDeleteDownload,
                )
            } else {
                ActionRow(Icons.Rounded.Delete, "Delete download") { confirmingDeleteDownload = true }
            }
        }
        Spacer(Modifier.height(24.dp))
    }
}

@Composable
private fun BrowseSheetHeader(target: BrowseTarget) {
    val shape = if (target.type == BrowseType.ARTIST) CircleShape else RoundedCornerShape(8.dp)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        AsyncImage(
            model = target.thumbnailUrl.artworkAt(ROW_ART_PX),
            contentDescription = null,
            modifier = Modifier
                .size(52.dp)
                .clip(shape)
                .thumbnailBorder(shape)
                .background(MaterialTheme.colorScheme.surfaceVariant),
        )
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(
                text = target.title,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onBackground,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = target.subtitle.ifBlank {
                    target.type.noun.replaceFirstChar { it.uppercase(Locale.ROOT) }.ifBlank {
                        target.songs.size.takeIf { it > 0 }
                            ?.let { "$it ${if (it == 1) "song" else "songs"}" }
                            .orEmpty()
                    }
                },
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun RenamePlaylistForm(
    playlist: UserPlaylist,
    onBack: () -> Unit,
    onRename: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    var name by remember { mutableStateOf(playlist.title) }
    val focusRequester = remember { FocusRequester() }
    val focusManager = LocalFocusManager.current

    LaunchedEffect(Unit) { focusRequester.requestFocus() }

    val submit: () -> Unit = {
        if (name.isNotBlank()) {
            focusManager.clearFocus()
            onRename(name)
        }
    }

    Column(
        modifier
            .fillMaxWidth()
            .imePadding(),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 8.dp, end = 22.dp, bottom = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onBack) {
                Icon(
                    Icons.AutoMirrored.Rounded.ArrowBack,
                    contentDescription = "Back",
                    tint = MaterialTheme.colorScheme.onBackground,
                )
            }
            Text(
                text = "Rename playlist",
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onBackground,
                modifier = Modifier.weight(1f),
            )
        }
        HorizontalDivider(thickness = 0.5.dp, color = MaterialTheme.colorScheme.outline)

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 22.dp, vertical = 16.dp)
                .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(11.dp))
                .padding(horizontal = 12.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            BasicTextField(
                value = name,
                onValueChange = { name = it },
                singleLine = true,
                textStyle = MaterialTheme.typography.bodyLarge.copy(
                    color = MaterialTheme.colorScheme.onBackground,
                ),
                cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = { submit() }),
                modifier = Modifier
                    .weight(1f)
                    .focusRequester(focusRequester),
            )
        }
        Button(
            onClick = submit,
            enabled = name.isNotBlank() && name != playlist.title,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 22.dp),
        ) {
            Text("Save name")
        }
        Spacer(Modifier.height(28.dp))
    }
}

private val BrowseType.noun: String
    get() = when (this) {
        BrowseType.ALBUM -> "album"
        BrowseType.PLAYLIST -> "playlist"
        BrowseType.ARTIST -> "artist"
        BrowseType.OTHER -> ""
    }
