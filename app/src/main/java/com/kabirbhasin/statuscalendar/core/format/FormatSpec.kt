package com.kabirbhasin.statuscalendar.core.format

enum class DisplayElement { DOW, DATE, TIME }

enum class DowStyle { FULL, SHORT, NONE }

enum class DateStyle { FULL_ORDINAL, FULL, SHORT, NUMERIC_SLASH, ISO, DAY_ONLY, NONE }

enum class TimeStyle { H24, H12, NONE }

data class FormatSpec(
    val order: List<DisplayElement>,
    val dowStyle: DowStyle,
    val dateStyle: DateStyle,
    val timeStyle: TimeStyle,
    val showSeconds: Boolean,
    val showAmPm: Boolean,
    val leadingZero: Boolean,
    val separator: String,
    val stackMode: Boolean
)

data class RenderedDisplay(
    val line: String,
    val stackTop: String?,
    val stackBottom: String?
)
