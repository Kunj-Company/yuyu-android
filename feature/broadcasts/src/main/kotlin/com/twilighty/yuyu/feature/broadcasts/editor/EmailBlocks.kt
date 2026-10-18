package com.twilighty.yuyu.feature.broadcasts.editor

import androidx.compose.runtime.Immutable

/** The kinds of text block the email editor supports (DESIGN: the allowed broadcast subset). */
enum class BlockKind { Paragraph, Heading2, Heading3, Bullet, Numbered, Quote;

    val isHeading: Boolean get() = this == Heading2 || this == Heading3
    val isListItem: Boolean get() = this == Bullet || this == Numbered
}

/** One block of the email body. */
@Immutable
sealed interface EmailBlock

/** Paragraph, heading, list item or quote, with inline bold/italic/link. */
@Immutable
data class TextBlock(val kind: BlockKind, val text: RichText) : EmailBlock

/** A call-to-action button: `<p><a class="cta" href="…">label</a></p>`. */
@Immutable
data class ButtonBlock(val label: String, val href: String) : EmailBlock

/** The plain text of the body, one block per line; used for emptiness checks and previews. */
fun List<EmailBlock>.plainText(): String = joinToString("\n") { block ->
    when (block) {
        is TextBlock -> block.text.text
        is ButtonBlock -> block.label
    }
}

/** True when the body has no visible text (formatting only), the server's "The message is empty." case. */
fun List<EmailBlock>.isEmptyBody(): Boolean = plainText().isBlank()
