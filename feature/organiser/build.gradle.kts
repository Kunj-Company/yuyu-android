plugins {
    alias(libs.plugins.yuyu.android.feature)
}

android {
    namespace = "com.twilighty.yuyu.feature.organiser"
}

dependencies {
    implementation(libs.coil.compose)
    implementation(libs.androidx.datastore.preferences)

    testImplementation(libs.androidx.compose.ui.test.junit4)
    testImplementation(libs.robolectric)
    testImplementation(libs.androidx.test.core)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
}
