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

## Added by an independent second assessment (different regions and metrics)

### V11. The compact icon never rendered once in the entire recording
**Method:** measured ink in the notification-icon zone (x 90-300 of 1440) for all 806 samples.
**Result:** median ink 0, maximum 25 (noise level). Even during the 2.9-5.1 s window when
Compact was the selected mode, no icon was drawn. So on this device the Compact mode produced
nothing at all for the whole 80 seconds, and the bar was simply empty (see V3).
**Significance:** this is the ColorOS logo-substitution path failing outright rather than
substituting, which makes Compact useless on this device instead of merely degraded.

### V12. Overlay contrast collapses in places
**Method:** measured the light-to-dark spread within the overlay strip every 2 s.
**Result:** median spread 108 grey levels, but it falls to 30-33 at 20 s, 22 s and 24 s, and
43-45 at 0 s, 2 s and 6 s. Below roughly 60 the text stops being comfortably legible.
**Significance:** the text is hard to read for sustained periods, not only for a frame or two.
The bold weight and shadow added after this recording address part of it, but placement over
bright content still needs an adaptive treatment.

### V13. Two sustained screen-change spikes align with mode switches
**Method:** frame-to-frame delta over the whole screen at 10 fps.
**Result:** two large spikes, 15.4 at 2.7 s and 15.2 at 5.0 s, bracketing the Compact window.
These are the interface redrawing wholesale on a mode change rather than updating in place,
consistent with the overlay being destroyed and recreated (V4).

### V14. Using the overlay adds a system notification of its own
**Observed on the emulator during replication:** enabling a text mode produces a second entry,
`com.android.server.wm.AlertWindowNotification`, posted by Android itself to say the app is
displaying over other apps. It cannot be suppressed while the overlay is in use, so the text
modes can never be truly notification free. Only Compact and the system clock route can.

---

## FIXES APPLIED AND VERIFIED 2026-07-28 (emulator replication of the video sequence)

Cycling Compact -> Full text -> Both -> Compact -> Full text with state checked at each step:

| Mode | Overlay windows | Our notifications |
|------|-----------------|-------------------|
| Compact | 0 | 1 |
| Full text | 1 | 2 (ours + the Android alert-window notice) |
| Both | 1 | 2 |
| Compact | 0 | 1 |
| Full text | 1 | 2 |

- **V1, V2 fixed:** exactly one overlay window in every text mode and none in Compact, with no
  accumulation across five switches. Cause removed by making the controller a single shared
  instance so only one engine, and therefore one window, can exist.
- **V4, V13 fixed:** the replacement is now posted before the previous one is removed, so the
  bar is never empty during a mode change.
- **V5, V6, V10 fixed:** the overlay start x is clamped between a reserve that clears the OEM
  clock and the screen edge, its width is bounded by the space remaining, and the Horizontal
  slider range is derived from the display width instead of a fixed 1400.
- **V7 fixed:** icon-specific guidance and the seconds caveat only appear when Compact is in use.
- **V8 fixed:** the preview shows the icon at true size and enlarged beside it.

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
