package com.twilighty.yuyu.core.common

/**
 * Build facts supplied by `:app` (library modules cannot see the app's BuildConfig).
 * [isDebug] gates debug-only behaviour such as http for 10.0.2.2/localhost
 * and the design system gallery.
 */
data class AppBuildInfo(
    val isDebug: Boolean,
    val versionName: String,
    val applicationId: String,
)

/** Light / dark / follow the system. Stored per device in DataStore. */
enum class AppearanceMode { System, Light, Dark }
