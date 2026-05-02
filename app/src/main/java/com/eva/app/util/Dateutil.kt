package com.eva.app.util

import java.time.LocalDate
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale

/** yyyy-MM-dd  →  dd.MM.yyyy */
fun formatDate(isoDate: String): String {
    return try {
        val d = LocalDate.parse(isoDate.take(10))
        d.format(DateTimeFormatter.ofPattern("dd.MM.yyyy"))
    } catch (e: Exception) { isoDate }
}

/** ISO datetime → "10:45" (today), "Вчера", "Пн" (this week), "3 мая" (older) */
fun formatNotifDate(isoDateTime: String): String {
    return try {
        val ldt   = LocalDateTime.parse(isoDateTime.take(19))
        val today = LocalDate.now()
        val date  = ldt.toLocalDate()
        when {
            date == today              -> ldt.format(DateTimeFormatter.ofPattern("HH:mm"))
            date == today.minusDays(1) -> "Вчера"
            date.isAfter(today.minusDays(7)) ->
                date.format(DateTimeFormatter.ofPattern("EEE", Locale("ru")))
                    .replaceFirstChar { it.uppercase() }
            else -> date.format(DateTimeFormatter.ofPattern("d MMM", Locale("ru")))
        }
    } catch (e: Exception) { formatDate(isoDateTime) }
}

/** ISO datetime → "Сегодня в 10:45", "Вчера в 10:45", "3 мая 2026, 10:45" */
fun formatDateFull(isoDateTime: String): String {
    return try {
        val ldt   = LocalDateTime.parse(isoDateTime.take(19))
        val today = LocalDate.now()
        val date  = ldt.toLocalDate()
        val time  = ldt.format(DateTimeFormatter.ofPattern("HH:mm"))
        when {
            date == today              -> "Сегодня в $time"
            date == today.minusDays(1) -> "Вчера в $time"
            else -> "${date.format(DateTimeFormatter.ofPattern("d MMMM yyyy", Locale("ru")))}, $time"
        }
    } catch (e: Exception) { formatDate(isoDateTime) }
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