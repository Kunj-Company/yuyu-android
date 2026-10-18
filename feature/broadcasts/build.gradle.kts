plugins {
    alias(libs.plugins.yuyu.android.feature)
}

android {
    namespace = "com.twilighty.yuyu.feature.broadcasts"
}

dependencies {
    implementation(libs.coil.compose)
}
