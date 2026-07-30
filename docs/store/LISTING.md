# Play listing copy

## Title
Status Calendar

## Short description (80 characters)
Put the date and time in your status bar, exactly the way you want them.

## Full description
Status Calendar shows the date, the day and the time in your Android status bar, in
whatever format suits you.

Choose from ready made formats or build your own: full weekday or short, month as a name
or a number, ordinal dates with a raised suffix, twelve or twenty four hour time, seconds,
am and pm, and the order of every part.

Three ways to show it:

Compact puts a real element in the status bar next to your other icons. On Android 16 and
newer this appears as a chip containing readable text. On earlier versions it is an icon,
and long formats become a desk calendar with the weekday above a large date number.

Draw over paints your text on top of the status bar. It handles any length and can tick
every second. It hides itself when a video goes fullscreen, and it is not shown on the
lock screen.

System clock integration changes your phone's own clock rather than adding to it: show
live seconds, hide the clock, switch between twelve and twenty four hour. This needs a
permission you grant once from a computer, and the app explains exactly how.

No adverts. No tracking. No network access. Nothing leaves your phone.

## Data safety declaration
- Data collected: none
- Data shared: none
- Data encrypted in transit: not applicable, the app makes no network requests
- Users can request deletion: not applicable, no data is collected

## Foreground service justification
Type: specialUse. Subtype: persistent status bar clock and calendar display.
The service exists so the date and time display stays current while the user is in other
apps. Android 14 and newer defer context registered broadcasts for cached processes, which
would stop the clock updating; a foreground service is the documented way to keep receiving
ACTION_TIME_TICK.
