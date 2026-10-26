package com.twilighty.yuyu.feature.auth

import com.twilighty.yuyu.feature.auth.about.ChangelogSection
import com.twilighty.yuyu.feature.auth.about.Contributor
import com.twilighty.yuyu.feature.auth.about.parseChangelog
import com.twilighty.yuyu.feature.auth.about.parseContributors
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AboutContentTest {
    @Test
    fun changelogReleasesSectionsAndPlainText() {
        val releases = parseChangelog(
            """
            # Changelog

            Header text that is not a release.

            ## [Unreleased]

            ### Added
            - Explore tab.

            ## [0.1.0] - 2026-10-27

            The first release.

            ### Added
            - Tickets that open [offline](https://example.org).
            - `Fast mode`.

            ### Fixed
            - Camera crash.
            """.trimIndent(),
        )
        assertEquals(listOf("Unreleased", "0.1.0"), releases.map { it.version })
        assertTrue(releases[0].isUnreleased)
        assertEquals(null, releases[0].date)
        assertEquals("2026-10-27", releases[1].date)
        assertEquals(listOf("The first release."), releases[1].intro)
        assertEquals(
            listOf(ChangelogSection("Added", listOf("Tickets that open offline.", "Fast mode.")), ChangelogSection("Fixed", listOf("Camera crash."))),
            releases[1].sections,
        )
    }

    @Test
    fun contributorsWithAndWithoutRoles() {
        val people = parseContributors(
            """
            # Contributors
            Intro line.
            - Abhiram N J — Creator: design and development
            - Ada Lovelace - Reviews
            - Grace Hopper
            """.trimIndent(),
        )
        assertEquals(
            listOf(Contributor("Abhiram N J", "Creator: design and development"), Contributor("Ada Lovelace", "Reviews"), Contributor("Grace Hopper", null)),
            people,
        )
    }

    /** The files the app bundles must keep parsing, so the About screen is never empty. */
    @Test
    fun repoFilesParse() {
        val root = generateSequence(File("").absoluteFile) { it.parentFile }.first { File(it, "CHANGELOG.md").exists() }
        val releases = parseChangelog(File(root, "CHANGELOG.md").readText())
        assertFalse(releases.isEmpty())
        assertTrue(releases.all { it.sections.isNotEmpty() })
        assertEquals("Abhiram N J", parseContributors(File(root, "CONTRIBUTORS.md").readText()).first().name)
    }
}
