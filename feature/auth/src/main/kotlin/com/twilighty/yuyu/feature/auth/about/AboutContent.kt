package com.twilighty.yuyu.feature.auth.about

/** One `## [version] - date` block of CHANGELOG.md. */
data class ChangelogRelease(
    val version: String,
    val date: String?,
    val intro: List<String>,
    val sections: List<ChangelogSection>,
) {
    val isUnreleased: Boolean get() = version.equals("Unreleased", ignoreCase = true)
}

/** A `### Added` / `### Fixed` group of bullets. */
data class ChangelogSection(val title: String, val items: List<String>)

/** A `- Name — what they did` line of CONTRIBUTORS.md. */
data class Contributor(val name: String, val role: String?)

private val LINK = Regex("""\[([^\]]+)]\([^)]*\)""")
private val RELEASE = Regex("""^##\s+\[?([^\]\s]+)]?\s*(?:-\s*(\S+))?\s*$""")

/** Markdown links become their text; the app shows plain text. */
private fun plain(text: String) = text.replace(LINK, "$1").replace("`", "").trim()

/**
 * Reads the Keep-a-Changelog layout used by the repo's CHANGELOG.md: releases
 * under `##`, groups under `###`, `-` bullets, and plain lines as the release's
 * intro. Text before the first release (the file's own header) is skipped.
 */
fun parseChangelog(markdown: String): List<ChangelogRelease> {
    val releases = mutableListOf<ChangelogRelease>()
    var version: String? = null
    var date: String? = null
    val intro = mutableListOf<String>()
    val sections = mutableListOf<ChangelogSection>()
    var sectionTitle: String? = null
    val items = mutableListOf<String>()

    fun closeSection() {
        if (sectionTitle != null && items.isNotEmpty()) sections += ChangelogSection(sectionTitle!!, items.toList())
        sectionTitle = null
        items.clear()
    }

    fun closeRelease() {
        closeSection()
        version?.let { releases += ChangelogRelease(it, date, intro.toList(), sections.toList()) }
        intro.clear()
        sections.clear()
    }

    for (raw in markdown.lineSequence()) {
        val line = raw.trim()
        val release = RELEASE.find(line)
        when {
            line.startsWith("## ") && release != null -> {
                closeRelease()
                version = release.groupValues[1]
                date = release.groupValues[2].ifBlank { null }
            }
            version == null -> Unit
            line.startsWith("### ") -> {
                closeSection()
                sectionTitle = plain(line.removePrefix("###"))
            }
            line.startsWith("- ") || line.startsWith("* ") -> {
                val text = plain(line.drop(2))
                if (sectionTitle == null) sectionTitle = "Changes"
                if (text.isNotEmpty()) items += text
            }
            line.isNotEmpty() && sectionTitle == null -> intro += plain(line)
        }
    }
    closeRelease()
    return releases
}

/** `- Name — role` (an en dash, em dash or hyphen separates the role). */
fun parseContributors(markdown: String): List<Contributor> = markdown.lineSequence()
    .map { it.trim() }
    .filter { it.startsWith("- ") }
    .mapNotNull { line ->
        val text = plain(line.drop(2))
        val parts = text.split(Regex("""\s+[—–-]\s+"""), limit = 2)
        parts.first().trim().takeIf { it.isNotEmpty() }?.let { Contributor(it, parts.getOrNull(1)?.trim()) }
    }
    .toList()
