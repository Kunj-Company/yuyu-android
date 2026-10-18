import com.android.build.api.dsl.LibraryExtension
import com.twilighty.yuyu.buildlogic.configureYuyuAndroid
import com.twilighty.yuyu.buildlogic.lib
import com.twilighty.yuyu.buildlogic.libs
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.tasks.testing.Test
import org.gradle.kotlin.dsl.configure
import org.gradle.kotlin.dsl.dependencies

class AndroidLibraryConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) = with(target) {
        pluginManager.apply("com.android.library")
        extensions.configure<LibraryExtension> {
            configureYuyuAndroid()
        }
        // Modules start without tests (feature placeholders); generated test sources
        // must not fail the build until real tests are added.
        tasks.withType(Test::class.java).configureEach { failOnNoDiscoveredTests.set(false) }
        dependencies {
            add("testImplementation", libs.lib("junit"))
            add("testImplementation", libs.lib("kotlinx-coroutines-test"))
        }
    }
}
