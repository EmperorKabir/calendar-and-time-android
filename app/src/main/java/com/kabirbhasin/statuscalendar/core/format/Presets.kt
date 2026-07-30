package com.kabirbhasin.statuscalendar.core.format

data class Preset(val id: String, val label: String, val spec: FormatSpec)

object Presets {

    private val plainDate = DateConfig(
        showDay = true, dayPadded = false, dayOrdinal = false, ordinalSuperscript = true,
        monthStyle = MonthStyle.NONE, yearStyle = YearStyle.NONE,
        order = DateOrder.DMY, separator = "/"
    )
    private val noTime = TimeConfig(HourStyle.NONE, showSeconds = false, amPm = AmPmStyle.NONE)

    val fullUk = Preset(
        id = "full_uk",
        label = "Full UK",
        spec = FormatSpec(
            order = listOf(DisplayElement.DOW, DisplayElement.DATE, DisplayElement.TIME),
            dowStyle = DowStyle.FULL,
            dateConfig = DateConfig(
                showDay = true, dayPadded = false, dayOrdinal = true,
                ordinalSuperscript = true, monthStyle = MonthStyle.FULL,
                yearStyle = YearStyle.FULL, order = DateOrder.DMY, separator = "/"
            ),
            timeConfig = TimeConfig(HourStyle.H12, showSeconds = false, amPm = AmPmStyle.LOWERCASE),
            separator = ", ",
            stackMode = false
        )
    )

    val fullDate = Preset(
        id = "full_date",
        label = "Full date",
        spec = FormatSpec(
            order = listOf(DisplayElement.DOW, DisplayElement.DATE),
            dowStyle = DowStyle.FULL,
            dateConfig = plainDate.copy(
                dayOrdinal = true, monthStyle = MonthStyle.FULL, yearStyle = YearStyle.FULL
            ),
            timeConfig = noTime,
            separator = ", ",
            stackMode = false
        )
    )

    val compactDate = Preset(
        id = "compact_date",
        label = "Compact date",
        spec = fullDate.spec.copy(
            dowStyle = DowStyle.SHORT,
            dateConfig = plainDate.copy(monthStyle = MonthStyle.SHORT),
            separator = " "
        )
    )

    val isoDate = Preset(
        id = "iso_date",
        label = "ISO date",
        spec = fullDate.spec.copy(
            order = listOf(DisplayElement.DATE),
            dowStyle = DowStyle.NONE,
            dateConfig = plainDate.copy(
                dayPadded = true, monthStyle = MonthStyle.NUMBER_PADDED,
                yearStyle = YearStyle.FULL, order = DateOrder.YMD, separator = "-"
            )
        )
    )

    val numericDate = Preset(
        id = "numeric_date",
        label = "Numeric date",
        spec = isoDate.spec.copy(
            dateConfig = plainDate.copy(
                dayPadded = true, monthStyle = MonthStyle.NUMBER_PADDED,
                yearStyle = YearStyle.FULL
            )
        )
    )

    val calendarIcon = Preset(
        id = "calendar_icon",
        label = "Calendar icon",
        spec = fullDate.spec.copy(
            dowStyle = DowStyle.NONE,
            dateConfig = plainDate.copy(showDay = false),
            stackMode = true
        )
    )

    val time24 = Preset(
        id = "time_24",
        label = "Time (24-hour)",
        spec = FormatSpec(
            order = listOf(DisplayElement.TIME),
            dowStyle = DowStyle.NONE,
            dateConfig = plainDate.copy(showDay = false),
            timeConfig = TimeConfig(HourStyle.H24_PADDED, showSeconds = false, amPm = AmPmStyle.NONE),
            separator = ", ",
            stackMode = false
        )
    )

    val time12 = Preset(
        id = "time_12",
        label = "Time (12-hour)",
        spec = time24.spec.copy(
            timeConfig = TimeConfig(HourStyle.H12, showSeconds = false, amPm = AmPmStyle.LOWERCASE)
        )
    )

    val timeSeconds = Preset(
        id = "time_seconds",
        label = "Time with seconds",
        spec = time24.spec.copy(
            timeConfig = TimeConfig(HourStyle.H24_PADDED, showSeconds = true, amPm = AmPmStyle.NONE)
        )
    )

    val dateAndTime = Preset(
        id = "date_time",
        label = "Date and time",
        spec = FormatSpec(
            order = listOf(DisplayElement.DOW, DisplayElement.DATE, DisplayElement.TIME),
            dowStyle = DowStyle.SHORT,
            dateConfig = plainDate.copy(monthStyle = MonthStyle.SHORT),
            timeConfig = TimeConfig(HourStyle.H24_PADDED, showSeconds = false, amPm = AmPmStyle.NONE),
            separator = " · ",
            stackMode = false
        )
    )

    val all: List<Preset> = listOf(
        fullUk, fullDate, compactDate, isoDate, numericDate, calendarIcon,
        time24, time12, timeSeconds, dateAndTime
    )

    fun byId(id: String): Preset? = all.firstOrNull { it.id == id }
}
