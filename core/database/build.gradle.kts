plugins {
    alias(libs.plugins.yuyu.android.library)
    alias(libs.plugins.yuyu.hilt)
    alias(libs.plugins.room)
}

android {
    namespace = "com.twilighty.yuyu.core.database"
}

room {
    // Exported schemas are committed so every future version ships a tested migration.
    schemaDirectory("$projectDir/schemas")
}

dependencies {
    api(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    ksp(libs.androidx.room.compiler)

    testImplementation(libs.androidx.room.testing)
    testImplementation(libs.robolectric)
    testImplementation(libs.androidx.test.core)
}
