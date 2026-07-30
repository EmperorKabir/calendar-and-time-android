# Video issue log — Record_2026-07-28-04-05-23

Source: 81 frames at 1 fps, covering 04:05:23 to 04:06:44 on the CPH2841 (ColorOS).
Every frame examined: status bar strip for all 81, plus full-screen contact sheets.
No code was changed while compiling this log.

---

## CRITICAL

### V1. Overlay windows accumulate and never get removed
**Frames:** first doubling at 007; permanent doubling 032–067; three or more copies 068–081.
**Observed:** two, then three, overlapping copies of the date text smeared over each other
("Tuesday Tu28sd8aluly28..."), at slightly different x positions.
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
**Frames:** 004, 005 (Compact selected, status bar completely empty).
**Observed:** selecting Compact removes the overlay but no icon appears in its place, so the
display vanishes entirely for the duration.

### V4. Overlay disappears for several seconds during mode changes
**Frames:** 004–006 blank bar.
**Observed:** switching modes tears the overlay down without anything replacing it.

---

## HIGH

### V5. Overlay text runs off the right edge of the screen
**Frames:** 022, 026, 028–031 — text begins near the right edge and is cut off.
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
**Frames:** 007–012 (Horizontal 116 → 289 → 415 → 478).
**Observed:** the slider allows offsets that push the text off-screen (see V5); its range is
not derived from the display width.

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
