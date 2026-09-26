// Top-level build file where you can add configuration options common to all sub-projects/modules.
buildscript {
    repositories {
        google()
    }
    dependencies {
        /*
         * Overrides the D8/R8 that AGP 8.10.1 bundles (8.10.9). Gradle's conflict
         * resolution picks the higher version, so this is the whole mechanism.
         *
         * 8.10.x miscompiles large composables — NowPlayingScreen in particular
         * is big enough to hit a register-allocation bug in debug-mode dexing:
         * resolving phis at a join point emits a const/16 over a register that
         * already holds a reference, and ART's verifier rejects the whole class,
         * so the dev build dies with a VerifyError the moment the player
         * composes. The JVM bytecode kotlinc produces is valid; the fault is
         * entirely in the dex lowering, and it is fixed as of 8.11.32. 8.13.x is
         * also the first 8.x line to read Kotlin 2.3 @Metadata — 8.10.9 caps out
         * at 2.2.0 and warns "malformed kotlin.Metadata" on every class here.
         *
         * Only debug dexing is affected; release-mode allocation is fine. This
         * can go once AGP itself bundles something past 8.11.32.
         */
        classpath("com.android.tools:r8:8.13.23")
    }
}
plugins {
    id("com.android.application") version "8.10.1" apply false
    id("org.jetbrains.kotlin.android") version "2.3.20" apply false
    id("org.jetbrains.kotlin.plugin.compose") version "2.3.20" apply false
    id("org.jetbrains.kotlin.plugin.serialization") version "2.3.20" apply false
}
