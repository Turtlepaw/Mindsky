package io.github.turtlepaw.mindsky.utils

import java.text.DecimalFormat
import java.text.NumberFormat
import java.util.Locale

object Formatters {
    fun formatNumberForLocale(number: Int): String {
        val numberFormat = NumberFormat.getNumberInstance(Locale.getDefault())
        return numberFormat.format(number)
    }

    fun formatCompactNumber(number: Int): String {
        return when {
            number >= 1_000_000_000 -> formatWithSuffix(number, 1_000_000_000, "B")
            number >= 1_000_000 -> formatWithSuffix(number, 1_000_000, "M")
            number >= 1_000 -> formatWithSuffix(number, 1_000, "K")
            else -> number.toString()
        }
    }

    private fun formatWithSuffix(number: Int, divisor: Int, suffix: String): String {
        val value = number.toDouble() / divisor
        val formatted = DecimalFormat("#.#").format(value) // 1 decimal place max
        return "$formatted$suffix"
    }
}
