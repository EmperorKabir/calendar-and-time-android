# Video issue log — Record_2026-07-28-04-05-23

Source: 1440x3168, 44.74 fps, 80.61 s (about 3,600 frames), CPH2841 (ColorOS).

Method, second pass: the status bar strip (1440x120, full resolution) was extracted at
10 fps giving 806 samples, and every sample measured programmatically for ink coverage,
text block count, block span and ghosting. Full-screen contact sheets at 1 fps were used
for the interface context. The first pass sampled only 81 frames at 1 fps and over-stated
two findings; the measured corrections are marked below.

---

## CRITICAL

### V1. Duplicate overlay renders (transient, not permanent — CORRECTED)
**Measured over 806 samples:** 51 frames (6.3%) show a text span more than 1.35x the median
617 px, i.e. two renders side by side. Bursts at 21.3 s, 21.8 s, 24.7 s, 26.5-26.7 s, and a
continuous 4.4 s run from 26.9 s to 31.3 s where the span reaches 958-962 px (two copies).
**Correction:** the first pass claimed permanent doubling from 32 s onward. That was wrong.
From 32 s to the end the render is stable: x = 324, span 617-648 px, ghost ratio flat at
0.58-0.61. The duplication is intermittent, concentrated in the 21-31 s window.
**Cause:** each `DisplayController` creates its own `OverlayEngine`, and `start()` only guards
`view != null` per instance. The settings screen created a second controller alongside the
service's, and a new instance always adds another `TYPE_APPLICATION_OVERLAY` window.

### V2. Stale overlay copies freeze and keep showing an old time
**Frames:** 028–031 (left copy stuck at 04:05:50 while the right ticks 51, 52, 53);
068–081 (one copy stuck at 04:06:30 while another advances to 04:06:44).
**Observed:** orphaned windows are never updated again and never removed, so the bar shows
several different times at once.
**Cause:** same as V1 — only the newest engine instance holds a reference to its view.

### V3. Compact mode shows nothing in the status bar
**Measured:** 29 blank frames in two runs — 2.9 s to 5.1 s (2.2 s) and 18.0 s to 18.5 s.
The longer run is Compact being selected: the overlay is removed and no icon replaces it.
**Observed:** selecting Compact removes the overlay but no icon appears in its place, so the
display vanishes entirely for the duration.

### V4. Overlay disappears during mode changes
**Measured:** the 18.0-18.5 s blank run coincides with a mode change; the bar carries nothing
for half a second while the old window is torn down before the new one is added.

---

## HIGH

### V5. Overlay text runs off the right edge of the screen
**Measured:** at 26.0 s the block starts at x = 763 with span 502 px, ending at 1265 px of a
1440 px display; larger Horizontal values push it past the edge entirely.
**Cause:** the window is `WRAP_CONTENT` with no width bound, so a large Horizontal offset
pushes the text past the display edge instead of clamping or truncating.

### V6. Overlay overlaps the phone's own clock and icons
**Frames:** 019–021, 023, 025 (text at x≈0, sitting on top of the system clock).
**Observed:** at Horizontal 0 the text is drawn directly over the OEM clock, making both
unreadable. Nothing prevents the user from choosing a colliding position.

### V7. Icon-specific guidance shows when Compact is not selected
**Frames:** 001–003, 007–081 (nearly the whole video).
**Observed:** the preview always shows "Too long for one row, so it is split over two rows…"
and "Seconds are left out here, because an icon cannot update every second", even when the
heading directly above reads "In the status bar (not selected)". Advice about an inactive
mode, presented as if it applies.

---

## MEDIUM

### V8. Preview icon is illegible at its rendered size
**Frames:** all. The compact icon preview renders as three tiny indistinct marks.
**Observed:** the raised suffix and stacked rows cannot be read at the preview size, so the
preview does not tell the user what they will actually get.

### V9. Overlay persists over other apps with no way to tell it is this app
**Frames:** 072–076 (home screen, Weather app, recents) — the piled-up text sits over
everything, including a full-screen weather app, with the frozen copies still visible.

### V10. Horizontal slider maximum exceeds usable width
**Measured position timeline:** x = 300, 478, 380, 300, 763, 300, then settles at 324.
The slider allows offsets that push text off-screen (see V5); its range is not derived from
the display width.

---

## CONFIRMED WORKING (no action needed)

- Mode radio buttons are mutually exclusive and switch correctly (004–006, 069).
- Separator dropdown opens and applies: Comma → Middle dot changes the output to
  "Tuesday · 28th July 2026 · 04:06:30" (066–068).
- Every Custom format control renders with its current value (064–067): Element order,
  Separator, Day of week, Day of month, Ordinal day, Raised suffix, Two digit day, Month,
  Year, Date part order, Hours, Seconds, AM/PM, Calendar-icon stack.
- Superscript ordinal renders correctly as 28ᵗʰ throughout.
- Seconds tick once per second in Full text (001–067).
- Advanced disclosure opens and closes (046–054).
- OEM detection is correct: "Your device: ColorOS / OxygenOS (OPPO, OnePlus, realme)" with
  the matching background-activity guidance (046–054).
- The adb grant command is shown in full and is correct (046–054).
- The new launcher icon appears on the home screen (072–074).
- Overlay survives leaving the app and returning (072–081).
