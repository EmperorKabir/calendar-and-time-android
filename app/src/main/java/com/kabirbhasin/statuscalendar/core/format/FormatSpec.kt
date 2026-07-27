package com.kabirbhasin.statuscalendar.core.format

enum class DisplayElement { DOW, DATE, TIME }

/** Day-of-week text: EEEE, EEE, EEEEE (narrow) or hidden. */
enum class DowStyle { FULL, SHORT, NARROW, NONE }

/** Month text: MMMM, MMM, MM, M or hidden. */
enum class MonthStyle { FULL, SHORT, NUMBER_PADDED, NUMBER, NONE }

/** Year text: yyyy, yy or hidden. */
enum class YearStyle { FULL, TWO_DIGIT, NONE }

/** Component arrangement inside the date element. */
enum class DateOrder { DMY, MDY, YMD }

/** Hour digits: HH, H, hh, h or no time. */
enum class HourStyle { H24_PADDED, H24, H12_PADDED, H12, NONE }

enum class AmPmStyle { NONE, LOWERCASE, UPPERCASE }

data class DateConfig(
    val showDay: Boolean,
    val dayPadded: Boolean,
    val dayOrdinal: Boolean,
    val monthStyle: MonthStyle,
    val yearStyle: YearStyle,
    val order: DateOrder,
    val separator: String
)

data class TimeConfig(
    val hourStyle: HourStyle,
    val showSeconds: Boolean,
    val amPm: AmPmStyle
)

data class FormatSpec(
    val order: List<DisplayElement>,
    val dowStyle: DowStyle,
    val dateConfig: DateConfig,
    val timeConfig: TimeConfig,
    val separator: String,
    val stackMode: Boolean
)

data class RenderedDisplay(
    val line: String,
    val stackTop: String?,
    val stackBottom: String?
)
