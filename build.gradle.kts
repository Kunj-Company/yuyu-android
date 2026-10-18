// Root build: declares plugin versions once; modules apply them through the
// convention plugins in build-logic (see README "Module map").
plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.android.library) apply false
    // Not applied anywhere (AGP 9 has built-in Kotlin); declared to pin the Kotlin Gradle plugin version.
    alias(libs.plugins.kotlin.android) apply false
    alias(libs.plugins.kotlin.compose) apply false
    alias(libs.plugins.kotlin.serialization) apply false
    alias(libs.plugins.ksp) apply false
    alias(libs.plugins.hilt) apply false
    alias(libs.plugins.room) apply false
}
