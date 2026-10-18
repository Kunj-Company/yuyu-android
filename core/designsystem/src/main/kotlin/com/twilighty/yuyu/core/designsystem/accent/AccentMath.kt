package com.twilighty.yuyu.core.designsystem.accent

import kotlin.math.pow
import kotlin.math.roundToInt

/**
 * Event accent tokens (docs/DESIGN.md §2.5), a direct port of the web's
 * `lib/theme/accent.ts` so the app and the site derive identical colours.
 * Pure Kotlin on `#RRGGBB` strings: unit-tested on the JVM.
 */
object AccentMath {
    const val DEFAULT_EVENT_ACCENT = "#C2410C" // Ember
    const val INK_ACCENT = "#18181B"
    const val LIGHT_CANVAS = "#FFFFFF"
    const val DARK_CANVAS = "#111113"
    private const val INK = "#18181B"
    private const val WHITE = "#FFFFFF"

    data class Preset(val name: String, val hex: String)

    val PRESETS = listOf(
        Preset("Ember", "#C2410C"),
        Preset("Iris", "#4B3FA8"),
        Preset("Teal", "#0F766E"),
        Preset("Ink", "#18181B"),
    )

    private val HEX6 = Regex("^#([0-9a-fA-F]{6})$")
    private val HEX3 = Regex("^#([0-9a-fA-F])([0-9a-fA-F])([0-9a-fA-F])$")

    /** Accepts `#RRGGBB` or `#RGB`; anything else (incl. injection attempts) becomes [fallback]. */
    fun normalizeHex(value: String?, fallback: String = DEFAULT_EVENT_ACCENT): String {
        val trimmed = value?.trim().orEmpty()
        if (HEX6.matches(trimmed)) return trimmed.uppercase()
        HEX3.matchEntire(trimmed)?.let { m ->
            val (r, g, b) = m.destructured
            return "#$r$r$g$g$b$b".uppercase()
        }
        return fallback
    }

    fun toRgb(hex: String): IntArray {
        val v = hex.removePrefix("#").toInt(16)
        return intArrayOf((v shr 16) and 255, (v shr 8) and 255, v and 255)
    }

    fun toHex(r: Double, g: Double, b: Double): String =
        "#" + listOf(r, g, b).joinToString("") { c ->
            c.coerceIn(0.0, 255.0).roundToInt().toString(16).padStart(2, '0')
        }.uppercase()

    fun luminance(hex: String): Double {
        val c = toRgb(hex).map { channel ->
            val s = channel / 255.0
            if (s <= 0.03928) s / 12.92 else ((s + 0.055) / 1.055).pow(2.4)
        }
        return 0.2126 * c[0] + 0.7152 * c[1] + 0.0722 * c[2]
    }

    /** WCAG 2.x contrast ratio, 1..21. */
    fun contrastRatio(a: String, b: String): Double {
        val la = luminance(a)
        val lb = luminance(b)
        val hi = maxOf(la, lb)
        val lo = minOf(la, lb)
        return (hi + 0.05) / (lo + 0.05)
    }

    /** sRGB colour-mix: [weight] of [a] mixed into [b]. */
    fun mix(a: String, b: String, weight: Double): String {
        val ca = toRgb(a)
        val cb = toRgb(b)
        return toHex(
            ca[0] * weight + cb[0] * (1 - weight),
            ca[1] * weight + cb[1] * (1 - weight),
            ca[2] * weight + cb[2] * (1 - weight),
        )
    }

    /** Move [color] towards [target] in 5% steps until it reaches [ratio] on every surface. */
    private fun adjustUntil(color: String, target: String, surfaces: List<String>, ratio: Double): String {
        var current = color
        for (step in 0..20) {
            if (surfaces.all { contrastRatio(current, it) >= ratio }) return current
            current = mix(target, color, minOf(1.0, (step + 1) * 0.05))
        }
        return target
    }

    data class Tokens(val accent: String, val accentSoft: String, val onAccent: String, val accentText: String)

    fun tokens(value: String?, dark: Boolean, fallback: String = DEFAULT_EVENT_ACCENT): Tokens {
        val accent = normalizeHex(value, fallback)
        val canvas = if (dark) DARK_CANVAS else LIGHT_CANVAS
        val soft = mix(accent, canvas, if (dark) 0.14 else 0.08)
        val onAccent = if (contrastRatio(accent, WHITE) >= 4.5) WHITE else INK
        val accentText = if (dark) {
            adjustUntil(mix(accent, WHITE, 0.65), WHITE, listOf(canvas, soft), 4.5)
        } else {
            adjustUntil(accent, INK, listOf(canvas, soft), 4.5)
        }
        return Tokens(accent, soft, onAccent, accentText)
    }
}
