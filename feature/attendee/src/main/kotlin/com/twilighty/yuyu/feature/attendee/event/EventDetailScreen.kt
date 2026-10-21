package com.twilighty.yuyu.feature.attendee.event

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.OpenInNew
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.ConfirmationNumber
import androidx.compose.material.icons.outlined.LocationOn
import androidx.compose.material.icons.outlined.RateReview
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material.icons.outlined.Videocam
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.LinkInteractionListener
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.compose.AsyncImage
import com.twilighty.yuyu.core.common.time.EventTimeFormatter
import com.twilighty.yuyu.core.designsystem.accent.EventAccentScope
import com.twilighty.yuyu.core.designsystem.brand.DotFaceMood
import com.twilighty.yuyu.core.designsystem.component.DateBlock
import com.twilighty.yuyu.core.designsystem.component.LoadingState
import com.twilighty.yuyu.core.designsystem.component.MessageState
import com.twilighty.yuyu.core.designsystem.component.SectionHeader
import com.twilighty.yuyu.core.designsystem.component.StatusChip
import com.twilighty.yuyu.core.designsystem.component.StatusTone
import com.twilighty.yuyu.core.designsystem.component.YuyuAvatar
import com.twilighty.yuyu.core.designsystem.component.YuyuButton
import com.twilighty.yuyu.core.designsystem.component.YuyuButtonSize
import com.twilighty.yuyu.core.designsystem.component.YuyuButtonVariant
import com.twilighty.yuyu.core.designsystem.component.YuyuCard
import com.twilighty.yuyu.core.designsystem.component.YuyuChip
import com.twilighty.yuyu.core.designsystem.component.YuyuChipStyle
import com.twilighty.yuyu.core.designsystem.component.YuyuIconButton
import com.twilighty.yuyu.core.designsystem.component.YuyuProgressBar
import com.twilighty.yuyu.core.designsystem.component.YuyuTopBar
import com.twilighty.yuyu.core.designsystem.motion.LocalReducedMotion
import com.twilighty.yuyu.core.designsystem.motion.YuyuSpecs
import com.twilighty.yuyu.core.designsystem.motion.YuyuStateTransition
import com.twilighty.yuyu.core.designsystem.motion.appearOnEnter
import com.twilighty.yuyu.core.designsystem.theme.MinTouchTarget
import com.twilighty.yuyu.core.designsystem.theme.YuyuRadius
import com.twilighty.yuyu.core.designsystem.theme.YuyuSpacing
import com.twilighty.yuyu.core.designsystem.theme.YuyuTheme
import com.twilighty.yuyu.core.designsystem.util.openInBrowser
import com.twilighty.yuyu.core.network.model.PublicEventDto
import com.twilighty.yuyu.core.network.model.PublicEventResponseDto
import com.twilighty.yuyu.core.network.model.PublicSessionDto
import com.twilighty.yuyu.core.network.model.RsvpStatus
import com.twilighty.yuyu.feature.attendee.common.AnimatedReveal
import com.twilighty.yuyu.feature.attendee.common.addToCalendar
import com.twilighty.yuyu.feature.attendee.common.isSafeHttpUrl
import com.twilighty.yuyu.feature.attendee.common.shareText
import com.twilighty.yuyu.feature.attendee.common.successPop
import java.time.Duration

@Composable
internal fun EventDetailRoute(
    onBack: () -> Unit,
    onRegister: () -> Unit,
    onOpenTicket: (String) -> Unit,
    onFeedback: () -> Unit,
    viewModel: EventDetailViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    // Refresh on return (e.g. after registering) so the register bar reflects the new RSVP.
    val resumedOnce = remember { booleanArrayOf(false) }
    LifecycleResumeEffect(Unit) {
        if (resumedOnce[0]) viewModel.load() else resumedOnce[0] = true
        onPauseOrDispose { }
    }
    EventDetailScreen(state, onBack, onRegister, onOpenTicket, onFeedback, onRetry = viewModel::load)
}

@Composable
internal fun EventDetailScreen(
    state: EventDetailUiState,
    onBack: () -> Unit,
    onRegister: () -> Unit,
    onOpenTicket: (String) -> Unit,
    onFeedback: () -> Unit,
    onRetry: () -> Unit,
) {
    val context = LocalContext.current
    val data = state.data
    EventAccentScope(data?.event?.accentColor) {
        Scaffold(
            topBar = {
                YuyuTopBar(data?.event?.organisation?.name ?: "Event", onBack = onBack) {
                    if (state.shareUrl != null && data != null) {
                        YuyuIconButton(Icons.Outlined.Share, contentDescription = "Share event", onClick = {
                            shareText(context, "${data.event.title}\n${state.shareUrl}", "Share event")
                        })
                    }
                }
            },
            bottomBar = {
                val reduced = LocalReducedMotion.current
                AnimatedVisibility(
                    visible = data != null,
                    enter = if (reduced) EnterTransition.None else slideInVertically(YuyuSpecs.standard(delayMillis = 120)) { it } + fadeIn(YuyuSpecs.standard(delayMillis = 120)),
                    exit = if (reduced) ExitTransition.None else fadeOut(YuyuSpecs.quick()),
                    label = "registerBar",
                ) {
                    // Keep the last event while the bar animates out.
                    val shown = remember { arrayOfNulls<PublicEventResponseDto>(1) }
                    if (data != null) shown[0] = data
                    shown[0]?.let { RegisterBar(it, onRegister, onOpenTicket) }
                }
            },
            containerColor = YuyuTheme.colors.bgCanvas,
        ) { padding ->
            Box(Modifier.fillMaxSize().padding(padding)) {
                YuyuStateTransition(
                    targetState = state,
                    modifier = Modifier.fillMaxSize(),
                    contentKey = { it.phase },
                    label = "eventDetail",
                ) { shown ->
                    val shownData = shown.data
                    when {
                        shown.phase == DetailPhase.Loading -> LoadingState(label = "Loading event")
                        shownData == null -> MessageState(
                            mood = if (shown.offline) DotFaceMood.Sleepy else DotFaceMood.Sad,
                            title = when {
                                shown.notFound -> "This event isn't available"
                                shown.offline -> "You're offline"
                                else -> "Couldn't load this event"
                            },
                            body = if (shown.notFound) "It may be private, unpublished or the link may be wrong." else shown.error,
                            actionLabel = if (shown.notFound) null else "Try again",
                            onAction = if (shown.notFound) null else onRetry,
                        )
                        else -> EventContent(shownData, onFeedback)
                    }
                }
            }
        }
    }
}

private enum class DetailPhase { Loading, Message, Content }

private val EventDetailUiState.phase: DetailPhase
    get() = when {
        loading -> DetailPhase.Loading
        data == null -> DetailPhase.Message
        else -> DetailPhase.Content
    }

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun EventContent(data: PublicEventResponseDto, onFeedback: () -> Unit) {
    val c = YuyuTheme.colors
    val a = YuyuTheme.accent
    val event = data.event
    val context = LocalContext.current
    val start = EventTimeFormatter.parseInstant(event.startDateTime)
    val end = EventTimeFormatter.parseInstant(event.endDateTime)
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = YuyuSpacing.gutter, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(YuyuSpacing.sectionGap),
    ) {
        if (event.coverImageUrl != null) {
            AsyncImage(
                model = event.coverImageUrl,
                contentDescription = "Cover image for ${event.title}",
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxWidth().appearOnEnter().aspectRatio(16f / 7f).clip(YuyuRadius.extraLarge).background(a.accentSoft),
            )
        }
        Column(Modifier.appearOnEnter(delayMillis = 40), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                if (event.isOnline) YuyuChip("Online", style = YuyuChipStyle.Accent, icon = Icons.Outlined.Videocam)
                event.tags.forEach { YuyuChip(it) }
            }
            Text(event.title, style = YuyuTheme.typography.displayM, color = a.accentText, modifier = Modifier.semantics { heading() })
            Text("Hosted by ${event.organisation.name}", style = YuyuTheme.typography.bodyUi, color = c.textSecondary)
        }

        if (start != null) {
            Row(Modifier.appearOnEnter(delayMillis = 80), horizontalArrangement = Arrangement.spacedBy(14.dp), verticalAlignment = Alignment.CenterVertically) {
                val block = EventTimeFormatter.dateBlock(start, event.timezone)
                DateBlock(block.month, block.day, accessibleDate = EventTimeFormatter.dayLong(start, event.timezone))
                Column(Modifier.weight(1f)) {
                    Text(EventTimeFormatter.dayLong(start, event.timezone), style = YuyuTheme.typography.h4, color = c.text)
                    Text(EventTimeFormatter.timeRange(start, end, event.timezone), style = YuyuTheme.typography.bodyUi, color = c.textSecondary)
                }
                YuyuIconButton(Icons.Outlined.CalendarMonth, contentDescription = "Add to calendar", onClick = {
                    addToCalendar(context, event.title, start, end, event.location, "${event.organisation.name}")
                })
            }
        }
        VenueRow(event, Modifier.appearOnEnter(delayMillis = 110))

        if (event.showRegistrationCount && event.confirmedCount != null) {
            Attendance(event.confirmedCount!!, event.capacity, Modifier.appearOnEnter(delayMillis = 130))
        }

        // Feedback can open while the page is up (refresh on return), so it slides in.
        AnimatedReveal(data.feedbackOpen) {
            YuyuCard(Modifier.fillMaxWidth().appearOnEnter(delayMillis = 150), onClick = onFeedback, onClickLabel = "Give feedback") {
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Outlined.RateReview, contentDescription = null, tint = a.accentText)
                    Column(Modifier.weight(1f)) {
                        Text("How was it?", style = YuyuTheme.typography.h4, color = c.text)
                        Text("Share your feedback with the organisers.", style = YuyuTheme.typography.bodyUi, color = c.textSecondary)
                    }
                }
            }
        }

        if (!event.description.isNullOrBlank()) {
            Column(Modifier.appearOnEnter(delayMillis = 170).animateContentSize(YuyuSpecs.standard()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                SectionHeader("About")
                Description(event.description!!)
            }
        }

        if (data.schedule.any { it.sessions.isNotEmpty() }) {
            Schedule(data, event, Modifier.appearOnEnter(delayMillis = 190))
        }

        if (data.speakers.isNotEmpty()) {
            Column(Modifier.appearOnEnter(delayMillis = 200), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                SectionHeader("Speakers")
                data.speakers.forEach { speaker ->
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                        YuyuAvatar(speaker.name, imageUrl = speaker.photoUrl, size = 48.dp)
                        Column(Modifier.weight(1f)) {
                            Text(speaker.name, style = YuyuTheme.typography.h4, color = c.text)
                            speaker.title?.let { Text(it, style = YuyuTheme.typography.bodyUi, color = c.textSecondary) }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun VenueRow(event: PublicEventDto, modifier: Modifier = Modifier) {
    val c = YuyuTheme.colors
    val context = LocalContext.current
    val location = event.location?.takeIf { it.isNotBlank() }
    val map = event.mapLinkUrl?.takeIf { isSafeHttpUrl(it) && !event.isOnline }
    if (location == null && !event.isOnline) return
    Row(
        modifier
            .fillMaxWidth()
            .let { if (map != null) it.clip(YuyuRadius.medium).clickable(role = Role.Button, onClickLabel = "Open map") { openMap(context, map) } else it }
            .padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(if (location == null) Icons.Outlined.Videocam else Icons.Outlined.LocationOn, contentDescription = null, tint = c.textSecondary)
        Column(Modifier.weight(1f)) {
            Text(location ?: "Online event", style = YuyuTheme.typography.h4, color = c.text)
            Text(
                when {
                    map != null -> "Open in maps"
                    event.isOnline -> "The joining details come with your registration email."
                    else -> "Venue"
                },
                style = YuyuTheme.typography.caption,
                color = c.textMuted,
            )
        }
        if (map != null) Icon(Icons.AutoMirrored.Outlined.OpenInNew, contentDescription = null, tint = c.textMuted, modifier = Modifier.size(18.dp))
    }
}

@Composable
private fun Attendance(confirmed: Int, capacity: Int?, modifier: Modifier = Modifier) {
    val c = YuyuTheme.colors
    Column(modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        val left = capacity?.let { (it - confirmed).coerceAtLeast(0) }
        Text(
            listOfNotNull("$confirmed going", left?.let { if (it == 0) "Full" else "$it spots left" }).joinToString(" · "),
            style = YuyuTheme.typography.label,
            color = c.text,
        )
        if (capacity != null && capacity > 0) YuyuProgressBar(confirmed.toFloat() / capacity)
    }
}

@Composable
private fun Description(html: String) {
    val c = YuyuTheme.colors
    val a = YuyuTheme.accent
    val context = LocalContext.current
    val listener = remember(context) {
        LinkInteractionListener { link ->
            val url = (link as? LinkAnnotation.Url)?.url ?: return@LinkInteractionListener
            if (url.startsWith("mailto:", ignoreCase = true)) {
                runCatching { context.startActivity(Intent(Intent.ACTION_SENDTO, url.toUri())) }
            } else if (isSafeHttpUrl(url)) {
                openInBrowser(context, url)
            }
        }
    }
    val headingSize = YuyuTheme.typography.h4.fontSize
    val text = remember(html, a.accentText, c.textSecondary, headingSize) {
        RichText.toAnnotatedString(html, RichText.Styles(linkColor = a.accentText, headingSize = headingSize, quoteColor = c.textSecondary), listener)
    }
    Text(text, style = YuyuTheme.typography.body, color = c.text, modifier = Modifier.testTag("event-description"))
}

@Composable
private fun Schedule(data: PublicEventResponseDto, event: PublicEventDto, modifier: Modifier = Modifier) {
    val c = YuyuTheme.colors
    val first = data.schedule.flatMap { it.sessions }.firstNotNullOfOrNull { EventTimeFormatter.parseInstant(it.startDateTime) }
    Column(modifier.animateContentSize(YuyuSpecs.standard()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        SectionHeader("Schedule")
        first?.let { Text("All times ${EventTimeFormatter.zoneLabel(it, event.timezone)}", style = YuyuTheme.typography.caption, color = c.textMuted) }
        data.schedule.filter { it.sessions.isNotEmpty() }.forEachIndexed { index, track ->
            if (data.schedule.size > 1 || track.trackName != null) {
                YuyuChip(track.trackName ?: "Programme", style = if (index == 0) YuyuChipStyle.Accent else YuyuChipStyle.Neutral)
            }
            track.sessions.forEach { session ->
                SessionRow(session, event.timezone)
                HorizontalDivider(color = c.border)
            }
        }
    }
}

@Composable
private fun SessionRow(session: PublicSessionDto, timezone: String) {
    val c = YuyuTheme.colors
    val start = EventTimeFormatter.parseInstant(session.startDateTime)
    val end = EventTimeFormatter.parseInstant(session.endDateTime)
    Row(Modifier.fillMaxWidth().padding(vertical = 6.dp), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
        Column(Modifier.size(width = 76.dp, height = MinTouchTarget)) {
            start?.let { Text(EventTimeFormatter.time(it, timezone), style = YuyuTheme.typography.label, color = c.text) }
            if (start != null && end != null) {
                val minutes = Duration.between(start, end).toMinutes()
                if (minutes > 0) Text(durationLabel(minutes), style = YuyuTheme.typography.caption, color = c.textMuted)
            }
        }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(session.title, style = YuyuTheme.typography.h4, color = c.text)
            if (session.speakers.isNotEmpty()) {
                Text(session.speakers.joinToString(", "), style = YuyuTheme.typography.bodyUi, color = c.textSecondary)
            }
            AnimatedReveal(session.delayMinutes > 0) {
                StatusChip(delayLabel(session.delayMinutes), StatusTone.Warning)
            }
        }
    }
}

internal fun delayLabel(minutes: Int): String = "Running $minutes min late"

internal fun durationLabel(minutes: Long): String = when {
    minutes < 60 -> "$minutes min"
    minutes % 60 == 0L -> "${minutes / 60} h"
    else -> "${minutes / 60} h ${minutes % 60} min"
}

@Composable
private fun RegisterBar(data: PublicEventResponseDto, onRegister: () -> Unit, onOpenTicket: (String) -> Unit) {
    val c = YuyuTheme.colors
    Column(
        Modifier
            .fillMaxWidth()
            .background(c.bgCanvas)
            .navigationBarsPadding(),
    ) {
        HorizontalDivider(color = c.border)
        Column(Modifier.padding(horizontal = YuyuSpacing.gutter, vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            // Only a change to "You're in" while this page is up (back from registering) pops.
            val startedRegistered = remember { registerCta(data) is RegisterCta.Registered }
            YuyuStateTransition(
                targetState = registerCta(data),
                modifier = Modifier.fillMaxWidth().animateContentSize(YuyuSpecs.standard()),
                label = "registerCta",
            ) { cta -> RegisterCtaContent(cta, celebrate = cta is RegisterCta.Registered && !startedRegistered, onRegister, onOpenTicket) }
        }
    }
}

@Composable
private fun RegisterCtaContent(cta: RegisterCta, celebrate: Boolean, onRegister: () -> Unit, onOpenTicket: (String) -> Unit) {
    val c = YuyuTheme.colors
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        when (cta) {
            is RegisterCta.Open -> {
                if (cta.waitlist) Text("This event is full. Join the waitlist and we'll let you know if a place opens up.", style = YuyuTheme.typography.caption, color = c.textMuted)
                if (cta.approval) Text("The organisers review each request.", style = YuyuTheme.typography.caption, color = c.textMuted)
                YuyuButton(cta.label, onRegister, variant = YuyuButtonVariant.Accent, size = YuyuButtonSize.Large, fullWidth = true, modifier = Modifier.testTag("register-button"))
            }
            is RegisterCta.Closed -> {
                YuyuButton("Registration closed", {}, enabled = false, size = YuyuButtonSize.Large, fullWidth = true, modifier = Modifier.testTag("register-button"))
                cta.reason?.let { Text(it, style = YuyuTheme.typography.caption, color = c.textMuted) }
            }
            is RegisterCta.Registered -> {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    StatusChip("You're in", StatusTone.Success, modifier = Modifier.successPop(enabled = celebrate))
                }
                val token = cta.ticketToken
                if (token != null) {
                    YuyuButton("View ticket", { onOpenTicket(token) }, variant = YuyuButtonVariant.Accent, size = YuyuButtonSize.Large, leadingIcon = Icons.Outlined.ConfirmationNumber, fullWidth = true, modifier = Modifier.testTag("register-button"))
                }
            }
            is RegisterCta.Waiting -> {
                StatusChip(if (cta.status == RsvpStatus.WAITLISTED) "On the waitlist" else "Request sent", StatusTone.Warning)
                Text(
                    if (cta.status == RsvpStatus.WAITLISTED) "We'll email you if a place opens up." else "We'll email you when the organisers respond.",
                    style = YuyuTheme.typography.caption,
                    color = c.textMuted,
                )
            }
        }
    }
}

private fun openMap(context: Context, url: String) {
    try {
        context.startActivity(Intent(Intent.ACTION_VIEW, url.toUri()).addCategory(Intent.CATEGORY_BROWSABLE))
    } catch (e: ActivityNotFoundException) {
        openInBrowser(context, url)
    }
}
