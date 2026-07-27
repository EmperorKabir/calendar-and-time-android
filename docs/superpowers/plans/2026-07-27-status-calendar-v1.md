# Status Calendar v1 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Configurable calendar/date/time display in the Android status bar via three engines (notification icon, overlay, system tweaks), full format catalogue, OEM adaptation, and a zero-notification path in the `full` flavour.

**Architecture:** A pure-Kotlin format core (spec → rendered strings, JVM-unit-tested) feeds three display engines behind one `DisplayEngine` contract. A single foreground `DisplayService` (play flavour) or `AccessibilityKeepAliveService` (full flavour) owns tick sources and engine lifecycles. Preferences via DataStore drive everything reactively. OEM profile detection tunes defaults; a Compose settings UI configures format, engine, permissions, and calibration.

**Tech Stack:** Kotlin 2.1.21, Compose (BOM 2025.06.00), AGP 8.13.2/Gradle 8.14.3, DataStore Preferences, java.time, JUnit4 JVM tests, flavour dimension `distribution` (`play`/`full`). Device: CPH2841 ColorOS (serial 3B166N000CZ00000), adb at `C:\Users\Kabir\AppData\Local\Android\Sdk\platform-tools\adb.exe`.

**Verification loop (every task):** `./gradlew testPlayDebugUnitTest` for core; `./gradlew assembleDebug` must pass; on-device phases use the bound `android-build-and-device-test` skill; diagnostics via `android-diagnostic-logger`. Commit + push after every green step (PROJECT_RULES 9).

---

## File structure (lock-in)

```
app/src/main/java/com/kabirbhasin/statuscalendar/
  core/format/FormatSpec.kt        — data model: ordered DisplayElement list + options
  core/format/FormatEngine.kt      — spec → RenderedDisplay (line/stack strings); pure JVM
  core/format/Presets.kt           — named preset specs (full date, compact, ISO, icon-style…)
  core/prefs/Settings.kt           — DataStore keys + SettingsRepository (Flow<AppSettings>)
  core/tick/TickSource.kt          — minute/second tick + date/tz/screen broadcast wiring
  core/oem/OemProfile.kt           — OEM detection (ColorOS/OneUI/MIUI/Pixel/Other) + presets
  engine/DisplayEngine.kt          — contract: start/stop/render(RenderedDisplay)
  engine/notification/IconFactory.kt      — text/calendar-stack → monochrome Bitmap (24dp slot)
  engine/notification/NotificationEngine.kt — channel, ongoing silent notif, sortKey bias
  engine/overlay/OverlayEngine.kt  — TYPE_APPLICATION_OVERLAY view in status bar region
  engine/system/SystemTweaks.kt    — WRITE_SECURE_SETTINGS ops: clock_seconds, icon_blacklist
  service/DisplayController.kt     — engine orchestration from settings (shared logic)
  service/DisplayService.kt        — foreground service host (both flavours; play's keep-alive)
  boot/BootReceiver.kt             — BOOT_COMPLETED → start per saved settings
  ui/MainActivity.kt               — Compose settings: engine picker, format builder, preview,
                                     permissions/onboarding, overlay calibration, OEM guidance
  ui/theme/… (as scaffolded)
app/src/full/java/…/keepalive/KeepAliveAccessibilityService.kt  — zero-notif host (full only)
app/src/full/AndroidManifest.xml  — accessibility service declaration
app/src/full/res/xml/keepalive_accessibility_config.xml
app/src/play/AndroidManifest.xml  — (nothing extra; FGS specialUse lives in main)
app/src/test/java/…/core/format/FormatEngineTest.kt — JVM tests (TDD core)
app/src/main/AndroidManifest.xml  — permissions, service, receiver additions
```

---

### Task 1: Format core (TDD)
**Files:** Create `core/format/FormatSpec.kt`, `core/format/FormatEngine.kt`, `core/format/Presets.kt`, test `app/src/test/java/com/kabirbhasin/statuscalendar/core/format/FormatEngineTest.kt`. Modify `gradle/libs.versions.toml` + `app/build.gradle.kts` (junit).

- [ ] Step 1: Add `junit = "4.13.2"` to catalog `[versions]`, `junit = { group = "junit", name = "junit", version.ref = "junit" }` to `[libraries]`, `testImplementation(libs.junit)` to app deps.
- [ ] Step 2: Write failing tests covering: full date `Wednesday, 1st January 2026` (ordinal on, full DOW+month); `Wed 1 Jan`; ISO `2026-01-01`; `01/01/2026`; time `09:05`, `9:05 am` (12h no leading zero, lowercase am), `21:05:07` (seconds); element ordering (time-before-date vs date-before-time); separator config; calendar-stack pair `WED`/`1`; ordinal edge cases 1st/2nd/3rd/4th/11th/12th/13th/21st/22nd/23rd/31st; locale default vs explicit.
- [ ] Step 3: `./gradlew testPlayDebugUnitTest` → expect FAIL (classes missing/compile error is the failing state for a new module).
- [ ] Step 4: Implement model:
```kotlin
enum class DisplayElement { DOW, DATE, TIME }
data class FormatSpec(
    val order: List<DisplayElement>,
    val dowStyle: DowStyle,           // FULL, SHORT, NONE
    val dateStyle: DateStyle,         // FULL_ORDINAL, FULL, SHORT, NUMERIC_SLASH, ISO, DAY_ONLY, NONE
    val timeStyle: TimeStyle,         // H24, H12, NONE
    val showSeconds: Boolean,
    val showAmPm: Boolean,
    val leadingZero: Boolean,
    val separator: String,            // between elements, e.g. ", " or " · "
    val stackMode: Boolean            // calendar-icon: DOW stacked over day-of-month
)
data class RenderedDisplay(val line: String, val stackTop: String?, val stackBottom: String?)
```
`FormatEngine.render(spec, ZonedDateTime, Locale): RenderedDisplay` via `java.time.format.DateTimeFormatter` + manual ordinal suffixing (pattern `d'St'` unavailable — compute suffix from dayOfMonth; 11–13 → th).
- [ ] Step 5: `./gradlew testPlayDebugUnitTest` → PASS. Commit `Add format core with full date and time catalogue`.

### Task 2: Settings repository
**Files:** Create `core/prefs/Settings.kt`. Modify catalog (+`androidx-datastore-preferences = 1.1.7`), app deps.
- [ ] `AppSettings` data class: enabled engines (tier1/tier2 booleans), `FormatSpec` fields, overlay position/size/colour, boot-start flag. `SettingsRepository(context)` exposing `Flow<AppSettings>` + suspend setters. Keys via `preferencesDataStore(name = "settings")`.
- [ ] `assembleDebug` green → commit `Persist display settings with DataStore`.

### Task 3: Notification engine (Tier 1)
**Files:** Create `engine/DisplayEngine.kt`, `engine/notification/IconFactory.kt`, `engine/notification/NotificationEngine.kt`. Modify main manifest (`POST_NOTIFICATIONS`).
- [ ] `DisplayEngine`: `fun start()`, `fun stop()`, `fun render(d: RenderedDisplay)`.
- [ ] `IconFactory`: 96×96 `ALPHA_8`-style white-on-transparent `Bitmap` — single-line auto-sized text (measure + scale to fit) and stack mode (top ~40% DOW, bottom ~60% day number). Cache last string → skip identical renders.
- [ ] `NotificationEngine`: channel `status_display` IMPORTANCE_HIGH, `setSound(null, null)`, no vibration; notification `setOngoing(true)`, `setOnlyAlertOnce(true)`, `setSortKey("0")`, `setShowWhen(false)`, `setSilent(true)`, small icon `IconCompat.createWithBitmap`, content tap → MainActivity. `render()` re-posts only on content change.
- [ ] Build green → commit `Render date and time as a status bar notification icon`.

### Task 4: Tick sources + controller + foreground service
**Files:** Create `core/tick/TickSource.kt`, `service/DisplayController.kt`, `service/DisplayService.kt`. Modify main manifest: `FOREGROUND_SERVICE`, `FOREGROUND_SERVICE_SPECIAL_USE`, `<service android:foregroundServiceType="specialUse">` + `PROPERTY_SPECIAL_USE_FGS_SUBTYPE` property "status bar clock/date display".
- [ ] `TickSource`: registers `ACTION_TIME_TICK`, `ACTION_TIME_CHANGED`, `ACTION_TIMEZONE_CHANGED`, `ACTION_DATE_CHANGED`, `ACTION_SCREEN_ON/OFF`; minute callback; optional aligned 1 s Handler ticker gated on screen-on AND seconds-enabled. No wakelocks, no alarms.
- [ ] `DisplayController`: collects `SettingsRepository.flow`, renders via `FormatEngine`, drives active engines; single place both flavours call.
- [ ] `DisplayService`: FGS hosting controller; its own notification IS the Tier-1 display when Tier 1 active; when only Tier 2 active (play flavour) FGS notification = minimised silent placeholder (IMPORTANCE_MIN channel `service_keepalive`).
- [ ] Build green → commit `Drive display engines from a foreground service`.

### Task 5: Minimal settings UI (functional v0.1)
**Files:** Rewrite `ui/MainActivity.kt`.
- [ ] Compose screen: master switch (starts/stops service), engine toggles, preset picker (from `Presets`), live preview `Text` of current `RenderedDisplay`, notification-permission request flow (Android 13+), battery-optimisation exemption prompt (`ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS`).
- [ ] Build green → commit. **ON-DEVICE CHECKPOINT via `android-build-and-device-test`:** install `play` debug on CPH2841, enable, verify icon in status bar, verify format changes live, screenshot evidence.

### Task 6: Overlay engine (Tier 2)
**Files:** Create `engine/overlay/OverlayEngine.kt`. Modify manifest (`SYSTEM_ALERT_WINDOW`), MainActivity (permission flow + calibration UI), Settings (position/size/colour used).
- [ ] `OverlayEngine`: `WindowManager.addView` custom `TextView`, `TYPE_APPLICATION_OVERLAY`, flags `NOT_FOCUSABLE|NOT_TOUCHABLE|LAYOUT_IN_SCREEN|LAYOUT_NO_LIMITS`, gravity TOP|START, x/y/size/colour from settings; full-line formats incl. seconds; hides on SCREEN_OFF, re-shows on SCREEN_ON.
- [ ] Calibration screen: sliders/drag for x, y, text size, colour, background chip; live overlay updates.
- [ ] Build green → commit. On-device: overlay visible beside/instead of system clock; seconds tick; screen-off stops ticking (dumpsys evidence).

### Task 7: OEM profiles
**Files:** Create `core/oem/OemProfile.kt`. Modify MainActivity (guidance card), OverlayEngine (default offsets).
- [ ] Detect: ColorOS (`ro.build.version.oplusrom`/manufacturer OPPO/OnePlus), OneUI (samsung), MIUI/HyperOS (`ro.miui.ui.version.name`/xiaomi), Pixel/AOSP, Other — via `Build` + cautious `SystemProperties` reflection with graceful fallback.
- [ ] Per-profile: default overlay offsets (clock side!), icon-cap warning text, autostart/battery guidance steps (ColorOS: "Allow background activity"; MIUI: Autostart; OneUI: Never-sleeping apps).
- [ ] Build green → commit. On-device: ColorOS detected on CPH2841, its guidance shown.

### Task 8: System tweaks (Tier 3)
**Files:** Create `engine/system/SystemTweaks.kt`. Modify manifest (`<uses-permission android:name="android.permission.WRITE_SECURE_SETTINGS" tools:ignore="ProtectedPermissions"/>`), MainActivity (Tier-3 card).
- [ ] `SystemTweaks`: granted() check; `setClockSeconds(Boolean)` → `Settings.Secure.putString("clock_seconds", "1"/"0")`; `hideSystemClock(Boolean)` → read-modify-write `icon_blacklist` (comma list, add/remove `clock`, preserve others); all guarded + result-reporting. Verify keys honoured on ColorOS 16 ON DEVICE; record evidence in code comments only if behavioural (no AI markers).
- [ ] UI card: grant state; copyable `adb shell pm grant com.kabirbhasin.statuscalendar android.permission.WRITE_SECURE_SETTINGS` (flavour-aware id incl. `.full`); Shizuku one-tap grant if Shizuku present (`dev.rikka.shizuku:api:13.1.5` + provider in manifest; permission request → `pm grant` via Shizuku remote process).
- [ ] Build green → commit. On-device (grant via adb): toggle seconds on system clock — observe; toggle hide system clock — observe; restore.

### Task 9: Zero-notification path (full flavour)
**Files:** Create `app/src/full/java/…/keepalive/KeepAliveAccessibilityService.kt`, `app/src/full/AndroidManifest.xml`, `app/src/full/res/xml/keepalive_accessibility_config.xml`. Modify controller start-logic (flavour-aware host selection via `BuildConfig.FLAVOR`).
- [ ] Accessibility service (no events subscribed: `accessibilityEventTypes="typeWindowStateChanged"` minimal, `canRetrieveWindowContent="false"`) hosting `DisplayController` — process alive, overlay + Tier 3 run with NO notification. Play flavour ignores this path entirely.
- [ ] Build both flavours green → commit. On-device (`full` debug): enable accessibility, disable service-FGS, verify overlay lives with zero notifications in shade across app-kill + 10-min doze test.

### Task 10: Boot behaviour
**Files:** Create `boot/BootReceiver.kt`. Modify manifests (`RECEIVE_BOOT_COMPLETED`, receiver).
- [ ] On `BOOT_COMPLETED` + settings.bootStart: play → `startForegroundService`; full → accessibility auto-restores, receiver nudges controller. Guard Android 15 FGS-from-boot rules (specialUse permitted).
- [ ] Build green → commit. On-device reboot test (device automation self-heals; expect Shizuku starter ~2 min post-unlock): icon/overlay restored without opening app.

### Task 11: Diagnostics + full-catalogue settings UI
- [ ] Invoke `android-diagnostic-logger` to instrument (NDJSON logger per its process).
- [ ] Expand UI: custom format builder (element order drag, every toggle in `FormatSpec`), per-engine capability gating (seconds/full-date disabled with explanation when only Tier 1), priority explanation card, notification-hiding guidance per OEM (play flavour).
- [ ] Build green → commit. On-device sweep of catalogue with logger evidence.

### Task 12: Final audit + release readiness
- [ ] `./gradlew testPlayDebugUnitTest assembleRelease` (unsigned) green both flavours.
- [ ] Invoke `android-efficiency-audit` (full project). Apply survivors.
- [ ] README.md (usage, permissions rationale, adb grant, flavour differences). Commit `Document setup and permission model`.
- [ ] Battery evidence: dumpsys batterystats before/after 1 h idle with engines on — no alarms, no wakelocks held.

---

## Self-review
- Spec coverage: brief §1 formats → Tasks 1/11; §3 T1/T2/T3 → Tasks 3/6/8; §4 ladder → Tasks 4 (FGS ceiling) + 9 (zero-notif) + 11 (guidance); OEM-dynamic → Task 7; boot → Task 10; efficiency → Tasks 4 (tick design) + 12 (audit/evidence); flavours → scaffold + 9; distribution/Play → 4 (specialUse) + 12. Gaps: none.
- Placeholders: none — every task names files, APIs, and verification commands.
- Type consistency: `FormatSpec`/`RenderedDisplay`/`DisplayEngine`/`DisplayController` names used uniformly.
