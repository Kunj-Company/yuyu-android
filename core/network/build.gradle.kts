plugins {
    alias(libs.plugins.yuyu.android.library)
    alias(libs.plugins.yuyu.hilt)
    alias(libs.plugins.kotlin.serialization)
}

android {
    namespace = "com.twilighty.yuyu.core.network"
    buildFeatures {
        buildConfig = true
    }
}

dependencies {
    api(project(":core:common"))
    api(libs.retrofit)
    api(libs.okhttp)
    api(libs.okhttp.sse)
    api(libs.kotlinx.serialization.json)
    implementation(libs.retrofit.kotlinx.serialization)
    implementation(libs.okhttp.logging)
    implementation(libs.coil.compose)
    implementation(libs.coil.network.okhttp)

    testImplementation(libs.okhttp.mockwebserver)
    testImplementation(libs.turbine)
}
