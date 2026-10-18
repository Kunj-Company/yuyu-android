plugins {
    alias(libs.plugins.yuyu.android.library)
    alias(libs.plugins.yuyu.hilt)
    alias(libs.plugins.kotlin.serialization)
}

android {
    namespace = "com.twilighty.yuyu.core.data"
}

dependencies {
    api(project(":core:common"))
    api(project(":core:network"))
    api(project(":core:database"))
    implementation(libs.androidx.datastore.preferences)
    implementation(libs.androidx.work.runtime.ktx)
    implementation(libs.androidx.hilt.work)
    ksp(libs.androidx.hilt.compiler)
    implementation(libs.kotlinx.serialization.json)

    testImplementation(libs.robolectric)
    testImplementation(libs.androidx.test.core)
    testImplementation(libs.okhttp.mockwebserver)
    testImplementation(libs.turbine)
}
