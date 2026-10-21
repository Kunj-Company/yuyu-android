# Motion

Motion in Yuyu should explain what happened: where a screen came from, what
changed, and what you can tap. Keep it quick and calm. Every helper respects
**Remove animations** (Settings › Accessibility) through `LocalReducedMotion`,
which `YuyuTheme` provides; under reduced motion, animations become instant
or short cross-fades.

Tokens live in `YuyuMotion` (`core/designsystem/theme/Tokens.kt`): PRESS 120,
HOVER 180, PANEL 240 and SUCCESS 360 ms, with the standard easing. Helpers are in
`core/designsystem/motion/Motion.kt`.

| Need | Use |
| --- | --- |
| Screen push/pop, tab switch | Automatic: `YuyuNavTransitions` on both NavHosts (shared-axis slide for depth, fade-through for tabs, predictive back) |
| Loading → content → error swap | `YuyuStateTransition(state) { … }` (keys on the state's class, so refreshes don't re-animate) |
| List items appearing | `val entrance = rememberEntranceState()` per list, then `Modifier.staggeredEntrance(entrance, key, index)` |
| One-off section appearing | `Modifier.appearOnEnter(delayMillis)` |
| Press feedback on custom tappables | `Modifier.pressScale(interactionSource)` (buttons and clickable cards already have it) |
| Changing numbers (counts, totals) | `AnimatedCounterText` (StatTile already uses it) |
| Expand/collapse | `AnimatedVisibility` or `Modifier.animateContentSize(YuyuSpecs.standard())` |
| Specs | `YuyuSpecs.standard()`, `.quick()`, `.settle()` (spring), `.pop()` (bouncy spring, for success moments) |

Built into components: button press spring and smooth loading-label resize,
clickable-card press, sliding segmented-control pill, filter-chip colour and
check animation, animated progress fill, rolling stat counters, field error
fade, and empty/error state entrance.

Rules: no motion that blocks input or delays content by more than about 100 ms;
no looping animation except loaders and live indicators; lists animate only the
first time an item appears; use `pop()` for real successes only (check-in
admitted, registration confirmed), never for routine taps.
