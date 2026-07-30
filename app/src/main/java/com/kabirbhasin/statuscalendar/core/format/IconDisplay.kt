package com.kabirbhasin.statuscalendar.core.format

import java.time.ZonedDateTime
import java.util.Locale

/**
 * The single rule for what the status bar icon draws, shared by the renderer and the
 * preview so they can never disagree. A notification icon is one small square, so
 * anything beyond a few characters is shown as a desk calendar instead: the weekday
 * above a large day number.
 */
object IconDisplay {

    const val MAX_SINGLE_LINE = 7

    fun forSpec(spec: FormatSpec, now: ZonedDateTime, locale: Locale): RenderedDisplay {
        val withoutSeconds = spec.copy(
            timeConfig = spec.timeConfig.copy(showSeconds = false)
        )
        val plain = FormatEngine.render(withoutSeconds, now, locale)
        if (spec.stackMode || plain.line.length > MAX_SINGLE_LINE) {
            return FormatEngine.render(withoutSeconds.copy(stackMode = true), now, locale)
        }
        return plain
    }

    /** True when the format is too long for one line and will be shown as a calendar. */
    fun willUseCalendar(spec: FormatSpec, now: ZonedDateTime, locale: Locale): Boolean =
        spec.stackMode ||
            FormatEngine.render(
                spec.copy(timeConfig = spec.timeConfig.copy(showSeconds = false)), now, locale
            ).line.length > MAX_SINGLE_LINE
}
