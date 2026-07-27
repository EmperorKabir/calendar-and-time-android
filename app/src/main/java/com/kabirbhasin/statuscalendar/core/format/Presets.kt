package com.kabirbhasin.statuscalendar.core.format

data class Preset(val id: String, val label: String, val spec: FormatSpec)

object Presets {

    val fullDate = Preset(
        id = "full_date",
        label = "Full date",
        spec = FormatSpec(
            order = listOf(DisplayElement.DOW, DisplayElement.DATE),
            dowStyle = DowStyle.FULL,
            dateStyle = DateStyle.FULL_ORDINAL,
            timeStyle = TimeStyle.NONE,
            showSeconds = false,
            showAmPm = false,
            leadingZero = true,
            separator = ", ",
            stackMode = false
        )
    )

    val compactDate = Preset(
        id = "compact_date",
        label = "Compact date",
        spec = fullDate.spec.copy(
            dowStyle = DowStyle.SHORT,
            dateStyle = DateStyle.SHORT,
            separator = " "
        )
    )

    val isoDate = Preset(
        id = "iso_date",
        label = "ISO date",
        spec = fullDate.spec.copy(
            order = listOf(DisplayElement.DATE),
            dowStyle = DowStyle.NONE,
            dateStyle = DateStyle.ISO
        )
    )

    val calendarIcon = Preset(
        id = "calendar_icon",
        label = "Calendar icon",
        spec = fullDate.spec.copy(
            dowStyle = DowStyle.NONE,
            dateStyle = DateStyle.NONE,
            stackMode = true
        )
    )

    val time24 = Preset(
        id = "time_24",
        label = "Time (24-hour)",
        spec = fullDate.spec.copy(
            order = listOf(DisplayElement.TIME),
            dowStyle = DowStyle.NONE,
            dateStyle = DateStyle.NONE,
            timeStyle = TimeStyle.H24
        )
    )

    val time12 = Preset(
        id = "time_12",
        label = "Time (12-hour)",
        spec = time24.spec.copy(
            timeStyle = TimeStyle.H12,
            showAmPm = true,
            leadingZero = false
        )
    )

    val timeSeconds = Preset(
        id = "time_seconds",
        label = "Time with seconds",
        spec = time24.spec.copy(showSeconds = true)
    )

    val dateAndTime = Preset(
        id = "date_time",
        label = "Date and time",
        spec = FormatSpec(
            order = listOf(DisplayElement.DOW, DisplayElement.DATE, DisplayElement.TIME),
            dowStyle = DowStyle.SHORT,
            dateStyle = DateStyle.SHORT,
            timeStyle = TimeStyle.H24,
            showSeconds = false,
            showAmPm = false,
            leadingZero = true,
            separator = " · ",
            stackMode = false
        )
    )

    val all: List<Preset> = listOf(
        fullDate, compactDate, isoDate, calendarIcon,
        time24, time12, timeSeconds, dateAndTime
    )

    fun byId(id: String): Preset? = all.firstOrNull { it.id == id }
}
