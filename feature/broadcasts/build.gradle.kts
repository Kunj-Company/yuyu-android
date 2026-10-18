plugins {
    alias(libs.plugins.yuyu.android.feature)
}

android {
    namespace = "com.twilighty.yuyu.feature.broadcasts"
}

dependencies {
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.datastore.preferences)

    testImplementation(libs.robolectric)
    testImplementation(libs.androidx.test.core)
    testImplementation(libs.androidx.compose.ui.test.junit4)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
}
