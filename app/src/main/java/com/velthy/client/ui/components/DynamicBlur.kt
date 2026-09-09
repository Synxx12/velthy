package com.velthy.client.ui.components

import android.os.Build
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.velthy.client.data.settings.AppSettings

/**
 * Whether real dynamic blur can actually be drawn on this device, right now.
 *
 * Every frosted surface in the app picks between a Haze / RenderEffect glass
 * layer and a flat, opaque material background. On a device where the blur has
 * nothing to draw with, the "glass" silently collapses to a barely-tinted
 * translucent film over whatever is behind it — which reads as broken UI on
 * any busy screen. Those devices must fill the surface solid instead.
 *
 * Blur needs API 31's `RenderEffect` to run on; the user's Reduce dynamic blur
 * switch turns it off on top of that. Both checks live here so the frosted
 * components agree about it rather than each guessing for itself.
 */
@Composable
fun rememberCanBlur(): Boolean {
    val reduceDynamicBlur by AppSettings.reduceDynamicBlur.collectAsStateWithLifecycle()
    return !reduceDynamicBlur &&
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.S
}
