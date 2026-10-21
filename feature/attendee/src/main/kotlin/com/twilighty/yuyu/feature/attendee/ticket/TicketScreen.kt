package com.twilighty.yuyu.feature.attendee.ticket

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.os.Build
import android.view.HapticFeedbackConstants
import android.view.WindowManager
import androidx.compose.animation.Animatable
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material3.Icon
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.LifecycleStartEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.twilighty.yuyu.core.common.time.EventTimeFormatter
import com.twilighty.yuyu.core.designsystem.accent.EventAccentScope
import com.twilighty.yuyu.core.designsystem.brand.DotFace
import com.twilighty.yuyu.core.designsystem.brand.DotFaceMood
import com.twilighty.yuyu.core.designsystem.component.ConfirmDialog
import com.twilighty.yuyu.core.designsystem.component.LoadingState
import com.twilighty.yuyu.core.designsystem.component.StatusChip
import com.twilighty.yuyu.core.designsystem.component.StatusTone
import com.twilighty.yuyu.core.designsystem.component.TicketStub
import com.twilighty.yuyu.core.designsystem.component.YuyuButton
import com.twilighty.yuyu.core.designsystem.component.YuyuButtonVariant
import com.twilighty.yuyu.core.designsystem.component.YuyuIconButton
import com.twilighty.yuyu.core.designsystem.component.YuyuSnackbarHost
import com.twilighty.yuyu.core.designsystem.component.YuyuTopBar
import com.twilighty.yuyu.core.designsystem.motion.LocalReducedMotion
import com.twilighty.yuyu.core.designsystem.motion.YuyuSpecs
import com.twilighty.yuyu.core.designsystem.motion.YuyuStateTransition
import com.twilighty.yuyu.core.designsystem.motion.appearOnEnter
import com.twilighty.yuyu.core.designsystem.theme.LightYuyuColors
import com.twilighty.yuyu.core.designsystem.theme.YuyuElevation
import com.twilighty.yuyu.core.designsystem.theme.YuyuMotion
import com.twilighty.yuyu.core.designsystem.theme.YuyuRadius
import com.twilighty.yuyu.core.designsystem.theme.YuyuSpacing
import com.twilighty.yuyu.core.designsystem.theme.YuyuTheme
import com.twilighty.yuyu.core.designsystem.theme.rememberReducedMotion
import com.twilighty.yuyu.core.designsystem.util.openInBrowser
import com.twilighty.yuyu.core.network.model.RsvpStatus
import com.twilighty.yuyu.feature.attendee.common.AnimatedReveal
import com.twilighty.yuyu.feature.attendee.common.addToCalendar
import com.twilighty.yuyu.feature.attendee.common.isSafeHttpUrl
import com.twilighty.yuyu.feature.attendee.common.rsvpStatusLabel
import com.twilighty.yuyu.feature.attendee.common.shareText
import com.twilighty.yuyu.feature.attendee.common.successPop

@Composable
internal fun TicketRoute(
    onBack: () -> Unit,
    onOpenEvent: (String, String) -> Unit,
    viewModel: TicketViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    // Live check-in only while the ticket is on screen.
    LifecycleStartEffect(viewModel) {
        viewModel.onVisible()
        onStopOrDispose { viewModel.onHidden() }
    }
    LaunchedEffect(state.cancelled) { if (state.cancelled) onBack() }
    TicketScreen(
        token = viewModel.token,
        state = state,
        onBack = onBack,
        onOpenEvent = onOpenEvent,
        onAskCancel = viewModel::askCancel,
        onConfirmCancel = viewModel::confirmCancel,
        onDismissCancel = viewModel::dismissCancel,
        onAnimationDone = viewModel::onCheckInAnimationDone,
        onMessageShown = viewModel::onMessageShown,
    )
}

@Composable
internal fun TicketScreen(
    token: String,
    state: TicketUiState,
    onBack: () -> Unit,
    onOpenEvent: (String, String) -> Unit,
    onAskCancel: () -> Unit,
    onConfirmCancel: () -> Unit,
    onDismissCancel: () -> Unit,
    onAnimationDone: () -> Unit,
    onMessageShown: () -> Unit,
) {
    val context = LocalContext.current
    val event = state.context?.event
    val snackbar = remember { SnackbarHostState() }
    LaunchedEffect(state.message) {
        val message = state.message ?: return@LaunchedEffect
        snackbar.showSnackbar(message)
        onMessageShown()
    }
    KeepScreenBright(enabled = state.showQr)

    EventAccentScope(event?.accentColor) {
        Scaffold(
            topBar = {
                YuyuTopBar("Ticket", onBack = onBack) {
                    if (event != null) {
                        YuyuIconButton(Icons.Outlined.Share, contentDescription = "Share ticket", onClick = {
                            val link = state.shareUrl
                            val text = buildString {
                                append("My ticket for ${event.title}")
                                if (link != null) append("\n").append(link)
                            }
                            shareText(context, text, "Share ticket")
                        })
                    }
                }
            },
            snackbarHost = { YuyuSnackbarHost(snackbar) },
            containerColor = YuyuTheme.colors.bgSubtle,
        ) { padding ->
            YuyuStateTransition(
                targetState = state,
                modifier = Modifier.padding(padding),
                contentKey = { it.loading },
                label = "ticket",
            ) { shown ->
                if (shown.loading) {
                    LoadingState(label = "Opening your ticket")
                } else {
                    TicketContent(token, shown, onOpenEvent, onAskCancel, onAnimationDone, Modifier)
                }
            }
        }
    }

    if (state.askCancel) {
        ConfirmDialog(
            title = "Cancel your registration?",
            body = "Your ticket will stop working and your place goes to the next person on the waitlist.",
            confirmLabel = "Cancel registration",
            dismissLabel = "Keep my ticket",
            onConfirm = onConfirmCancel,
            onDismiss = onDismissCancel,
            danger = true,
            confirmLoading = state.cancelling,
        )
    }
}

@Composable
private fun TicketContent(
    token: String,
    state: TicketUiState,
    onOpenEvent: (String, String) -> Unit,
    onAskCancel: () -> Unit,
    onAnimationDone: () -> Unit,
    modifier: Modifier,
) {
    val c = YuyuTheme.colors
    val context = LocalContext.current
    val view = LocalView.current
    val reduced = rememberReducedMotion()
    val ticketContext = state.context
    val event = ticketContext?.event
    val firstName = ticketContext?.attendeeName?.trim()?.substringBefore(' ')?.takeIf { it.isNotEmpty() }

    // The tear-away: the stub peels left from its top-right hinge and drops (DESIGN §4.11).
    val tear = remember { Animatable(0f) }
    var torn by remember { mutableStateOf(state.checkedIn && !state.justCheckedIn) }
    // True once a live check-in happened on screen: the "checked in" panel then celebrates.
    var celebrate by remember { mutableStateOf(false) }
    LaunchedEffect(state.checkedIn, state.justCheckedIn) {
        if (!state.checkedIn) return@LaunchedEffect
        if (state.justCheckedIn) {
            performCheckInHaptic(view)
            if (!reduced) tear.animateTo(1f, tween(900, easing = YuyuMotion.GravityEasing)) else tear.animateTo(1f, tween(YuyuMotion.PRESS))
            celebrate = true
            torn = true
            onAnimationDone()
        } else {
            torn = true
        }
    }

    Column(
        modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = YuyuSpacing.gutter, vertical = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Column(Modifier.widthIn(max = 420.dp).fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            // Heading + status.
            // The chip and heading swap the moment the door scans the ticket, so they pop then.
            val liveCheckIn = state.justCheckedIn || celebrate
            // Status chips cross-fade when the live mode or check-in state changes.
            val chip = when {
                state.checkedIn -> LiveChip.CheckedIn
                state.liveMode == LiveMode.Offline -> LiveChip.Offline
                state.liveMode == LiveMode.Streaming || state.liveMode == LiveMode.Polling -> LiveChip.Live
                else -> LiveChip.None
            }
            YuyuStateTransition(
                targetState = chip to state,
                contentKey = { (kind, s) -> kind to (s.context?.status.takeIf { !s.checkedIn }) },
                label = "ticketChips",
            ) { (shownChip, shown) ->
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    when (shownChip) {
                        LiveChip.CheckedIn -> StatusChip("Checked in", StatusTone.Success, modifier = Modifier.successPop(enabled = liveCheckIn, from = 0.7f))
                        LiveChip.Offline -> StatusChip("Offline", StatusTone.Neutral)
                        LiveChip.Live -> StatusChip("Live", StatusTone.Neutral, icon = null)
                        LiveChip.None -> Unit
                    }
                    val shownContext = shown.context
                    if (shownContext != null && !shown.checkedIn) {
                        val label = rsvpStatusLabel(shownContext.status)
                        StatusChip(label.text, label.tone)
                    }
                }
            }
            val heading = when {
                state.checkedIn -> TicketHeading.CheckedIn
                state.isConfirmed -> TicketHeading.Confirmed
                ticketContext?.status == RsvpStatus.WAITLISTED -> TicketHeading.Waitlisted
                else -> null
            }
            YuyuStateTransition(targetState = heading, label = "ticketHeading", contentKey = { it }) { shownHeading ->
                when (shownHeading) {
                    TicketHeading.CheckedIn -> HeadingRow(DotFaceMood.Wink, checkedInHeading(state.checkedInAt, event?.timezone), celebrate = liveCheckIn)
                    TicketHeading.Confirmed -> HeadingRow(DotFaceMood.Cheering, if (firstName != null) "You're in, $firstName!" else "You're in!")
                    TicketHeading.Waitlisted -> HeadingRow(DotFaceMood.Waiting, "You're on the waitlist")
                    null -> Unit
                }
            }

            val header: @Composable () -> Unit = { TicketHeader(state) }
            when {
                torn -> TornTicket(header, state.checkedInAt, event?.timezone, celebrate)
                state.isConfirmed -> LiveTicketStub(token, header, tear.value, state.checkedIn)
                else -> {
                    TornTicketMain(header, jagged = false)
                    Text(
                        "A scannable ticket will be available after this registration is confirmed.",
                        style = YuyuTheme.typography.bodyUi,
                        color = c.textSecondary,
                    )
                }
            }

            AnimatedReveal(state.showQr) {
                Text(
                    "Show this at the door. It only holds a private ticket link.",
                    style = YuyuTheme.typography.caption,
                    color = c.textMuted,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth(),
                )
            }

            if (event != null) {
                val start = EventTimeFormatter.parseInstant(event.startDateTime)
                AnimatedReveal(start != null && !state.checkedIn) {
                    if (start != null) YuyuButton(
                        "Add to calendar",
                        {
                            addToCalendar(
                                context,
                                event.title,
                                start,
                                EventTimeFormatter.parseInstant(event.endDateTime),
                                event.location,
                                event.organisation.name,
                            )
                        },
                        leadingIcon = Icons.Outlined.CalendarMonth,
                        fullWidth = true,
                    )
                }
                YuyuButton(
                    "Event page",
                    { onOpenEvent(event.organisation.slug, event.slug) },
                    variant = YuyuButtonVariant.Secondary,
                    fullWidth = true,
                )
            } else if (state.shareUrl != null) {
                YuyuButton("Open on the website", { openInBrowser(context, state.shareUrl) }, variant = YuyuButtonVariant.Secondary, fullWidth = true)
            }
            AnimatedReveal(state.canCancel) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
                    Text("Can't make it?", style = YuyuTheme.typography.bodyUi, color = c.textSecondary)
                    YuyuButton("Cancel registration", onAskCancel, variant = YuyuButtonVariant.Quiet)
                }
            }
        }
    }
}

private enum class LiveChip { CheckedIn, Offline, Live, None }

private enum class TicketHeading { CheckedIn, Confirmed, Waitlisted }

@Composable
private fun HeadingRow(mood: DotFaceMood, text: String, celebrate: Boolean = false) {
    Row(horizontalArrangement = Arrangement.spacedBy(16.dp), verticalAlignment = Alignment.CenterVertically) {
        DotFace(mood = mood, size = 64.dp, interactive = true, modifier = Modifier.successPop(enabled = celebrate, from = 0.5f))
        Text(
            text,
            style = YuyuTheme.typography.displayM,
            color = YuyuTheme.colors.text,
            modifier = Modifier.weight(1f).semantics { heading(); liveRegion = LiveRegionMode.Polite },
        )
    }
}

/** "Checked in at 6:04 PM" in the event's timezone. */
internal fun checkedInHeading(checkedInAt: String?, timezone: String?): String {
    val at = EventTimeFormatter.parseInstant(checkedInAt) ?: return "Checked in"
    return "Checked in at ${EventTimeFormatter.time(at, timezone)}"
}

/** Accent band (organisation + title) and the DATE / TIME / VENUE / GUEST grid. */
@Composable
private fun TicketHeader(state: TicketUiState) {
    val c = YuyuTheme.colors
    val a = YuyuTheme.accent
    val context = LocalContext.current
    val ticket = state.context
    val event = ticket?.event
    Column(Modifier.fillMaxWidth()) {
        Column(
            Modifier.fillMaxWidth().background(a.accent).padding(horizontal = 24.dp, vertical = 20.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text(event?.organisation?.name ?: "Yuyu", style = YuyuTheme.typography.label, color = a.onAccent)
            Text(event?.title ?: "Your ticket", style = YuyuTheme.typography.h1, color = a.onAccent)
        }
        if (event != null) {
            val start = EventTimeFormatter.parseInstant(event.startDateTime)
            Column(Modifier.padding(horizontal = 24.dp, vertical = 20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    DetailCell("Date", start?.let { EventTimeFormatter.dayShort(it, event.timezone) } ?: "—", Modifier.weight(1f))
                    DetailCell(
                        "Time",
                        start?.let { "${EventTimeFormatter.time(it, event.timezone)} ${EventTimeFormatter.zoneLabel(it, event.timezone)}" } ?: "—",
                        Modifier.weight(1f),
                    )
                }
                val venue = event.location?.takeIf { it.isNotBlank() } ?: if (event.isOnline) "Online" else null
                if (venue != null) {
                    val map = event.mapLinkUrl?.takeIf { isSafeHttpUrl(it) && !event.isOnline }
                    DetailCell(
                        "Venue",
                        venue,
                        Modifier.fillMaxWidth(),
                        onClick = map?.let { { openInBrowser(context, it) } },
                    )
                }
                ticket.attendeeName?.takeIf { it.isNotBlank() }?.let { DetailCell("Guest", it, Modifier.fillMaxWidth()) }
            }
        } else {
            Text(
                "Details will appear once you're online.",
                style = YuyuTheme.typography.bodyUi,
                color = c.textSecondary,
                modifier = Modifier.padding(24.dp),
            )
        }
    }
}

@Composable
private fun DetailCell(label: String, value: String, modifier: Modifier = Modifier, onClick: (() -> Unit)? = null) {
    val c = YuyuTheme.colors
    Column(modifier, verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(label.uppercase(), style = YuyuTheme.typography.overline, color = c.textMuted)
        if (onClick != null) {
            YuyuButton(value, onClick, variant = YuyuButtonVariant.Quiet)
        } else {
            Text(value, style = YuyuTheme.typography.bodyUiMedium, color = c.text)
        }
    }
}

/** The intact stub with the QR; [tear] 0→1 animates it falling away after a live check-in. */
@Composable
private fun LiveTicketStub(token: String, header: @Composable () -> Unit, tear: Float, checkedIn: Boolean) {
    val density = LocalDensity.current
    var headerHeight by remember { mutableStateOf<Dp?>(null) }
    TicketStub(
        modifier = Modifier.testTag("ticket-stub"),
        headerHeight = headerHeight ?: 300.dp,
        header = {
            Box(
                Modifier
                    .fillMaxWidth()
                    .wrapContentHeight(unbounded = true, align = Alignment.Top)
                    .onSizeChanged { size -> with(density) { headerHeight = size.height.toDp() } },
            ) { header() }
        },
        stub = {
            Box(
                Modifier
                    .fillMaxWidth()
                    .padding(24.dp)
                    .graphicsLayer {
                        transformOrigin = TransformOrigin(1f, 0f)
                        translationX = -36.dp.toPx() * tear
                        translationY = 380.dp.toPx() * tear * tear
                        rotationZ = -26f * tear
                        alpha = 1f - tear * 0.6f
                    },
                contentAlignment = Alignment.Center,
            ) {
                if (!checkedIn || tear < 1f) {
                    // White plate in both themes so scanners can read it.
                    Box(
                        Modifier
                            .widthIn(max = 280.dp)
                            .fillMaxWidth()
                            .clip(YuyuRadius.large)
                            .background(LightYuyuColors.bgCanvas)
                            .padding(16.dp)
                            .testTag("ticket-qr"),
                    ) {
                        QrCode(token, dark = LightYuyuColors.text, light = LightYuyuColors.bgCanvas, modifier = Modifier.fillMaxWidth())
                    }
                }
            }
        },
    )
}

/** After check-in: the main part with a ripped edge, then the "You're checked in" panel. Never a QR. */
@Composable
private fun TornTicket(header: @Composable () -> Unit, checkedInAt: String?, timezone: String?, celebrate: Boolean = false) {
    val c = YuyuTheme.colors
    val reduced = LocalReducedMotion.current
    val play = celebrate && !reduced
    // After a live check-in the panel washes from the canvas to the success tint
    // while the check pops in; a ticket opened already checked in shows at rest.
    val panel = remember { Animatable(if (play) c.bgCanvas else c.successBg) }
    LaunchedEffect(c.successBg) { panel.animateTo(c.successBg, if (play) YuyuSpecs.standard(YuyuMotion.SUCCESS) else snap()) }
    Column(Modifier.fillMaxWidth().testTag("ticket-torn"), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        TornTicketMain(header, jagged = true)
        Column(
            Modifier
                .fillMaxWidth()
                .then(if (play) Modifier.appearOnEnter() else Modifier)
                .clip(YuyuRadius.extraLarge)
                .background(panel.value)
                .border(1.5.dp, c.borderStrong, YuyuRadius.extraLarge)
                .padding(24.dp)
                .semantics(mergeDescendants = true) { liveRegion = LiveRegionMode.Polite },
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Box(
                Modifier.size(64.dp).successPop(enabled = play, from = 0.3f, delayMillis = 120).clip(CircleShape).background(c.successSolid),
                contentAlignment = Alignment.Center,
            ) {
                Icon(Icons.Outlined.Check, contentDescription = null, tint = c.bgCanvas, modifier = Modifier.size(36.dp))
            }
            Spacer(Modifier.height(4.dp))
            Text("You're checked in", style = YuyuTheme.typography.h2, color = c.successFg, textAlign = TextAlign.Center)
            EventTimeFormatter.parseInstant(checkedInAt)?.let {
                Text(
                    "at ${EventTimeFormatter.time(it, timezone)} ${EventTimeFormatter.zoneLabel(it, timezone)}",
                    style = YuyuTheme.typography.bodyUi,
                    color = c.successFg,
                )
            }
            Text("Enjoy the event", style = YuyuTheme.typography.bodyUi, color = c.successFg)
        }
    }
}

@Composable
private fun TornTicketMain(header: @Composable () -> Unit, jagged: Boolean) {
    val c = YuyuTheme.colors
    val shape = if (jagged) RippedBottomShape() else YuyuRadius.extraExtraLarge
    Box(
        Modifier
            .fillMaxWidth()
            .shadow(YuyuElevation.ticket, shape, ambientColor = c.shadow, spotColor = c.shadow)
            .clip(shape)
            .background(c.bgCanvas)
            .padding(bottom = if (jagged) 8.dp else 0.dp),
    ) { header() }
}

/** Rounded top corners and a row of 10×7dp teeth along the bottom: the ripped edge. */
private class RippedBottomShape(private val corner: Dp = 24.dp, private val toothWidth: Dp = 10.dp, private val toothHeight: Dp = 7.dp) : Shape {
    override fun createOutline(size: Size, layoutDirection: LayoutDirection, density: Density): Outline = with(density) {
        val r = corner.toPx()
        val tw = toothWidth.toPx()
        val th = toothHeight.toPx()
        val bottom = size.height
        val path = Path().apply {
            moveTo(0f, r)
            quadraticTo(0f, 0f, r, 0f)
            lineTo(size.width - r, 0f)
            quadraticTo(size.width, 0f, size.width, r)
            lineTo(size.width, bottom - th)
            var x = size.width
            var down = true
            while (x > 0f) {
                val nx = (x - tw / 2).coerceAtLeast(0f)
                lineTo(nx, if (down) bottom else bottom - th)
                down = !down
                x = nx
            }
            lineTo(0f, bottom - th)
            close()
        }
        Outline.Generic(path)
    }
}

/** Keeps the screen at full brightness while [enabled] (QR on screen); restores it afterwards. */
@Composable
private fun KeepScreenBright(enabled: Boolean) {
    val context = LocalContext.current
    DisposableEffect(enabled) {
        val window = context.findActivity()?.window
        if (!enabled || window == null) return@DisposableEffect onDispose { }
        val previous = window.attributes.screenBrightness
        window.attributes = window.attributes.apply { screenBrightness = WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_FULL }
        onDispose {
            window.attributes = window.attributes.apply { screenBrightness = previous }
        }
    }
}

private fun Context.findActivity(): Activity? {
    var current: Context? = this
    while (current is ContextWrapper) {
        if (current is Activity) return current
        current = current.baseContext
    }
    return null
}

private fun performCheckInHaptic(view: android.view.View) {
    val constant = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) HapticFeedbackConstants.CONFIRM else HapticFeedbackConstants.LONG_PRESS
    view.performHapticFeedback(constant)
}
