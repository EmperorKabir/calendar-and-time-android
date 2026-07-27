# UX audit — Status Calendar

Audited against the question: what would a person who knows nothing about Android
internals expect each control to do, and what does it actually do?

## Verdict
The app exposes **engineering plumbing as user choices**. A person wants "show me the
date in my status bar, like this". Instead they are asked to pick between four
rendering engines whose limits they cannot see until something looks wrong.

---

## Finding 1 — The icon renders unreadable text (severity: critical)
**Expected:** turn on the icon, see a readable date.
**Actual:** the whole format is squeezed onto one line inside a 24dp square. "Mon 27 Jul"
becomes microscopic. The app scales text to fit rather than refusing or adapting.
**Root cause:** `IconFactory.fitText` scales to whatever is asked, with no legibility floor.
**Fix:** enforce a minimum legible size. If the text cannot fit at that size:
1. automatically fall back to the calendar stack (two lines, e.g. `MON` over `27`), or
2. show only the most important element, and
3. tell the user in the preview: "Too long for an icon. Showing MON 27 instead."

## Finding 2 — Two modes collide on screen (severity: critical)
**Expected:** enabling both shows both, tidily.
**Actual:** the overlay defaults to x=0, y=0, which is exactly where the system clock and
our icon already sit, so the text prints on top of them. That is why the text appears
missing until the icon is switched off.
**Fix:** never default to 0,0. Measure the clock's width on first run and place the overlay
after the existing icons, or default to the right-hand side. Offer "Move me somewhere clear"
as a one-tap action.

## Finding 3 — Four toggles for one decision (severity: high)
**Expected:** one choice: how should this look?
**Actual:** Show in status bar, Icon option, Text option, Extra icon slots, Multi icon option.
Five switches whose interactions are undocumented. Nothing states that Icon and Text can run
together, or that Text is required for seconds.
**Fix:** replace with a single choice list:
- **Icon** — compact, always inside the bar
- **Text** — any length and live seconds, drawn over the bar
- **Both**
Keep the platform detail in a "Why?" link, not in the main flow.

## Finding 4 — Toggles that silently do nothing (severity: high)
**Actual:** Text option does nothing until "display over other apps" is granted; the switch
still moves. Extra icon slots does nothing unless companion apps are installed, and the app
offers no way to install them.
**Fix:** a switch must never move without effect. If a prerequisite is missing, keep the
switch off and show the action inline: "Text needs permission — Grant". For slots, either
bundle installation or hide the option entirely.

## Finding 5 — Seconds live in the wrong place (severity: medium)
**Actual:** Seconds sits in the Time section, but only works when Text option is on. The
explanation is on the toggle, far from the mode choice.
**Fix:** disable the Seconds switch when no mode can show them, with the reason attached, or
promote seconds to a mode capability shown next to the mode choice.

## Finding 6 — A mode that is documented not to work (severity: medium)
**Actual:** Multi icon option is shown, then its own description explains that Android merges
the icons so it achieves nothing on Android 14+.
**Fix:** remove it. Measured evidence already shows the platform defeats it. Keeping a broken
option in the interface teaches users the app is unreliable.

## Finding 7 — Preview does not reflect what is switched on (severity: medium)
**Actual:** the preview always shows both an icon preview and a text preview, regardless of
which modes are enabled, so it promises output the user will not get.
**Fix:** show only enabled modes; grey out disabled ones with "Turn on Icon to see this".

## Finding 8 — Naming still leaks implementation (severity: low)
"Icon option" and "Text option" describe mechanisms. A layman thinks in outcomes:
**"Compact (fits in the bar)"** and **"Full text (sits over the bar)"**.

---

## Recommended rebuild of the main screen
1. **Preview** — exactly what will appear, for enabled modes only.
2. **One switch:** Show in status bar.
3. **One choice:** Compact / Full text / Both, each with a one-line consequence and any
   prerequisite handled inline.
4. **Format** — presets first, custom below, with capability warnings inline.
5. **Advanced** (collapsed) — system clock integration, extra icon slots, boot behaviour.

## Platform truths that must stay visible somewhere honest
- One icon slot per app; the system merges an app's own notifications into that slot.
- The bar caps visible icons and pushes the rest into an overflow dot, so extra companion
  slots are entitled to space but may not be displayed.
- Only notification icons and the system's own clock live inside the bar. Everything else is
  drawn on top of it.
