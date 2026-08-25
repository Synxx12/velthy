package com.velthy.client.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.velthy.client.R

/**
 * Clean, pure, minimalist OLED Splash Screen with the authentic Velthy logo.
 * Masks cold-start network / skeleton loads with a smooth gentle breathing animation.
 */
@Composable
fun LaunchSplashScreen(
    visible: Boolean,
    modifier: Modifier = Modifier,
) {
    AnimatedVisibility(
        visible = visible,
        enter = androidx.compose.animation.EnterTransition.None,
        exit = fadeOut(animationSpec = tween(durationMillis = 400, easing = FastOutSlowInEasing)) +
                scaleOut(targetScale = 1.05f, animationSpec = tween(durationMillis = 400, easing = FastOutSlowInEasing)),
        modifier = modifier,
    ) {
        val infiniteTransition = rememberInfiniteTransition(label = "splashBreathing")
        val scale by infiniteTransition.animateFloat(
            initialValue = 0.95f,
            targetValue = 1.03f,
            animationSpec = infiniteRepeatable(
                animation = tween(durationMillis = 1100, easing = FastOutSlowInEasing),
                repeatMode = RepeatMode.Reverse,
            ),
            label = "scalePulse",
        )

        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color(0xFF000000)),
            contentAlignment = Alignment.Center,
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                // Authentic Velthy Logo with smooth breathing scale
                Image(
                    painter = painterResource(R.drawable.ic_logo),
                    contentDescription = "Velthy",
                    colorFilter = ColorFilter.tint(Color.White),
                    modifier = Modifier
                        .size(84.dp)
                        .scale(scale),
                )

                Spacer(Modifier.height(24.dp))

                Text(
                    text = "Velthy",
                    color = Color.White,
                    fontSize = 26.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = (-0.5).sp,
                )

                Spacer(Modifier.height(4.dp))

                Text(
                    text = "Music & Audio",
                    color = Color(0xFF8E8E93),
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Medium,
                    letterSpacing = 0.2.sp,
                )
            }
        }
    }
}
