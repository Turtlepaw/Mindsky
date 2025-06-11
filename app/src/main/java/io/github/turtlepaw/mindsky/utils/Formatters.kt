package io.github.turtlepaw.mindsky.utils

import java.text.NumberFormat
import java.util.Locale

object Formatters {
    fun formatNumberForLocale(number: Int): String {
        val numberFormat = NumberFormat.getNumberInstance(Locale.getDefault())
        return numberFormat.format(number)
    }
}