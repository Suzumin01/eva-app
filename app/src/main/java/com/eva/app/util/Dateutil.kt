package com.eva.app.util

import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

/** yyyy-MM-dd  →  dd.MM.yyyy */
fun formatDate(isoDate: String): String {
    return try {
        val d = LocalDate.parse(isoDate.take(10))
        d.format(DateTimeFormatter.ofPattern("dd.MM.yyyy"))
    } catch (e: Exception) { isoDate }
}

/** dd.MM.yyyy  →  yyyy-MM-dd (для API) */
fun parseDisplayDate(display: String): String {
    return try {
        val d = LocalDate.parse(display, DateTimeFormatter.ofPattern("dd.MM.yyyy"))
        d.toString()
    } catch (e: Exception) { display }
}

/** yyyy-MM-dd  →  "Сегодня", "Завтра", "Пт, 14.06" и т.д. */
fun formatDateLabel(isoDate: String): String {
    return try {
        val d     = LocalDate.parse(isoDate.take(10))
        val today = LocalDate.now()
        when (d) {
            today            -> "Сегодня, ${d.format(DateTimeFormatter.ofPattern("dd.MM"))}"
            today.plusDays(1) -> "Завтра, ${d.format(DateTimeFormatter.ofPattern("dd.MM"))}"
            else             -> d.format(DateTimeFormatter.ofPattern("EEE, dd.MM", Locale("ru")))
                .replaceFirstChar { it.uppercase() }
        }
    } catch (e: Exception) { isoDate }
}

/** HH:mm:ss  →  HH:mm */
fun formatTime(isoTime: String): String = isoTime.take(5)