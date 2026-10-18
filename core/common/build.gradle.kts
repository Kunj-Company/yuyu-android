plugins {
    alias(libs.plugins.yuyu.android.library)
    alias(libs.plugins.yuyu.hilt)
}

android {
    namespace = "com.twilighty.yuyu.core.common"
}

dependencies {
    api(libs.kotlinx.coroutines.core)
    implementation(libs.kotlinx.coroutines.android)
}
