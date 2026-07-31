# Launcher icon — 2026-07-31

## Why the old one was cut off
An adaptive icon is a 108dp canvas, but each launcher applies its own mask: square,
rounded square, circle, squircle or teardrop. Only the centre **66dp circle**, a radius of
33 from the centre point, is guaranteed to survive every mask.

The previous artwork was measured against that limit and failed:

| Element | Worst corner from centre | Result |
|---|---|---|
| Calendar body | 36.9 | clipped by circular and squircle masks |
| Binder rings | 28.2 | safe |

The body corners sat almost 4dp outside the safe circle, which is exactly what produced the
cut off appearance.

## The replacement
Everything now sits inside a 46dp square centred on the canvas, whose worst corner is
**32.5** from the centre, just inside the 33 limit, with rounded corners pulling the
extremes further in still.

| Element | Worst corner | Result |
|---|---|---|
| Page | 32.5 | safe |
| Header band | 32.5 | safe |
| Furthest day square | 21.3 | safe |

The mark itself is simpler: a page with a header band and a three by two grid of day
squares, one of them picked out in orange as a focal point. No text and no thin strokes, so
it stays legible when a launcher renders it small.

## Themed icons
A `monochrome` layer is supplied for Android 13 and newer, where the system tints a
silhouette to match the wallpaper. It cannot reuse the colour artwork, because a solid page
would tint to a featureless block, so the page is drawn as an outline with the grid cut
into it.

## Verified
Rendered on the emulator under a circular mask with clear margin on every side and no
clipping. Both `ic_launcher` and `ic_launcher_round` reference the same three layers, so
every mask shape draws from identical artwork.
