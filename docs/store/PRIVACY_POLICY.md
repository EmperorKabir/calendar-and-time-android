# Privacy policy — Status Calendar

Last updated: 28 July 2026

## The short version
Status Calendar collects nothing, stores nothing about you, and sends nothing anywhere.
It has no analytics, no advertising, no crash reporting and no network permission.

## What the app stores
Your display preferences (format, mode, position, saved presets) are stored only on your
device, in the app's private storage. They never leave the device. Uninstalling the app
deletes them.

## What the app can see
- **The date and time** from the system clock, which is what it displays.
- **Which of the companion slot apps are installed**, so it knows how many status bar
  icon spaces are available. This is a yes or no check against three fixed package names
  and nothing else.

## Permissions and why they exist
- **POST_NOTIFICATIONS** — the status bar icon is delivered as a notification. Without
  this permission there is nothing to show.
- **FOREGROUND_SERVICE / FOREGROUND_SERVICE_SPECIAL_USE** — keeps the display running
  while you use other apps. The special use is a persistent status bar clock and calendar.
- **POST_PROMOTED_NOTIFICATIONS** — on Android 16 and newer, lets the display appear as a
  status bar chip containing text.
- **SYSTEM_ALERT_WINDOW** — only used if you choose the draw over option, to paint your
  text on top of the status bar area. It draws text and nothing else.
- **RECEIVE_BOOT_COMPLETED** — restores the display after a restart, if you asked it to.
- **WRITE_SECURE_SETTINGS** — optional and never granted automatically. If you grant it
  yourself from a computer, the app can change your phone's own clock settings (show
  seconds, hide the clock, 12 or 24 hour). It writes only those settings.

## The sideload build
The separately distributed build includes an accessibility service whose only purpose is
to keep the display running without a notification. It subscribes to no events, reads no
screen content, and collects nothing. It is not present in the Google Play build.

## Children
The app is not directed at children and collects no data from anyone.

## Contact
Raise an issue at the project repository.
