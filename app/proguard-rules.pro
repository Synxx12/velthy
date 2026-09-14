# ─────────────────────────────────────────────────────────────────────────────
# Velthy release keep rules
#
# R8 is enabled for release builds (see app/build.gradle.kts). Everything below
# exists because a library — or this app — reaches a class, a member or a
# resource by a *name* the shrinker cannot see at build time. Removing a rule
# that is actually needed does not fail the build; it fails on a user's phone.
# When adding a dependency that reflects, keep that in mind.
# ─────────────────────────────────────────────────────────────────────────────

# Annotations and generic signatures are read reflectively by serialization,
# dependency injection and the Android runtime itself.
-keepattributes *Annotation*, InnerClasses, Signature, EnclosingMethod

# Keep line numbers so user-submitted stack traces stay readable, but hide the
# original (obfuscated) source file name.
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile

# Native methods are resolved by their exact class + method name, so R8 must not
# rename either. The Smart Fade analysers (MelSpectrogram, TrackFeatures,
# VocalTracker) land here.
-keepclasseswithmembernames class * {
    native <methods>;
}

# Enum valueOf()/values() are invoked reflectively by serializers.
-keepclassmembers enum * {
    public static **[] values();
    public static ** valueOf(java.lang.String);
}

# WebView JavaScript bridges (@JavascriptInterface) are called from JS by name.
-keepclassmembers class * {
    @android.webkit.JavascriptInterface <methods>;
}


# ─────────────────────────────────────────────────────────────────────────────
# kotlinx.serialization
#
# @Serializable classes get a generated `$serializer` and a `Companion`; the
# runtime looks both up by their generated names.
# ─────────────────────────────────────────────────────────────────────────────
-keepattributes RuntimeVisibleAnnotations,AnnotationDefault
-if @kotlinx.serialization.Serializable class **
-keepclassmembers class <1> {
    static <1>$Companion Companion;
}
-if @kotlinx.serialization.Serializable class **
-keepclasseswithmembers class <1> {
    static <1>$Companion Companion;
}
-if @kotlinx.serialization.Serializable class ** {
    static **$* *;
}
-keepclassmembers class <1>$<2> {
    static <1>$<2> serializer(...);
}
-if @kotlinx.serialization.Serializable class **
-keep class <1>$$serializer { *; }


# ─────────────────────────────────────────────────────────────────────────────
# NewPipeExtractor
#
# Loads its service classes and the YouTube player's JavaScript (through Rhino)
# by name. Kept whole: shrinking it is possible but the breakage is silent until
# a stream fails to resolve.
# ─────────────────────────────────────────────────────────────────────────────
-keep class org.schabi.newpipe.extractor.** { *; }
-keepclassmembers class org.schabi.newpipe.extractor.** { *; }
-dontwarn org.schabi.newpipe.extractor.**

# ─────────────────────────────────────────────────────────────────────────────
# Mozilla Rhino (and its javax.script engine factory)
#
# NewPipe runs the player JavaScript through Rhino. The engine is discovered via
# ServiceLoader (META-INF/services/javax.script.ScriptEngineFactory), so both the
# implementation and the service file entry must survive.
# ─────────────────────────────────────────────────────────────────────────────
-keep class org.mozilla.javascript.** { *; }
-keep class org.mozilla.classfile.** { *; }
-keep class org.mozilla.javascript.engine.** { *; }
-keepnames class org.mozilla.javascript.engine.RhinoScriptEngineFactory
-adaptresourcefilecontents META-INF/services/javax.script.ScriptEngineFactory
-dontwarn org.mozilla.javascript.**
-dontwarn org.mozilla.classfile.**


# ─────────────────────────────────────────────────────────────────────────────
# QuickJS (Convx-style source modules) and ONNX Runtime (Smart Fade)
#
# Both are JNI bindings: the Java side and the .so agree on class and method
# names. Keep the packages and the native methods.
# ─────────────────────────────────────────────────────────────────────────────
-keep class io.github.dokar3.quickjs.** { *; }
-keepclassmembers class io.github.dokar3.quickjs.** { *; }
-dontwarn io.github.dokar3.quickjs.**

-keep class ai.onnxruntime.** { *; }
-keepclassmembers class ai.onnxruntime.** { *; }
-dontwarn ai.onnxruntime.**


# ─────────────────────────────────────────────────────────────────────────────
# Ktor client + engines
# ─────────────────────────────────────────────────────────────────────────────
-keep class io.ktor.** { *; }
-keepclassmembers class io.ktor.** { volatile <fields>; }
-dontwarn io.ktor.**
-dontwarn org.slf4j.**

# OkHttp / Okio (Ktor's engine and Coil's network stack sit on top of them).
-dontwarn okhttp3.**
-dontwarn okio.**
-dontwarn org.conscrypt.**
-dontwarn org.bouncycastle.**
-dontwarn org.openjsse.**


# ─────────────────────────────────────────────────────────────────────────────
# Jetpack / AndroidX / Media3 / Coil / Haze
# Shipped consumer rules cover most of this; the explicit keeps are the ones
# that are reliably needed when R8 runs in full mode.
# ─────────────────────────────────────────────────────────────────────────────
-keep class androidx.media3.** { *; }
-dontwarn androidx.media3.**

-keep class coil3.** { *; }
-dontwarn coil3.**

-keep class androidx.security.crypto.** { *; }
-keep class com.google.crypto.tink.** { *; }
-dontwarn com.google.crypto.tink.**

# Compose keeps the tooling-preview classes off the release path via consumer
# rules, but reflective @Composable lookups in some helpers still need the
# runtime marked.
-dontwarn androidx.compose.**


# ─────────────────────────────────────────────────────────────────────────────
# Discord Rich Presence (kizzy facade)
# ─────────────────────────────────────────────────────────────────────────────
-keep class com.my.kizzy.** { *; }
-keepclassmembers class com.my.kizzy.** { *; }
-dontwarn com.my.kizzy.**


# ─────────────────────────────────────────────────────────────────────────────
# App models / entry points that are instantiated by name
# ─────────────────────────────────────────────────────────────────────────────
# Manifest-declared components (services, receivers, the Application) are kept
# automatically; these are the app's own reflective touch points.
-keep class com.velthy.client.VelthyApplication { *; }
-keep class com.velthy.client.playback.PlaybackService { *; }
-keep class com.velthy.client.download.DownloadService { *; }
-keep class com.velthy.client.widget.** { *; }
-keepclassmembers class com.velthy.client.data.model.** { *; }

# Parcelable CREATOR fields are looked up reflectively by the framework.
-keepclassmembers class * implements android.os.Parcelable {
    public static final ** CREATOR;
}
