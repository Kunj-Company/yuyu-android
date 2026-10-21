package com.twilighty.yuyu.feature.organiser.common

import android.content.ClipData
import android.content.Context
import android.content.Intent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.ClipEntry
import androidx.compose.ui.platform.Clipboard
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.twilighty.yuyu.core.common.time.EventTimeFormatter
import com.twilighty.yuyu.core.designsystem.accent.EventAccentScope
import com.twilighty.yuyu.core.designsystem.component.DateBlock
import com.twilighty.yuyu.core.designsystem.component.StatusChip
import com.twilighty.yuyu.core.designsystem.component.StatusTone
import com.twilighty.yuyu.core.designsystem.component.YuyuCard
import com.twilighty.yuyu.core.designsystem.component.YuyuCardTone
import com.twilighty.yuyu.core.designsystem.component.YuyuProgressBar
import com.twilighty.yuyu.core.designsystem.motion.AnimatedCounterText
import com.twilighty.yuyu.core.designsystem.theme.YuyuTheme
import com.twilighty.yuyu.core.network.model.EventCountsDto
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.ui.semantics.contentDescription

/** The event's own date block: the only place (with the progress bar) where its accent shows in the dashboard. */
@Composable
fun EventDateBlock(startDateTime: String, timezone: String?, accentColor: String?, draft: Boolean, modifier: Modifier = Modifier) {
    val start = EventTimeFormatter.parseInstant(startDateTime) ?: return
    val block = EventTimeFormatter.dateBlock(start, timezone)
    EventAccentScope(accentColor) {
        DateBlock(
            month = block.month,
            day = block.day,
            draft = draft,
            accessibleDate = EventTimeFormatter.dayLong(start, timezone),
            modifier = modifier,
        )
    }
}

@Composable
fun EventStatusChip(status: String?, modifier: Modifier = Modifier) {
    val label = eventStatusLabel(status)
    AnimatedStatusChip(label.text, label.tone, modifier)
}

/** Capacity bar (event accent) plus the going / waitlist / pending / checked-in line. */
@Composable
fun EventCountsSummary(counts: EventCountsDto, accentColor: String?, modifier: Modifier = Modifier) {
    val c = YuyuTheme.colors
    val t = YuyuTheme.typography
    Column(modifier, verticalArrangement = Arrangement.spacedBy(6.dp)) {
        val capacity = counts.capacity
        if (capacity != null && capacity > 0) {
            EventAccentScope(accentColor) {
                YuyuProgressBar(
                    progress = counts.confirmed.toFloat() / capacity,
                    height = 6.dp,
                    modifier = Modifier.semantics { contentDescription = "${counts.confirmed} of $capacity places taken" },
                )
            }
        }
        val parts = buildList {
            add(if (capacity != null) "${counts.confirmed}/$capacity going" else "${counts.confirmed} going")
            if (counts.waitlisted > 0) add("${counts.waitlisted} waitlist")
            if (counts.pending > 0) add("${counts.pending} pending")
            add("${counts.checkedIn} checked in")
        }
        // Rolls when a live count changes (someone checks in, a place is taken).
        AnimatedCounterText(parts.joinToString(" · "), style = t.caption, color = c.textMuted)
    }
}

/** Event row card for the organisation's list. */
@Composable
fun OrgEventCard(
    title: String,
    startDateTime: String,
    endDateTime: String?,
    timezone: String,
    accentColor: String?,
    status: String?,
    location: String?,
    isOnline: Boolean,
    counts: EventCountsDto?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val c = YuyuTheme.colors
    val t = YuyuTheme.typography
    YuyuCard(
        modifier = modifier.fillMaxWidth(),
        tone = if (status == "DRAFT") YuyuCardTone.Dashed else YuyuCardTone.Default,
        onClick = onClick,
        onClickLabel = "Open $title",
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(14.dp), verticalAlignment = Alignment.Top) {
            EventDateBlock(startDateTime, timezone, accentColor, draft = status == "DRAFT")
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(title, style = t.h4, color = c.text, maxLines = 2, overflow = TextOverflow.Ellipsis)
                val start = EventTimeFormatter.parseInstant(startDateTime)
                if (start != null) {
                    val where = if (isOnline) "Online" else location
                    val line = EventTimeFormatter.dayShort(start, timezone) + ", " +
                        EventTimeFormatter.timeRange(start, EventTimeFormatter.parseInstant(endDateTime), timezone)
                    Text(
                        listOfNotNull(line, where).joinToString(" · "),
                        style = t.caption,
                        color = c.textMuted,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    EventStatusChip(status)
                    RevealVisibility((counts?.pending ?: 0) > 0) { StatusChip("Needs approval", StatusTone.Warning) }
                }
                if (counts != null) EventCountsSummary(counts, accentColor, Modifier.fillMaxWidth())
            }
        }
    }
}

/** Copies plain text (event links) to the clipboard. */
suspend fun Clipboard.copyText(label: String, text: String) {
    setClipEntry(ClipEntry(ClipData.newPlainText(label, text)))
}

/** Opens the system share sheet for a link. */
fun shareLink(context: Context, title: String, url: String) {
    val send = Intent(Intent.ACTION_SEND).apply {
        type = "text/plain"
        putExtra(Intent.EXTRA_SUBJECT, title)
        putExtra(Intent.EXTRA_TEXT, url)
    }
    context.startActivity(Intent.createChooser(send, "Share event link").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
}
