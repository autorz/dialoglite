package me.zippert.dialoglite.util

import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * O servidor DEVOLVE horario em `HH:MM:SS` (pydantic `datetime.time`) mas
 * `/day/bulk_update` ACEITA apenas `HH:MM` — `core.update_day_periods` faz
 * `datetime.strptime(entry_str, '%H:%M')`, que estoura com segundos e vira
 * `{"error": "erro nos horarios: ..."}` dentro de um HTTP 200.
 *
 * Reenviar cru o que veio do `/api/history` e, portanto, falha garantida.
 */
object TimeFormats {

    /** `"09:00:00"` -> `"09:00"`. Ja aceita `"09:00"` sem alterar. */
    fun toHourMinute(raw: String?): String? {
        val value = raw?.trim().orEmpty()
        if (value.isEmpty()) return null
        val parts = value.split(":")
        if (parts.size < 2) return null
        val hour = parts[0].padStart(2, '0')
        val minute = parts[1].padStart(2, '0')
        return "$hour:$minute"
    }

    /** Valida `HH:MM` com hora 00-23 e minuto 00-59. */
    fun isValidHourMinute(value: String): Boolean {
        val match = Regex("""^(\d{2}):(\d{2})$""").find(value) ?: return false
        val (h, m) = match.destructured
        return h.toInt() in 0..23 && m.toInt() in 0..59
    }

    fun minutesOf(hhmm: String): Int {
        val parts = hhmm.split(":")
        return parts[0].toInt() * 60 + parts[1].toInt()
    }
}

object DateFormats {
    private val iso: DateTimeFormatter = DateTimeFormatter.ISO_LOCAL_DATE
    private val ptBr = Locale.forLanguageTag("pt-BR")
    private val dayMonth: DateTimeFormatter = DateTimeFormatter.ofPattern("dd/MM", ptBr)

    private val weekdays = listOf("Seg", "Ter", "Qua", "Qui", "Sex", "Sáb", "Dom")

    fun parseOrNull(value: String): LocalDate? = runCatching { LocalDate.parse(value, iso) }.getOrNull()

    fun prettyDayMonth(value: String): String =
        parseOrNull(value)?.format(dayMonth) ?: value

    /** `weekdays` segue `LocalDate.dayOfWeek.value` (1 = segunda). */
    fun weekdayShort(value: String): String =
        parseOrNull(value)?.let { weekdays[it.dayOfWeek.value - 1] } ?: ""

    fun today(): String = LocalDate.now().format(iso)
}
