package com.twilighty.yuyu.feature.attendee.common

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.provider.CalendarContract
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.material3.Text
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.twilighty.yuyu.core.common.time.EventTimeFormatter
import com.twilighty.yuyu.core.designsystem.accent.EventAccentScope
import com.twilighty.yuyu.core.designsystem.component.DateBlock
import com.twilighty.yuyu.core.designsystem.component.StatusChip
import com.twilighty.yuyu.core.designsystem.component.StatusTone
import com.twilighty.yuyu.core.designsystem.component.YuyuCard
import com.twilighty.yuyu.core.designsystem.component.YuyuChip
import com.twilighty.yuyu.core.designsystem.theme.YuyuRadius
import com.twilighty.yuyu.core.designsystem.theme.YuyuTheme
import com.twilighty.yuyu.core.network.model.EventSummaryDto
import com.twilighty.yuyu.core.network.model.RsvpStatus
import java.time.Instant

/** Attendee-facing label and tone for an RSVP status (one word where possible). */
data class StatusLabel(val text: String, val tone: StatusTone)

fun rsvpStatusLabel(status: String, checkedInAt: String? = null): StatusLabel = when {
    checkedInAt != null -> StatusLabel("Checked in", StatusTone.Success)
    status == RsvpStatus.CONFIRMED -> StatusLabel("Going", StatusTone.Success)
    status == RsvpStatus.WAITLISTED -> StatusLabel("Waitlist", StatusTone.Warning)
    status == RsvpStatus.PENDING_APPROVAL -> StatusLabel("Pending approval", StatusTone.Warning)
    status == RsvpStatus.REJECTED -> StatusLabel("Not approved", StatusTone.Danger)
    status == RsvpStatus.CANCELLED -> StatusLabel("Cancelled", StatusTone.Neutral)
    else -> StatusLabel(status.lowercase().replace('_', ' ').replaceFirstChar { it.uppercase() }, StatusTone.Neutral)
}

@Composable
fun RsvpStatusChip(status: String, checkedInAt: String? = null, modifier: Modifier = Modifier) {
    val label = rsvpStatusLabel(status, checkedInAt)
    StatusChip(label.text, label.tone, modifier)
}

/** Date block for an event, in the event's accent and timezone. */
@Composable
fun EventDateBlock(startIso: String, timezone: String, accentHex: String?, modifier: Modifier = Modifier, width: androidx.compose.ui.unit.Dp = 56.dp) {
    val start = EventTimeFormatter.parseInstant(startIso) ?: return
    val block = EventTimeFormatter.dateBlock(start, timezone)
    EventAccentScope(accentHex) {
        DateBlock(block.month, block.day, modifier, width = width, accessibleDate = EventTimeFormatter.dayLong(start, timezone))
    }
}

/** "Wed 14 Oct · 9:30 AM BST" in the event's timezone. */
fun eventWhen(startIso: String, timezone: String): String {
    val start = EventTimeFormatter.parseInstant(startIso) ?: return ""
    return "${EventTimeFormatter.dayShort(start, timezone)} · ${EventTimeFormatter.time(start, timezone)} ${EventTimeFormatter.zoneLabel(start, timezone)}"
}

fun EventSummaryDto.venueLabel(): String? = when {
    !location.isNullOrBlank() -> location
    isOnline -> "Online"
    else -> null
}

/** Discover/search card: cover band (or accent band), date block, title, when, venue and organisation. */
@Composable
fun EventCard(event: EventSummaryDto, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val c = YuyuTheme.colors
    EventAccentScope(event.accentColor) {
        YuyuCard(modifier.fillMaxWidth(), contentPadding = androidx.compose.foundation.layout.PaddingValues(0.dp), onClick = onClick, onClickLabel = "Open event") {
            Box(
                Modifier
                    .fillMaxWidth()
                    .aspectRatio(16f / 7f)
                    .clip(YuyuRadius.large)
                    .background(YuyuTheme.accent.accentSoft),
            ) {
                if (event.coverImageUrl != null) {
                    AsyncImage(
                        model = event.coverImageUrl,
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize(),
                    )
                }
            }
            Row(
                Modifier.padding(16.dp),
                horizontalArrangement = Arrangement.spacedBy(14.dp),
                verticalAlignment = Alignment.Top,
            ) {
                EventDateBlock(event.startDateTime, event.timezone, event.accentColor)
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(event.title, style = YuyuTheme.typography.h3, color = c.text, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    Text(eventWhen(event.startDateTime, event.timezone), style = YuyuTheme.typography.bodyUi, color = c.textSecondary)
                    event.venueLabel()?.let {
                        Text(it, style = YuyuTheme.typography.bodyUi, color = c.textSecondary, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text(event.organisation.name, style = YuyuTheme.typography.caption, color = c.textMuted, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false))
                        if (event.isOnline) YuyuChip("Online")
                    }
                }
            }
        }
    }
}

// ---- Intents ---------------------------------------------------------------------------------

/** Opens the system share sheet with [text]. */
fun shareText(context: Context, text: String, title: String = "Share") {
    val send = Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, text)
    runCatching { context.startActivity(Intent.createChooser(send, title)) }
}

/** CalendarContract insert intent; false when no calendar app can take it. */
fun addToCalendar(
    context: Context,
    title: String,
    start: Instant,
    end: Instant?,
    location: String?,
    description: String?,
): Boolean {
    val intent = Intent(Intent.ACTION_INSERT)
        .setData(CalendarContract.Events.CONTENT_URI)
        .putExtra(CalendarContract.EXTRA_EVENT_BEGIN_TIME, start.toEpochMilli())
        .putExtra(CalendarContract.EXTRA_EVENT_END_TIME, (end ?: start.plusSeconds(3600)).toEpochMilli())
        .putExtra(CalendarContract.Events.TITLE, title)
        .apply {
            location?.takeIf { it.isNotBlank() }?.let { putExtra(CalendarContract.Events.EVENT_LOCATION, it) }
            description?.takeIf { it.isNotBlank() }?.let { putExtra(CalendarContract.Events.DESCRIPTION, it) }
        }
    return try {
        context.startActivity(intent)
        true
    } catch (e: ActivityNotFoundException) {
        false
    }
}

/** True for http(s) URLs only (map links, description links). */
fun isSafeHttpUrl(url: String?): Boolean {
    val lower = url?.trim()?.lowercase() ?: return false
    return lower.startsWith("https://") || lower.startsWith("http://")
}
