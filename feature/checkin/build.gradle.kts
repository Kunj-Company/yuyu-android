plugins {
    alias(libs.plugins.yuyu.android.feature)
}

android {
    namespace = "com.twilighty.yuyu.feature.checkin"
}

dependencies {
    implementation(libs.androidx.camera.core)
    implementation(libs.androidx.camera.camera2)
    implementation(libs.androidx.camera.lifecycle)
    implementation(libs.androidx.camera.view)
    // Bundled model: works offline at the door without Play services downloads.
    implementation(libs.mlkit.barcode.scanning)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.core.ktx)
    implementation(libs.coil.compose)

    testImplementation(libs.okhttp.mockwebserver)
    testImplementation(libs.retrofit)
    testImplementation(libs.retrofit.kotlinx.serialization)
    testImplementation(libs.androidx.work.runtime.ktx)
    testImplementation(libs.robolectric)
    testImplementation(libs.androidx.test.core)
    testImplementation(libs.androidx.compose.ui.test.junit4)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
}
