package com.velthy.client.ui.onboarding

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ArrowBack
import androidx.compose.material.icons.rounded.ArrowForward
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.CloudSync
import androidx.compose.material.icons.rounded.DarkMode
import androidx.compose.material.icons.rounded.GraphicEq
import androidx.compose.material.icons.rounded.LibraryMusic
import androidx.compose.material.icons.rounded.LightMode
import androidx.compose.material.icons.rounded.Mic
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.Palette
import androidx.compose.material.icons.rounded.PhoneAndroid
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.compose.AsyncImage
import com.velthy.client.R
import com.velthy.client.data.model.Account
import com.velthy.client.data.settings.AppSettings
import com.velthy.client.data.settings.ThemeMode
import kotlin.random.Random

private const val TOTAL_STEPS = 6

/** Dynamic palette that morphs in real-time when the user switches themes */
internal data class OnboardingPalette(
    val isDark: Boolean,
    val background: Color,
    val cardBackground: Color,
    val cardBorder: Color,
    val textPrimary: Color,
    val textSecondary: Color,
    val textTertiary: Color,
    val segmentBackground: Color,
    val subtleBadgeBg: Color,
    val pillSecondaryBg: Color,
    val pillSecondaryText: Color,
    val blobColor: Color,
    val iconTint: Color,
)

/**
 * Production-Grade Apple Music / Cider-Style First-Launch Onboarding Wizard.
 * Guides new users seamlessly through initial setup with live real-time theme morphing.
 */
@Composable
fun OnboardingScreen(
    account: Account?,
    onSignInWithGoogle: () -> Unit,
    onComplete: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var currentStep by remember { mutableIntStateOf(0) }

    // Intercept hardware / gesture back button during onboarding
    BackHandler(enabled = currentStep > 0) {
        currentStep = (currentStep - 1).coerceAtLeast(0)
    }

    // Reactive Theme Resolution
    val themeMode by AppSettings.themeMode.collectAsStateWithLifecycle()
    val isSystemDark = isSystemInDarkTheme()
    val isDark = when (themeMode) {
        ThemeMode.SYSTEM -> isSystemDark
        ThemeMode.LIGHT -> false
        ThemeMode.DARK -> true
    }

    // Smooth animated color transitions so theme changes morph cleanly in real-time
    val animBg by animateColorAsState(
        targetValue = if (isDark) Color(0xFF0A0A0E) else Color(0xFFF2F2F7),
        animationSpec = tween(320),
        label = "animBg",
    )
    val animCardBg by animateColorAsState(
        targetValue = if (isDark) Color(0xFF16161A) else Color(0xFFFFFFFF),
        animationSpec = tween(320),
        label = "animCardBg",
    )
    val animCardBorder by animateColorAsState(
        targetValue = if (isDark) Color.White.copy(alpha = 0.08f) else Color.Black.copy(alpha = 0.07f),
        animationSpec = tween(320),
        label = "animCardBorder",
    )
    val animTextPrimary by animateColorAsState(
        targetValue = if (isDark) Color.White else Color(0xFF1C1C1E),
        animationSpec = tween(320),
        label = "animTextPrimary",
    )
    val animTextSecondary by animateColorAsState(
        targetValue = if (isDark) Color(0xFF8E8E93) else Color(0xFF636366),
        animationSpec = tween(320),
        label = "animTextSecondary",
    )
    val animTextTertiary by animateColorAsState(
        targetValue = if (isDark) Color(0xFF48484A) else Color(0xFF8E8E93),
        animationSpec = tween(320),
        label = "animTextTertiary",
    )
    val animSegmentBg by animateColorAsState(
        targetValue = if (isDark) Color(0xFF222228) else Color(0xFFE5E5EA),
        animationSpec = tween(320),
        label = "animSegmentBg",
    )
    val animSubtleBadgeBg by animateColorAsState(
        targetValue = if (isDark) Color(0xFF24242A) else Color(0xFFE5E5EA),
        animationSpec = tween(320),
        label = "animSubtleBadgeBg",
    )
    val animPillSecBg by animateColorAsState(
        targetValue = if (isDark) Color(0xFF2C2C32) else Color(0xFFE5E5EA),
        animationSpec = tween(320),
        label = "animPillSecBg",
    )
    val animPillSecText by animateColorAsState(
        targetValue = if (isDark) Color.White else Color(0xFF1C1C1E),
        animationSpec = tween(320),
        label = "animPillSecText",
    )
    val animBlobColor by animateColorAsState(
        targetValue = if (isDark) Color(0xFF1E1E24).copy(alpha = 0.35f) else Color(0xFFD8D8DE).copy(alpha = 0.5f),
        animationSpec = tween(320),
        label = "animBlobColor",
    )
    val animIconTint by animateColorAsState(
        targetValue = if (isDark) Color.White else Color(0xFF1C1C1E),
        animationSpec = tween(320),
        label = "animIconTint",
    )

    val colors = OnboardingPalette(
        isDark = isDark,
        background = animBg,
        cardBackground = animCardBg,
        cardBorder = animCardBorder,
        textPrimary = animTextPrimary,
        textSecondary = animTextSecondary,
        textTertiary = animTextTertiary,
        segmentBackground = animSegmentBg,
        subtleBadgeBg = animSubtleBadgeBg,
        pillSecondaryBg = animPillSecBg,
        pillSecondaryText = animPillSecText,
        blobColor = animBlobColor,
        iconTint = animIconTint,
    )

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(colors.background),
    ) {
        // ─── Ambient Organic Blobs (Apple Music Aesthetic) ───
        OrganicAmbientBackdrop(blobColor = colors.blobColor)

        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding(),
        ) {
            // ─── Top Header: Back Button & Step Indicators ───
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp)
                    .padding(horizontal = 16.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                if (currentStep > 0) {
                    IconButton(
                        onClick = { currentStep = (currentStep - 1).coerceAtLeast(0) },
                        modifier = Modifier.size(40.dp),
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.ArrowBack,
                            contentDescription = "Back",
                            tint = colors.iconTint,
                            modifier = Modifier.size(22.dp),
                        )
                    }
                } else {
                    Spacer(Modifier.size(40.dp))
                }

                // Dot Progress Indicators
                StepDotsIndicator(
                    totalSteps = TOTAL_STEPS,
                    currentStep = currentStep,
                    activeColor = colors.textPrimary,
                    inactiveColor = colors.textTertiary.copy(alpha = 0.4f),
                )

                Spacer(Modifier.size(40.dp))
            }

            // ─── Animated Step Content (Scrollable Container) ───
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
            ) {
                AnimatedContent(
                    targetState = currentStep,
                    transitionSpec = {
                        if (targetState > initialState) {
                            (slideInHorizontally { width -> width } + fadeIn()).togetherWith(
                                slideOutHorizontally { width -> -width } + fadeOut()
                            )
                        } else {
                            (slideInHorizontally { width -> -width } + fadeIn()).togetherWith(
                                slideOutHorizontally { width -> width } + fadeOut()
                            )
                        }
                    },
                    label = "onboardingStepTransition",
                ) { step ->
                    when (step) {
                        0 -> WelcomeStep(colors = colors)
                        1 -> SyncIntegrationsStep(colors = colors)
                        2 -> ConnectMusicStep(
                            colors = colors,
                            account = account,
                            onSignIn = onSignInWithGoogle,
                        )
                        3 -> UserReadyStep(
                            colors = colors,
                            account = account,
                        )
                        4 -> PersonalizeStep(colors = colors)
                        5 -> AllSetStep(
                            colors = colors,
                            account = account,
                        )
                    }
                }
            }

            // ─── Stable & Fixed Bottom Action Bar (Anchored at Bottom of Screen) ───
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp, vertical = 16.dp),
            ) {
                when (currentStep) {
                    0 -> OnboardingPillButton(
                        text = "Get Started",
                        showArrow = true,
                        colors = colors,
                        onClick = { currentStep = 1 },
                    )
                    1 -> OnboardingPillButton(
                        text = "Continue",
                        showArrow = true,
                        colors = colors,
                        onClick = { currentStep = 2 },
                    )
                    2 -> OnboardingPillButton(
                        text = if (account != null) "Continue" else "Continue as Guest",
                        showArrow = true,
                        colors = colors,
                        onClick = { currentStep = if (account != null) 3 else 4 },
                    )
                    3 -> OnboardingPillButton(
                        text = "Continue",
                        showArrow = true,
                        colors = colors,
                        onClick = { currentStep = 4 },
                    )
                    4 -> OnboardingPillButton(
                        text = "Continue",
                        showArrow = true,
                        colors = colors,
                        onClick = { currentStep = 5 },
                    )
                    5 -> OnboardingPillButton(
                        text = "Start Listening",
                        showArrow = false,
                        isAccent = true,
                        colors = colors,
                        onClick = onComplete,
                    )
                }
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// 🌟 1. WELCOME STEP ("Hola / Welcome to Velthy")
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun WelcomeStep(
    colors: OnboardingPalette,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        // Authentic Velthy Logo
        Image(
            painter = painterResource(R.drawable.ic_logo),
            contentDescription = "Velthy",
            colorFilter = ColorFilter.tint(colors.textPrimary),
            modifier = Modifier.size(88.dp),
        )

        Spacer(Modifier.height(36.dp))

        Text(
            text = "Hola",
            color = colors.textPrimary,
            fontSize = 36.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = (-0.8).sp,
        )

        Spacer(Modifier.height(12.dp))

        Text(
            text = "Welcome to Velthy.\nLet's get everything set up for you.",
            color = colors.textSecondary,
            fontSize = 15.sp,
            fontWeight = FontWeight.Medium,
            textAlign = TextAlign.Center,
            lineHeight = 22.sp,
        )
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// 🛡️ 2. SYNC & INTEGRATIONS STEP ("Set up Sync & Privileges")
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun SyncIntegrationsStep(
    colors: OnboardingPalette,
) {
    val scrollState = rememberScrollState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 24.dp)
            .verticalScroll(scrollState),
        horizontalAlignment = Alignment.Start,
    ) {
        Spacer(Modifier.height(12.dp))

        Text(
            text = "Set up Sync & Integrations",
            color = colors.textPrimary,
            fontSize = 26.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = (-0.5).sp,
        )

        Spacer(Modifier.height(6.dp))

        Text(
            text = "Connect live telemetry, automatic updates, and cloud privileges.",
            color = colors.textSecondary,
            fontSize = 14.sp,
            lineHeight = 20.sp,
        )

        Spacer(Modifier.height(24.dp))

        // Central Mini Illustration Badge
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 12.dp),
            contentAlignment = Alignment.Center,
        ) {
            Box(
                modifier = Modifier
                    .size(76.dp)
                    .clip(CircleShape)
                    .background(colors.subtleBadgeBg),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = Icons.Rounded.CloudSync,
                    contentDescription = null,
                    tint = colors.textPrimary,
                    modifier = Modifier.size(38.dp),
                )
            }
        }

        // Alpha Tester / Active Status Card
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(16.dp))
                .background(colors.cardBackground)
                .border(1.dp, colors.cardBorder, RoundedCornerShape(16.dp))
                .padding(horizontal = 16.dp, vertical = 14.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(32.dp)
                            .clip(CircleShape)
                            .background(colors.subtleBadgeBg),
                        contentAlignment = Alignment.Center,
                    ) {
                        Image(
                            painter = painterResource(R.drawable.ic_logo),
                            contentDescription = "Velthy",
                            colorFilter = ColorFilter.tint(colors.textPrimary),
                            modifier = Modifier.size(16.dp),
                        )
                    }
                    Spacer(Modifier.width(12.dp))
                    Text(
                        text = "Alpha Tester Client",
                        color = colors.textPrimary,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.SemiBold,
                    )
                }

                Text(
                    text = "Active",
                    color = Color(0xFF30D158),
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .background(Color(0xFF30D158).copy(alpha = 0.15f))
                        .padding(horizontal = 10.dp, vertical = 4.dp),
                )
            }
        }

        Spacer(Modifier.height(24.dp))

        Text(
            text = "Built-in Capabilities",
            color = colors.textSecondary,
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = 0.5.sp,
        )

        Spacer(Modifier.height(14.dp))

        FeatureRow(
            icon = Icons.Rounded.CloudSync,
            title = "Continuous In-App Updates",
            subtitle = "Automatic background checks for release APKs and instant patch updates.",
            colors = colors,
        )

        Spacer(Modifier.height(16.dp))

        FeatureRow(
            icon = Icons.Rounded.GraphicEq,
            title = "Live Sync & Telemetry",
            subtitle = "Broadcast real-time now-playing telemetry to your official live ticker on velthy.my.id/stats.",
            colors = colors,
        )

        Spacer(Modifier.height(16.dp))

        FeatureRow(
            icon = Icons.Rounded.AutoAwesome,
            title = "Discord Rich Presence",
            subtitle = "Showcase your currently playing track and album artwork to friends in Discord.",
            colors = colors,
        )

        Spacer(Modifier.height(24.dp))
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// 🎵 3. CONNECT MUSIC SOURCE STEP ("Connect Music Account")
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun ConnectMusicStep(
    colors: OnboardingPalette,
    account: Account?,
    onSignIn: () -> Unit,
) {
    val scrollState = rememberScrollState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 24.dp)
            .verticalScroll(scrollState),
        horizontalAlignment = Alignment.Start,
    ) {
        Spacer(Modifier.height(12.dp))

        Text(
            text = "Connect Music Account",
            color = colors.textPrimary,
            fontSize = 26.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = (-0.5).sp,
        )

        Spacer(Modifier.height(6.dp))

        Text(
            text = "Link your Google account or continue as Guest to explore music.",
            color = colors.textSecondary,
            fontSize = 14.sp,
            lineHeight = 20.sp,
        )

        Spacer(Modifier.height(24.dp))

        if (account != null) {
            // Already Signed In Card
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp))
                    .background(colors.cardBackground)
                    .border(1.dp, colors.cardBorder, RoundedCornerShape(16.dp))
                    .padding(16.dp),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        if (!account.thumbnailUrl.isNullOrBlank()) {
                            AsyncImage(
                                model = account.thumbnailUrl,
                                contentDescription = account.name,
                                contentScale = ContentScale.Crop,
                                modifier = Modifier
                                    .size(44.dp)
                                    .clip(CircleShape),
                            )
                        } else {
                            Box(
                                modifier = Modifier
                                    .size(44.dp)
                                    .clip(CircleShape)
                                    .background(colors.subtleBadgeBg),
                                contentAlignment = Alignment.Center,
                            ) {
                                Text(
                                    text = account.name.take(1).uppercase(),
                                    color = colors.textPrimary,
                                    fontSize = 18.sp,
                                    fontWeight = FontWeight.Bold,
                                )
                            }
                        }
                        Spacer(Modifier.width(14.dp))
                        Column {
                            Text(
                                text = account.name.ifBlank { "Google User" },
                                color = colors.textPrimary,
                                fontSize = 15.sp,
                                fontWeight = FontWeight.Bold,
                            )
                            Text(
                                text = account.email.ifBlank { "YouTube Music Connected" },
                                color = colors.textSecondary,
                                fontSize = 12.sp,
                            )
                        }
                    }

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .background(Color(0xFF30D158).copy(alpha = 0.15f))
                            .padding(horizontal = 8.dp, vertical = 4.dp),
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Check,
                            contentDescription = null,
                            tint = Color(0xFF30D158),
                            modifier = Modifier.size(14.dp),
                        )
                        Spacer(Modifier.width(4.dp))
                        Text(
                            text = "Connected",
                            color = Color(0xFF30D158),
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                        )
                    }
                }
            }
        } else {
            // Not Signed In -> Google Sign-in Option
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(16.dp))
                        .background(colors.cardBackground)
                        .border(1.dp, colors.cardBorder, RoundedCornerShape(16.dp))
                        .clickable { onSignIn() }
                        .padding(16.dp),
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                modifier = Modifier
                                    .size(36.dp)
                                    .clip(CircleShape)
                                    .background(colors.subtleBadgeBg),
                                contentAlignment = Alignment.Center,
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.LibraryMusic,
                                    contentDescription = null,
                                    tint = colors.textPrimary,
                                    modifier = Modifier.size(20.dp),
                                )
                            }
                            Spacer(Modifier.width(12.dp))
                            Column {
                                Text(
                                    text = "Sign in with Google",
                                    color = colors.textPrimary,
                                    fontSize = 15.sp,
                                    fontWeight = FontWeight.Bold,
                                )
                                Text(
                                    text = "Sync your playlists and personal library",
                                    color = colors.textSecondary,
                                    fontSize = 12.sp,
                                )
                            }
                        }

                        Icon(
                            imageVector = Icons.Rounded.ArrowForward,
                            contentDescription = null,
                            tint = colors.textSecondary,
                            modifier = Modifier.size(18.dp),
                        )
                    }
                }
            }
        }

        Spacer(Modifier.height(24.dp))

        Text(
            text = "Features & Experience",
            color = colors.textSecondary,
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = 0.5.sp,
        )

        Spacer(Modifier.height(14.dp))

        FeatureRow(
            icon = Icons.Rounded.LibraryMusic,
            title = "Personal Music Library",
            subtitle = "Access all your saved albums, curated playlists, and favorite artists seamlessly.",
            colors = colors,
        )

        Spacer(Modifier.height(16.dp))

        FeatureRow(
            icon = Icons.Rounded.Mic,
            title = "Live Synced Karaoke Lyrics",
            subtitle = "Syllable-accurate vocal timing with smooth line animations and translation support.",
            colors = colors,
        )

        Spacer(Modifier.height(16.dp))

        FeatureRow(
            icon = Icons.Rounded.AutoAwesome,
            title = "Smart Discovery & Mixes",
            subtitle = "Explore personalized mixes, global top charts, and newly released music with Smart DJ Automix.",
            colors = colors,
        )

        Spacer(Modifier.height(24.dp))
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// 👤 4. USER READY PROFILE STEP ("Hi, [Username]!")
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun UserReadyStep(
    colors: OnboardingPalette,
    account: Account?,
) {
    val displayName = account?.name?.ifBlank { "Music Lover" } ?: "Music Lover"

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(
            text = "Hi, $displayName!",
            color = colors.textPrimary,
            fontSize = 32.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = (-0.6).sp,
            textAlign = TextAlign.Center,
        )

        Spacer(Modifier.height(36.dp))

        // Central Orbital Profile Showcase
        Box(
            modifier = Modifier.size(190.dp),
            contentAlignment = Alignment.Center,
        ) {
            // Background Outer Subtle Glow Ring
            Box(
                modifier = Modifier
                    .size(160.dp)
                    .clip(CircleShape)
                    .background(colors.subtleBadgeBg),
            )

            // User Avatar
            if (account?.thumbnailUrl != null) {
                AsyncImage(
                    model = account.thumbnailUrl,
                    contentDescription = displayName,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .size(96.dp)
                        .clip(CircleShape)
                        .border(2.dp, colors.cardBorder, CircleShape),
                )
            } else {
                Box(
                    modifier = Modifier
                        .size(96.dp)
                        .clip(CircleShape)
                        .background(colors.cardBackground)
                        .border(2.dp, colors.cardBorder, CircleShape),
                    contentAlignment = Alignment.Center,
                ) {
                    Image(
                        painter = painterResource(R.drawable.ic_logo),
                        contentDescription = "Velthy",
                        colorFilter = ColorFilter.tint(colors.textPrimary),
                        modifier = Modifier.size(48.dp),
                    )
                }
            }

            // 4 Floating Orbital Badges
            MiniOrbitalBadge(
                icon = Icons.Rounded.Settings,
                colors = colors,
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .offset(x = 12.dp, y = 12.dp),
            )
            MiniOrbitalBadge(
                icon = Icons.Rounded.MusicNote,
                colors = colors,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .offset(x = (-12).dp, y = 12.dp),
            )
            MiniOrbitalBadge(
                icon = Icons.Rounded.GraphicEq,
                colors = colors,
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .offset(x = 12.dp, y = (-12).dp),
            )
            MiniOrbitalBadge(
                icon = Icons.Rounded.AutoAwesome,
                colors = colors,
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .offset(x = (-12).dp, y = (-12).dp),
            )
        }

        Spacer(Modifier.height(36.dp))

        Text(
            text = "Your library and account are ready to roll.\nLet's finish personalizing your setup.",
            color = colors.textSecondary,
            fontSize = 15.sp,
            fontWeight = FontWeight.Medium,
            textAlign = TextAlign.Center,
            lineHeight = 22.sp,
        )
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// 🎨 5. PERSONALIZE STEP ("Appearance & Playback")
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun PersonalizeStep(
    colors: OnboardingPalette,
) {
    val scrollState = rememberScrollState()
    val currentTheme by AppSettings.themeMode.collectAsStateWithLifecycle()
    val smartFade by AppSettings.smartFadeEnabled.collectAsStateWithLifecycle()
    val skipSilence by AppSettings.skipSilence.collectAsStateWithLifecycle()
    val reduceBlur by AppSettings.reduceDynamicBlur.collectAsStateWithLifecycle()
    val stopOnTaskRemoved by AppSettings.stopOnTaskRemoved.collectAsStateWithLifecycle()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 24.dp)
            .verticalScroll(scrollState),
        horizontalAlignment = Alignment.Start,
    ) {
        Spacer(Modifier.height(12.dp))

        Text(
            text = "Personalize Velthy",
            color = colors.textPrimary,
            fontSize = 26.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = (-0.5).sp,
        )

        Spacer(Modifier.height(6.dp))

        Text(
            text = "Fine-tune appearance and playback settings in real-time.",
            color = colors.textSecondary,
            fontSize = 14.sp,
            lineHeight = 20.sp,
        )

        Spacer(Modifier.height(20.dp))

        // Group 1: Appearance Theme
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(20.dp))
                .background(colors.cardBackground)
                .border(1.dp, colors.cardBorder, RoundedCornerShape(20.dp))
                .padding(16.dp),
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                Text(
                    text = "Appearance Theme",
                    color = colors.textPrimary,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                )

                // 3-Segment Theme Selector
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(colors.segmentBackground)
                        .padding(4.dp),
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    ThemeSegmentItem(
                        title = "Device",
                        icon = Icons.Rounded.PhoneAndroid,
                        selected = currentTheme == ThemeMode.SYSTEM,
                        colors = colors,
                        onClick = { AppSettings.setThemeMode(ThemeMode.SYSTEM) },
                        modifier = Modifier.weight(1f),
                    )
                    ThemeSegmentItem(
                        title = "Light",
                        icon = Icons.Rounded.LightMode,
                        selected = currentTheme == ThemeMode.LIGHT,
                        colors = colors,
                        onClick = { AppSettings.setThemeMode(ThemeMode.LIGHT) },
                        modifier = Modifier.weight(1f),
                    )
                    ThemeSegmentItem(
                        title = "Dark",
                        icon = Icons.Rounded.DarkMode,
                        selected = currentTheme == ThemeMode.DARK,
                        colors = colors,
                        onClick = { AppSettings.setThemeMode(ThemeMode.DARK) },
                        modifier = Modifier.weight(1f),
                    )
                }
            }
        }

        Spacer(Modifier.height(16.dp))

        // Group 2: Audio & Transitions
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(20.dp))
                .background(colors.cardBackground)
                .border(1.dp, colors.cardBorder, RoundedCornerShape(20.dp))
                .padding(16.dp),
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                Text(
                    text = "Audio Engine & Playback",
                    color = colors.textPrimary,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                )

                // Smart Fade / DJ Automix Switch
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(
                            text = "AutoMix DJ & Smart Fade",
                            color = colors.textPrimary,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.SemiBold,
                        )
                        Text(
                            text = "Smooth 7s beatmatched crossfade between songs",
                            color = colors.textSecondary,
                            fontSize = 12.sp,
                        )
                    }
                    Switch(
                        checked = smartFade,
                        onCheckedChange = { AppSettings.setSmartFadeEnabled(it) },
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = Color.White,
                            checkedTrackColor = Color(0xFFFA2D48),
                            uncheckedThumbColor = if (colors.isDark) Color(0xFF8E8E93) else Color.White,
                            uncheckedTrackColor = if (colors.isDark) Color(0xFF2C2C32) else Color(0xFFD1D1D6),
                        ),
                    )
                }

                HorizontalDivider(
                    thickness = 0.5.dp,
                    color = colors.cardBorder,
                )

                // Skip Silence Switch
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(
                            text = "Skip Silence",
                            color = colors.textPrimary,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.SemiBold,
                        )
                        Text(
                            text = "Instantly skip dead silence at starts & ends",
                            color = colors.textSecondary,
                            fontSize = 12.sp,
                        )
                    }
                    Switch(
                        checked = skipSilence,
                        onCheckedChange = { AppSettings.setSkipSilence(it) },
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = Color.White,
                            checkedTrackColor = Color(0xFFFA2D48),
                            uncheckedThumbColor = if (colors.isDark) Color(0xFF8E8E93) else Color.White,
                            uncheckedTrackColor = if (colors.isDark) Color(0xFF2C2C32) else Color(0xFFD1D1D6),
                        ),
                    )
                }
            }
        }

        Spacer(Modifier.height(16.dp))

        // Group 3: Performance & Battery Options
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(20.dp))
                .background(colors.cardBackground)
                .border(1.dp, colors.cardBorder, RoundedCornerShape(20.dp))
                .padding(16.dp),
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                Text(
                    text = "Performance & System",
                    color = colors.textPrimary,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                )

                // Battery Saver / Reduce Blur
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(
                            text = "Energy Efficiency Mode",
                            color = colors.textPrimary,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.SemiBold,
                        )
                        Text(
                            text = "Reduce dynamic mesh blur for maximum battery life",
                            color = colors.textSecondary,
                            fontSize = 12.sp,
                        )
                    }
                    Switch(
                        checked = reduceBlur,
                        onCheckedChange = { AppSettings.setReduceDynamicBlur(it) },
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = Color.White,
                            checkedTrackColor = Color(0xFFFA2D48),
                            uncheckedThumbColor = if (colors.isDark) Color(0xFF8E8E93) else Color.White,
                            uncheckedTrackColor = if (colors.isDark) Color(0xFF2C2C32) else Color(0xFFD1D1D6),
                        ),
                    )
                }

                HorizontalDivider(
                    thickness = 0.5.dp,
                    color = colors.cardBorder,
                )

                // Background Playback Lifecycle
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(
                            text = "Stop on Task Removed",
                            color = colors.textPrimary,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.SemiBold,
                        )
                        Text(
                            text = "Stop audio immediately when swiping away app",
                            color = colors.textSecondary,
                            fontSize = 12.sp,
                        )
                    }
                    Switch(
                        checked = stopOnTaskRemoved,
                        onCheckedChange = { AppSettings.setStopOnTaskRemoved(it) },
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = Color.White,
                            checkedTrackColor = Color(0xFFFA2D48),
                            uncheckedThumbColor = if (colors.isDark) Color(0xFF8E8E93) else Color.White,
                            uncheckedTrackColor = if (colors.isDark) Color(0xFF2C2C32) else Color(0xFFD1D1D6),
                        ),
                    )
                }
            }
        }

        Spacer(Modifier.height(24.dp))
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// 🎉 6. ALL SET STEP ("You're all set! & Confetti")
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun AllSetStep(
    colors: OnboardingPalette,
    account: Account?,
) {
    val scrollState = rememberScrollState()
    val themeMode by AppSettings.themeMode.collectAsStateWithLifecycle()
    val smartFade by AppSettings.smartFadeEnabled.collectAsStateWithLifecycle()
    val accountLabel = account?.name ?: "Guest Session"

    val appearanceLabel = when (themeMode) {
        ThemeMode.LIGHT -> "Apple Music Light Mode"
        ThemeMode.DARK -> "Apple Music Dark Glass"
        ThemeMode.SYSTEM -> "Device Adaptive Theme"
    }

    val audioLabel = if (smartFade) "AutoMix DJ & High-Res" else "Standard High-Res"

    Box(
        modifier = Modifier.fillMaxSize(),
    ) {
        // Confetti Particle Explosion
        ConfettiBurstEffect()

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 24.dp)
                .verticalScroll(scrollState),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Spacer(Modifier.height(16.dp))

            // Authentic Velthy Logo
            Image(
                painter = painterResource(R.drawable.ic_logo),
                contentDescription = "Velthy",
                colorFilter = ColorFilter.tint(colors.textPrimary),
                modifier = Modifier.size(72.dp),
            )

            Spacer(Modifier.height(20.dp))

            Text(
                text = "You're all set!",
                color = colors.textPrimary,
                fontSize = 32.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = (-0.6).sp,
            )

            Spacer(Modifier.height(6.dp))

            Text(
                text = "Everything is configured and ready to go.",
                color = colors.textSecondary,
                fontSize = 14.sp,
                fontWeight = FontWeight.Medium,
            )

            Spacer(Modifier.height(24.dp))

            // Summary Checklist Card
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(20.dp))
                    .background(colors.cardBackground)
                    .border(1.dp, colors.cardBorder, RoundedCornerShape(20.dp))
                    .padding(18.dp),
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                    Text(
                        text = "Ready to Play",
                        color = colors.textSecondary,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 0.5.sp,
                    )

                    SummaryCheckItem(
                        icon = Icons.Rounded.CloudSync,
                        title = "Sync & Integrations",
                        subtitle = "Telemetry & In-App Updates",
                        colors = colors,
                    )

                    SummaryCheckItem(
                        icon = Icons.Rounded.MusicNote,
                        title = "Music Source",
                        subtitle = accountLabel,
                        colors = colors,
                    )

                    SummaryCheckItem(
                        icon = Icons.Rounded.Palette,
                        title = "Appearance",
                        subtitle = appearanceLabel,
                        colors = colors,
                    )

                    SummaryCheckItem(
                        icon = Icons.Rounded.Tune,
                        title = "Audio Engine",
                        subtitle = audioLabel,
                        colors = colors,
                    )
                }
            }

            Spacer(Modifier.height(24.dp))
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// 🎨 REUSABLE COMPONENTS & UTILITIES
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun OrganicAmbientBackdrop(blobColor: Color) {
    Box(modifier = Modifier.fillMaxSize()) {
        // Top-left organic shape
        Box(
            modifier = Modifier
                .size(260.dp)
                .offset(x = (-80).dp, y = (-60).dp)
                .clip(RoundedCornerShape(percent = 45))
                .background(blobColor),
        )

        // Bottom-right organic shape
        Box(
            modifier = Modifier
                .size(300.dp)
                .align(Alignment.BottomEnd)
                .offset(x = 90.dp, y = 80.dp)
                .clip(RoundedCornerShape(percent = 45))
                .background(blobColor),
        )
    }
}

@Composable
private fun StepDotsIndicator(
    totalSteps: Int,
    currentStep: Int,
    activeColor: Color,
    inactiveColor: Color,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        repeat(totalSteps) { index ->
            val isSelected = index == currentStep
            val width by animateFloatAsState(
                targetValue = if (isSelected) 24f else 6f,
                animationSpec = spring(dampingRatio = 0.7f, stiffness = 400f),
                label = "dotWidth",
            )
            val color = if (isSelected) activeColor else inactiveColor

            Box(
                modifier = Modifier
                    .size(width = width.dp, height = 6.dp)
                    .clip(CircleShape)
                    .background(color),
            )
        }
    }
}

@Composable
private fun FeatureRow(
    icon: ImageVector,
    title: String,
    subtitle: String,
    colors: OnboardingPalette,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.Top,
    ) {
        Box(
            modifier = Modifier
                .size(36.dp)
                .clip(CircleShape)
                .background(colors.subtleBadgeBg),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = colors.iconTint,
                modifier = Modifier.size(18.dp),
            )
        }

        Spacer(Modifier.width(14.dp))

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                color = colors.textPrimary,
                fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold,
            )
            Spacer(Modifier.height(2.dp))
            Text(
                text = subtitle,
                color = colors.textSecondary,
                fontSize = 12.sp,
                lineHeight = 18.sp,
            )
        }
    }
}

@Composable
private fun MiniOrbitalBadge(
    icon: ImageVector,
    colors: OnboardingPalette,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .size(34.dp)
            .clip(CircleShape)
            .background(colors.cardBackground)
            .border(1.dp, colors.cardBorder, CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = colors.iconTint,
            modifier = Modifier.size(16.dp),
        )
    }
}

@Composable
private fun ThemeSegmentItem(
    title: String,
    icon: ImageVector,
    selected: Boolean,
    colors: OnboardingPalette,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val itemBg = if (selected) Color(0xFFFA2D48) else Color.Transparent
    val itemFg = if (selected) Color.White else colors.textSecondary

    Box(
        modifier = modifier
            .clip(RoundedCornerShape(8.dp))
            .background(itemBg)
            .clickable(onClick = onClick)
            .padding(vertical = 8.dp),
        contentAlignment = Alignment.Center,
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center,
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = itemFg,
                modifier = Modifier.size(16.dp),
            )
            Spacer(Modifier.width(6.dp))
            Text(
                text = title,
                color = itemFg,
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold,
            )
        }
    }
}

@Composable
private fun SummaryCheckItem(
    icon: ImageVector,
    title: String,
    subtitle: String,
    colors: OnboardingPalette,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.weight(1f),
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = colors.textSecondary,
                modifier = Modifier.size(20.dp),
            )
            Spacer(Modifier.width(12.dp))
            Column {
                Text(
                    text = title,
                    color = colors.textPrimary,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    text = subtitle,
                    color = colors.textSecondary,
                    fontSize = 12.sp,
                )
            }
        }

        Box(
            modifier = Modifier
                .size(22.dp)
                .clip(CircleShape)
                .background(Color(0xFF30D158)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = Icons.Rounded.Check,
                contentDescription = null,
                tint = Color.Black,
                modifier = Modifier.size(14.dp),
            )
        }
    }
}

@Composable
private fun OnboardingPillButton(
    text: String,
    colors: OnboardingPalette,
    showArrow: Boolean = false,
    isAccent: Boolean = false,
    onClick: () -> Unit,
) {
    val bgColor = if (isAccent) Color(0xFFFA2D48) else colors.pillSecondaryBg
    val textColor = if (isAccent) Color.White else colors.pillSecondaryText

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(54.dp)
            .clip(RoundedCornerShape(27.dp))
            .background(bgColor)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center,
        ) {
            Text(
                text = text,
                color = textColor,
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold,
            )
            if (showArrow) {
                Spacer(Modifier.width(8.dp))
                Icon(
                    imageVector = Icons.Rounded.ArrowForward,
                    contentDescription = null,
                    tint = textColor,
                    modifier = Modifier.size(18.dp),
                )
            }
        }
    }
}

/** Lightweight decorative confetti particle burst simulation */
@Composable
private fun ConfettiBurstEffect() {
    val particles = remember {
        List(40) {
            ConfettiParticle(
                x = Random.nextFloat(),
                y = Random.nextFloat(),
                radius = Random.nextFloat() * 4f + 2f,
                color = listOf(
                    Color(0xFFFA2D48),
                    Color(0xFF30D158),
                    Color(0xFF0A84FF),
                    Color(0xFFFFD60A),
                    Color(0xFFBF5AF2),
                ).random(),
            )
        }
    }

    Canvas(modifier = Modifier.fillMaxSize()) {
        particles.forEach { p ->
            drawCircle(
                color = p.color.copy(alpha = 0.75f),
                radius = p.radius,
                center = Offset(p.x * size.width, p.y * size.height * 0.7f),
            )
        }
    }
}

private data class ConfettiParticle(
    val x: Float,
    val y: Float,
    val radius: Float,
    val color: Color,
)
