# Status Calendar

Configurable calendar and clock for the Android status bar. Shows the date,
day and time exactly how you want them — from `Wednesday, 1ˢᵗ January 2026`
to `01-07`, `21:05:07` or a desk-calendar style `WED` over `1`.

## Three display engines

| Engine | What it is | Strengths | Limits |
|---|---|---|---|
| Status bar icon | A real notification icon in the system bar | Works everywhere, survives lockscreen, text auto-scales to fit (`Mon 27 Jul` verified legible) | One slot; some OEMs (ColorOS) show the app logo instead; needs a notification entry |
| Chained text icons (experimental, off by default) | Several notifications claiming extra icon slots | May help on older Android / some OEM builds | Measured on Android 14+: the system collapses an app's icons into ONE slot, so it only adds shade entries |
| Text overlay | A window drawn over the bar's empty space | Any format, live seconds, per-pixel position/size | Hidden on lockscreen and in fullscreen apps |
| System clock integration | Writes the hidden SystemUI settings | The phone's OWN clock ticks seconds; can hide the system clock so your display replaces it | Needs `WRITE_SECURE_SETTINGS` via one adb command (below); honouring varies by OEM |

```
adb shell pm grant com.kabirbhasin.statuscalendar android.permission.WRITE_SECURE_SETTINGS
```

## Formats
Element order (Day · Date · Time in any order), weekday `Wednesday/Wed/W`,
month `January/Jan/01/1`, year `2026/26`, day `1/01/1st/1ˢᵗ` (raised suffix
optional), date order `D-M-Y / M-D-Y / Y-M-D` with `/ - .` or space,
hours `09/9` in 24h or 12h, seconds, `am/pm` or `AM/PM`, separators, and a
calendar-icon stack. Quick presets included; everything is also individually
configurable with plain-English explanations in the app.

## Flavours
- **play** — Google Play build: foreground service with a silent, minimised
  notification (the Play-policy ceiling).
- **full** — sideload build: an accessibility service keeps the display alive
  with **no notification at all**. The service reads no screen content and
  collects nothing; it exists purely so the process survives.

## Efficiency
No alarms, no wakelocks, no polling. Minute updates ride the system's own
`TIME_TICK` broadcast; seconds tick only while the screen is on and only when
an engine that can show them is active; renders are suppressed while the
screen is off. The app theme is pure-black for AMOLED displays.

## Build
Gradle 8.14.3 / AGP 8.13.2 / Kotlin 2.1.21 / compileSdk 36 / minSdk 26.
`./gradlew assembleDebug` builds both flavours; unit tests: `./gradlew testPlayDebugUnitTest`.
