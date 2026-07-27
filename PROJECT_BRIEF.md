# PROJECT_BRIEF — Status-Bar Calendar & Clock (Android)

> Authoritative requirements + design baseline, written 2026-07-27 from the originating session
> (full user context). Read FULLY before any work. This folder is the project root.
> **Ignore the parent `tasker-fix` CLAUDE.md / HANDOFF.md for this project** — they belong to a
> separate, finished automation project; a guard note exists there too. The global rules at
> `C:\Users\Kabir\CLAUDE.md` DO apply (no-deferral, evidence-based, Context7, British English, bullets).

## 1. Product vision
- Android app that shows a **highly configurable calendar/date/time display in the status bar**.
- Every format the user wants, e.g.: `Wednesday, 1st January 2026` · `Wed 1 Jan` · `01/01/2026` ·
  ISO `2026-01-01` · day-number only · day-of-week only · time `12:45` / `12:45:07` / `12:45 pm` /
  24h · **calendar-icon style: "WED" stacked above "1"** (like a physical desk calendar).
- User controls: which elements show, their **order**, separators, ordinal suffix (1st/2nd) on/off,
  seconds on/off, AM/PM on/off, leading zeros, font size/weight/colour (where engine allows),
  locale-aware patterns + a custom `DateTimeFormatter` pattern builder.
- **Dynamic to the OS/OEM**: ColorOS/OxygenOS, OneUI, MIUI/HyperOS, Pixel/AOSP differ in status-bar
  clock position, icon caps, padding, behaviours — detect and adapt (presets + runtime probes +
  manual calibration).
- Distribution-ready for general public (Google Play + sideload), zero dependence on Tasker/
  Automate/Shizuku for normal users.

## 2. Locked decisions (user-approved 2026-07-27 — do not re-litigate)
- **Architecture: THREE-TIER engine** (dual engine + system-integration tier; detail §3). "As robust
  as possible, no shortcuts."
- **Zero-notification: chase to the maximum.** "Go as deeply as possible… absolutely chase hard
  having no notification." Strategy ladder in §4. Dual build flavours (Play / Full-sideload).
- **Folder**: `C:\Users\Kabir\tasker-fix\calendar-and-time-android` (user chose subfolder knowingly).
- **GitHub repo: public from day one** (created by `/android-project-init`; repo name = folder name).
- **minSdk 26, targetSdk latest stable.** Kotlin + Jetpack Compose for the settings/config UI.
- **Full format catalogue in v1** — no trimming, no "later".

## 3. Display engines (capability-gated settings UI: only offer what the active engine can do)
### Tier 1 — Notification-icon engine (universal, Play-safe)
- Dynamically rendered bitmap as the notification **small icon** (~24dp, monochrome alpha mask on
  modern Android — white silhouette; design glyphs for legibility at that size).
- Feasible formats: calendar-icon (DOW over day), day number, DOW, compact date (`WED 1`), `HH:MM`.
- **Not feasible here (physics)**: long/full dates, ticking seconds (update rate-limits + battery).
- Updates: `ACTION_TIME_TICK` receiver (free minute ticks while process alive), plus
  `TIMEZONE_CHANGED`/`DATE_CHANGED`/`TIME_SET` receivers. Cache rendered bitmaps.
- Priority bias for leftmost placement: max-importance **silent** channel, `setSortKey`, ongoing;
  document OEM icon caps (OneUI ~3 icons, MIUI hiding, ColorOS limits).
- Intrinsic: this engine's icon IS a notification → a shade entry exists whenever Tier 1 is the
  chosen display. Zero-notif modes therefore use Tiers 2/3 (make the in-app engine picker explain this).

### Tier 2 — Overlay engine (rich display)
- `TYPE_APPLICATION_OVERLAY` window drawn in the status bar's transparent regions
  (`SYSTEM_ALERT_WINDOW` consent). Arbitrary text: **full dates, live seconds**, any styling/order.
- Seconds: frame-callback/`TextClock` tick **only while screen on** (SCREEN_ON/OFF receivers);
  no alarms, no wakelocks.
- Collision with the OEM clock/icons: per-OEM position presets + user calibration screen
  (drag/offset/size/colour) + optional Tier-3 hide-system-clock for a clean replacement.
- Known limits to document in-app: not shown on lockscreen/secure screens; fullscreen apps hide the
  status bar (option: auto-hide with it); OEM quirks catalogued at implementation.

### Tier 3 — System-integration tier (deepest; the "more integrated the better" ask)
- Via **`WRITE_SECURE_SETTINGS`** — grantable WITHOUT root by one-time
  `adb shell pm grant <pkg> android.permission.WRITE_SECURE_SETTINGS`, or programmatically through
  **Shizuku** if present. Established Play-published precedent (SystemUI Tuner et al.) for shipping
  with guided adb instructions.
- Capabilities (verify each per-OEM at implementation — Context7 + web research; evidence-based):
  - `Settings.Secure clock_seconds` → **system status-bar clock shows live seconds** (the exact
    feature the user has seen on their phone).
  - `Settings.Secure icon_blacklist` (include `clock`) → **hide the system clock**, freeing space so
    Tier-2 overlay (or T1 icon) becomes THE clock/date — solves collision cleanly.
  - Other status-bar keys where OEMs honour them (battery style etc. — catalogue, don't promise).
- `Settings.System time_12_24` (12/24h) needs only user-grantable `WRITE_SETTINGS` — offer it.
- Graceful degradation: detect grant state; if absent, full guided-grant UX (copy-paste adb command,
  Shizuku one-tap if installed, plain-English explanation); everything else keeps working without it.

## 4. Zero-notification strategy ladder (chase hard, per user)
1. **Full/sideload flavour**: **AccessibilityService keep-alive** → process persistence with **NO
   notification at all**; overlay + Tier 3 deliver the display. (Accessibility also enables reading
   the status-bar layout → auto-positioning the overlay. Synergy.)
2. **Play flavour ceiling**: FGS (`specialUse`, declared) + silent minimised channel → entry demoted
   to the Silent section; research + implement every per-OEM user-side demotion (Samsung minimise,
   MIUI fold, Android 14+ ongoing-dismiss behaviour) and in-app guided steps per OEM.
3. Catalogue further demotion tricks at implementation via research; adopt any that are robust.
   No wakelocks anywhere. Accessibility route stays OUT of the Play flavour (policy risk) unless
   research at implementation time shows an accepted path.
- Boot: `BOOT_COMPLETED` receiver + per-OEM autostart guidance (ColorOS "Allow background activity"
  etc. — in-app onboarding checklists per OEM).
- Efficiency targets: no polling, minute-aligned ticks, screen-off = fully idle, cached renders,
  small memory; measure with the diagnostic-logger skill during dev.

## 5. Build flavours
- Flavour dimension `distribution`: **`play`** (FGS + silent notif ceiling, no accessibility) and
  **`full`** (accessibility keep-alive, zero-notif). Shared core; engines/settings identical
  otherwise. Play policies to satisfy: `POST_NOTIFICATIONS` runtime ask, FGS type declaration,
  `SYSTEM_ALERT_WINDOW` consent flow, foldable/large-screen support (PROJECT_RULES).

## 6. Test rig (this machine/device)
- Physical device: OnePlus/OPPO **CPH2841**, ColorOS/OxygenOS 16, USB serial `3B166N000CZ00000`.
- adb NOT on PATH: `C:\Users\Kabir\AppData\Local\Android\Sdk\platform-tools\adb.exe`.
- Git Bash: `export MSYS_NO_PATHCONV=1` before `/sdcard` paths.
- Device already runs **Shizuku** (ideal for Tier-3 grant testing) plus a Tasker/Automate boot
  automation (density 451 / font_scale 0.84) — **do not disturb** Tasker, Automate, or Shizuku
  configs. Reboots are safe (automation self-heals); expect a Shizuku starter screen ~2 min
  post-unlock that auto-dismisses. Display density is 451 (swdp 510), font scale 0.84 — test UI at
  these values AND defaults.
- ColorOS knowledge already earned: aggressive background kill ("Allow background activity"
  required), autostart limits, notification-channel behaviours — apply it.

## 7. Workflow (binding)
1. **`/android-project-init`** — run first, with THIS folder open as the project root (user invokes
   manually). Repo `calendar-and-time-android`, **public**. Global PROJECT_RULES loader line already
   exists in `C:\Users\Kabir\.claude\CLAUDE.md` (do not duplicate).
2. Then **superpowers:writing-plans** → implementation plan from this brief (design baseline already
   user-approved; re-confirm open questions only).
3. Build/on-device test cycles → **android-build-and-device-test** skill.
4. Diagnostics during dev → **android-diagnostic-logger** (strip only on explicit user prompt).
5. Final audit → **android-efficiency-audit**.
6. Context7 for every library/AGP/Jetpack/API question; Superpowers methodology throughout;
   every conclusion evidence-cited.

## 8. Open items for the implementation plan (not blockers)
- Per-OEM verification matrix for `clock_seconds` / `icon_blacklist` honouring (ColorOS 16 first —
  device on hand).
- Overlay z-order/inset behaviour on notches/cutouts across the OEM set; auto-position via
  accessibility (full flavour) vs manual calibration (play flavour).
- Icon-slot glyph design for legibility at 24dp alpha-mask (calendar-icon style).
- Exact Play policy wording for `specialUse` FGS + adb-grant guidance screens.
