package com.twilighty.yuyu.feature.attendee.tickets

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyItemScope
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CloudOff
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material.icons.outlined.WorkspacePremium
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.twilighty.yuyu.core.common.time.EventTimeFormatter
import com.twilighty.yuyu.core.designsystem.component.ConfirmDialog
import com.twilighty.yuyu.core.designsystem.component.EmptyState
import com.twilighty.yuyu.core.designsystem.component.ErrorState
import com.twilighty.yuyu.core.designsystem.component.LoadingState
import com.twilighty.yuyu.core.designsystem.component.SectionHeader
import com.twilighty.yuyu.core.designsystem.component.YuyuButton
import com.twilighty.yuyu.core.designsystem.component.YuyuButtonVariant
import com.twilighty.yuyu.core.designsystem.component.YuyuCard
import com.twilighty.yuyu.core.designsystem.component.YuyuIconButton
import com.twilighty.yuyu.core.designsystem.component.YuyuSnackbarHost
import com.twilighty.yuyu.core.designsystem.component.YuyuSwitchRow
import com.twilighty.yuyu.core.designsystem.component.YuyuTopBar
import com.twilighty.yuyu.core.designsystem.motion.YuyuStateTransition
import com.twilighty.yuyu.core.designsystem.motion.rememberEntranceState
import com.twilighty.yuyu.core.designsystem.theme.YuyuRadius
import com.twilighty.yuyu.core.designsystem.theme.YuyuSpacing
import com.twilighty.yuyu.core.designsystem.theme.YuyuTheme
import com.twilighty.yuyu.core.network.model.CertificateDto
import com.twilighty.yuyu.core.network.model.RegistrationDto
import com.twilighty.yuyu.feature.attendee.common.EventDateBlock
import com.twilighty.yuyu.feature.attendee.common.RsvpStatusChip
import com.twilighty.yuyu.feature.attendee.common.eventWhen
import com.twilighty.yuyu.feature.attendee.common.listItemMotion
import com.twilighty.yuyu.feature.attendee.common.venueLabel

@Composable
internal fun MyTicketsRoute(
    onOpenTicket: (String) -> Unit,
    onOpenEvent: (String, String) -> Unit,
    onDiscover: () -> Unit,
    viewModel: MyTicketsViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val share by viewModel.pendingShare.collectAsStateWithLifecycle()
    val context = LocalContext.current

    val notificationLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { }
    val storageLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { }

    LaunchedEffect(share) {
        val uri = share ?: return@LaunchedEffect
        val send = Intent(Intent.ACTION_SEND)
            .setType("image/jpeg")
            .putExtra(Intent.EXTRA_STREAM, uri)
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        runCatching { context.startActivity(Intent.createChooser(send, "Share certificate")) }
        viewModel.onShareHandled()
    }

    MyTicketsScreen(
        state = state,
        onRefresh = { viewModel.refresh() },
        onOpenRegistration = { reg ->
            val token = reg.ticketToken
            if (token != null) onOpenTicket(token) else onOpenEvent(reg.event.organisation.slug, reg.event.slug)
        },
        onOpenEvent = { reg -> onOpenEvent(reg.event.organisation.slug, reg.event.slug) },
        onAskCancel = viewModel::askCancel,
        onConfirmCancel = viewModel::confirmCancel,
        onDismissCancel = viewModel::dismissCancel,
        onRemindersChange = viewModel::setRemindersEnabled,
        onAllowNotifications = {
            viewModel.onNotificationRationaleDone()
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) notificationLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        },
        onDeclineNotifications = viewModel::onNotificationRationaleDone,
        onSaveCertificate = { cert ->
            val needsStorage = Build.VERSION.SDK_INT < Build.VERSION_CODES.Q &&
                ContextCompat.checkSelfPermission(context, Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED
            if (needsStorage) storageLauncher.launch(Manifest.permission.WRITE_EXTERNAL_STORAGE) else viewModel.saveCertificate(cert)
        },
        onShareCertificate = viewModel::shareCertificate,
        onDiscover = onDiscover,
        onMessageShown = viewModel::onMessageShown,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun MyTicketsScreen(
    state: MyTicketsUiState,
    onRefresh: () -> Unit,
    onOpenRegistration: (RegistrationDto) -> Unit,
    onOpenEvent: (RegistrationDto) -> Unit,
    onAskCancel: (RegistrationDto) -> Unit,
    onConfirmCancel: () -> Unit,
    onDismissCancel: () -> Unit,
    onRemindersChange: (Boolean) -> Unit,
    onAllowNotifications: () -> Unit,
    onDeclineNotifications: () -> Unit,
    onSaveCertificate: (CertificateDto) -> Unit,
    onShareCertificate: (CertificateDto) -> Unit,
    onDiscover: () -> Unit,
    onMessageShown: () -> Unit,
) {
    val snackbar = remember { SnackbarHostState() }
    LaunchedEffect(state.message) {
        val message = state.message ?: return@LaunchedEffect
        snackbar.showSnackbar(message)
        onMessageShown()
    }
    Scaffold(
        topBar = {
            YuyuTopBar("Tickets") {
                YuyuIconButton(Icons.Outlined.Search, contentDescription = "Discover events", onClick = onDiscover)
            }
        },
        snackbarHost = { YuyuSnackbarHost(snackbar) },
        containerColor = YuyuTheme.colors.bgCanvas,
    ) { padding ->
        PullToRefreshBox(
            isRefreshing = state.refreshing,
            onRefresh = onRefresh,
            modifier = Modifier.fillMaxSize().padding(padding),
        ) {
            YuyuStateTransition(
                targetState = state,
                modifier = Modifier.fillMaxSize(),
                contentKey = { it.phase },
                label = "tickets",
            ) { shown ->
                when (shown.phase) {
                    TicketsPhase.Loading -> LoadingState(label = "Loading your tickets")
                    TicketsPhase.Error -> LazyColumn(Modifier.fillMaxSize()) {
                        item { ErrorState(shown.error.orEmpty(), title = "Couldn't load your tickets", onRetry = onRefresh) }
                    }
                    TicketsPhase.Content -> TicketsList(shown, onOpenRegistration, onOpenEvent, onAskCancel, onRemindersChange, onSaveCertificate, onShareCertificate, onDiscover)
                }
            }
        }
    }

    state.cancelTarget?.let { target ->
        ConfirmDialog(
            title = "Cancel your registration?",
            body = "You'll give up your place at ${target.event.title}. If there's a waitlist, the next person gets your spot.",
            confirmLabel = "Cancel registration",
            dismissLabel = "Keep my place",
            onConfirm = onConfirmCancel,
            onDismiss = onDismissCancel,
            danger = true,
            confirmLoading = state.cancelling,
        )
    }
    if (state.showNotificationRationale) {
        ConfirmDialog(
            title = "Get event reminders?",
            body = "Yuyu can remind you the day before and an hour before events you're going to. Allow notifications to get them. You can turn reminders off here any time.",
            confirmLabel = "Allow",
            dismissLabel = "Not now",
            onConfirm = onAllowNotifications,
            onDismiss = onDeclineNotifications,
        )
    }
}

private enum class TicketsPhase { Loading, Error, Content }

private val MyTicketsUiState.phase: TicketsPhase
    get() = when {
        loading -> TicketsPhase.Loading
        error != null && isEmpty -> TicketsPhase.Error
        else -> TicketsPhase.Content
    }

@Composable
private fun TicketsList(
    state: MyTicketsUiState,
    onOpenRegistration: (RegistrationDto) -> Unit,
    onOpenEvent: (RegistrationDto) -> Unit,
    onAskCancel: (RegistrationDto) -> Unit,
    onRemindersChange: (Boolean) -> Unit,
    onSaveCertificate: (CertificateDto) -> Unit,
    onShareCertificate: (CertificateDto) -> Unit,
    onDiscover: () -> Unit,
) {
    val entrance = rememberEntranceState()
    LazyColumn(
        Modifier.fillMaxSize().testTag("tickets-list"),
        contentPadding = PaddingValues(horizontal = YuyuSpacing.gutter, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        // Running position for the first-appearance stagger (capped by the helper).
        var position = 0
        fun LazyListScope.motionItem(key: String, content: @Composable LazyItemScope.() -> Unit) {
            val index = position++
            item(key) { Box(Modifier.listItemMotion(this, entrance, key, index)) { content() } }
        }
        if (state.offline) {
            motionItem("offline") { OfflineBanner() }
        }
        if (state.upcoming.isEmpty() && state.past.isEmpty()) {
            motionItem("empty") {
                EmptyState(
                    title = "No tickets yet",
                    body = "When you register for an event, your ticket shows up here, ready for the door even offline.",
                    actionLabel = "Discover events",
                    onAction = onDiscover,
                )
            }
        }
        if (state.upcoming.isNotEmpty()) {
            motionItem("upcoming-header") { SectionHeader("Upcoming") }
            state.upcoming.forEach { reg ->
                motionItem("u-" + reg.rsvpId) { RegistrationCard(reg, onOpenRegistration, onOpenEvent, onAskCancel) }
            }
            motionItem("reminders") {
                YuyuSwitchRow(
                    title = "Remind me before events",
                    description = "A notification the day before and an hour before.",
                    checked = state.remindersEnabled,
                    onCheckedChange = onRemindersChange,
                )
            }
        }
        if (state.past.isNotEmpty()) {
            motionItem("past-header") { SectionHeader("Past") }
            state.past.forEach { reg ->
                motionItem("p-" + reg.rsvpId) { RegistrationCard(reg, onOpenRegistration, onOpenEvent, onAskCancel, past = true) }
            }
        }
        if (state.certificates.isNotEmpty()) {
            motionItem("cert-header") { SectionHeader("Certificates") }
            state.certificates.forEach { cert ->
                motionItem("c-" + cert.certificateUrl) {
                    CertificateCard(cert, busy = state.certificateBusy == cert.certificateUrl, onSave = { onSaveCertificate(cert) }, onShare = { onShareCertificate(cert) })
                }
            }
        }
        if (state.upcoming.isNotEmpty() || state.past.isNotEmpty()) {
            motionItem("discover") {
                YuyuButton(
                    "Discover more events",
                    onDiscover,
                    variant = YuyuButtonVariant.Secondary,
                    leadingIcon = Icons.Outlined.Search,
                    fullWidth = true,
                    modifier = Modifier.padding(top = 8.dp),
                )
            }
        }
    }
}

@Composable
private fun OfflineBanner() {
    val c = YuyuTheme.colors
    Row(
        Modifier.fillMaxWidth().background(c.warningBg, YuyuRadius.large).padding(14.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(Icons.Outlined.CloudOff, contentDescription = null, tint = c.warningFg)
        Text(
            "You're offline. These are your saved tickets; they still open at the door.",
            style = YuyuTheme.typography.bodyUi,
            color = c.warningFg,
        )
    }
}

@Composable
private fun RegistrationCard(
    reg: RegistrationDto,
    onOpen: (RegistrationDto) -> Unit,
    onOpenEvent: (RegistrationDto) -> Unit,
    onAskCancel: (RegistrationDto) -> Unit,
    past: Boolean = false,
) {
    val c = YuyuTheme.colors
    val event = reg.event
    YuyuCard(
        Modifier.fillMaxWidth().testTag("registration-${reg.rsvpId}"),
        onClick = { onOpen(reg) },
        onClickLabel = if (reg.ticketToken != null) "Open ticket" else "Open event",
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            EventDateBlock(event.startDateTime, event.timezone, event.accentColor)
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    event.title,
                    style = YuyuTheme.typography.h4,
                    color = c.text,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.semantics { heading() },
                )
                val start = EventTimeFormatter.parseInstant(event.startDateTime)
                val relative = start?.takeIf { !past }?.let { EventTimeFormatter.relativeDay(it, event.timezone) }
                Text(
                    listOfNotNull(relative, eventWhen(event.startDateTime, event.timezone)).joinToString(" · "),
                    style = YuyuTheme.typography.bodyUi,
                    color = c.textSecondary,
                )
                event.venueLabel()?.let { Text(it, style = YuyuTheme.typography.caption, color = c.textMuted, maxLines = 1, overflow = TextOverflow.Ellipsis) }
                Text(event.organisation.name, style = YuyuTheme.typography.caption, color = c.textMuted, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Row(Modifier.padding(top = 4.dp), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    RsvpStatusChip(reg.status, reg.checkedInAt)
                }
            }
        }
        if (!past && (reg.canCancel || reg.ticketToken != null)) {
            Row(Modifier.fillMaxWidth().padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End)) {
                if (reg.ticketToken != null) {
                    YuyuButton("Event page", { onOpenEvent(reg) }, variant = YuyuButtonVariant.Quiet)
                }
                if (reg.canCancel) {
                    YuyuButton("Cancel", { onAskCancel(reg) }, variant = YuyuButtonVariant.DangerQuiet)
                }
            }
        }
    }
}

@Composable
private fun CertificateCard(cert: CertificateDto, busy: Boolean, onSave: () -> Unit, onShare: () -> Unit) {
    val c = YuyuTheme.colors
    YuyuCard(Modifier.fillMaxWidth()) {
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Outlined.WorkspacePremium, contentDescription = null, tint = c.text)
            Column(Modifier.weight(1f)) {
                Text(cert.eventTitle, style = YuyuTheme.typography.h4, color = c.text)
                val issued = EventTimeFormatter.parseInstant(cert.issuedAt)?.let { EventTimeFormatter.relativeTime(it) }
                Text(listOfNotNull(cert.organisationName, issued?.let { "Issued $it" }).joinToString(" · "), style = YuyuTheme.typography.caption, color = c.textMuted)
            }
        }
        Row(Modifier.fillMaxWidth().padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End)) {
            YuyuButton("Save", onSave, variant = YuyuButtonVariant.Secondary, leadingIcon = Icons.Outlined.Download, enabled = !busy, loading = busy)
            YuyuButton("Share", onShare, variant = YuyuButtonVariant.Quiet, leadingIcon = Icons.Outlined.Share, enabled = !busy)
        }
    }
}
