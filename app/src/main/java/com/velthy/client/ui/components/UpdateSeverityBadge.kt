package com.velthy.client.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material.icons.rounded.NewReleases
import androidx.compose.material.icons.rounded.WarningAmber
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.velthy.client.data.UpdateSeverity

/**
 * The coloured pill that says how urgent a release is.
 *
 * Colour carries the weight and the icon repeats it, because the label alone
 * ("Important", "Critical") is the kind of thing people read past. Critical is
 * the error colour, important the warning amber, routine a neutral surface —
 * the same ordering Android uses for battery and storage warnings, so the
 * signal needs no learning.
 */
@Composable
fun UpdateSeverityBadge(
    severity: UpdateSeverity,
    modifier: Modifier = Modifier,
) {
    // NORMAL has no badge: if every release wore a pill, none of them would read
    // as urgent, which is the whole point of the split. The caller decides what
    // to show for a plain release.
    if (severity == UpdateSeverity.NORMAL) return

    val (tint, container) = when (severity) {
        UpdateSeverity.CRITICAL -> Color(0xFFFF5252) to Color(0xFFFF5252).copy(alpha = 0.16f)
        UpdateSeverity.IMPORTANT -> Color(0xFFFFB300) to Color(0xFFFFB300).copy(alpha = 0.16f)
        UpdateSeverity.NORMAL -> Color.Unspecified to Color.Unspecified
    }
    val icon = when (severity) {
        UpdateSeverity.CRITICAL -> Icons.Rounded.ErrorOutline
        UpdateSeverity.IMPORTANT -> Icons.Rounded.WarningAmber
        UpdateSeverity.NORMAL -> Icons.Rounded.NewReleases
    }

    Box(
        modifier = modifier
            .clip(RoundedCornerShape(8.dp))
            .background(container)
            .padding(horizontal = 8.dp, vertical = 3.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = tint,
                modifier = Modifier.size(13.dp),
            )
            Spacer(Modifier.width(4.dp))
            Text(
                text = severity.label.uppercase(),
                style = MaterialTheme.typography.labelSmall.copy(
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 0.4.sp,
                ),
                color = tint,
            )
        }
    }
}
