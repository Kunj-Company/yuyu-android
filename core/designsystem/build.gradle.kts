plugins {
    alias(libs.plugins.yuyu.android.library)
    alias(libs.plugins.yuyu.android.compose)
}

android {
    namespace = "com.twilighty.yuyu.core.designsystem"
}

dependencies {
    api(project(":core:common"))
    api(platform(libs.androidx.compose.bom))
    api(libs.androidx.compose.material3)
    api(libs.androidx.compose.material.icons.extended)
    api(libs.androidx.compose.animation)
    implementation(libs.androidx.core.ktx)
    implementation(libs.coil.compose)
    implementation(libs.androidx.browser)

    testImplementation(libs.androidx.compose.ui.test.junit4)
    testImplementation(libs.robolectric)
    testImplementation(libs.androidx.test.core)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
}
