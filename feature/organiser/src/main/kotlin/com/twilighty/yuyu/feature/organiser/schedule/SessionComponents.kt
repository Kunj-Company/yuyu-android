package com.twilighty.yuyu.feature.organiser.schedule

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.twilighty.yuyu.core.common.time.EventTimeFormatter
import com.twilighty.yuyu.core.designsystem.component.StatusChip
import com.twilighty.yuyu.core.designsystem.component.StatusTone
import com.twilighty.yuyu.core.designsystem.component.YuyuAvatar
import com.twilighty.yuyu.core.designsystem.component.YuyuCard
import com.twilighty.yuyu.core.designsystem.component.YuyuChip
import com.twilighty.yuyu.core.designsystem.component.YuyuProgressBar
import com.twilighty.yuyu.core.designsystem.theme.YuyuTheme
import com.twilighty.yuyu.core.network.model.ScheduleSpeakerDto
import java.time.Instant
import kotlinx.coroutines.delay

/** The current time, ticking every [periodMillis] while on screen, for live countdowns. */
@Composable
fun rememberNow(periodMillis: Long = 1_000): Instant {
    var now by remember { mutableStateOf(Instant.now()) }
    LaunchedEffect(periodMillis) {
        while (true) {
            now = Instant.now()
            delay(periodMillis - now.toEpochMilli() % periodMillis)
        }
    }
    return now
}

/**
 * One programme session: live times, type and visibility, its speakers and
 * where it is right now (a countdown and progress while running).
 */
@Composable
fun SessionRow(
    item: SessionUi,
    timezone: String,
    now: Instant,
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    onClickLabel: String? = null,
    divider: Boolean = false,
) {
    val c = YuyuTheme.colors
    val t = YuyuTheme.typography
    val s = item.session
    val status = item.liveStatus(now)
    val ended = status == LiveStatus.Ended
    Column(modifier) {
        Column(
            Modifier
                .fillMaxWidth()
                .then(if (onClick != null) Modifier.clickable(onClickLabel = onClickLabel, role = Role.Button, onClick = onClick) else Modifier)
                .semantics(mergeDescendants = true) {}
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            val start = item.liveStart
            if (start != null) {
                Text(
                    EventTimeFormatter.timeRange(start, item.liveEnd, timezone, withZone = false),
                    style = t.label,
                    color = if (ended) c.textMuted else c.textSecondary,
                )
            }
            Text(s.title, style = t.bodyUiMedium, color = if (ended) c.textMuted else c.text, maxLines = 3, overflow = TextOverflow.Ellipsis)
            val planned = item.plannedStart
            if (item.shiftMinutes > 0 && planned != null) {
                Text(
                    "Planned " + EventTimeFormatter.timeRange(planned, item.plannedEnd, timezone, withZone = false) +
                        " · ${item.shiftMinutes} min behind",
                    style = t.caption,
                    color = c.warningFg,
                )
            }
            SessionChips(item, status)
            if (s.speakers.isNotEmpty()) SpeakerList(s.speakers)
            when (status) {
                is LiveStatus.Live -> Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    YuyuProgressBar(status.progress, height = 6.dp, color = c.successSolid)
                    Text(status.label().orEmpty(), style = t.caption, color = c.successFg)
                }
                is LiveStatus.Upcoming -> if (status.startsIn.toHours() < 12) {
                    Text(status.label().orEmpty(), style = t.caption, color = c.textMuted)
                }
                else -> Unit
            }
        }
        if (divider) HorizontalDivider(color = c.border, thickness = 1.dp)
    }
}

@Composable
private fun SessionChips(item: SessionUi, status: LiveStatus) {
    val s = item.session
    val chips = buildList<@Composable () -> Unit> {
        if (status is LiveStatus.Live) add { StatusChip("Now", StatusTone.Success) }
        if (status == LiveStatus.Ended) add { StatusChip("Ended", StatusTone.Neutral, icon = null) }
        s.type?.takeIf { it.isNotBlank() }?.let { add { YuyuChip(it) } }
        when (s.visibility) {
            "DRAFT" -> add { StatusChip("Draft", StatusTone.Neutral, icon = null) }
            "HIDDEN" -> add { StatusChip("Hidden", StatusTone.Neutral, icon = null) }
        }
        if (s.delayMinutes > 0) add { StatusChip("+${s.delayMinutes} min", StatusTone.Warning) }
    }
    if (chips.isEmpty()) return
    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        chips.forEach { it() }
    }
}

private const val SPEAKERS_SHOWN = 4

@Composable
fun SpeakerList(speakers: List<ScheduleSpeakerDto>, modifier: Modifier = Modifier) {
    val c = YuyuTheme.colors
    val t = YuyuTheme.typography
    Column(modifier, verticalArrangement = Arrangement.spacedBy(6.dp)) {
        speakers.take(SPEAKERS_SHOWN).forEach { speaker ->
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                YuyuAvatar(speaker.name, imageUrl = speaker.photoUrl, size = 28.dp)
                Column(Modifier.weight(1f)) {
                    Text(speaker.name, style = t.caption, color = c.text, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    val about = listOfNotNull(speaker.headline, speaker.organisation).joinToString(" · ")
                    if (about.isNotEmpty()) {
                        Text(about, style = t.caption, color = c.textMuted, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                }
            }
        }
        val more = speakers.size - SPEAKERS_SHOWN
        if (more > 0) Text("+$more more", style = t.caption, color = c.textMuted)
    }
}

/** A running session in the "Happening now" strip: big countdown and progress. */
@Composable
fun NowCard(tracked: TrackedSession, timezone: String, now: Instant, modifier: Modifier = Modifier) {
    val c = YuyuTheme.colors
    val t = YuyuTheme.typography
    val item = tracked.item
    val status = item.liveStatus(now) as? LiveStatus.Live ?: return
    YuyuCard(modifier.fillMaxWidth().semantics(mergeDescendants = true) {}) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            StatusChip("Now", StatusTone.Success)
            Text(tracked.trackName, style = t.caption, color = c.textMuted, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        Text(item.session.title, style = t.h4, color = c.text, modifier = Modifier.padding(top = 8.dp))
        if (item.session.speakers.isNotEmpty()) {
            Text(item.session.speakers.joinToString(", ") { it.name }, style = t.bodyUi, color = c.textSecondary)
        }
        Column(Modifier.padding(top = 12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            YuyuProgressBar(status.progress, color = c.successSolid)
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(formatCountdown(status.remaining) + " left", style = t.bodyUiMedium, color = c.successFg, modifier = Modifier.weight(1f))
                item.liveEnd?.let { Text("Ends " + EventTimeFormatter.time(it, timezone), style = t.caption, color = c.textMuted) }
            }
        }
    }
}
