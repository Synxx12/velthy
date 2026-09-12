package com.velthy.client.ui.components

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.basicMarquee
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextOverflow

/** How long a line sits at its start before the next pass, once it is moving. */
private const val MARQUEE_REST_MS = 5_000

/**
 * A single line of text that crawls in place when it is too long for the space
 * it is given, and sits perfectly still when it is not.
 *
 * Built on [Modifier.basicMarquee] rather than a hand-rolled animation, and
 * that is the whole point of it. A marquee is a race between measuring the text
 * with no width bound at all and clipping the result to the width the layout
 * actually has, and the hand-rolled version this replaces lost that race in the
 * one way nothing can see: the row simply never moved, and the title sat there
 * cut off with no sign that anything had gone wrong. The framework's own
 * implementation measures its content unbounded, animates only when the content
 * genuinely does not fit, and is the one Compose runs its own tests against.
 *
 * With [enabled] false the line is a plain ellipsised one — no animation, for a
 * caller that would rather have the cut.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun MarqueeText(
    text: String,
    style: TextStyle,
    color: Color,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    Text(
        text = text,
        style = style,
        color = color,
        maxLines = 1,
        softWrap = false,
        // Clip rather than ellipsise. An ellipsis is decided from the bounded
        // width and cuts the string there, which would leave the marquee with
        // nothing left to move.
        overflow = TextOverflow.Clip,
        modifier = modifier.then(
            if (enabled) {
                Modifier.basicMarquee(
                    iterations = Int.MAX_VALUE,
                    repeatDelayMillis = MARQUEE_REST_MS,
                    // The first pass starts at once; only the repeats rest.
                    initialDelayMillis = 0,
                )
            } else {
                Modifier
            },
        ),
    )
}
