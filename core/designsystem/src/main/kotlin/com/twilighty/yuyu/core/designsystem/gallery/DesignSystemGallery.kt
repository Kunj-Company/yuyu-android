package com.twilighty.yuyu.core.designsystem.gallery

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Event
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.twilighty.yuyu.core.designsystem.accent.AccentMath
import com.twilighty.yuyu.core.designsystem.accent.EventAccentScope
import com.twilighty.yuyu.core.designsystem.brand.DotFace
import com.twilighty.yuyu.core.designsystem.brand.DotFaceMood
import com.twilighty.yuyu.core.designsystem.brand.DotLoader
import com.twilighty.yuyu.core.designsystem.brand.DotLockup
import com.twilighty.yuyu.core.designsystem.brand.DotMark
import com.twilighty.yuyu.core.designsystem.brand.DotMarkVariant
import com.twilighty.yuyu.core.designsystem.component.ConfirmDialog
import com.twilighty.yuyu.core.designsystem.component.DateBlock
import com.twilighty.yuyu.core.designsystem.component.EmptyState
import com.twilighty.yuyu.core.designsystem.component.ErrorState
import com.twilighty.yuyu.core.designsystem.component.ListRow
import com.twilighty.yuyu.core.designsystem.component.SectionHeader
import com.twilighty.yuyu.core.designsystem.component.SegmentedControl
import com.twilighty.yuyu.core.designsystem.component.StatTile
import com.twilighty.yuyu.core.designsystem.component.StatusChip
import com.twilighty.yuyu.core.designsystem.component.StatusTone
import com.twilighty.yuyu.core.designsystem.component.TicketStub
import com.twilighty.yuyu.core.designsystem.component.YuyuAvatar
import com.twilighty.yuyu.core.designsystem.component.YuyuBottomSheet
import com.twilighty.yuyu.core.designsystem.component.YuyuButton
import com.twilighty.yuyu.core.designsystem.component.YuyuButtonSize
import com.twilighty.yuyu.core.designsystem.component.YuyuButtonVariant
import com.twilighty.yuyu.core.designsystem.component.YuyuCard
import com.twilighty.yuyu.core.designsystem.component.YuyuCardTone
import com.twilighty.yuyu.core.designsystem.component.YuyuChip
import com.twilighty.yuyu.core.designsystem.component.YuyuChipStyle
import com.twilighty.yuyu.core.designsystem.component.YuyuFilterChip
import com.twilighty.yuyu.core.designsystem.component.YuyuProgressBar
import com.twilighty.yuyu.core.designsystem.component.YuyuSearchField
import com.twilighty.yuyu.core.designsystem.component.YuyuSnackbarHost
import com.twilighty.yuyu.core.designsystem.component.YuyuSwitchRow
import com.twilighty.yuyu.core.designsystem.component.YuyuTextField
import com.twilighty.yuyu.core.designsystem.component.YuyuTopBar
import com.twilighty.yuyu.core.designsystem.component.showUndo
import com.twilighty.yuyu.core.designsystem.theme.YuyuTheme
import kotlinx.coroutines.launch

/**
 * Debug-only catalogue of every design-system piece, rendered in light and
 * dark side by side (toggle at the top). Reached from Account in debug builds.
 */
@Composable
fun DesignSystemGallery(onBack: () -> Unit) {
    var dark by remember { mutableStateOf(false) }
    YuyuTheme(darkTheme = dark) {
        val snackbar = remember { SnackbarHostState() }
        Scaffold(
            topBar = { YuyuTopBar("Design system", onBack = onBack) },
            snackbarHost = { YuyuSnackbarHost(snackbar) },
            containerColor = YuyuTheme.colors.bgCanvas,
        ) { padding ->
            Column(
                Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .verticalScroll(rememberScrollState())
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(24.dp),
            ) {
                SegmentedControl(listOf("Light", "Dark"), if (dark) 1 else 0, { dark = it == 1 })
                GalleryContent(snackbar)
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun GalleryContent(snackbar: SnackbarHostState) {
    val c = YuyuTheme.colors
    val t = YuyuTheme.typography
    val scope = rememberCoroutineScope()
    var confirm by remember { mutableStateOf(false) }
    var sheet by remember { mutableStateOf(false) }
    var text by remember { mutableStateOf("") }
    var search by remember { mutableStateOf("") }
    var segment by remember { mutableIntStateOf(0) }
    var chip by remember { mutableStateOf(true) }
    var switch by remember { mutableStateOf(true) }
    var accent by remember { mutableStateOf(AccentMath.DEFAULT_EVENT_ACCENT) }

    SectionHeader("Brand")
    Row(horizontalArrangement = Arrangement.spacedBy(16.dp), verticalAlignment = Alignment.CenterVertically) {
        DotMark(size = 64.dp)
        DotMark(size = 40.dp)
        DotMark(size = 24.dp)
        DotMark(size = 32.dp, variant = DotMarkVariant.Mono)
        DotLoader()
    }
    DotLockup()
    FlowRow(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        DotFaceMood.entries.forEach { mood ->
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                DotFace(mood, size = 48.dp, interactive = true, label = mood.name)
                Text(mood.name, style = t.caption, color = c.textMuted)
            }
        }
    }

    SectionHeader("Typography")
    Text("Display L", style = t.displayL, color = c.text)
    Text("Heading 1", style = t.h1, color = c.text)
    Text("Heading 2", style = t.h2, color = c.text)
    Text("Heading 3", style = t.h3, color = c.text)
    Text("Heading 4", style = t.h4, color = c.text)
    Text("Body copy for public pages, Figtree 16.", style = t.body, color = c.textSecondary)
    Text("Label", style = t.label, color = c.text)
    Text("Caption · 2 min ago", style = t.caption, color = c.textMuted)
    Text("Overline".uppercase(), style = t.overline, color = c.textMuted)
    Text("aB3x9Kq", style = t.mono, color = c.text)

    SectionHeader("Colours")
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        listOf(
            c.bgCanvas, c.bgSubtle, c.bgMuted, c.bgEmphasis, c.border, c.borderStrong, c.text, c.textMuted,
            c.primary, c.brandMarigold, c.successBg, c.successFg, c.warningBg, c.warningFg, c.dangerBg, c.dangerFg,
        ).forEach { Swatch(it) }
    }

    SectionHeader("Buttons")
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        YuyuButton("Save", {}, variant = YuyuButtonVariant.Primary)
        YuyuButton("Share", {}, variant = YuyuButtonVariant.Secondary, leadingIcon = Icons.Outlined.Share)
        YuyuButton("Back", {}, variant = YuyuButtonVariant.Quiet)
        YuyuButton("Delete…", { confirm = true }, variant = YuyuButtonVariant.Danger)
        YuyuButton("Revoke", {}, variant = YuyuButtonVariant.DangerQuiet)
        YuyuButton("Saving", {}, loading = true, loadingText = "Saving…")
        YuyuButton("Disabled", {}, enabled = false)
        YuyuButton("Small", {}, size = YuyuButtonSize.Small, variant = YuyuButtonVariant.Secondary)
        YuyuButton("Large", {}, size = YuyuButtonSize.Large)
    }
    Box(Modifier.fillMaxWidth().background(c.inverseSurface).padding(12.dp)) {
        YuyuButton("Inverse", {}, variant = YuyuButtonVariant.Inverse)
    }

    SectionHeader("Event accent")
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        (AccentMath.PRESETS.map { it.name to it.hex } + listOf("Lemon" to "#FDE047")).forEach { (name, hex) ->
            YuyuFilterChip(name, accent == hex, { accent = hex })
        }
    }
    EventAccentScope(accent) {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                DateBlock("Oct", "14")
                DateBlock("Oct", "15", draft = true)
                Column(Modifier.weight(1f)) {
                    Text("Demo Summit", style = t.h2, color = YuyuTheme.accent.accentText)
                    YuyuChip("Weekly", style = YuyuChipStyle.Accent)
                }
            }
            YuyuButton("Register — it's free", {}, variant = YuyuButtonVariant.Accent, fullWidth = true)
            YuyuProgressBar(0.62f)
        }
    }

    SectionHeader("Fields")
    YuyuTextField(text, { text = it }, "Name", helper = "Shown on your ticket.")
    YuyuTextField("", {}, "Email", error = "Enter a valid email address.")
    YuyuTextField("hunter2", {}, "Password", isPassword = true)
    YuyuTextField("yuyu.idliapps.com", {}, "Server", readOnly = true)
    YuyuSearchField(search, { search = it }, placeholder = "Search events")

    SectionHeader("Choices")
    SegmentedControl(listOf("Upcoming", "Past"), segment, { segment = it })
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        YuyuFilterChip("Checked in", chip, { chip = it })
        YuyuFilterChip("Waitlist", !chip, { chip = !it })
    }
    YuyuSwitchRow("Let waitlisted guests in", switch, { switch = it }, description = "They get an email straight away.")

    SectionHeader("Status")
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        StatusChip("Going", StatusTone.Success)
        StatusChip("Waitlist", StatusTone.Warning)
        StatusChip("Cancelled", StatusTone.Danger)
        StatusChip("In person", StatusTone.Neutral)
        YuyuChip("Online")
    }

    SectionHeader("Cards & rows")
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        StatTile("Checked in", "34", Modifier.weight(1f), progress = 0.7f)
        StatTile("Needs you", "3", Modifier.weight(1f), needsAttention = true)
    }
    YuyuCard {
        ListRow("Demo Summit", supporting = "Wed 14 Oct · 9:30 AM", leadingIcon = Icons.Outlined.Event, showChevron = true, onClick = {})
        ListRow("Adithya Rao", supporting = "adithya@example.org", leading = { YuyuAvatar("Adithya Rao") }, trailing = { StatusChip("Going", StatusTone.Success) })
    }
    YuyuCard(tone = YuyuCardTone.Dashed) { Text("Dashed empty card", style = t.bodyUi, color = c.textMuted) }
    YuyuCard(tone = YuyuCardTone.Danger) { Text("Danger zone", style = t.h4, color = c.dangerFg) }

    SectionHeader("Ticket")
    EventAccentScope(accent) {
        TicketStub(
            headerHeight = 120.dp,
            header = {
                Column(Modifier.fillMaxSize().background(YuyuTheme.accent.accent).padding(20.dp)) {
                    Text("Bengaluru Makers", style = t.caption, color = YuyuTheme.accent.onAccent)
                    Text("Demo Summit", style = t.h2, color = YuyuTheme.accent.onAccent)
                }
            },
            stub = {
                Box(Modifier.fillMaxWidth().padding(24.dp), contentAlignment = Alignment.Center) {
                    Box(Modifier.size(140.dp).background(Color.White))
                }
            },
        )
    }

    SectionHeader("States")
    EmptyState("No tickets yet", body = "Events you register for show up here.")
    ErrorState("You're offline or the server can't be reached.", onRetry = {})

    SectionHeader("Overlays")
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        YuyuButton("Dialog", { confirm = true }, variant = YuyuButtonVariant.Secondary)
        YuyuButton("Sheet", { sheet = true }, variant = YuyuButtonVariant.Secondary)
        YuyuButton("Snackbar", { scope.launch { snackbar.showUndo("Guest removed") } }, variant = YuyuButtonVariant.Secondary)
    }
    Spacer(Modifier.height(48.dp))

    if (confirm) {
        ConfirmDialog(
            title = "Remove this guest?",
            body = "They'll get an email saying their registration was cancelled.",
            confirmLabel = "Remove guest",
            danger = true,
            onConfirm = { confirm = false },
            onDismiss = { confirm = false },
        )
    }
    if (sheet) {
        YuyuBottomSheet(onDismiss = { sheet = false }, title = "Bottom sheet") {
            Text("Sheets hold short, focused tasks.", style = t.bodyUi, color = c.textSecondary)
            YuyuButton("Done", { sheet = false }, fullWidth = true)
        }
    }
}

@Composable
private fun Swatch(color: Color) {
    Box(
        Modifier
            .size(36.dp)
            .background(YuyuTheme.colors.border, androidx.compose.foundation.shape.CircleShape)
            .padding(1.dp)
            .background(color, androidx.compose.foundation.shape.CircleShape),
    )
}
