package com.twilighty.yuyu.feature.auth.about

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.OpenInNew
import androidx.compose.material.icons.outlined.Code
import androidx.compose.material3.Icon
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.twilighty.yuyu.core.designsystem.component.ListRow
import com.twilighty.yuyu.core.designsystem.component.SectionHeader
import com.twilighty.yuyu.core.designsystem.component.StatusChip
import com.twilighty.yuyu.core.designsystem.component.StatusTone
import com.twilighty.yuyu.core.designsystem.component.YuyuAvatar
import com.twilighty.yuyu.core.designsystem.component.YuyuCard
import com.twilighty.yuyu.core.designsystem.component.YuyuCardTone
import com.twilighty.yuyu.core.designsystem.component.YuyuTopBar
import com.twilighty.yuyu.core.designsystem.theme.YuyuTheme
import com.twilighty.yuyu.core.designsystem.util.openInBrowser
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

/** About Yuyu: version, who builds it, and what changed in each release. */
@Composable
fun AboutScreen(onBack: () -> Unit, viewModel: AboutViewModel = hiltViewModel()) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val c = YuyuTheme.colors
    val t = YuyuTheme.typography
    val context = LocalContext.current
    Scaffold(
        topBar = { YuyuTopBar("About Yuyu", onBack = onBack) },
        containerColor = c.bgCanvas,
    ) { padding ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 8.dp)
                .widthIn(max = 840.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            YuyuCard(tone = YuyuCardTone.Tint) {
                Text("Yuyu for Android", style = t.h2, color = c.text, modifier = Modifier.semantics { heading() })
                Text("Version ${state.versionName}", style = t.bodyUi, color = c.textSecondary)
                state.builtBy?.let {
                    Spacer(Modifier.height(12.dp))
                    Text("Built by $it", style = t.bodyUiMedium, color = c.text)
                }
                Text(
                    "Free, self-hosted events for communities: tickets, check-in and the day-of programme.",
                    style = t.bodyUi,
                    color = c.textSecondary,
                    modifier = Modifier.padding(top = 4.dp),
                )
            }

            if (state.contributors.isNotEmpty()) {
                SectionHeader("Contributors")
                YuyuCard(contentPadding = PaddingValues(0.dp)) {
                    state.contributors.forEachIndexed { index, person ->
                        ListRow(
                            title = person.name,
                            supporting = person.role,
                            leading = { YuyuAvatar(person.name, size = 40.dp) },
                            divider = index < state.contributors.lastIndex,
                        )
                    }
                }
            }

            YuyuCard(contentPadding = PaddingValues(0.dp)) {
                ListRow(
                    title = "Source code on GitHub",
                    supporting = "Kunj-Company/yuyu-android",
                    leadingIcon = Icons.Outlined.Code,
                    trailing = { Icon(Icons.AutoMirrored.Outlined.OpenInNew, null, tint = c.textMuted) },
                    onClick = { openInBrowser(context, AboutViewModel.SOURCE_URL) },
                )
            }

            if (state.releases.isNotEmpty()) {
                SectionHeader("What's new")
                state.releases.forEach { ReleaseNotes(it) }
            }
            Spacer(Modifier.height(16.dp))
        }
    }
}

@Composable
private fun ReleaseNotes(release: ChangelogRelease) {
    val c = YuyuTheme.colors
    val t = YuyuTheme.typography
    YuyuCard {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(
                if (release.isUnreleased) "Latest changes" else "Version ${release.version}",
                style = t.h4,
                color = c.text,
                modifier = Modifier.weight(1f).semantics { heading() },
            )
            if (release.isUnreleased) {
                StatusChip("New", StatusTone.Success, icon = null)
            } else {
                release.date?.let { Text(prettyDate(it), style = t.caption, color = c.textMuted) }
            }
        }
        release.intro.forEach { Text(it, style = t.bodyUi, color = c.textSecondary, modifier = Modifier.padding(top = 6.dp)) }
        release.sections.forEach { section ->
            Text(section.title, style = t.label, color = c.text, modifier = Modifier.padding(top = 12.dp, bottom = 4.dp))
            section.items.forEach { item ->
                Row(Modifier.padding(vertical = 2.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("•", style = t.bodyUi, color = c.textMuted)
                    Text(item, style = t.bodyUi, color = c.textSecondary, modifier = Modifier.weight(1f))
                }
            }
        }
    }
}

private val DATE_FORMAT: DateTimeFormatter = DateTimeFormatter.ofPattern("d MMM yyyy", Locale.getDefault())

/** `2026-10-27` → `27 Oct 2026`; anything else is shown as written. */
private fun prettyDate(iso: String): String = runCatching { LocalDate.parse(iso).format(DATE_FORMAT) }.getOrDefault(iso)
