package com.kabirbhasin.statuscalendar.core.format

import java.time.ZonedDateTime
import java.time.format.TextStyle
import java.util.Locale

object FormatEngine {

    fun render(spec: FormatSpec, dateTime: ZonedDateTime, locale: Locale): RenderedDisplay {
        val parts = spec.order.mapNotNull { element ->
            when (element) {
                DisplayElement.DOW -> dowPart(spec, dateTime, locale)
                DisplayElement.DATE -> datePart(spec, dateTime, locale)
                DisplayElement.TIME -> timePart(spec, dateTime)
            }
        }
        val line = parts.joinToString(spec.separator)

        val stackTop = if (spec.stackMode) {
            dateTime.dayOfWeek.getDisplayName(TextStyle.SHORT, locale).uppercase(locale)
        } else null
        val stackBottom = if (spec.stackMode) dateTime.dayOfMonth.toString() else null

        return RenderedDisplay(line, stackTop, stackBottom)
    }

    private fun dowPart(spec: FormatSpec, dt: ZonedDateTime, locale: Locale): String? =
        when (spec.dowStyle) {
            DowStyle.FULL -> dt.dayOfWeek.getDisplayName(TextStyle.FULL, locale)
            DowStyle.SHORT -> dt.dayOfWeek.getDisplayName(TextStyle.SHORT, locale)
            DowStyle.NONE -> null
        }

    private fun datePart(spec: FormatSpec, dt: ZonedDateTime, locale: Locale): String? {
        val day = dt.dayOfMonth
        val monthFull = dt.month.getDisplayName(TextStyle.FULL, locale)
        val monthShort = dt.month.getDisplayName(TextStyle.SHORT, locale)
        return when (spec.dateStyle) {
            DateStyle.FULL_ORDINAL -> "${withOrdinal(day)} $monthFull ${dt.year}"
            DateStyle.FULL -> "$day $monthFull ${dt.year}"
            DateStyle.SHORT -> "$day $monthShort"
            DateStyle.NUMERIC_SLASH ->
                "%02d/%02d/%04d".format(day, dt.monthValue, dt.year)
            DateStyle.ISO -> "%04d-%02d-%02d".format(dt.year, dt.monthValue, day)
            DateStyle.DAY_ONLY -> day.toString()
            DateStyle.NONE -> null
        }
    }

    private fun timePart(spec: FormatSpec, dt: ZonedDateTime): String? {
        if (spec.timeStyle == TimeStyle.NONE) return null
        val hour = when (spec.timeStyle) {
            TimeStyle.H24 -> dt.hour
            else -> ((dt.hour + 11) % 12) + 1
        }
        val hourText = if (spec.leadingZero) "%02d".format(hour) else hour.toString()
        val builder = StringBuilder(hourText)
            .append(":")
            .append("%02d".format(dt.minute))
        if (spec.showSeconds) builder.append(":").append("%02d".format(dt.second))
        if (spec.timeStyle == TimeStyle.H12 && spec.showAmPm) {
            builder.append(if (dt.hour < 12) " am" else " pm")
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
