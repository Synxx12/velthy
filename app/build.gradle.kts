import java.util.Properties

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
    id("org.jetbrains.kotlin.plugin.serialization")
}

/**
 * Signing details, kept out of the repository in `keystore.properties`
 * (see keystore.properties.example). Absent on a fresh checkout, the release
 * build is refused by [releaseSigningGuard] rather than quietly signed with the
 * debug key — only whoever holds the key can produce a shippable APK.
 */
val localProps = Properties().apply {
    val file = rootProject.file("local.properties")
    if (file.exists()) file.inputStream().use { load(it) }
}
val moduleIndexUrl: String = System.getenv("MODULE_INDEX_URL")
    ?: localProps.getProperty("MODULE_INDEX_URL")
    ?: "https://monochrome.rickyaddons.dpdns.org/8spine-source.json"
val lastfmApiKey: String = (
    localProps.getProperty("LASTFM_API_KEY")
        ?: System.getenv("LASTFM_API_KEY")
        ?: ""
    ).trim()
val lastfmSecret: String = (
    localProps.getProperty("LASTFM_SECRET")
        ?: System.getenv("LASTFM_SECRET")
        ?: ""
    ).trim()

val signing = Properties().apply {
    val file = rootProject.file("keystore.properties")
    if (file.exists()) file.inputStream().use { load(it) }
}
val hasReleaseSigning: Boolean = signing.isNotEmpty()

// Release identity comes from the release tag when CI drives the build
// (-Pvelthy.versionName / -Pvelthy.versionCode), and from the checked-in values
// otherwise. Keeping the tag and the APK's versionName in lockstep stops a
// release from shipping under a name that does not match its tag.
val appVersionName: String =
    providers.gradleProperty("velthy.versionName").orNull ?: "1.4.6.5"
val appVersionCode: Int =
    providers.gradleProperty("velthy.versionCode").orNull?.toInt() ?: 26

android {
    namespace = "com.velthy.client"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.velthy.client"
        // 26 keeps reach wide; real-time blur (RenderEffect) kicks in on API 31+,
        // Haze falls back to a translucent scrim below that.
        minSdk = 26
        targetSdk = 36
        versionCode = appVersionCode
        versionName = appVersionName

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        buildConfigField("String", "MODULE_INDEX_URL", "\"${moduleIndexUrl}\"")
        buildConfigField("String", "LASTFM_API_KEY", "\"${lastfmApiKey.replace("\\", "\\\\").replace("\"", "\\\"")}\"")
        buildConfigField("String", "LASTFM_SECRET", "\"${lastfmSecret.replace("\\", "\\\\").replace("\"", "\\\"")}\"")
    }

    splits {
        abi {
            isEnable = true
            reset()
            include("arm64-v8a", "armeabi-v7a", "x86_64")
            isUniversalApk = true
        }
    }

    // applicationId can only be overridden per flavor, not per build type, so a
    // dev/prod dimension exists purely to let both sit installed side by side
    // on the same device instead of the dev build overwriting the prod one.
    flavorDimensions += "env"
    productFlavors {
        create("dev") {
            dimension = "env"
            applicationId = "com.velthy.client.dev"
            resValue("string", "app_name", "Velthy Dev")
        }
        create("prod") {
            dimension = "env"
            applicationId = "com.velthy.client"
            resValue("string", "app_name", "Velthy")
        }
    }

    signingConfigs {
        // Only ever the real release key. A debug-key fallback here used to let a
        // "release" APK come out that Android would refuse to install over an
        // existing one — the failure surfaced on the user's phone, not in the
        // build. [releaseSigningGuard] now stops that build before it starts.
        if (hasReleaseSigning) {
            create("release") {
                val path = signing.getProperty("storeFile")
                val resolved = if (file(path).exists()) file(path) else rootProject.file(path)
                storeFile = resolved
                storePassword = signing.getProperty("storePassword")
                keyAlias = signing.getProperty("keyAlias")
                keyPassword = signing.getProperty("keyPassword")
            }
        }
    }

    buildTypes {
        release {
            /*
             * R8 is on. The classes that reach for themselves by name — NewPipe's
             * extractors, Rhino's script-engine factory, Ktor's serializers,
             * kotlinx.serialization's generated companions, the QuickJS and ONNX
             * JNI entry points — carry keep rules in proguard-rules.pro rather
             * than being left out of the shrinker's reach entirely. Resource
             * shrinking is on too; there are no getIdentifier() lookups, so no
             * res/raw/keep.xml is needed.
             *
             * If a release ever crashes at runtime with a NoClassDefFoundError or
             * a missing-serializer error, that is a keep rule to add, not a
             * reason to switch minification back off.
             */
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            if (hasReleaseSigning) {
                signingConfig = signingConfigs.getByName("release")
            }
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
    lint {
        checkReleaseBuilds = false
        abortOnError = false
    }
}

kotlin {
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
    }
}

/*
 * A release without the real key is not a release: it would either be unsigned
 * or signed with the debug key, and either way Android rejects it over an
 * installed copy. Fail the build up front with an explanation instead of
 * producing an APK that only fails on the device.
 *
 * Wired only into the tasks that actually package the release APK/AAB —
 * matching `package*` broadly would also catch `packageProdReleaseResources`
 * and make even a plain compile require the key.
 */
val releaseSigningGuard by tasks.registering {
    doFirst {
        if (!hasReleaseSigning) {
            throw GradleException(
                "Release signing is not configured. Create keystore.properties " +
                    "(see keystore.properties.example) before building a release. " +
                    "Refusing to produce an APK signed with the debug key."
            )
        }
    }
}

listOf(
    "assembleProdRelease",
    "assembleProdReleaseUniversal",
    "bundleProdRelease",
    "packageProdRelease",
    "packageProdReleaseUniversal",
).forEach { taskName ->
    tasks.matching { it.name == taskName }.configureEach { dependsOn(releaseSigningGuard) }
}

/*
 * NewPipeExtractor ships its own org.schabi.newpipe.extractor.utils.Utils, and
 * app/src/main/java carries a patched copy at the same package path (see that
 * file for why it exists). A debug build keeps project and library dex separate,
 * so the project copy simply wins at class-load time and the two coexist; a
 * release build merges every input into one dex set, where D8 rejects the
 * duplicate type outright ("Utils is defined multiple times"). So the library's
 * copy is stripped from its jar before it reaches dexing, leaving exactly one
 * definition of the class in the build.
 *
 * The artifact is resolved on its own and non-transitive purely to re-jar it;
 * the transitive dependencies it would otherwise have carried are declared by
 * hand in the dependencies block below, since dropping the module drops them too.
 */
val newPipeExtractorRaw: Configuration by configurations.creating {
    isTransitive = false
    isCanBeConsumed = false
}
dependencies {
    newPipeExtractorRaw("com.github.TeamNewPipe:NewPipeExtractor:v0.26.3")
}
val newPipeExtractorStripped = tasks.register<org.gradle.api.tasks.bundling.Jar>(
    "stripNewPipeExtractorUtils"
) {
    archiveFileName.set("NewPipeExtractor-v0.26.3-noutils.jar")
    destinationDirectory.set(layout.buildDirectory.dir("stripped-libs"))
    from(provider { newPipeExtractorRaw.map { zipTree(it) } }) {
        // The class itself, plus any nested or synthetic siblings the upstream
        // compiler emitted alongside it, so nothing from the jar's Utils survives.
        exclude("org/schabi/newpipe/extractor/utils/Utils.class")
        exclude("org/schabi/newpipe/extractor/utils/Utils\$*.class")
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
    implementation("androidx.appcompat:appcompat:1.7.0")
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

    implementation("com.halilibo.compose-richtext:richtext-ui-material3:0.20.0")
    implementation("com.halilibo.compose-richtext:richtext-commonmark:0.20.0")

    // ---- Innertube (YouTube Music) client: Ktor + kotlinx.serialization ----
    implementation("io.ktor:ktor-client-core:3.0.3")
    implementation("io.ktor:ktor-client-okhttp:3.0.3")
    implementation("io.ktor:ktor-client-content-negotiation:3.0.3")
    implementation("io.ktor:ktor-serialization-kotlinx-json:3.0.3")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.7.3")

    // ---- Discord Rich Presence: Ktor WebSocket gateway client ----
    implementation("io.ktor:ktor-client-websockets:3.0.3")

    // ---- Stream resolution: NewPipe solves YouTube's signature + `n` throttling ----
    // Pinned to v0.26.3, not the newer v0.26.4: v0.26.4's player-JS parser fails with
    // "Could not parse deobfuscation function" on the current player build, which blocks
    // WEB_REMIX's ciphered formats entirely. v0.26.3 solves the same signatures cleanly
    // against the same player JS — confirmed side by side against PixelMusic-ref, which
    // pins v0.26.3 and doesn't hit the parse failure.
    //
    // Consumed as a stripped jar rather than as the module, so its own
    // Utils.class does not reach dexing. See newPipeExtractorStripped above; the
    // transitive dependencies the module would have brought are listed here
    // because dropping its artifact drops them too. If the version changes,
    // re-derive this list with
    //   ./gradlew :app:dependencies --configuration prodReleaseRuntimeClasspath
    implementation(files(newPipeExtractorStripped))
    implementation("com.github.TeamNewPipe:nanojson:e9d656ddb49a412a5a0a5d5ef20ca7ef09549996")
    implementation("org.jsoup:jsoup:1.22.2")
    implementation("com.google.code.findbugs:jsr305:3.0.2")
    implementation("com.google.protobuf:protobuf-javalite:4.35.0")
    implementation("org.mozilla:rhino:1.8.1")
    implementation("org.mozilla:rhino-engine:1.8.1")

    // ---- Auth/session storage ----
    implementation("androidx.security:security-crypto:1.1.0")

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
