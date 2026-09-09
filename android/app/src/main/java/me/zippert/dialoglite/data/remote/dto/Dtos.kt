package me.zippert.dialoglite.data.remote.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Espelho do contrato do servidor (`app/api.py` e `app/routes.py` do Dia Log Lite).
 *
 * Cuidados que o contrato impoe e que estao codificados aqui:
 *  - `/api/history` devolve a lista em `days` (nao `history`).
 *  - horarios saem em `HH:MM:SS`, mas `/day/bulk_update` so aceita `HH:MM`
 *    (o servidor faz `strptime(..., '%H:%M')`). A conversao vive em
 *    [me.zippert.dialoglite.util.TimeFormats].
 */

@Serializable
data class PeriodDto(
    @SerialName("entry_time") val entryTime: String,
    @SerialName("exit_time") val exitTime: String? = null,
)

@Serializable
data class DayDto(
    val date: String,
    @SerialName("is_weekend") val isWeekend: Boolean = false,
    @SerialName("is_holiday") val isHoliday: Boolean = false,
    @SerialName("worked_hours") val workedHours: Double = 0.0,
    @SerialName("worked_hours_pretty") val workedHoursPretty: String = "",
    @SerialName("expected_hours") val expectedHours: Double = 0.0,
    @SerialName("expected_hours_pretty") val expectedHoursPretty: String = "",
    @SerialName("daily_delta") val dailyDelta: Double = 0.0,
    @SerialName("daily_delta_pretty") val dailyDeltaPretty: String = "",
    val balance: Double = 0.0,
    @SerialName("balance_pretty") val balancePretty: String = "",
    val notes: String = "",
    @SerialName("manual_holiday") val manualHoliday: Boolean = false,
    val override: Double? = null,
    @SerialName("override_pretty") val overridePretty: String? = null,
    @SerialName("is_consolidated") val isConsolidated: Boolean = false,
    val periods: List<PeriodDto> = emptyList(),
)

@Serializable
data class HistoryDto(
    @SerialName("current_balance") val currentBalance: Double = 0.0,
    @SerialName("current_balance_pretty") val currentBalancePretty: String = "",
    val days: List<DayDto> = emptyList(),
)

@Serializable
data class SettingsDto(
    @SerialName("start_date") val startDate: String,
    @SerialName("default_entry") val defaultEntry: String,
    @SerialName("default_lunch_start") val defaultLunchStart: String,
    @SerialName("default_lunch_end") val defaultLunchEnd: String,
    @SerialName("default_exit") val defaultExit: String,
)

/**
 * Uma linha de `/day/bulk_update`.
 *
 * Semantica do servidor, que dita quando cada campo pode ser omitido:
 *  - `entries`/`exits` ausentes  -> horarios nao sao tocados.
 *  - `entries` presente e VAZIO  -> **apaga todos os periodos do dia**.
 *  - `notes` ausente             -> observacao nao e tocada.
 *
 * Por isso os campos sao nullable e o encoder e configurado com
 * `explicitNulls = false`: null aqui significa "nao mexe", nao "limpa".
 */
@Serializable
data class BulkRowDto(
    val date: String,
    val entries: List<String>? = null,
    val exits: List<String?>? = null,
    val notes: String? = null,
)

@Serializable
data class BulkUpdateRequest(val rows: List<BulkRowDto>)

@Serializable
data class BulkErrorDto(
    val date: String? = null,
    val error: String = "",
)

/**
 * ARMADILHA: este corpo chega com HTTP 200 mesmo em falha total.
 * `status` continua `"ok"` e as falhas vem em [errors]. Um `date` que aparece
 * em [errors] falhou, ponto — mesmo que [updated] o tenha contado.
 */
@Serializable
data class BulkUpdateResponse(
    val status: String = "",
    val updated: Int = 0,
    val errors: List<BulkErrorDto> = emptyList(),
    val message: String? = null,
)
