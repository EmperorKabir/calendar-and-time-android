# What the status bar will and will not accept

Measured on the test phone: OPPO CPH2841, Oplus ROM V16.1.0, Android 16 (API 36).
Every claim below was checked on the device, not inferred.

## Getting the icon to lead the row

Icons are ordered by the platform notification comparator. What was tried:

| Attempt | Result |
| --- | --- |
| `setWhen(now)` | 5th of 5, next to the overflow dot |
| `setWhen(future)` | Unchanged — the ranker clamps a future value to the post time |
| `setWhen(1)` | Worse: dropped out of the row into the overflow dot |
| Compact mode, so the icon leaves the silent channel | 2nd |
| `setColorized(true)` | **1st**, and stays there |

The silent channel was the real cause of the first measurements: `display_mode`
was `FULL_TEXT`, restored by auto-backup on install, which routes the post to the
`IMPORTANCE_MIN` service channel. The last step works because the comparator
ranks colorized foreground-service notifications above conversations, and a
messaging app was what held the lead.

Note the skin prints the timestamp even under `setShowWhen(false)`, so `when` has
to stay at the post time. A doctored value showed up in the shade as "in 73 y".

## Getting live text or a live date into the bar

Three routes, all closed on this ROM:

1. **Bitmap small icon.** The skin substitutes the application icon. Proven by
   setting the icon colour to red with the date at the 31st: the preview drew a
   red "FRI 31" while the bar kept the teal "MON 1" launcher artwork.
2. **Resource small icon.** Same substitution. `setSmallIcon(R.drawable.ic_day_31)`
   changed nothing in the bar.
3. **Promoted ongoing chip (Android 16).** `canPostPromotedNotifications()`
   returns false from both the compat and the platform call, with no exception,
   even though the device's own flags report `api_rich_ongoing=true`,
   `ui_rich_ongoing=true` and the notification service records this app as
   `promoted=true`. Requesting promotion anyway is accepted — the record carries
   `requestPromotedOngoing=true` and a six character `shortCriticalText` — but
   SystemUI renders no chip. The request is left in place, since the capability
   call is demonstrably unreliable and the platform ignores what it will not use.

The activity-alias approach cannot help either: with `.Day31` enabled and
`.ui.MainActivity` disabled, `resolve-activity` confirmed the launcher entry had
changed, and the bar still showed the application icon after a forced restart and
a fresh post. The skin resolves `<application android:icon>`, which no alias
changes. The dated icon therefore only affects the app list.

**So the overlay is the only way to put the date in the bar on this ROM**, and it
has to share the bar with notification icons that cannot be hidden — a blank icon
is substituted, and the foreground service must keep its notification.

## Placing the overlay

Nothing reports how wide the run of notification icons is, so the text is placed
where they are not: pushed to the trailing edge, in the gap between them and the
system icons. The old fixed default of x=420 ran straight through them.
