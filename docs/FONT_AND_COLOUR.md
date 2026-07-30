# Fonts and colours investigation — 2026-07-31

## The raised ordinal endings

They are real Unicode characters, not styling, so they only appear if the active font
contains them. The six in use:

| Character | Codepoint | Unicode block | Typical coverage |
|---|---|---|---|
| ˢ | U+02E2 | Spacing Modifier Letters | wide |
| ʳ | U+02B3 | Spacing Modifier Letters | wide |
| ʰ | U+02B0 | Spacing Modifier Letters | wide |
| ⁿ | U+207F | Superscripts and Subscripts | wide |
| **ᵈ** | **U+1D48** | **Phonetic Extensions** | **often missing** |
| **ᵗ** | **U+1D57** | **Phonetic Extensions** | **often missing** |

`ᵗ` is used by every "th" ending, which is the majority of dates, so a font without it
would have shown empty boxes on most days of the month. Manufacturer faces such as
OnePlus Sans, One UI Sans and MiSans are exactly the kind that omit Phonetic Extensions.

**What was done:** `GlyphProbe` measures coverage with `Paint.hasGlyph` against every
typeface the app actually draws with — the default face for the interface and the bold
face used by both the status bar icon and the overlay. The result is recorded in
`SuperscriptSupport`, and `FormatEngine` silently uses plain "1st" when any character is
missing. The format engine stays pure Kotlin, so the fallback is unit tested.

**Surfaces covered:** all three render paths share `FormatEngine`, so the interface
description, the overlay text and the icon bitmap all fall back together. The icon matters
most: it is drawn with `Canvas.drawText`, so a missing glyph would have been baked into the
bitmap as a permanent box.

**Verified:** on the emulator the probe finds full coverage and the date renders as
"31ˢᵗ July 2026". Five unit tests cover both branches, including that the whole date, not
just the suffix, falls back.

## The overlay against different fonts

The overlay forces the bold system face and sizes itself in scale independent pixels, so it
follows the user's font size setting. Its width is bounded by the space remaining after the
start position and it truncates with an ellipsis rather than overflowing, which is what
protects it when a wide manufacturer face or a large font scale makes the same text longer.

## Colours

The overlay previously assumed a light on dark bar: white text with a dark shadow, with no
way to change it. That is the same wrong assumption that produced the black status bar icon
— a status bar is white on many phones, black on others, and changes with the wallpaper.

**What was done:** a Text colour control offering White, Black, Grey, Amber and Sky blue,
stored per position preset so a saved position carries its colour. The contrast shadow is
kept, so light text stays readable on a light bar and dark text on a dark one.

**Verified:** selecting Black renders black text inside the bar on the emulator's light bar.

## Two defects found and fixed during this work

1. **The overlay was placed below the status bar.** Window inspection showed it at y=126 on
   a bar roughly 60px tall, so it drew over app content instead of the bar. Insets on a
   `LAYOUT_NO_LIMITS` window report a larger region than the bar itself. The framework
   `status_bar_height` resource is now the authority, insets are a clamped fallback, and the
   vertical nudge can no longer push the text out of the bar.
2. **A layout loop was reintroduced.** `refreshPlacement()` cleared the same latch the
   insets callback sets, so the two triggered each other endlessly. Clearing now happens
   only on a real posture change, through `allowReplacement()`, called from the service's
   configuration change handler.
