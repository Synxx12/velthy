package com.velthy.client.ui.player

/** Smoothstep used by every reveal curve so nothing ever starts or stops hard. */
private fun smooth(x: Float): Float {
    val v = x.coerceIn(0f, 1f)
    return v * v * (3f - 2f * v)
}

/** The full player's own chrome: backdrop, credits, controls and artwork. */
fun playerReveal(progress: Float): Float = smooth(progress)

/** The mini player and tab bar, the exact complement of [playerReveal]. */
fun miniReveal(progress: Float): Float = 1f - playerReveal(progress)

/** A slight grow, so the player's fade has a little weight behind it. */
fun playerArtScale(progress: Float): Float = 0.96f + 0.04f * playerReveal(progress)

/** A slight shrink for the mini player's artwork as it leaves. */
fun miniArtScale(progress: Float): Float = 0.96f + 0.04f * miniReveal(progress)
