plugins {
    alias(libs.plugins.yuyu.android.application)
    alias(libs.plugins.yuyu.android.compose)
    alias(libs.plugins.yuyu.hilt)
    alias(libs.plugins.kotlin.serialization)
}

/**
 * Host verified for https App Links (assetlinks.json on the Yuyu server).
 * Override per build: ./gradlew assembleRelease -Pyuyu.appLinkHost=events.example.org
 */
val appLinkHost: String = providers.gradleProperty("yuyu.appLinkHost").getOrElse("yuyu.idliapps.com")

// CI passes the commit count as versionCode and a computed version name
// (docs/RELEASING.md), so published builds always increase.
val yuyuVersionCode: Int = providers.gradleProperty("yuyu.versionCode").map(String::toInt).getOrElse(1)
val yuyuVersionName: String = providers.gradleProperty("yuyu.versionName").getOrElse("0.1.0")

android {
    namespace = "com.twilighty.yuyu"

    defaultConfig {
        applicationId = "com.twilighty.yuyu"
        versionCode = yuyuVersionCode
        versionName = yuyuVersionName
        manifestPlaceholders["yuyuHost"] = appLinkHost
    }

    signingConfigs {
        // Release signing comes only from the environment (CI secrets). When the
        // variables are absent, release falls back to debug signing so
        // `assembleRelease` still works locally; such builds must not be shipped.
        val keystorePath = System.getenv("YUYU_KEYSTORE_PATH")
        if (!keystorePath.isNullOrBlank()) {
            create("release") {
                storeFile = file(keystorePath)
                storePassword = System.getenv("YUYU_KEYSTORE_PASSWORD")
                keyAlias = System.getenv("YUYU_KEY_ALIAS")
                keyPassword = System.getenv("YUYU_KEY_PASSWORD")
            }
        }
    }

    buildTypes {
        debug {
            isMinifyEnabled = false
        }
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
            signingConfig = signingConfigs.findByName("release") ?: signingConfigs.getByName("debug")
        }
    }

    buildFeatures {
        buildConfig = true
    }

    packaging {
        resources {
            excludes += setOf("/META-INF/{AL2.0,LGPL2.1}", "/META-INF/LICENSE*", "/META-INF/NOTICE*")
        }
    }
}

dependencies {
    implementation(project(":core:common"))
    implementation(project(":core:designsystem"))
    implementation(project(":core:network"))
    implementation(project(":core:database"))
    implementation(project(":core:data"))
    implementation(project(":feature:auth"))
    implementation(project(":feature:attendee"))
    implementation(project(":feature:checkin"))
    implementation(project(":feature:organiser"))
    implementation(project(":feature:broadcasts"))

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.core.splashscreen)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.browser)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.navigation.compose)
    implementation(libs.androidx.hilt.navigation.compose)
    implementation(libs.androidx.hilt.work)
    ksp(libs.androidx.hilt.compiler)
    implementation(libs.androidx.work.runtime.ktx)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.coil.compose)

    testImplementation(libs.robolectric)
    testImplementation(libs.androidx.test.core)
}
