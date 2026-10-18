package com.twilighty.yuyu.feature.broadcasts

import com.twilighty.yuyu.core.network.model.BroadcastAttendance
import com.twilighty.yuyu.core.network.model.BroadcastTemplateDto
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.util.Locale

/** Wording shared with the web Emails tab (`lib/broadcastTemplate.ts`, `EventBroadcastPanel.tsx`). */
object BroadcastCopy {
    const val SUBJECT_MAX = 150
    const val UNDO_SECONDS = 5
    const val EMPTY_MESSAGE = "The message is empty."

    /** Registration statuses an email can target, in the web's order. */
    val STATUSES = listOf("CONFIRMED", "WAITLISTED", "PENDING_APPROVAL", "REJECTED")

    /** Short chip labels. */
    fun statusChipLabel(status: String): String = when (status) {
        "CONFIRMED" -> "Going"
        "WAITLISTED" -> "Waitlist"
        "PENDING_APPROVAL" -> "Pending approval"
        "REJECTED" -> "Declined"
        else -> status
    }

    /** Labels used in audience summaries ("confirmed, waitlisted registrations"). */
    private fun statusSummaryLabel(status: String): String = when (status) {
        "CONFIRMED" -> "confirmed"
        "WAITLISTED" -> "waitlisted"
        "PENDING_APPROVAL" -> "pending approval"
        "REJECTED" -> "rejected"
        else -> status.lowercase()
    }

    val ATTENDANCE = listOf(BroadcastAttendance.ANY, BroadcastAttendance.CHECKED_IN, BroadcastAttendance.NOT_CHECKED_IN)

    fun attendanceLabel(value: String): String = when (value) {
        BroadcastAttendance.CHECKED_IN -> "Checked in"
        BroadcastAttendance.NOT_CHECKED_IN -> "Not checked in"
        else -> "Anyone"
    }

    /** Port of `describeBroadcastAudience`. */
    fun describeAudience(statuses: Collection<String>, attendance: String): String {
        val labels = STATUSES.filter { it in statuses }.map(::statusSummaryLabel)
        val statusText = when (labels.size) {
            STATUSES.size -> "all registrations"
            0 -> "no registrations"
            else -> "${labels.joinToString(", ")} registrations"
        }
        return when (attendance) {
            BroadcastAttendance.CHECKED_IN -> "$statusText who checked in"
            BroadcastAttendance.NOT_CHECKED_IN -> "$statusText who did not check in"
            else -> statusText
        }
    }

    fun people(count: Int): String = "$count ${if (count == 1) "person" else "people"}"

    fun recipientLine(count: Int): String =
        if (count <= 0) "No one matches this audience yet." else "This will go to ${people(count)}"

    /** The first templates shown, as on the web; the rest follow grouped. */
    val FEATURED_TEMPLATE_IDS = listOf("update", "reminder", "know-before-you-go", "agenda", "thank-you")

    data class TemplateSection(val title: String, val templates: List<BroadcastTemplateDto>)

    /** Featured first ("Suggested"), then the remaining templates by group in server order. */
    fun templateSections(templates: List<BroadcastTemplateDto>): List<TemplateSection> {
        val featured = FEATURED_TEMPLATE_IDS.mapNotNull { id -> templates.firstOrNull { it.id == id } }
        val rest = templates.filter { it !in featured }
        val sections = mutableListOf<TemplateSection>()
        if (featured.isNotEmpty()) sections += TemplateSection("Suggested", featured)
        rest.groupBy { it.group?.takeIf(String::isNotBlank) ?: "More templates" }
            .forEach { (group, items) -> sections += TemplateSection(group, items) }
        return sections
    }

    fun featuredTemplates(templates: List<BroadcastTemplateDto>): List<BroadcastTemplateDto> {
        val featured = FEATURED_TEMPLATE_IDS.mapNotNull { id -> templates.firstOrNull { it.id == id } }
        return featured.ifEmpty { templates.take(5) }
    }

    private val PLACEHOLDER_LABELS = mapOf(
        "{{name}}" to "Attendee name",
        "{{event}}" to "Event title",
        "{{eventDate}}" to "Date & time",
        "{{location}}" to "Location",
        "{{eventUrl}}" to "Event page link",
        "{{ticketLink}}" to "Ticket link",
        "{{feedbackUrl}}" to "Feedback form link",
        "{{scheduleUrl}}" to "Programme link",
        "{{organisation}}" to "Organisation",
    )

    fun placeholderLabel(token: String): String = PLACEHOLDER_LABELS[token] ?: token

    /** Placeholders offered for the subject line (plain text, no links). */
    val SUBJECT_PLACEHOLDERS = listOf("{{name}}", "{{event}}", "{{eventDate}}", "{{organisation}}")

    /** `sentAt` (ISO-8601 UTC) in the device's locale and time zone. */
    fun formatSentAt(iso: String, locale: Locale = Locale.getDefault(), zone: ZoneId = ZoneId.systemDefault()): String =
        runCatching {
            DateTimeFormatter.ofLocalizedDateTime(FormatStyle.MEDIUM, FormatStyle.SHORT).withLocale(locale).withZone(zone).format(Instant.parse(iso))
        }.getOrDefault(iso)
}
