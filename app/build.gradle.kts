import java.util.Properties

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
    id("org.jetbrains.kotlin.plugin.serialization")
}

/**
 * Signing details, kept out of the repository in `keystore.properties`
 * (see keystore.properties.example). Absent on a fresh checkout, in which case
 * the release build still runs and simply comes out unsigned rather than
 * failing — only whoever holds the key can produce a shippable APK.
 */
val localProps = Properties().apply {
    val file = rootProject.file("local.properties")
    if (file.exists()) file.inputStream().use { load(it) }
}
val moduleIndexUrl: String = localProps.getProperty(
    "MODULE_INDEX_URL",
    "https://monochrome.rickyaddons.dpdns.org/8spine-source.json",
)

val signing = Properties().apply {
    val file = rootProject.file("keystore.properties")
    if (file.exists()) file.inputStream().use { load(it) }
}

android {
    namespace = "com.music.musique"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.musique.client"
        // 26 keeps reach wide; real-time blur (RenderEffect) kicks in on API 31+,
        // Haze falls back to a translucent scrim below that.
        minSdk = 26
        targetSdk = 36
        versionCode = 12
        versionName = "1.3.7"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        buildConfigField("String", "MODULE_INDEX_URL", "\"${moduleIndexUrl}\"")

        ndk {
            abiFilters.add("arm64-v8a")
        }
    }

    // applicationId can only be overridden per flavor, not per build type, so a
    // dev/prod dimension exists purely to let both sit installed side by side
    // on the same device instead of the dev build overwriting the prod one.
    flavorDimensions += "env"
    productFlavors {
        create("dev") {
            dimension = "env"
            applicationId = "com.musique.client.dev"
            resValue("string", "app_name", "Musique Native Dev")
        }
        create("prod") {
            dimension = "env"
            applicationId = "com.musique.client"
            resValue("string", "app_name", "Musique Native")
        }
    }

    signingConfigs {
        val fallbackKeystore = file("musique-release.jks")
        if (signing.isNotEmpty()) {
            create("release") {
                val path = signing.getProperty("storeFile")
                val resolved = if (file(path).exists()) file(path) else rootProject.file(path)
                storeFile = resolved
                storePassword = signing.getProperty("storePassword")
                keyAlias = signing.getProperty("keyAlias")
                keyPassword = signing.getProperty("keyPassword")
            }
        } else if (fallbackKeystore.exists()) {
            create("release") {
                storeFile = fallbackKeystore
                storePassword = "musiqueappsecretkey"
                keyAlias = "musique"
                keyPassword = "musiqueappsecretkey"
            }
        } else {
            create("release") {
                val debugConfig = signingConfigs.getByName("debug")
                storeFile = debugConfig.storeFile
                storePassword = debugConfig.storePassword
                keyAlias = debugConfig.keyAlias
                keyPassword = debugConfig.keyPassword
            }
        }
    }

    buildTypes {
        release {
            /*
             * Off deliberately. Stream resolution runs YouTube's own player
             * JavaScript through Rhino, and NewPipe, Ktor and
             * kotlinx.serialization all reach for classes reflectively — none
             * of which R8 can see. Shrinking that reliably is a set of keep
             * rules to be written and then proven on a device, because the
             * breakage it causes appears at runtime rather than at build time.
             * Until then, a larger APK that works beats a smaller one that
             * might not. The rules below stay wired up for when it's revisited.
             */
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            signingConfig = signingConfigs.getByName("release")
        }
    }
    compileOptions {
        isCoreLibraryDesugaringEnabled = true
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    buildFeatures {
        compose = true
        buildConfig = true
    }
}

kotlin {
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
    }
}

dependencies {
    coreLibraryDesugaring("com.android.tools:desugar_jdk_libs:2.1.4")

    // ---- Compose (Material 3) ----
    val composeBom = platform("androidx.compose:compose-bom:2024.12.01")
    implementation(composeBom)
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")
    implementation("androidx.activity:activity-compose:1.9.3")
    implementation("androidx.navigation:navigation-compose:2.8.5")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.7")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.7")
    implementation("androidx.core:core-ktx:1.15.0")
    implementation("androidx.browser:browser:1.8.0")
    debugImplementation("androidx.compose.ui:ui-tooling")

    // ---- Media playback: Media3 / ExoPlayer ----
    implementation("androidx.media3:media3-exoplayer:1.5.1")
    implementation("androidx.media3:media3-session:1.5.1")
    implementation("androidx.media3:media3-common:1.5.1")
    implementation("androidx.media3:media3-datasource-okhttp:1.5.1")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-guava:1.9.0")

    // ---- Images: Coil 3 + Palette (dominant colors for the mesh gradient) ----
    implementation("io.coil-kt.coil3:coil-compose:3.0.4")
    implementation("io.coil-kt.coil3:coil-network-okhttp:3.0.4")
    implementation("androidx.palette:palette-ktx:1.0.0")

    // ---- Frosted glass / progressive blur (Telegram-style bars) ----
    implementation("dev.chrisbanes.haze:haze:1.3.1")
    implementation("dev.chrisbanes.haze:haze-materials:1.3.1")

    // ---- Innertube (YouTube Music) client: Ktor + kotlinx.serialization ----
    implementation("io.ktor:ktor-client-core:3.0.3")
    implementation("io.ktor:ktor-client-okhttp:3.0.3")
    implementation("io.ktor:ktor-client-content-negotiation:3.0.3")
    implementation("io.ktor:ktor-serialization-kotlinx-json:3.0.3")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.7.3")

    // ---- Stream resolution: NewPipe solves YouTube's signature + `n` throttling ----
    // Pinned to v0.26.3, not the newer v0.26.4: v0.26.4's player-JS parser fails with
    // "Could not parse deobfuscation function" on the current player build, which blocks
    // WEB_REMIX's ciphered formats entirely. v0.26.3 solves the same signatures cleanly
    // against the same player JS — confirmed side by side against PixelMusic-ref, which
    // pins v0.26.3 and doesn't hit the parse failure.
    implementation("com.github.TeamNewPipe:NewPipeExtractor:v0.26.3")

    // ---- Auth/session storage ----
    implementation("androidx.security:security-crypto:1.1.0-alpha06")

    // Audio is progressive, but Apple serves its motion artwork as HLS — this
    // is what lets the animated sleeve play it. See CanvasArtworkPlayer.
    implementation("androidx.media3:media3-exoplayer-hls:1.5.1")

    // ---- JS module execution: QuickJS VM for Convx-style source plugins ----
    implementation("io.github.dokar3:quickjs-kt-android:1.0.5")

    // ---- Smart Fade: on-device beat/downbeat model (Beat This!, MIT-licensed) ----
    implementation("com.microsoft.onnxruntime:onnxruntime-android:1.28.0")

    testImplementation("junit:junit:4.13.2")
    androidTestImplementation("androidx.test.ext:junit:1.3.0")
    androidTestImplementation("androidx.test.espresso:espresso-core:3.7.0")
}
