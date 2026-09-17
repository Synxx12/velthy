package com.velthy.client.ui.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.halilibo.richtext.markdown.Markdown
import com.halilibo.richtext.ui.CodeBlockStyle
import com.halilibo.richtext.ui.RichTextStyle
import com.halilibo.richtext.ui.material3.RichText
import com.halilibo.richtext.ui.string.RichTextStringStyle

/**
 * Renders GitHub Release notes as rich formatted Markdown.
 *
 * Supports headings, bullet lists, bold/italic, code blocks, blockquotes,
 * and clickable hyperlinks that open in the user's browser.
 *
 * Automatically sanitizes internal machine-readable markers (such as
 * `<!-- Velthy-Severity: ... -->`) so the displayed notes stay clean and
 * consumer-ready.
 */
@Composable
fun MarkdownReleaseNotes(
    content: String,
    modifier: Modifier = Modifier,
) {
    val uriHandler = LocalUriHandler.current
    val cleanContent = remember(content) { sanitizeReleaseNotes(content) }
    if (cleanContent.isBlank()) return

    val primaryColor = MaterialTheme.colorScheme.primary
    val surfaceColor = MaterialTheme.colorScheme.surfaceVariant

    val richTextStyle = remember(primaryColor, surfaceColor) {
        RichTextStyle(
            stringStyle = RichTextStringStyle(
                linkStyle = SpanStyle(
                    color = primaryColor,
                    fontWeight = FontWeight.SemiBold,
                ),
                codeStyle = SpanStyle(
                    fontFamily = FontFamily.Monospace,
                    fontSize = 12.sp,
                    background = surfaceColor.copy(alpha = 0.5f),
                ),
            ),
            codeBlockStyle = CodeBlockStyle(
                textStyle = TextStyle(
                    fontFamily = FontFamily.Monospace,
                    fontSize = 12.sp,
                ),
                modifier = Modifier.fillMaxWidth(),
            ),
            headingStyle = { level, textStyle ->
                when (level) {
                    0 -> textStyle.copy(fontSize = 18.sp, fontWeight = FontWeight.Bold)
                    1 -> textStyle.copy(fontSize = 16.sp, fontWeight = FontWeight.Bold)
                    2 -> textStyle.copy(fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                    else -> textStyle.copy(fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                }
            },
        )
    }

    Box(modifier = modifier) {
        RichText(
            style = richTextStyle,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Markdown(
                content = cleanContent,
                onLinkClicked = { url ->
                    runCatching { uriHandler.openUri(url) }
                },
            )
        }
    }
}

/**
 * Strips HTML comments and metadata headers from markdown release notes.
 */
internal fun sanitizeReleaseNotes(raw: String): String {
    if (raw.isBlank()) return ""
    return raw
        // Strip HTML comments like <!-- Velthy-Severity: ... -->
        .replace(Regex("""<!--[\s\S]*?-->"""), "")
        // Strip duplicate trailing newlines
        .trim()
}
