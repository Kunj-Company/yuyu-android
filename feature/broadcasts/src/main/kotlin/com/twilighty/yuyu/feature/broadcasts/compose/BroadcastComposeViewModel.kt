package com.twilighty.yuyu.feature.broadcasts.compose

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.twilighty.yuyu.core.common.result.AppError
import com.twilighty.yuyu.core.common.result.AppResult
import com.twilighty.yuyu.core.data.repository.BroadcastsRepository
import com.twilighty.yuyu.core.network.model.BroadcastAttendance
import com.twilighty.yuyu.core.network.model.BroadcastContentRequest
import com.twilighty.yuyu.core.network.model.BroadcastCountRequest
import com.twilighty.yuyu.core.network.model.BroadcastDesign
import com.twilighty.yuyu.core.network.model.BroadcastDesignDto
import com.twilighty.yuyu.core.network.model.BroadcastHistoryDto
import com.twilighty.yuyu.core.network.model.BroadcastTemplateDto
import com.twilighty.yuyu.core.network.model.SendBroadcastRequest
import com.twilighty.yuyu.feature.broadcasts.BroadcastCopy
import com.twilighty.yuyu.feature.broadcasts.draft.BroadcastDraft
import com.twilighty.yuyu.feature.broadcasts.draft.BroadcastDraftStore
import com.twilighty.yuyu.feature.broadcasts.editor.EmailHtml
import com.twilighty.yuyu.feature.broadcasts.editor.RichEmailEditorState
import com.twilighty.yuyu.feature.broadcasts.history.ReuseTarget
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** The live recipient count for the selected audience. */
sealed interface RecipientCount {
    data object Counting : RecipientCount
    data class Ready(val count: Int) : RecipientCount
    data class Failed(val message: String) : RecipientCount
}

/** The review sheet: what will be sent, and the required "I've checked" tick. */
data class ReviewState(val count: Int, val audience: String, val subject: String, val checked: Boolean = false)

/** A template or previous email waiting for "Replace your draft?" confirmation. */
sealed interface PendingReplace {
    data class Template(val template: BroadcastTemplateDto) : PendingReplace
    data class History(val row: BroadcastHistoryDto) : PendingReplace
}

data class BroadcastComposeUiState(
    val metaLoading: Boolean = true,
    val metaError: String? = null,
    val templates: List<BroadcastTemplateDto> = emptyList(),
    val designs: List<BroadcastDesignDto> = emptyList(),
    val placeholders: List<String> = DEFAULT_PLACEHOLDERS,
    val history: List<BroadcastHistoryDto> = emptyList(),
    val templateId: String? = null,
    val statuses: Set<String> = setOf("CONFIRMED"),
    val attendance: String = BroadcastAttendance.ANY,
    val design: String = BroadcastDesign.CLASSIC,
    val count: RecipientCount = RecipientCount.Counting,
    val subjectError: String? = null,
    val bodyError: String? = null,
    val audienceError: String? = null,
    val testSending: Boolean = false,
    val review: ReviewState? = null,
    /** Seconds left before the send request is made; null when no send is pending. */
    val countdownSeconds: Int? = null,
    /** Recipients reviewed for the pending send (shown in the countdown bar). */
    val pendingSendCount: Int = 0,
    val sending: Boolean = false,
    /** Set after a successful send: how many people it was queued for. */
    val sentCount: Int? = null,
    /** Seconds until sending is allowed again after RATE_LIMITED. */
    val rateLimitSeconds: Int = 0,
    val pendingReplace: PendingReplace? = null,
    val message: String? = null,
) {
    val sendPending: Boolean get() = countdownSeconds != null || sending

    val recipientCount: Int? get() = (count as? RecipientCount.Ready)?.count

    companion object {
        val DEFAULT_PLACEHOLDERS = listOf(
            "{{name}}", "{{event}}", "{{eventDate}}", "{{location}}", "{{eventUrl}}",
            "{{ticketLink}}", "{{feedbackUrl}}", "{{scheduleUrl}}", "{{organisation}}",
        )
    }
}

/**
 * Why Send is unavailable, or null when the email can be sent. Mirrors the web
 * send bar (`sendBarLabel`) plus the phone's extra states.
 */
fun sendBlocker(state: BroadcastComposeUiState, subject: String, bodyEmpty: Boolean, bodyTooLong: Boolean): String? = when {
    state.sendPending -> "Sending…"
    state.statuses.isEmpty() -> "Pick at least one group to send to"
    subject.isBlank() || bodyEmpty -> "Add a subject and a message to send"
    subject.length > BroadcastCopy.SUBJECT_MAX -> "The subject is too long"
    bodyTooLong -> "The message is too long"
    state.rateLimitSeconds > 0 -> "Too many emails. Try again in ${state.rateLimitSeconds} s"
    state.count is RecipientCount.Counting -> "Counting recipients…"
    state.count is RecipientCount.Failed -> state.count.message
    (state.recipientCount ?: 0) <= 0 -> "No one matches this audience yet"
    else -> null
}

/**
 * Compose screen state, shared with Preview (and History when opened from
 * here) by scoping it to the Compose back-stack entry.
 *
 * The subject and the editor are snapshot state (typed text never round-trips
 * through a flow); everything else is an immutable [BroadcastComposeUiState].
 * The draft is mirrored to [SavedStateHandle] on every change (process death)
 * and to [BroadcastDraftStore] after a short pause (app restarts), and cleared
 * after a successful send.
 */
@HiltViewModel
class BroadcastComposeViewModel @Inject constructor(
    private val savedStateHandle: SavedStateHandle,
    private val repository: BroadcastsRepository,
    private val draftStore: BroadcastDraftStore,
) : ViewModel(), ReuseTarget {
    val orgSlug: String = checkNotNull(savedStateHandle.get<String>("orgSlug")) { "orgSlug missing" }
    val eventId: String = checkNotNull(savedStateHandle.get<String>("eventId")) { "eventId missing" }

    private val state = MutableStateFlow(BroadcastComposeUiState())
    val uiState: StateFlow<BroadcastComposeUiState> = state.asStateFlow()

    var subject by mutableStateOf(TextFieldValue(""))
        private set

    val editor = RichEmailEditorState()

    private data class Audience(val statuses: Set<String>, val attendance: String)

    private val audience = MutableStateFlow(Audience(state.value.statuses, state.value.attendance))
    /** Bumped to recount an unchanged audience (retry, or a template with the same audience). */
    private val recountRequests = MutableStateFlow(0)
    private val draftVersion = MutableStateFlow(0)
    private var ready = false
    private var restoredDraft = false
    private var baseline: Pair<String, String>? = null
    private var countdownJob: Job? = null
    private var rateLimitJob: Job? = null

    init {
        editor.onContentChange = {
            if (state.value.bodyError != null) state.update { it.copy(bodyError = null) }
            onDraftChanged()
        }
        viewModelScope.launch {
            // StateFlow drops equal values, so an unchanged audience alone would never
            // retry a failed count; recountRequests forces a fresh request.
            combine(audience, recountRequests) { target, _ -> target }.collectLatest { recount(it) }
        }
        viewModelScope.launch {
            draftVersion.collectLatest { version ->
                if (version == 0) return@collectLatest
                delay(AUTOSAVE_DELAY_MS)
                if (state.value.sentCount == null) draftStore.save(orgSlug, eventId, currentDraft())
            }
        }
        viewModelScope.launch {
            val draft = BroadcastDraft.decode(savedStateHandle[KEY_DRAFT]) ?: draftStore.load(orgSlug, eventId)
            if (draft != null) {
                applyDraft(draft)
                restoredDraft = true
            }
            ready = true
            fetchMeta()
        }
    }

    // ------------------------------------------------------------------- meta

    fun retryMeta() {
        viewModelScope.launch { fetchMeta() }
    }

    private suspend fun fetchMeta() {
        state.update { it.copy(metaLoading = true, metaError = null) }
        when (val result = repository.meta(orgSlug, eventId)) {
            is AppResult.Success -> {
                val meta = result.data
                state.update {
                    it.copy(
                        metaLoading = false,
                        templates = meta.templates,
                        designs = meta.designs,
                        placeholders = meta.placeholders.ifEmpty { BroadcastComposeUiState.DEFAULT_PLACEHOLDERS },
                        history = meta.history,
                    )
                }
                if (!restoredDraft && baseline == null) {
                    BroadcastCopy.featuredTemplates(meta.templates).firstOrNull()?.let { applyTemplate(it, announce = false) }
                } else if (baseline == null) {
                    // A restored, untouched template counts as unedited.
                    val template = meta.templates.firstOrNull { it.id == state.value.templateId }
                    if (template != null && subject.text == template.subject && editor.html == EmailHtml.serialize(EmailHtml.parse(template.bodyHtml))) {
                        baseline = subject.text to editor.html
                    }
                }
                // The count races the meta load; if it failed while the server was unreachable, retry now.
                if (state.value.count is RecipientCount.Failed) retryCount()
            }
            is AppResult.Failure -> state.update { it.copy(metaLoading = false, metaError = result.error.message) }
        }
    }

    // ---------------------------------------------------------------- content

    fun onSubjectChange(value: TextFieldValue) {
        if (value.text.length > BroadcastCopy.SUBJECT_MAX) return
        val changed = value.text != subject.text
        subject = value
        if (changed) {
            if (state.value.subjectError != null) state.update { it.copy(subjectError = null) }
            onDraftChanged()
        }
    }

    /** Inserts [token] at the subject caret, unless it would pass the 150-character limit. */
    fun insertSubjectPlaceholder(token: String) {
        val current = subject
        val start = current.selection.min.coerceIn(0, current.text.length)
        val end = current.selection.max.coerceIn(0, current.text.length)
        val next = current.text.substring(0, start) + token + current.text.substring(end)
        if (next.length > BroadcastCopy.SUBJECT_MAX) {
            state.update { it.copy(message = "The subject can be at most ${BroadcastCopy.SUBJECT_MAX} characters.") }
            return
        }
        onSubjectChange(TextFieldValue(next, TextRange(start + token.length)))
    }

    fun toggleStatus(status: String) {
        val next = state.value.statuses.let { if (status in it) it - status else it + status }
        state.update { it.copy(statuses = next, audienceError = null) }
        audience.value = Audience(next, state.value.attendance)
        onDraftChanged()
    }

    fun setAttendance(value: String) {
        state.update { it.copy(attendance = value, audienceError = null) }
        audience.value = Audience(state.value.statuses, value)
        onDraftChanged()
    }

    fun setDesign(id: String) {
        state.update { it.copy(design = id) }
        onDraftChanged()
    }

    fun currentContent(): BroadcastContentRequest = BroadcastContentRequest(subject = subject.text, body = editor.html, design = state.value.design)

    fun currentDraft(): BroadcastDraft = state.value.let {
        BroadcastDraft(
            subject = subject.text,
            bodyHtml = editor.html,
            statuses = BroadcastCopy.STATUSES.filter { status -> status in it.statuses },
            attendance = it.attendance,
            design = it.design,
            templateId = it.templateId,
        )
    }

    override fun hasNonEmptyDraft(): Boolean = subject.text.isNotBlank() || !editor.isEmpty

    /** True when the draft differs from the template or email it was loaded from. */
    fun isEdited(): Boolean {
        if (!hasNonEmptyDraft()) return false
        return (subject.text to editor.html) != baseline
    }

    fun blocker(): String? = sendBlocker(state.value, subject.text, editor.isEmpty, editor.html.length > EmailHtml.BODY_MAX)

    private fun onDraftChanged() {
        if (!ready || state.value.sentCount != null) return
        savedStateHandle[KEY_DRAFT] = currentDraft().encode()
        draftVersion.value++
    }

    private fun applyDraft(draft: BroadcastDraft) {
        subject = TextFieldValue(draft.subject.take(BroadcastCopy.SUBJECT_MAX), TextRange(draft.subject.length.coerceAtMost(BroadcastCopy.SUBJECT_MAX)))
        editor.setHtml(draft.bodyHtml)
        val statuses = draft.statuses.filter { it in BroadcastCopy.STATUSES }.toSet()
        state.update { it.copy(statuses = statuses, attendance = draft.attendance, design = draft.design, templateId = draft.templateId) }
        audience.value = Audience(statuses, draft.attendance)
    }

    // ------------------------------------------------------ templates & reuse

    fun pickTemplate(id: String) {
        val template = state.value.templates.firstOrNull { it.id == id } ?: return
        if (isEdited()) state.update { it.copy(pendingReplace = PendingReplace.Template(template)) } else applyTemplate(template)
    }

    /** Reuse a previous email; asks first when the current draft has any content. */
    fun reuse(row: BroadcastHistoryDto) {
        if (hasNonEmptyDraft()) state.update { it.copy(pendingReplace = PendingReplace.History(row)) } else applyHistory(row)
    }

    fun confirmReplace() {
        when (val pending = state.value.pendingReplace) {
            is PendingReplace.Template -> applyTemplate(pending.template)
            is PendingReplace.History -> applyHistory(pending.row)
            null -> Unit
        }
        state.update { it.copy(pendingReplace = null) }
    }

    fun dismissReplace() = state.update { it.copy(pendingReplace = null) }

    private fun applyTemplate(template: BroadcastTemplateDto, announce: Boolean = true) {
        subject = TextFieldValue(template.subject.take(BroadcastCopy.SUBJECT_MAX), TextRange(template.subject.length.coerceAtMost(BroadcastCopy.SUBJECT_MAX)))
        editor.setHtml(template.bodyHtml)
        val statuses = template.audience.statuses.filter { it in BroadcastCopy.STATUSES }.toSet()
        state.update {
            it.copy(
                templateId = template.id,
                statuses = statuses,
                attendance = template.audience.attendance,
                subjectError = null,
                bodyError = null,
                audienceError = null,
                message = if (announce) "Loaded the “${template.name}” template. Edit anything before sending." else it.message,
            )
        }
        audience.value = Audience(statuses, template.audience.attendance)
        if (state.value.count is RecipientCount.Failed) retryCount()
        baseline = subject.text to editor.html
        onDraftChanged()
    }

    /** Loads a sent email's subject, body, design and audience into the editor. */
    override fun applyHistory(row: BroadcastHistoryDto) {
        subject = TextFieldValue(row.subject.take(BroadcastCopy.SUBJECT_MAX), TextRange(row.subject.length.coerceAtMost(BroadcastCopy.SUBJECT_MAX)))
        row.bodyHtml?.let(editor::setHtml)
        val statuses = row.audience.statuses.filter { it in BroadcastCopy.STATUSES }.toSet()
        state.update {
            it.copy(
                templateId = null,
                statuses = statuses,
                attendance = row.audience.attendance,
                design = row.design ?: it.design,
                subjectError = null,
                bodyError = null,
                audienceError = null,
                pendingReplace = null,
                message = "Loaded the previous email into the editor",
            )
        }
        audience.value = Audience(statuses, row.audience.attendance)
        if (state.value.count is RecipientCount.Failed) retryCount()
        baseline = subject.text to editor.html
        onDraftChanged()
    }

    // ------------------------------------------------------------------ count

    /** Retry the recipient count, e.g. after a dropped connection. */
    fun retryCount() {
        recountRequests.update { it + 1 }
    }

    private suspend fun recount(target: Audience) {
        if (target.statuses.isEmpty()) {
            state.update { it.copy(count = RecipientCount.Ready(0)) }
            return
        }
        state.update { it.copy(count = RecipientCount.Counting) }
        delay(COUNT_DEBOUNCE_MS)
        val request = BroadcastCountRequest(statuses = BroadcastCopy.STATUSES.filter { it in target.statuses }, attendance = target.attendance)
        val count = when (val result = repository.count(orgSlug, eventId, request)) {
            is AppResult.Success -> RecipientCount.Ready(result.data)
            is AppResult.Failure -> RecipientCount.Failed(result.error.message)
        }
        state.update { it.copy(count = count) }
    }

    // ------------------------------------------------------------ test & send

    fun sendTest() {
        if (subject.text.isBlank() || editor.isEmpty) {
            state.update { it.copy(message = "Add a subject and a message first.") }
            return
        }
        if (state.value.testSending) return
        state.update { it.copy(testSending = true) }
        val content = currentContent()
        viewModelScope.launch {
            val result = repository.sendTest(orgSlug, eventId, content)
            state.update { it.copy(testSending = false) }
            when (result) {
                is AppResult.Success -> state.update { it.copy(message = "Test sent to ${result.data}") }
                is AppResult.Failure -> handleError(result.error)
            }
        }
    }

    fun openReview() {
        if (blocker() != null) return
        val current = state.value
        val count = current.recipientCount ?: return
        state.update { it.copy(review = ReviewState(count, BroadcastCopy.describeAudience(current.statuses, current.attendance), subject.text)) }
    }

    fun setReviewChecked(checked: Boolean) = state.update { it.copy(review = it.review?.copy(checked = checked)) }

    fun dismissReview() = state.update { it.copy(review = null) }

    /**
     * Starts the client-side undo window. Nothing reaches the server until
     * [BroadcastCopy.UNDO_SECONDS] have passed; the reviewed content is
     * snapshotted now so edits during the countdown don't change it.
     * Returns false unless the review box is ticked.
     */
    fun confirmSend(): Boolean {
        val review = state.value.review ?: return false
        if (!review.checked || state.value.sendPending) return false
        val current = state.value
        val request = SendBroadcastRequest(
            statuses = BroadcastCopy.STATUSES.filter { it in current.statuses },
            attendance = current.attendance,
            subject = subject.text,
            body = editor.html,
            design = current.design,
        )
        state.update { it.copy(review = null, countdownSeconds = BroadcastCopy.UNDO_SECONDS, pendingSendCount = review.count) }
        countdownJob = viewModelScope.launch {
            for (left in BroadcastCopy.UNDO_SECONDS downTo 1) {
                state.update { it.copy(countdownSeconds = left) }
                delay(1_000)
            }
            state.update { it.copy(countdownSeconds = null, sending = true) }
            // Once the request is made it must finish, even if the screen goes away.
            val result = withContext(NonCancellable) { repository.send(orgSlug, eventId, request) }
            state.update { it.copy(sending = false) }
            when (result) {
                is AppResult.Success -> onSent(result.data.recipientCount)
                is AppResult.Failure -> handleError(result.error)
            }
        }
        return true
    }

    fun undoSend() {
        if (cancelCountdown()) state.update { it.copy(message = "Sending cancelled. Nothing was sent.") }
    }

    /** The user left the screen (Back or the app went to the background) during the countdown. */
    fun cancelSendBecauseLeft() {
        if (cancelCountdown()) state.update { it.copy(message = "You left the screen, so sending was cancelled. Nothing was sent.") }
    }

    private fun cancelCountdown(): Boolean {
        if (state.value.countdownSeconds == null) return false
        countdownJob?.cancel()
        countdownJob = null
        state.update { it.copy(countdownSeconds = null) }
        return true
    }

    private suspend fun onSent(recipientCount: Int) {
        state.update { it.copy(sentCount = recipientCount) }
        savedStateHandle.remove<String>(KEY_DRAFT)
        draftStore.clear(orgSlug, eventId)
        fetchMeta()
    }

    /** "Write another" after a send: a fresh draft from the default template. */
    fun startAnother() {
        state.update { it.copy(sentCount = null, templateId = null) }
        baseline = null
        val template = BroadcastCopy.featuredTemplates(state.value.templates).firstOrNull()
        if (template != null) {
            applyTemplate(template, announce = false)
        } else {
            subject = TextFieldValue("")
            editor.setHtml("")
        }
    }

    private fun handleError(error: AppError) {
        when (error) {
            is AppError.RateLimited -> {
                state.update { it.copy(message = error.message) }
                startRateLimit((error.retryAfterSeconds ?: DEFAULT_RETRY_SECONDS).toInt().coerceAtLeast(1))
            }
            is AppError.InvalidRequest -> {
                val fields = error.fieldErrors.mapValues { (_, messages) -> messages.firstOrNull() }
                val subjectError = fields["subject"]
                val bodyError = fields["body"] ?: error.message.takeIf { it == BroadcastCopy.EMPTY_MESSAGE }
                val audienceError = fields["statuses"] ?: fields["attendance"]
                val mapped = subjectError != null || bodyError != null || audienceError != null
                state.update {
                    it.copy(
                        subjectError = subjectError ?: it.subjectError,
                        bodyError = bodyError ?: it.bodyError,
                        audienceError = audienceError ?: it.audienceError,
                        message = if (mapped) "Check the highlighted fields." else error.message,
                    )
                }
            }
            else -> state.update { it.copy(message = error.message) }
        }
    }

    private fun startRateLimit(seconds: Int) {
        rateLimitJob?.cancel()
        rateLimitJob = viewModelScope.launch {
            for (left in seconds downTo 1) {
                state.update { it.copy(rateLimitSeconds = left) }
                delay(1_000)
            }
            state.update { it.copy(rateLimitSeconds = 0) }
        }
    }

    fun onMessageShown() = state.update { it.copy(message = null) }

    companion object {
        const val KEY_DRAFT = "broadcast.draft"
        const val COUNT_DEBOUNCE_MS = 400L
        const val AUTOSAVE_DELAY_MS = 500L
        private const val DEFAULT_RETRY_SECONDS = 60L
    }
}
