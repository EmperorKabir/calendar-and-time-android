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
- Icon bitmaps were 192px ARGB_8888 at 144 KB each; now 96px **ARGB_8888**, a **75%**
  reduction, and the blank icon is cached rather than reallocated. CORRECTION: an earlier
  version of this file claimed ALPHA_8 and 94%. ALPHA_8 was tried and reverted because it
  carries no colour channel and rendered the icon as a solid black block. Do not reapply it.
- Slot detection made three package-manager binder calls per tick; now cached (R5).
- A disabled chained engine issued six cancel calls per settings emission; now idempotent (R6).
- `FOREGROUND_SERVICE_IMMEDIATE` was requested even when the icon was meant to be hidden.

## Acted on after the audit, on the owner's instruction

- **Notification promotion on Android 16 (C7-50) — IMPLEMENTED.** Verified against current
  documentation: a promoted ongoing notification is rendered as a status bar chip carrying
  `shortCriticalText`. Requirements confirmed and all met: `POST_PROMOTED_NOTIFICATIONS`
  declared, `setRequestPromotedOngoing(true)`, ongoing flag, a content title, standard style,
  and a channel above IMPORTANCE_MIN. `androidx.core` moved 1.16.0 to 1.17.0; 1.19.0 was
  rejected because it demands compileSdk 37 and AGP 9.1. Compact now uses the chip
  automatically where the platform supports it, and the interface explains this.
  **Status on the emulator:** the extras are present on the posted notification
  (`android.shortCriticalText` confirmed via dumpsys on an API 36 image) but this AOSP
  SystemUI does not draw chips, so rendering is unverified and needs a real Android 16 device.

## Findings recorded but deliberately not acted on
- **IMPORTANCE_MIN does not hide a foreground service icon** for an ordinary app; the
  documented suppression applies to privileged or platform-signed apps. The hidden-icon design
  rests on a false premise and needs rethinking rather than patching.
- **Compose recomposition**: `AppSettings` is unstable because of its list fields, so the whole
  settings screen recomposes on every emission. Real, but a restructure rather than a fix.
- Preservation gates rejected several tempting removals, notably that `build()`'s assignment to
  `lastRendered` is load-bearing, and that `SystemBarStyle.dark` must never become `auto`.

## Large screens and foldables
The settings screen now caps its content at 720dp on displays 600dp and wider, so an
unfolded foldable or tablet does not stretch lines to an unreadable width. CORRECTION: an
earlier version of this file said the content is centred. It is not; it is width capped and
left aligned. No androidx.window dependency is present, so true hinge aware layout is not
implemented. The
activity declares `resizeableActivity` and handles size, layout, orientation, density and
uiMode changes itself rather than being recreated, which previously risked tearing down the
overlay on every fold or rotation. Verified by rotating the emulator to landscape and back:
no crash, notification intact, no duplicate windows.

## Play readiness
`docs/store/PRIVACY_POLICY.md` and `docs/store/LISTING.md` now carry the privacy policy, the
listing copy, the data safety declaration (nothing collected, nothing shared, no network
permission) and the written justification for the specialUse foreground service type.

## Verification
37 unit tests pass (25 before the audit; boundary tests added for 12-hour midnight and noon,
where a regression to `hour % 12` would previously have passed every test, plus a new suite for
the icon fallback rule). Both flavours and the companion build. Emulator sweep after the fixes:
Compact 0 overlays / 1 notification, Full text and Both 1 overlay / 2 notifications, back to
Compact 0 / 1, zero crashes.

---

# Second audit — 2026-07-31

Three lenses re-run over the entire project. The first pass's own fixes were treated as
suspect, which proved correct: one of them had created a critical regression.

## Critical

**A fix from the first audit made slot installs fail.** Declaring the `SLOT_CONTROL`
permission in *both* manifests means the second package to install hits
`INSTALL_FAILED_DUPLICATE_PERMISSION` once the certificates differ, which they do under
Play App Signing. Only the main app now defines it; the companions merely use it.

## High

- **The Android 16 chip was shipping broken.** The platform documents a maximum of **7**
  characters for the chip's short text and drops the text entirely when less than half
  fits. The code used 24, so most formats produced an icon-only chip while the interface
  promised words. The limit is now 7, promotion is requested only when the text actually
  fits, and `canPostPromotedNotifications()` is consulted instead of trusting `SDK_INT`.
- **The overlay had a self-sustaining layout loop.** `refreshPlacement()` was called from
  inside `onApplyWindowInsets`, and `updateViewLayout` triggers another insets pass. On a
  window that lives all day this never settles. It now re-places exactly once, posted
  off the callback.
- **The overlay could be permanently invisible.** The fullscreen branch set `View.GONE`
  with no `else`, so turning the setting off while hidden left it hidden forever.
- **The foreground service posted the wrong icon at every launch.** The notification was
  built before settings arrived, using a visible icon and the default format even in
  text-only modes. The icon now starts blank until real settings exist.
- **Rejected system-clock writes reported success.** `Settings.*.put*` returns a boolean
  that was discarded, so a refused write latched the switch on. The result is now returned.
- **The default preset broke slot splitting.** Splitting recognised punctuation but not a
  plain space, which is the separator of the app's own default format, so one slot was
  crammed and the rest blank. Whitespace is now a fallback.
- **`BootReceiver` had `try`/`finally` with no `catch`** on an unsupervised scope, so a
  failure during boot could crash there. It now catches.
- **The accessibility service was `exported="false"`**, which would prevent the sideload
  build's no-notification mode from being enabled at all.
- **`WRITE_SECURE_SETTINGS` moved to the sideload flavour.** Play will not grant it, so the
  Play build no longer requests it and its card explains that the feature needs the
  sideload version rather than showing a control that cannot work.
- **Runtime receiver registration** now states its export intent, as Android 14 requires.
- **A slider mid-drag could be reset** by an unrelated settings emission.

## Removed
`ChainedIconEngine` (115 lines) is deleted. It was proven not to work on Android 14+, was
hardcoded unreachable, and caused both remaining lint errors. The companion slots supersede
it. A one-time cleanup of its old notification range is preserved for upgrading installs.

## Documentation corrections
Three claims in this repository's own documents were false and are corrected in place: the
icon bitmap config and its saving, "centred" large-screen content, foldable support and Play
readiness marked done, and the preview showing an enlarged icon. These files are written at
commit time and had never been re-checked when later commits reversed the change.

## Verification
`lintPlayRelease` now passes; it previously reported 2 errors and was only missed because
`assembleRelease` runs the fatal subset. Tests went 37 to 43, including replacing a vacuous
assertion that was true by construction for every input, and adding the first coverage of the
saved-preset codecs. Emulator sweep: Compact 0 overlays / 1 notification, Full text and Both
1 overlay / 2, back to Compact 0 / 1, zero crashes.

## Third lens (reasoning) — findings applied 2026-07-31

Four criticals were still open in the committed code, and four defects were introduced by
the previous commit. All eight are now fixed.

**Still open, now fixed**
- **The chip showed a bare number.** It was fed `stackBottom`, which is the day of month
  alone, so the promise of readable text produced "30". It now sends the stacked pair,
  "FRI 31", six characters against the seven character budget.
- **The preset codecs demanded an exact field count.** Adding one property to `FormatSpec`
  would have silently dropped every saved preset, and the next save would have overwritten
  the originals on disk. Decoding now tolerates extra fields and refuses only when required
  ones are missing.
- **A single transient IO error froze settings for the process lifetime.** `catch` is
  terminal, and the controller's `collecting` flag was never reset, so every later toggle
  wrote to disk and changed nothing. The flow now retries IO failures before degrading, and
  the flag clears on completion so the collector can restart.
- **Switching the display off left a 1 Hz ticker and a minute-cadence receiver running**
  for the rest of the process lifetime. The disable path now stops the tick source first.

**Introduced by the previous commit, now fixed**
- Deleting the withdrawn engine removed its notification channel cleanup, undoing a fix for
  orphans the owner had actually seen. The channel is now deleted alongside the IDs.
- The overlay placement latch never cleared, so a fold was never honoured on hosts with no
  service to deliver a configuration change.
- The Play flavour lost the OEM icon caveat, which is the most useful guidance on devices
  that substitute the app logo, and Play is the build most people install.

**Measured**
Release APK 1,268,769 to 1,176,437 bytes, a 92 KB reduction, by shipping only the English
resources that actually exist rather than 87 merged locale tables. Lint passes. 43 tests
pass. Emulator sweep: Compact 0 overlays / 1 notification, Full text and Both 1 / 2, back to
Compact 0 / 1, disabled 0 / 0, zero crashes. The capability check is doing its job: on this
AOSP image `canPostPromotedNotifications()` reports false, so no promotion is requested, the
advertising card is hidden, and Compact is described as an icon rather than a chip.
