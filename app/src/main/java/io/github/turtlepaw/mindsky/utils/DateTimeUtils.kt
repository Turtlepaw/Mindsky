package io.github.turtlepaw.mindsky.utils

import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import kotlinx.datetime.TimeZone
import kotlinx.datetime.periodUntil

fun Instant.toRelativeTimeString(
    clock: Clock = Clock.System,
    timeZone: TimeZone = TimeZone.currentSystemDefault()
): String {
    val now = clock.now()
    val period = this.periodUntil(now, timeZone)

    return when {
        period.years > 0 -> "${period.years}y"
        period.months > 0 -> "${period.months}mo"
        period.days > 0 -> "${period.days}d"
        period.hours > 0 -> "${period.hours}h"
        period.minutes > 0 -> "${period.minutes}m"
        else -> "now" // For periods less than a minute
    }
}
