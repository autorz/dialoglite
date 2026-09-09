package me.zippert.dialoglite.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey
import kotlinx.serialization.Serializable

/** Cache do que o servidor disse na ultima vez que deu pra falar com ele. */
@Entity(tableName = "days")
data class DayEntity(
    @PrimaryKey val date: String,
    val isWeekend: Boolean,
    val isHoliday: Boolean,
    val manualHoliday: Boolean,
    val isConsolidated: Boolean,
    val workedHours: Double,
    val workedHoursPretty: String,
    val expectedHours: Double,
    val expectedHoursPretty: String,
    val dailyDelta: Double,
    val dailyDeltaPretty: String,
    val balance: Double,
    val balancePretty: String,
    val notes: String,
    val overrideValue: Double?,
    val overridePretty: String?,
    val periodsJson: String,
    val fetchedAt: Long,
)

/** Periodo em `HH:MM` — ja no formato que `/day/bulk_update` aceita. */
@Serializable
data class PeriodValue(
    val entry: String,
    val exit: String? = null,
)

/**
 * Uma edicao local ainda nao confirmada pelo servidor. Ha no maximo uma por
 * data: `bulk_update` substitui os periodos do dia inteiro, entao edicoes
 * sucessivas do mesmo dia colapsam numa so (e o replay e seguro).
 *
 * `notes` e `periodsJson` nulos significam "este aspecto nao foi editado" e
 * saem omitidos do payload — mandar `entries: []` APAGARIA os periodos do dia.
 */
@Entity(tableName = "pending_edits")
data class PendingEditEntity(
    @PrimaryKey val date: String,
    val notes: String?,
    val periodsJson: String?,
    val createdAt: Long,
    val updatedAt: Long,
    val attempts: Int = 0,
    val lastError: String? = null,
    /**
     * Falha que nao adianta repetir (ex.: dia com mais de 2 periodos, que o
     * servidor manda editar na tela avancada). Sai da fila de envio e vira
     * aviso na UI ate o usuario resolver ou descartar.
     */
    val blocked: Boolean = false,
)

/** Linha unica com o saldo consolidado que veio do servidor. */
@Entity(tableName = "balance")
data class BalanceEntity(
    @PrimaryKey val id: Int = 0,
    val currentBalance: Double,
    val currentBalancePretty: String,
    val fetchedAt: Long,
)
