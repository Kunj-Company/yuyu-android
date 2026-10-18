package com.twilighty.yuyu.buildlogic

import com.android.build.api.dsl.ApplicationExtension
import com.android.build.api.dsl.LibraryExtension
import org.gradle.api.JavaVersion
import org.gradle.api.Project
import org.gradle.api.artifacts.VersionCatalog
import org.gradle.api.artifacts.VersionCatalogsExtension
import org.gradle.kotlin.dsl.getByType

object YuyuSdk {
    /** Current AndroidX/Compose/Coil releases require compiling against API 37; behaviour targets 36. */
    const val COMPILE = 37
    const val TARGET = 36
    const val MIN = 26
    val JAVA = JavaVersion.VERSION_17
}

val Project.libs: VersionCatalog
    get() = extensions.getByType<VersionCatalogsExtension>().named("libs")

fun VersionCatalog.lib(alias: String) = findLibrary(alias).orElseThrow {
    IllegalArgumentException("Unknown library alias '$alias' in libs.versions.toml")
}

/** Applies shared SDK, Java and test settings to an application module. */
internal fun ApplicationExtension.configureYuyuAndroid() {
    compileSdk = YuyuSdk.COMPILE
    defaultConfig {
        minSdk = YuyuSdk.MIN
        targetSdk = YuyuSdk.TARGET
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        vectorDrawables.useSupportLibrary = true
    }
    compileOptions {
        sourceCompatibility = YuyuSdk.JAVA
        targetCompatibility = YuyuSdk.JAVA
    }
    testOptions {
        unitTests.isIncludeAndroidResources = true
        unitTests.isReturnDefaultValues = true
    }
    lint {
        abortOnError = true
        checkDependencies = true
        warningsAsErrors = false
        disable += setOf("GradleDependency", "NewerVersionAvailable", "AndroidGradlePluginVersion")
    }
}

/** Applies shared SDK, Java and test settings to a library module. */
internal fun LibraryExtension.configureYuyuAndroid() {
    compileSdk = YuyuSdk.COMPILE
    defaultConfig {
        minSdk = YuyuSdk.MIN
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        consumerProguardFiles("consumer-rules.pro")
    }
    compileOptions {
        sourceCompatibility = YuyuSdk.JAVA
        targetCompatibility = YuyuSdk.JAVA
    }
    testOptions {
        unitTests.isIncludeAndroidResources = true
        unitTests.isReturnDefaultValues = true
    }
    lint {
        abortOnError = true
        warningsAsErrors = false
        disable += setOf("GradleDependency", "NewerVersionAvailable", "AndroidGradlePluginVersion")
    }
}
