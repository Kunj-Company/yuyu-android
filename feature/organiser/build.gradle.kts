plugins {
    alias(libs.plugins.yuyu.android.feature)
}

android {
    namespace = "com.twilighty.yuyu.feature.organiser"
}

dependencies {
    implementation(libs.coil.compose)
}
