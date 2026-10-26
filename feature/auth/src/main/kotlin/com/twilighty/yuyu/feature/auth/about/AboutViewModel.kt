package com.twilighty.yuyu.feature.auth.about

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.twilighty.yuyu.core.common.AppBuildInfo
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class AboutUiState(
    val versionName: String = "",
    val contributors: List<Contributor> = emptyList(),
    val releases: List<ChangelogRelease> = emptyList(),
) {
    /** "Built by …": the first person in CONTRIBUTORS.md. */
    val builtBy: String? get() = contributors.firstOrNull()?.name
}

/** Version, contributors and the changelog, from the files bundled at build time. */
@HiltViewModel
class AboutViewModel @Inject constructor(
    @param:ApplicationContext private val context: Context,
    buildInfo: AppBuildInfo,
) : ViewModel() {
    private val state = MutableStateFlow(AboutUiState(versionName = buildInfo.versionName))
    val uiState: StateFlow<AboutUiState> = state.asStateFlow()

    init {
        viewModelScope.launch {
            val (contributors, changelog) = withContext(Dispatchers.IO) { readAsset(CONTRIBUTORS) to readAsset(CHANGELOG) }
            state.update {
                it.copy(contributors = parseContributors(contributors), releases = parseChangelog(changelog))
            }
        }
    }

    /** A missing file (e.g. a build without the bundling task) shows an empty section, never an error. */
    private fun readAsset(name: String): String =
        runCatching { context.assets.open(name).bufferedReader().use { it.readText() } }.getOrDefault("")

    companion object {
        const val CHANGELOG = "CHANGELOG.md"
        const val CONTRIBUTORS = "CONTRIBUTORS.md"
        const val SOURCE_URL = "https://github.com/Kunj-Company/yuyu-android"
    }
}
