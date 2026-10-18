pluginManagement {
    includeBuild("build-logic")
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
    }
}

rootProject.name = "yuyu-android"

include(":app")
include(":core:common")
include(":core:designsystem")
include(":core:network")
include(":core:database")
include(":core:data")
include(":feature:auth")
include(":feature:attendee")
include(":feature:checkin")
include(":feature:organiser")
include(":feature:broadcasts")
