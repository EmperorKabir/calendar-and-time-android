package com.kabirbhasin.statuscalendar.core.format

import java.time.ZonedDateTime
import java.time.format.TextStyle
import java.util.Locale

object FormatEngine {

    fun render(spec: FormatSpec, dateTime: ZonedDateTime, locale: Locale): RenderedDisplay {
        val parts = spec.order.mapNotNull { element ->
            when (element) {
                DisplayElement.DOW -> dowPart(spec.dowStyle, dateTime, locale)
                DisplayElement.DATE -> datePart(spec.dateConfig, dateTime, locale)
                DisplayElement.TIME -> timePart(spec.timeConfig, dateTime)
            }
        }
        val line = parts.joinToString(spec.separator)

        val stackTop = if (spec.stackMode) {
            dateTime.dayOfWeek.getDisplayName(TextStyle.SHORT, locale).uppercase(locale)
        } else null
        val stackBottom = if (spec.stackMode) dateTime.dayOfMonth.toString() else null

        return RenderedDisplay(line, stackTop, stackBottom)
    }

    private fun dowPart(style: DowStyle, dt: ZonedDateTime, locale: Locale): String? {
        val textStyle = when (style) {
            DowStyle.FULL -> TextStyle.FULL
            DowStyle.SHORT -> TextStyle.SHORT
            DowStyle.NARROW -> TextStyle.NARROW
            DowStyle.NONE -> return null
        }
        return dt.dayOfWeek.getDisplayName(textStyle, locale)
    }

    private fun datePart(config: DateConfig, dt: ZonedDateTime, locale: Locale): String? {
        val namedMonth = config.monthStyle == MonthStyle.FULL || config.monthStyle == MonthStyle.SHORT

        val day = when {
            !config.showDay -> null
            config.dayOrdinal -> withOrdinal(dt.dayOfMonth)
            config.dayPadded -> "%02d".format(dt.dayOfMonth)
            else -> dt.dayOfMonth.toString()
        }
        val month = when (config.monthStyle) {
            MonthStyle.FULL -> dt.month.getDisplayName(TextStyle.FULL, locale)
            MonthStyle.SHORT -> dt.month.getDisplayName(TextStyle.SHORT, locale)
            MonthStyle.NUMBER_PADDED -> "%02d".format(dt.monthValue)
            MonthStyle.NUMBER -> dt.monthValue.toString()
            MonthStyle.NONE -> null
        }
        val year = when (config.yearStyle) {
            YearStyle.FULL -> dt.year.toString()
            YearStyle.TWO_DIGIT -> "%02d".format(dt.year % 100)
            YearStyle.NONE -> null
        }

        val components = when (config.order) {
            DateOrder.DMY -> listOf(day, month, year)
            DateOrder.MDY -> listOf(month, day, year)
            DateOrder.YMD -> listOf(year, month, day)
        }.filterNotNull()
        if (components.isEmpty()) return null

        // Named months read as prose (spaces); numeric dates use the chosen separator.
        return components.joinToString(if (namedMonth) " " else config.separator)
    }

    private fun timePart(config: TimeConfig, dt: ZonedDateTime): String? {
        val hour24 = config.hourStyle == HourStyle.H24_PADDED || config.hourStyle == HourStyle.H24
        val padded = config.hourStyle == HourStyle.H24_PADDED || config.hourStyle == HourStyle.H12_PADDED
        val hour = when {
            config.hourStyle == HourStyle.NONE -> return null
            hour24 -> dt.hour
            else -> ((dt.hour + 11) % 12) + 1
        }
        val builder = StringBuilder(if (padded) "%02d".format(hour) else hour.toString())
            .append(":")
            .append("%02d".format(dt.minute))
        if (config.showSeconds) builder.append(":").append("%02d".format(dt.second))
        when (config.amPm) {
            AmPmStyle.LOWERCASE -> builder.append(if (dt.hour < 12) " am" else " pm")
            AmPmStyle.UPPERCASE -> builder.append(if (dt.hour < 12) " AM" else " PM")
            AmPmStyle.NONE -> Unit
        }
        return builder.toString()
    }

    fun withOrdinal(day: Int): String = "$day${ordinalSuffix(day)}"

    private fun ordinalSuffix(day: Int): String = when {
        day % 100 in 11..13 -> "th"
        day % 10 == 1 -> "st"
        day % 10 == 2 -> "nd"
        day % 10 == 3 -> "rd"
        else -> "th"
    }
}
