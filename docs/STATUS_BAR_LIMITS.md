# What the status bar will and will not accept

> **Status: open.** The compact icon is still being worked on. The measurements
> here are accurate but the conclusion drawn from the first three was too broad —
> read "Open lead" below.

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

**The overlay is the only route proven to work so far** — but the substitution is
not as universal as those three tests implied, and the compact icon is NOT
settled. See the open lead below before treating any of this as final.

## Open lead: the substitution may be conditional, not universal

Comparing what the other apps in the bar actually supply, from `dumpsys`:

| App | Small icon | Renders as |
| --- | --- | --- |
| Instagram, Google Home, Ring | `0x7f10…`, `0x7f11…` — **mipmap** | Full colour |
| Maps | `0x7f08…` — drawable | **Its own monochrome glyph** |
| This app | drawable, and separately a bitmap | The application icon |

Two things follow. The colourful icons are not the skin substituting anything —
those apps deliberately set their launcher mipmap as the small icon. And Maps
proves the skin *does* render an app-supplied drawable rather than replacing it.

So the question is not "does this ROM substitute" but "what makes it substitute
for us". The one property separating this app from every app that renders
correctly is `FLAG_FOREGROUND_SERVICE`: the skin plausibly swaps in the app icon
for foreground-service notifications, as an "this app is running" indicator. That
fits every measurement taken so far, including the very first one, which was
already a foreground-service post.

**Cheapest decisive test:** post a second, ordinary notification from this app —
no foreground service, no ongoing flag, a different id — carrying the same dated
bitmap, and see whether the bar draws the glyph or the app icon. If it draws the
glyph, compact mode works with no overlay at all: keep the service notification
silent on the `IMPORTANCE_MIN` channel and let a separate ordinary notification
carry the display.

Further avenues, in order of expected value:

1. The non-foreground-service test above.
2. Vary one property at a time from that baseline — ongoing, colorized, category,
   channel importance — to find exactly which one triggers the swap.
3. Pull and decompile the ROM's SystemUI and Oplus notification classes and read
   the branch directly, including any package allowlist driving it.
4. `launcher_support_dynamic_icon=true` is set in global settings, and the device
   carries an Oplus "uxicon" system (`uxicon.config_version`, `key_ux_icon_config`,
   `customize_uxicon_font`). If the ROM has a supported dynamic app-icon
   mechanism, and the bar resolves the application icon, a dynamic application
   icon would reach the status bar even where the small icon cannot.
5. Re-test the small icon as a plain PNG, since the drawable tried was a vector
   and vector loading across packages is a known weak spot.

## Placing the overlay

Nothing reports how wide the run of notification icons is, so the text is placed
where they are not: pushed to the trailing edge, in the gap between them and the
system icons. The old fixed default of x=420 ran straight through them.
