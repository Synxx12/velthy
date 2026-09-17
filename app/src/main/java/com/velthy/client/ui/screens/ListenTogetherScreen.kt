package com.velthy.client.ui.screens

import android.content.Intent
import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Login
import androidx.compose.material.icons.automirrored.rounded.Logout
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.Dns
import androidx.compose.material.icons.rounded.PersonAdd
import androidx.compose.material.icons.rounded.Podcasts
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.velthy.client.data.listentogether.JamInviteLink
import com.velthy.client.data.listentogether.ListenTogether
import com.velthy.client.data.listentogether.PartyMember
import com.velthy.client.ui.haptics.Haptic
import com.velthy.client.ui.haptics.rememberHaptics
import kotlinx.coroutines.launch

/**
 * Listen Together — one party shared by up to five signed-in devices.
 *
 * Laid out in Velthy's own pushed-screen shape (inset cards, hairline rules,
 * the same type scale as Settings) in the order that answers the questions a
 * person actually has: *can I reach the server*, then *start or join*, then
 * *who else is here*. Everything about a server address comes last, because
 * nobody setting up a party needs to think about one — there is a built-in
 * default — and it is only somebody running their own who comes looking.
 *
 * The player is deliberately untouched by anything here. What this screen (and
 * the layer under it) publishes is where the party is; binding that to the
 * transport is `PartySync`'s job, in the playback service.
 */
@Composable
fun ListenTogetherScreen(
    signedIn: Boolean,
    onSignIn: () -> Unit,
    contentPadding: PaddingValues,
    modifier: Modifier = Modifier,
) {
    val state by ListenTogether.state.collectAsStateWithLifecycle()
    val serverStatus by ListenTogether.serverStatus.collectAsStateWithLifecycle()
    val customServer by ListenTogether.customServerUrl.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()
    val haptics = rememberHaptics()
    val clipboard = LocalClipboardManager.current
    val context = LocalContext.current

    var codeInput by remember { mutableStateOf("") }
    var serverInput by remember(customServer) { mutableStateOf(customServer) }
    var busy by remember { mutableStateOf(false) }
    var failure by remember { mutableStateOf<String?>(null) }

    // A membership restored from storage is worth a socket while somebody is
    // looking at it — which is exactly now.
    LaunchedEffect(Unit) {
        ListenTogether.refreshServerHealth()
        ListenTogether.ensureConnected()
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(contentPadding),
    ) {
        Text(
            text = "Listen Together",
            style = MaterialTheme.typography.displayLarge,
            color = MaterialTheme.colorScheme.onBackground,
            modifier = Modifier.padding(start = 20.dp, end = 20.dp, top = 8.dp, bottom = 14.dp),
        )

        if (!signedIn) {
            SettingsGroup(
                footer = "Listening together uses your name and picture so everyone in the " +
                    "party can see who is here.",
            ) {
                SettingsRow(
                    icon = Icons.AutoMirrored.Rounded.Login,
                    title = "Sign in",
                    subtitle = "A party needs a name to put on it",
                    onClick = onSignIn,
                )
            }
        }

        ServerHealthRow(status = serverStatus, onRecheck = ListenTogether::refreshServerHealth)

        if (!state.inParty) {
            NotInAParty(
                signedIn = signedIn,
                hasServer = ListenTogether.hasServer,
                codeInput = codeInput,
                onCodeInput = { typed ->
                    // Filtered here rather than at the field: the code is a
                    // sequence of letters and digits in the alphabet the server
                    // mints from, and anything else typed is simply not part of
                    // it — never an error the user has to clear.
                    codeInput = typed.filter(Char::isLetterOrDigit)
                        .uppercase()
                        .take(ListenTogether.CODE_LENGTH)
                },
                busy = busy,
                onCreate = {
                    busy = true
                    failure = null
                    haptics.play(Haptic.ToggleOn)
                    scope.launch {
                        failure = ListenTogether.createParty().exceptionOrNull()?.message
                        busy = false
                    }
                },
                onJoin = {
                    busy = true
                    failure = null
                    haptics.play(Haptic.Tap)
                    scope.launch {
                        failure = ListenTogether.joinParty(codeInput).exceptionOrNull()?.message
                        if (failure == null) codeInput = ""
                        busy = false
                    }
                },
            )
        } else {
            InAParty(
                state = state,
                onCopy = {
                    haptics.play(Haptic.Tap)
                    clipboard.setText(AnnotatedString(state.code.orEmpty()))
                },
                onShare = {
                    val code = state.code ?: return@InAParty
                    val link = JamInviteLink.url(code)
                    haptics.play(Haptic.Tap)
                    context.startActivity(
                        Intent.createChooser(
                            Intent(Intent.ACTION_SEND).apply {
                                type = "text/plain"
                                putExtra(Intent.EXTRA_TEXT, "$link\n\nJoin my party with code $code")
                            },
                            null,
                        ),
                    )
                },
                onLeave = {
                    haptics.play(Haptic.Tap)
                    scope.launch { ListenTogether.leaveParty() }
                },
            )
        }

        (failure ?: state.error)?.let { message ->
            Text(
                text = message,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.error,
                modifier = Modifier.padding(start = GROUP_INSET + 4.dp, end = GROUP_INSET + 4.dp, top = 12.dp),
            )
        }

        // Last, and empty by default. Nothing here forces anybody to read an
        // address; the build already carries one.
        SettingsGroup(
            header = "Party server",
            footer = if (ListenTogether.hasServer) {
                "This build already points at a server. Anything typed here overrides it " +
                    "for this device and is kept."
            } else {
                "No server is configured in this build — type the address of one you run."
            },
        ) {
            Column(Modifier.padding(horizontal = ROW_INSET, vertical = 14.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Rounded.Dns,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onBackground,
                        modifier = Modifier.size(ICON_SIZE),
                    )
                    Spacer(Modifier.width(ICON_GAP))
                    PillField(
                        value = serverInput,
                        onValueChange = { serverInput = it },
                        placeholder = "Use the built-in server",
                        enabled = !state.inParty,
                        imeAction = ImeAction.Done,
                        onDone = { ListenTogether.setCustomServerUrl(serverInput) },
                    )
                }
            }
            RowDivider()
            ActionRow(
                icon = Icons.Rounded.Refresh,
                title = "Use this address",
                enabled = !state.inParty,
                emphasised = true,
                onClick = {
                    haptics.play(Haptic.Tap)
                    ListenTogether.setCustomServerUrl(serverInput)
                    ListenTogether.refreshServerHealth()
                },
            )
        }

        Spacer(Modifier.height(32.dp))
    }
}

/** Whether the server is up, first, because every other failure looks like this one. */
@Composable
private fun ServerHealthRow(
    status: ListenTogether.ServerStatus,
    onRecheck: () -> Unit,
) {
    val (label, colour) = when (status.health) {
        ListenTogether.Health.ONLINE ->
            "Online · ${status.latencyMs} ms" to MaterialTheme.colorScheme.primary
        ListenTogether.Health.CHECKING ->
            "Checking…" to MaterialTheme.colorScheme.onSurfaceVariant
        ListenTogether.Health.OFFLINE ->
            "Can't reach the party server" to MaterialTheme.colorScheme.error
        ListenTogether.Health.UNKNOWN ->
            "Not checked yet" to MaterialTheme.colorScheme.onSurfaceVariant
    }
    SettingsGroup {
        SettingsRow(
            icon = Icons.Rounded.Podcasts,
            title = "Party server",
            subtitleContent = {
                Text(
                    text = label,
                    style = MaterialTheme.typography.bodyMedium,
                    color = colour,
                )
            },
            trailing = {
                Icon(
                    imageVector = Icons.Rounded.Refresh,
                    contentDescription = "Check server",
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier
                        .size(20.dp)
                        .clickable(onClick = onRecheck),
                )
            },
            onClick = onRecheck,
        )
    }
}

@Composable
private fun NotInAParty(
    signedIn: Boolean,
    hasServer: Boolean,
    codeInput: String,
    onCodeInput: (String) -> Unit,
    busy: Boolean,
    onCreate: () -> Unit,
    onJoin: () -> Unit,
) {
    val ready = signedIn && hasServer && !busy

    SettingsGroup(
        header = "Join a party",
        footer = "A code is six letters or digits, read out by whoever started the party. " +
            "Up to five devices can listen at once.",
    ) {
        Column(Modifier.padding(horizontal = ROW_INSET, vertical = 16.dp)) {
            PartyCodeField(
                code = codeInput,
                onCodeChange = onCodeInput,
                enabled = ready,
                onSubmit = { if (codeInput.length == ListenTogether.CODE_LENGTH) onJoin() },
            )
        }
        RowDivider()
        ActionRow(
            icon = Icons.Rounded.Podcasts,
            title = if (busy) "Joining…" else "Join with code",
            enabled = ready && codeInput.length == ListenTogether.CODE_LENGTH,
            emphasised = true,
            onClick = onJoin,
        )
        RowDivider()
        ActionRow(
            icon = Icons.Rounded.PersonAdd,
            title = if (busy) "Starting…" else "Start a new party",
            enabled = ready,
            onClick = onCreate,
        )
    }
}

@Composable
private fun InAParty(
    state: ListenTogether.State,
    onCopy: () -> Unit,
    onShare: () -> Unit,
    onLeave: () -> Unit,
) {
    val connection = when (state.connection) {
        ListenTogether.Connection.LIVE -> "Connected"
        ListenTogether.Connection.CONNECTING -> "Connecting…"
        ListenTogether.Connection.OFFLINE -> "Offline"
    }

    SettingsGroup(
        header = "Your party",
        footer = if (state.clockSynced) {
            "In sync — round trip ${state.roundTripMs} ms."
        } else {
            "Waiting for the first round trip before the playhead is trusted."
        },
    ) {
        SettingsRow(
            icon = Icons.Rounded.Podcasts,
            title = state.code.orEmpty(),
            subtitle = connection,
            trailing = {
                Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                    Icon(
                        imageVector = Icons.Rounded.Share,
                        contentDescription = "Share invite",
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier
                            .size(20.dp)
                            .clickable(onClick = onShare),
                    )
                    Icon(
                        imageVector = Icons.Rounded.ContentCopy,
                        contentDescription = "Copy party code",
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier
                            .size(20.dp)
                            .clickable(onClick = onCopy),
                    )
                }
            },
            onClick = onCopy,
        )
        RowDivider()
        ActionRow(
            icon = Icons.AutoMirrored.Rounded.Logout,
            title = "Leave party",
            destructive = true,
            onClick = onLeave,
        )
    }

    MembersGroup(state)
}

/** The member list, host first as the server orders it. */
@Composable
private fun MembersGroup(state: ListenTogether.State) {
    SettingsGroup(header = "${state.members.size} of ${state.maxMembers} here") {
        state.members.forEachIndexed { index, member ->
            if (index > 0) RowDivider()
            MemberRow(member = member, isYou = member.memberId == state.you?.memberId)
        }
    }
}

@Composable
private fun MemberRow(member: PartyMember, isYou: Boolean) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 52.dp)
            .padding(horizontal = ROW_INSET, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // A monogram rather than the remote avatar: the picture is a URL
        // belonging to somebody else's account, and a row that has to fetch one
        // before it can be drawn flickers on every rejoin. The initial is what
        // the list is actually read by.
        Box(
            modifier = Modifier
                .size(ICON_SIZE)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.18f)),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = member.displayName.take(1).uppercase().ifBlank { "?" },
                style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                color = MaterialTheme.colorScheme.primary,
                maxLines = 1,
            )
        }
        Spacer(Modifier.width(ICON_GAP))
        Column(Modifier.weight(1f)) {
            Text(
                text = member.displayName.ifBlank { "Listener" },
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onBackground,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            val detail = buildList {
                if (member.isHost) add("Host")
                if (isYou) add("You")
                if (!member.connected) add("Reconnecting…")
            }.joinToString(" · ")
            if (detail.isNotEmpty()) {
                Text(
                    text = detail,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

/**
 * The six cells of a party code, over one real text field.
 *
 * The field is *visible and tappable* — not the invisible overlay a
 * `matchParentSize` field would be. That overlay version worked on most phones
 * and failed on some: with nothing drawn over the cells it relied on the IME
 * accepting focus on a transparent view, and where it did not, the code could
 * not be typed at all. An input that only sometimes accepts a code is worse than
 * a plain box, so the field here is honest — it *is* the control, laid across
 * the cells with its own text invisible only because the cells are drawing it.
 *
 * Six cells rather than one box because a code that gets read out loud and typed
 * in by somebody else is a sequence of characters, not a word: the cells show
 * how many are wanted and how far the reading has got without a hint line.
 */
@Composable
private fun PartyCodeField(
    code: String,
    onCodeChange: (String) -> Unit,
    enabled: Boolean,
    onSubmit: () -> Unit,
) {
    var focused by remember { mutableStateOf(false) }

    Box(Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            repeat(ListenTogether.CODE_LENGTH) { index ->
                CodeCell(
                    char = code.getOrNull(index),
                    active = focused && index == code.length.coerceAtMost(ListenTogether.CODE_LENGTH - 1),
                    enabled = enabled,
                    modifier = Modifier.weight(1f),
                )
            }
        }
        // One field for all six, laid over them. Tapping anywhere on the row
        // focuses it; the caret is kept at the end so the next character always
        // lands in the lit cell rather than wherever the tap happened to fall.
        BasicTextField(
            value = code,
            onValueChange = onCodeChange,
            enabled = enabled,
            singleLine = true,
            // Invisible text and caret: the cells above are what draws the
            // characters. The *field* is still real, which is the part that
            // makes typing work everywhere.
            textStyle = TextStyle(color = androidx.compose.ui.graphics.Color.Transparent, fontSize = 1.sp),
            cursorBrush = SolidColor(androidx.compose.ui.graphics.Color.Transparent),
            keyboardOptions = KeyboardOptions(
                capitalization = KeyboardCapitalization.Characters,
                imeAction = ImeAction.Done,
            ),
            keyboardActions = KeyboardActions(
                onDone = { if (code.length == ListenTogether.CODE_LENGTH) onSubmit() },
            ),
            modifier = Modifier
                .matchParentSize()
                .onFocusChanged { focused = it.isFocused },
        )
    }
}

/** One character's worth of [PartyCodeField]. */
@Composable
private fun CodeCell(
    char: Char?,
    active: Boolean,
    enabled: Boolean,
    modifier: Modifier = Modifier,
) {
    // Animated because the ring moves cell to cell as the code is typed, and a
    // border that simply appeared one box to the right on each keystroke would
    // read as flicker rather than as travel.
    val ring by animateColorAsState(
        targetValue = when {
            !enabled -> MaterialTheme.colorScheme.outline.copy(alpha = 0.35f)
            active -> MaterialTheme.colorScheme.primary
            else -> MaterialTheme.colorScheme.outline
        },
        label = "codeCellRing",
    )
    Box(
        modifier = modifier
            .heightIn(min = 52.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(MaterialTheme.colorScheme.surface)
            .border(1.5.dp, ring, RoundedCornerShape(12.dp)),
        contentAlignment = Alignment.Center,
    ) {
        if (char != null) {
            Text(
                text = char.toString(),
                style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.SemiBold),
                color = MaterialTheme.colorScheme.onBackground,
                textAlign = TextAlign.Center,
            )
        }
    }
}

/**
 * A pill-shaped text field, matching the one the search bar uses.
 *
 * Not a Material `OutlinedTextField`: that brings its own container, its own
 * label animation and its own idea of a corner radius, and lands as the one
 * stock widget on a screen where nothing else is stock.
 */
@Composable
private fun PillField(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    imeAction: ImeAction = ImeAction.Done,
    onDone: () -> Unit = {},
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(40.dp)
            .clip(RoundedCornerShape(11.dp))
            .background(MaterialTheme.colorScheme.background)
            .padding(horizontal = 12.dp),
        contentAlignment = Alignment.CenterStart,
    ) {
        if (value.isEmpty()) {
            Text(
                text = placeholder,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        BasicTextField(
            value = value,
            onValueChange = onValueChange,
            enabled = enabled,
            singleLine = true,
            textStyle = MaterialTheme.typography.bodyMedium.copy(
                color = MaterialTheme.colorScheme.onBackground,
            ),
            cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
            keyboardOptions = KeyboardOptions(imeAction = imeAction),
            keyboardActions = KeyboardActions(onDone = { onDone() }),
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

/**
 * A full-width action inside a group card, wearing the row metrics so it lines
 * up with the rows around it rather than reading as a foreign widget.
 */
@Composable
private fun ActionRow(
    title: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector?,
    enabled: Boolean = true,
    emphasised: Boolean = false,
    destructive: Boolean = false,
    onClick: () -> Unit,
) {
    val tint = when {
        !enabled -> MaterialTheme.colorScheme.onSurfaceVariant
        destructive -> MaterialTheme.colorScheme.error
        emphasised -> MaterialTheme.colorScheme.primary
        else -> MaterialTheme.colorScheme.onBackground
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(enabled = enabled, onClick = onClick)
            .heightIn(min = 52.dp)
            .padding(horizontal = ROW_INSET, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center,
    ) {
        if (icon != null) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = tint,
                modifier = Modifier.size(ICON_SIZE),
            )
            Spacer(Modifier.width(ICON_GAP))
        }
        Text(
            text = title,
            style = MaterialTheme.typography.bodyLarge,
            color = tint,
            textAlign = TextAlign.Center,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}
