import com.twilighty.yuyu.buildlogic.lib
import com.twilighty.yuyu.buildlogic.libs
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.kotlin.dsl.dependencies
import org.gradle.kotlin.dsl.project

/**
 * A feature module: Android library + Compose + Hilt + type-safe navigation,
 * depending on the shared core modules. Feature modules never depend on each other.
 */
class AndroidFeatureConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) = with(target) {
        pluginManager.apply("yuyu.android.library")
        pluginManager.apply("yuyu.android.compose")
        pluginManager.apply("yuyu.hilt")
        pluginManager.apply("org.jetbrains.kotlin.plugin.serialization")
        dependencies {
            add("implementation", project(":core:common"))
            add("implementation", project(":core:designsystem"))
            add("implementation", project(":core:data"))
            add("implementation", project(":core:network"))
            add("implementation", libs.lib("androidx-navigation-compose"))
            add("implementation", libs.lib("androidx-hilt-navigation-compose"))
            add("implementation", libs.lib("androidx-lifecycle-runtime-compose"))
            add("implementation", libs.lib("androidx-lifecycle-viewmodel-compose"))
            add("implementation", libs.lib("kotlinx-serialization-json"))
            add("implementation", libs.lib("kotlinx-coroutines-android"))
            add("implementation", libs.lib("androidx-compose-material-icons-extended"))
            add("testImplementation", libs.lib("turbine"))
        }
    }
}
