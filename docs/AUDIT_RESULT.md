# Three-lens audit result — 2026-07-28

Lenses: model reasoning (38 findings), Context7 documentation (53 findings, 26 MCP
queries), Superpowers verification-first (28 findings). Cross-examined; where they
overlapped the same defect they are recorded once below under the strongest evidence.

## Blockers found by two or more lenses, all fixed and verified

1. **Shared controller borrowed a host's coroutine scope** (reasoning R1, superpowers F-22).
   The singleton stored whichever host called first, normally the settings screen's Compose
   scope. Destroying that host cancelled the scope, so the settings collector and the seconds
   ticker died silently for every other host. Fixed: the controller owns a supervised scope,
   starts its collector once, and no host tears down the shared instance.
   Verified: one overlay running, none after force-stop, exactly one after relaunch.

2. **DataStore corruption could not be caught** (reasoning R2, Context7 C7-19).
   `CorruptionException` is thrown upstream by `dataStore.data`, so the `runCatching` inside
   `map` could never see it: crash at launch and at boot, with no recovery. Fixed with
   `ReplaceFileCorruptionHandler` at instance creation plus `.catch` on the flow.

3. **The companion slot feature was dead in release** (reasoning R10/R11, Context7 C7-43/C7-45).
   Three independent causes: the signature permission was declared only by the companion, so
   the main app never held it; the companion had no signing config, so release builds carried a
   different certificate; and the slot APKs had no activity, so notifications could never be
   permitted on Android 13+. All three fixed.

## Also fixed

- Overlay `stop()` cleared its reference outside the `runCatching`, so a failed removal
  orphaned a window with nothing left to remove it (superpowers F-01).
- A refused `startForeground` called `stopSelf()` while initialisation continued (F-24).
- The calendar explanation was unreachable: two identical `when` conditions (F-03, R-doc).
- Cutout placement read insets before the view was attached, so it was always zero (F-29).
- Icon bitmaps were 192px ARGB_8888, 144 KB each; now 96px ALPHA_8, a 94% reduction, and the
  blank icon is cached rather than reallocated (Context7 C7-bitmap, reasoning R9).
- Slot detection made three package-manager binder calls per tick; now cached (R5).
- A disabled chained engine issued six cancel calls per settings emission; now idempotent (R6).
- `FOREGROUND_SERVICE_IMMEDIATE` was requested even when the icon was meant to be hidden.

## Findings recorded but deliberately not acted on

- **Notification promotion on Android 16** (C7-50): `core-ktx` 1.17.0 adds
  `setRequestPromotedOngoing` and `EXTRA_SHORT_CRITICAL_TEXT`, which render a status bar chip
  carrying text natively. This would replace much of what three subsystems here approximate.
  It changes the product's architecture, so it is a decision for the owner, not the audit.
- **IMPORTANCE_MIN does not hide a foreground service icon** for an ordinary app; the
  documented suppression applies to privileged or platform-signed apps. The hidden-icon design
  rests on a false premise and needs rethinking rather than patching.
- **Compose recomposition**: `AppSettings` is unstable because of its list fields, so the whole
  settings screen recomposes on every emission. Real, but a restructure rather than a fix.
- Preservation gates rejected several tempting removals, notably that `build()`'s assignment to
  `lastRendered` is load-bearing, and that `SystemBarStyle.dark` must never become `auto`.

## Verification
37 unit tests pass (25 before the audit; boundary tests added for 12-hour midnight and noon,
where a regression to `hour % 12` would previously have passed every test, plus a new suite for
the icon fallback rule). Both flavours and the companion build. Emulator sweep after the fixes:
Compact 0 overlays / 1 notification, Full text and Both 1 overlay / 2 notifications, back to
Compact 0 / 1, zero crashes.
