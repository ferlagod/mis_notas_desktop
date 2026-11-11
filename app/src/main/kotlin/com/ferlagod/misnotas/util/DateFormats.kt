// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 ferlagod
// Mis Notas — https://github.com/ferlagod/mis_notas_desktop

package com.ferlagod.misnotas.util

import com.ferlagod.misnotas.R
import com.ferlagod.misnotas.i18n.Strings
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.time.temporal.ChronoUnit

/** Fechas en el idioma de la app (sustituye a `android.text.format.DateFormat`/`DateUtils`). */
object DateFormats {

    private fun zoned(timestamp: Long) = Instant.ofEpochMilli(timestamp).atZone(ZoneId.systemDefault())

    /** Fecha y hora cortas, p. ej. "11 nov 2026, 16:38". */
    fun dateTime(timestamp: Long): String =
        DateTimeFormatter.ofLocalizedDateTime(FormatStyle.MEDIUM, FormatStyle.SHORT)
            .withLocale(Strings.locale)
            .format(zoned(timestamp))

    /** Solo la fecha, p. ej. "11 nov 2026". */
    fun date(timestamp: Long): String =
        DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM).withLocale(Strings.locale).format(zoned(timestamp))

    /** "Ahora", "Hace 5 min", "Hace 3 h", "Ayer", "Hace 4 días"; fecha corta a partir de una semana. */
    fun relative(timestamp: Long, now: Long = System.currentTimeMillis()): String {
        val diff = now - timestamp
        if (diff < 0) return date(timestamp)
        val minutes = diff / 60_000
        val days = ChronoUnit.DAYS.between(zoned(timestamp).toLocalDate(), LocalDate.now(ZoneId.systemDefault())).toInt()
        return when {
            minutes < 1 -> Strings.get(R.string.time_just_now)
            minutes < 60 -> Strings.quantity(R.plurals.time_minutes_ago, minutes.toInt())
            days == 0 -> (minutes / 60).toInt().let { Strings.quantity(R.plurals.time_hours_ago, it) }
            days == 1 -> Strings.get(R.string.time_yesterday)
            days < 7 -> Strings.quantity(R.plurals.time_days_ago, days)
            else -> date(timestamp)
        }
    }
}
