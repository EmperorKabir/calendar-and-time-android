# Missed and skipped work

An honest record of everything asked for across this project that I did not deliver,
delivered partially, or claimed as done without proper verification. Written at the
user's request after repeated quality failures.

## A. Asked for and never built
1. ~~**Quick presets as a dropdown.**~~ DONE 2026-07-28: "Built in presets" and
   "Your saved presets" dropdowns replace the chip row.
2. **Named preset entry (save, apply, delete).** Code exists in `MainActivity` and
   `SettingsRepository`, but the user reports it is not visible on screen and I never
   confirmed it renders. Treat as broken until proven otherwise.
3. ~~**Pinned header.**~~ DONE 2026-07-28: preview moved into the Scaffold topBar and
   verified staying in view while the settings scroll.
4. **Advanced section collapse** (UX audit finding 3). System clock integration, extra
   icon slots and boot behaviour still sit inline in the main flow.
5. ~~**Seconds control disables itself in Compact mode**~~ DONE 2026-07-28: switch is
   disabled with the reason shown; enabled and described plainly in Full text and Both.
6. **Outcome based naming in preview headers** (finding 8). Partially done at the mode
   choices, not in the preview.
7. **Icon cap workaround.** Companion slots are entitled to icon space but Android pushes
   them into the overflow dot. No solution attempted.
8. **Foldable support.** PROJECT_RULES rule 1 requires fold and unfold postures, hinge
   aware layouts and tabletop mode. Never addressed at all.
9. **App icon design.** Still the placeholder vector from the scaffold.
10. **Play Store readiness.** No signing config, no store listing assets, no privacy
    policy, despite the stated goal of public distribution.

## B. Claimed or implied done without verification
11. **Full UK preset.** Added to code; never applied and visually checked.
12. **Superscript ordinals.** Unit tested only; never seen rendered on a device.
13. **Overlay calibration sliders.** Never exercised; overlay position never visually tuned.
14. **Companion slots side by side.** Notifications from three packages proven, but the
    icons were never seen rendering together in the bar.
15. **Text option and Both modes.** Never tested end to end, because the draw over apps
    permission path was not completed on the emulator.
16. **Every dropdown in Custom format.** Element order, joiner, day of week, month, year,
    date order, date separator, hours, AM/PM: none individually verified after the
    dropdown rework.
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
27. **Overlay is unsuitable as the primary long-format path.** Punch hole cameras, notches,
    curved edges and foldable hinges all move or occupy the status bar strip, and the
    overlay cannot know about them reliably. The companion slot apps must become the
    supported way to fit longer text, with the overlay demoted to a last resort.
28. **Companion slot work is unfinished.** Built and proven to post from separate packages,
    but blocked by the visible icon cap pushing extra icons into the overflow dot, and
    there is no in app way to install or manage the companions.
29. **Cutout and foldable awareness.** Overlay placement must read WindowInsets
    (display cutout, status bar bounds) rather than a bare resource height, and must
    re-place itself on fold, unfold and rotation.

## Suggested order of work
1. Preset dropdown and named preset entry (A1, A2) — visible, requested, small.
2. Pinned header (A3) and advanced collapse (A4).
3. Systematic verification pass over every control (B16, B18, B15).
4. Diagnostic logger, then efficiency audit, then battery measurement (C19, C20, C21).
5. Foldable support and store readiness (A8, A10).
