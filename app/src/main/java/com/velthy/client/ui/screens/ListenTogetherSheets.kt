package com.velthy.client.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.velthy.client.data.listentogether.PartyPreview
import com.velthy.client.data.listentogether.PartyPreviewMember
import com.velthy.client.ui.components.QrCode

/**
 * The party an invite points at, before a device slot is committed to it.
 *
 * The lookup behind this is unauthenticated and says only what a person holding
 * the code would learn by joining — a name, a face, how full it is — so this
 * works from a code read out loud as readily as from a tapped link. What it
 * buys is the one thing a join cannot: a chance to see that the code was typed
 * wrong, or that the party is full, *before* the listener has been moved into
 * it.
 */
@Composable
fun JoinConfirmDialog(
    preview: PartyPreview,
    busy: Boolean,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = { if (!busy) onDismiss() },
        title = {
            Text(
                text = if (preview.isFull) "This party is full" else "Join this party?",
                style = MaterialTheme.typography.titleLarge,
            )
        },
        text = {
            Column {
                if (preview.hostName.isNotBlank()) {
                    Text(
                        text = "${preview.hostName} is hosting",
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    Spacer(Modifier.height(6.dp))
                }
                Text(
                    text = "${preview.memberCount} of ${preview.maxMembers} listening · " +
                        preview.code,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                if (preview.members.isNotEmpty()) {
                    Spacer(Modifier.height(14.dp))
                    MemberAvatarStack(preview.members)
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = onConfirm,
                enabled = !busy && !preview.isFull,
            ) {
                Text(if (busy) "Joining…" else "Join")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, enabled = !busy) {
                Text("Cancel")
            }
        },
    )
}

/**
 * The faces already in the party, overlapping.
 *
 * A monogram rather than the remote avatar, for the same reason the member list
 * uses one: a URL belonging to somebody else's account is a fetch that may not
 * have landed, and a row of grey circles is worse than a row of initials. The
 * host is drawn first, which is also where the server sorts them.
 */
@Composable
private fun MemberAvatarStack(
    members: List<PartyPreviewMember>,
    max: Int = 5,
) {
    val shown = members.take(max)
    Row(horizontalArrangement = Arrangement.spacedBy((-10).dp)) {
        shown.forEach { member ->
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .clip(CircleShape)
                    .background(
                        if (member.isHost) {
                            MaterialTheme.colorScheme.primary.copy(alpha = 0.85f)
                        } else {
                            MaterialTheme.colorScheme.surfaceVariant
                        },
                    )
                    .padding(2.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.background),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = member.displayName.take(1).uppercase().ifBlank { "?" },
                    style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                    color = if (member.isHost) {
                        MaterialTheme.colorScheme.onPrimary
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
                    maxLines = 1,
                )
            }
        }
        if (members.size > shown.size) {
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.surfaceVariant),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = "+${members.size - shown.size}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

/**
 * The invite itself: the code, and a square somebody else can point a camera at.
 *
 * The QR is drawn rather than shown as an image because there is nothing to
 * show — the link is built here, from the code and whichever server this party
 * is on. A link that named the built-in server would leak it into every chat
 * it was pasted in, so that one is left out and the ordinary web link carries
 * the code instead.
 */
@Composable
fun InviteQrCard(
    code: String,
    link: String,
    onCopy: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            modifier = Modifier
                .clip(RoundedCornerShape(20.dp))
                .background(Color.White)
                .padding(10.dp),
        ) {
            QrCode(
                content = link,
                size = 196.dp,
                foreground = Color.Black,
                background = Color.White,
                quietZone = 4.dp,
            )
        }
        Spacer(Modifier.height(14.dp))
        Row(
            modifier = Modifier
                .clip(RoundedCornerShape(10.dp))
                .clickable(onClick = onCopy)
                .padding(horizontal = 12.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = code,
                style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.SemiBold),
                color = MaterialTheme.colorScheme.onBackground,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.width(10.dp))
            Icon(
                imageVector = Icons.Rounded.ContentCopy,
                contentDescription = "Copy code",
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(18.dp),
            )
        }
        Text(
            text = "Point a camera here to join",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/**
 * The invite as a dialog, for showing a code to a room.
 *
 * A dialog rather than a sheet because it has one job and one way out, and
 * because what it draws has to be *facing* somebody else — a QR read off the
 * back of a phone is the ordinary way this gets used, and a sheet that could be
 * dragged half-down would be a scannable square that is only sometimes scannable.
 */
@Composable
fun InviteQrDialog(
    code: String,
    link: String,
    onCopy: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = "Invite to the party",
                style = MaterialTheme.typography.titleLarge,
            )
        },
        text = {
            InviteQrCard(code = code, link = link, onCopy = onCopy)
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("Done")
            }
        },
    )
}
