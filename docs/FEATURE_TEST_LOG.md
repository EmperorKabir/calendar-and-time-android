# Feature test log — emulator, API 36, 2026-07-28

Every control exercised individually with its observable effect measured, not assumed.

| Feature | Method | Result |
|---|---|---|
| Show in status bar | toggled, counted windows and notifications | On: service foreground, 1 notification. Off: 0. PASS |
| Compact mode | selected, screenshot of the bar | White "THU" above a large "30" beside the clock. PASS |
| Draw over mode | selected, counted overlay windows | Exactly 1 overlay window; 0 in Compact. PASS |
| Both mode | selected | 1 overlay plus the icon. PASS |
| Mode exclusivity | cycled all three five times | Never more than one overlay; no accumulation. PASS |
| Full UK preset | applied, read the bar | "Thursday, 30th July 2026, 11:07 pm" with 12 hour and pm. PASS |
| Icon colour | screenshot at 3x zoom | White. The earlier black icon was an ALPHA_8 bitmap with no colour channel. FIXED |
| Extra icon slots toggle | toggled | true to false observed. PASS |
| Companion slot apps | installed slot1 and slot2 | Both post; two extra icons visible in the bar. PASS |
| Slot legibility | screenshot at 3x zoom | "30th July / 2026" and "Thur / sday" readable after splitting by meaning. PASS |
| System clock seconds | toggled with permission granted | secure clock_seconds became 1. PASS |
| Hide system clock | toggled | secure icon_blacklist became "clock". PASS |
| 24 hour system clock | toggled | system time_12_24 became 24. PASS |
| Open battery settings | tapped | Android Settings opened. PASS |
| Advanced disclosure | tapped Show | Section expanded. PASS |
| Overlay reset | tapped | Horizontal returned to 420, text size to 13. PASS |
| Rotation | landscape and back | No crash, notification intact, no duplicate windows. PASS |
| Process death | force stop then relaunch | 1 overlay before, 0 after, 1 again. No orphans. PASS |
| Unit tests | ./gradlew test | 37 pass. PASS |

## Defects found and fixed during this pass
1. Compact icon rendered black. ALPHA_8 has no colour channel and cannot carry white
   through IconCompat. Reverted to ARGB_8888 at 96px, which is still a 75% memory cut.
2. Slot text was illegible: the text was chopped by word count across slots. Slots are now
   divided by meaning, one element each, with the same legibility floor as the main icon.
3. A single long word in a slot rendered as the whole word above its own tail
   ("Thursday" over "sday"), because an empty remainder fell back to a character split of
   the untruncated original. Now halved properly.

## Performance investigation (freezes)
Measured with `dumpsys gfxinfo` while scrolling, then calibrated against a reference app:

| App | Janky frames | 50th percentile |
|---|---|---|
| Status Calendar | 72.2% | 53 ms |
| Android Settings (system app, same emulator) | 73.4% | 61 ms |

The emulator uses software rendering, so these figures are the environment rather than the
app: the system's own settings screen is marginally worse than ours. No further optimisation
was chased against these numbers. The changes below target the real causes of stalls on a
physical device, each of which is a genuine defect regardless of what the emulator reports:

- Sliders wrote to storage on every drag frame, forcing a file sync per frame. The value is
  now held locally while dragging and written once when the finger lifts.
- The preview refreshed once a second even for formats without seconds, recomposing the
  screen sixty times more often than the content changed. It now refreshes every twenty
  seconds unless seconds are actually being displayed.
- Roughly fifty option strings in ten lists were rebuilt on every settings emission. They
  are constant, so they are now built once.
- The settings data classes were inferred unstable by the Compose compiler because of their
  list fields, defeating skipping. They are now marked immutable.

## Added in this pass
- Reset for the overlay position and size.
- Named overlay position presets with save, apply and delete, mirroring the format presets.
- Saved presets are chosen from dropdowns rather than listed as rows: "Your saved presets"
  and "Delete a saved preset" for formats, "Apply a saved position" and "Delete a saved
  position" for the overlay. Verified: all ten preset controls present, no leftover rows.
