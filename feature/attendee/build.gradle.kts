plugins {
    alias(libs.plugins.yuyu.android.feature)
}

android {
    namespace = "com.twilighty.yuyu.feature.attendee"
}

dependencies {
    implementation(libs.coil.compose)
}
