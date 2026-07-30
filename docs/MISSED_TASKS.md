# Missed and skipped work

An honest record of everything asked for across this project that I did not deliver,
delivered partially, or claimed as done without proper verification. Written at the
user's request after repeated quality failures.

## A. Asked for and never built
1. ~~**Quick presets as a dropdown.**~~ DONE 2026-07-28: "Built in presets" and
   "Your saved presets" dropdowns replace the chip row.
2. ~~**Named preset entry.**~~ DONE 2026-07-28: "Preset name" field and Save button render;
   saved names persist to DataStore and list in the "Your saved presets" dropdown
   (verified with two saved entries listed in the open menu).
3. ~~**Pinned header.**~~ DONE 2026-07-28: preview moved into the Scaffold topBar and
   verified staying in view while the settings scroll.
4. ~~**Advanced section collapse.**~~ DONE 2026-07-28: system clock integration, extra icon
   slots, no-notification mode and reliability now sit behind a Show/Hide disclosure.
5. ~~**Seconds control disables itself in Compact mode**~~ DONE 2026-07-28: switch is
   disabled with the reason shown; enabled and described plainly in Full text and Both.
6. ~~**Outcome based naming.**~~ DONE 2026-07-28: preview headings read "In the status bar"
   and "Over the status bar" rather than naming mechanisms.
7. **Icon cap workaround.** Companion slots are entitled to icon space but Android pushes
   them into the overflow dot. No solution attempted.
8. **Foldable and large screen support — PARTIAL, not done.** Content width is capped at
   720dp at 600dp and above (left aligned, not centred as previously claimed); the activity handles configuration changes itself instead
   of being recreated. Rotation verified on the emulator with no crash and no window churn.
   Hinge aware two pane layout is not implemented; the screen is a single scrolling column by
   design, so a hinge split would add no information.
9. ~~**App icon design.**~~ DONE 2026-07-28: adaptive icon redrawn as a calendar with a
   legible day numeral, with a monochrome layer for themed icons.
10. **Play Store readiness — PARTIAL.** `lintPlayRelease` still reports 2 errors, so this
    was wrongly marked done. release signing reads an untracked
    `keystore.properties`; privacy policy, listing copy, data safety declaration and the
    specialUse justification are written in `docs/store/`. Screenshots and the feature
    graphic still need to be captured from a real device before submission.

## B. Claimed or implied done without verification
11. ~~**Full UK preset.**~~ DONE 2026-07-28: rendered live in the status bar as
    "Tuesday, 28th July 2026, 03:11".
12. ~~**Superscript ordinals.**~~ DONE 2026-07-28: the raised "th" is visible in the
    status bar overlay on the emulator.
13. **Overlay calibration sliders.** Never exercised; overlay position never visually tuned.
14. **Companion slots side by side.** Notifications from three packages proven, but the
    icons were never seen rendering together in the bar.
15. ~~**Text and Both modes.**~~ DONE 2026-07-28: both switch correctly, add the overlay
    window, and Compact removes it. Overlay text confirmed legible and correctly placed.
16. ~~**Custom format controls present.**~~ VERIFIED RENDERING 2026-07-28: Element order,
    Separator between parts, Day of week, Month, Year, Date part order, Hours, AM/PM,
    Seconds and Calendar-icon stack all enumerated on screen with their current values.
17. **Start after reboot.** Receiver registration confirmed; an actual reboot test of this
    app was never run.
18. **Extra icon slots toggle.** Never tested.

## C. Blocked externally, still outstanding
19. **`/android-diagnostic-logger`.** User invoke only; never run. Required by
    PROJECT_RULES rule 8 throughout development.
20. **`/android-efficiency-audit`.** User invoke only; never run. Required by rule 7 for
    final audit.
21. **Measured battery evidence.** No `dumpsys batterystats` run over an idle period. The
    efficiency claims in README rest on design reasoning, not measurement.
22. **Physical device verification.** Tier 3 system clock toggles, accessibility zero
    notification mode, and boot behaviour were only ever proven on the emulator or by
    manual settings writes, not through the app on the phone.

## E. Fixed 2026-07-28
30. Blank icon reserved a status bar slot when Compact was off. Fixed with a
    minimum-importance channel so the slot is released; verified icons sit flush.
31. Overlay text sat too high. Fixed by centring within the measured status bar height.
32. Overlay text was invisible on light status bars. Fixed with a bold weight and a
    contrast shadow; verified legible on the emulator's light bar.

## D. Process failures that caused rework
23. Reported toggles as broken when my own adb taps were landing on empty space. Happened
    more than once; wasted the user's time and eroded trust in every other claim.
24. Left the chained icon mode enabled in test settings, producing six orphaned
    notifications that the user then saw as a bug in the product.
25. Tested engines in isolation rather than as user flows, so the foreground service icon
    ignoring its own toggle went unnoticed until the user found it.
26. Shipped a preview that showed the configuration rather than what each engine actually
    renders, until corrected.

## Added 2026-07-28 (user findings)
27. **Overlay demotion still pending.** Placement is now insets and cutout aware, but the
    companion slots must still become the supported long-format path.
28. **Companion slot work is unfinished.** Built and proven to post from separate packages,
    but blocked by the visible icon cap pushing extra icons into the overflow dot, and
    there is no in app way to install or manage the companions.
29. ~~**Cutout and foldable awareness.**~~ DONE 2026-07-28: placement reads live
    WindowInsets status bar height, avoids displayCutout bounding rects, and re-places on
    configuration change (fold, unfold, rotate). Rotation verified on the emulator.

## Suggested order of work
1. Preset dropdown and named preset entry (A1, A2) — visible, requested, small.
2. Pinned header (A3) and advanced collapse (A4).
3. Systematic verification pass over every control (B16, B18, B15).
4. Diagnostic logger, then efficiency audit, then battery measurement (C19, C20, C21).
5. Foldable support and store readiness (A8, A10).
