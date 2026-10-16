// AGP 9.0+ has built-in Kotlin support: do NOT apply org.jetbrains.kotlin.android.
//
// AGP 9 depends on KGP 2.2.10 at runtime and only auto-upgrades *to* that version.
// A higher KGP must be requested explicitly through the buildscript classpath — this is
// the mechanism documented in the AGP 9.0 release notes ("Runtime dependency on Kotlin
// Gradle plugin"). The Compose compiler plugin version must equal the KGP version.
//
// KSP (for Room) goes in the same block once Room lands:
//   classpath("com.google.devtools.ksp:symbol-processing-gradle-plugin:KSP_VERSION")
// KSP versions are KGP-prefixed, e.g. 2.4.21-2.0.2.
buildscript {
    dependencies {
        classpath("org.jetbrains.kotlin:kotlin-gradle-plugin:2.4.21")
    }
}

plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.kotlin.compose) apply false
}
