package com.twilighty.yuyu.core.designsystem

import com.twilighty.yuyu.core.designsystem.accent.AccentMath
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Mirrors the web's tests/unit/accent-tokens.test.ts so both platforms derive the same colours. */
class AccentMathTest {

    @Test
    fun normalisesHexAndRejectsInjection() {
        assertEquals("#C2410C", AccentMath.normalizeHex("#c2410c"))
        assertEquals("#AABBCC", AccentMath.normalizeHex(" #abc "))
        assertEquals(AccentMath.DEFAULT_EVENT_ACCENT, AccentMath.normalizeHex("red"))
        assertEquals(AccentMath.DEFAULT_EVENT_ACCENT, AccentMath.normalizeHex("#C2410C; background: url(x)"))
        assertEquals(AccentMath.DEFAULT_EVENT_ACCENT, AccentMath.normalizeHex(null))
        assertEquals("#18181B", AccentMath.normalizeHex(null, "#18181B"))
    }

    @Test
    fun contrastRatioMatchesWcagReferenceValues() {
        assertEquals(21.0, AccentMath.contrastRatio("#000000", "#FFFFFF"), 0.01)
        assertEquals(1.0, AccentMath.contrastRatio("#777777", "#777777"), 0.0001)
        // White on success solid is 5.0:1 per DESIGN §2.5.
        assertEquals(5.0, AccentMath.contrastRatio("#FFFFFF", "#15803D"), 0.05)
        // Order doesn't matter.
        assertEquals(AccentMath.contrastRatio("#C2410C", "#FFFFFF"), AccentMath.contrastRatio("#FFFFFF", "#C2410C"), 1e-9)
    }

    @Test
    fun matchesTheDesignEmberValues() {
        assertEquals(
            AccentMath.Tokens(accent = "#C2410C", accentSoft = "#FAF0EC", onAccent = "#FFFFFF", accentText = "#C2410C"),
            AccentMath.tokens("#C2410C", dark = false),
        )
        assertEquals("#2A1812", AccentMath.tokens("#C2410C", dark = true).accentSoft)
    }

    @Test
    fun presetSwatchesProduceReadablePairs() {
        val expectedOnAccent = mapOf("Ember" to "#FFFFFF", "Iris" to "#FFFFFF", "Teal" to "#FFFFFF", "Ink" to "#FFFFFF")
        AccentMath.PRESETS.forEach { preset ->
            val light = AccentMath.tokens(preset.hex, dark = false)
            assertEquals(preset.name, expectedOnAccent[preset.name], light.onAccent)
            assertTrue(preset.name, AccentMath.contrastRatio(light.onAccent, preset.hex) >= 4.5)
            listOf(false, true).forEach { dark ->
                val t = AccentMath.tokens(preset.hex, dark)
                val canvas = if (dark) AccentMath.DARK_CANVAS else AccentMath.LIGHT_CANVAS
                assertTrue("${preset.name} text on canvas", AccentMath.contrastRatio(t.accentText, canvas) >= 4.5)
                assertTrue("${preset.name} text on soft", AccentMath.contrastRatio(t.accentText, t.accentSoft) >= 4.5)
            }
        }
        // Dark accent-text is always lighter than the accent itself.
        AccentMath.PRESETS.forEach { preset ->
            assertTrue(AccentMath.luminance(AccentMath.tokens(preset.hex, dark = true).accentText) > AccentMath.luminance(preset.hex))
        }
    }

    @Test
    fun everyDerivedPairingStaysReadable() {
        listOf("#C2410C", "#4B3FA8", "#0F766E", "#18181B", "#FDE047", "#A7F3D0", "#FFFFFF", "#000000", "#EC4899").forEach { accent ->
            listOf(false, true).forEach { dark ->
                val t = AccentMath.tokens(accent, dark)
                val canvas = if (dark) AccentMath.DARK_CANVAS else AccentMath.LIGHT_CANVAS
                val onAccentMin = if (!dark && AccentMath.contrastRatio(accent, "#FFFFFF") < 4.5) 1.0 else 4.5
                assertTrue("$accent on-accent dark=$dark", AccentMath.contrastRatio(t.onAccent, t.accent) >= onAccentMin)
                assertTrue("$accent text/canvas dark=$dark", AccentMath.contrastRatio(t.accentText, canvas) >= 4.5)
                assertTrue("$accent text/soft dark=$dark", AccentMath.contrastRatio(t.accentText, t.accentSoft) >= 4.5)
            }
        }
    }

    @Test
    fun neverPutsWhiteTextOnALightAccent() {
        assertEquals("#18181B", AccentMath.tokens("#FDE047", dark = false).onAccent)
    }

    @Test
    fun mixIsSrgbWeighted() {
        assertEquals("#808080", AccentMath.mix("#FFFFFF", "#000000", 0.5025))
        assertEquals("#FFFFFF", AccentMath.mix("#FFFFFF", "#000000", 1.0))
    }
}
