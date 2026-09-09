package me.zippert.dialoglite.data

import me.zippert.dialoglite.data.local.PeriodValue

/**
 * O que a tela mostra: o dia como o servidor o conhece, com a edicao local
 * ainda nao sincronizada sobreposta por cima.
 *
 * Os numeros (saldo, horas esperadas, feriado) sao SEMPRE os do servidor —
 * a regra de calculo e do servidor e nao e replicada aqui. Quando ha edicao
 * pendente, [hasPendingEdit] avisa a UI que esses numeros ainda nao refletem
 * o que o usuario digitou.
 */
data class DayUi(
    val date: String,
    val isWeekend: Boolean,
    val isHoliday: Boolean,
    val isConsolidated: Boolean,
    /** Periodos ja com a edicao local aplicada, se houver. */
    val periods: List<PeriodValue>,
    /** Periodos como o servidor os tem — base pra saber o que mudou. */
    val serverPeriods: List<PeriodValue>,
    val notes: String,
    val workedHoursPretty: String,
    val expectedHoursPretty: String,
    val dailyDeltaPretty: String,
    val balancePretty: String,
    val overridePretty: String?,
    val hasPendingEdit: Boolean,
    val pendingBlocked: Boolean,
    val pendingError: String?,
) {
    /**
     * O servidor recusa edicao rapida em dia com mais de 2 periodos
     * ("use edicao avancada"). O app respeita isso e nem oferece o formulario:
     * enviar 2 periodos aqui apagaria os demais.
     */
    val requiresAdvancedEditing: Boolean get() = serverPeriods.size > 2
}

data class SyncStatus(
    val inProgress: Boolean = false,
    val lastSuccessAt: Long? = null,
    val lastAttemptAt: Long? = null,
    val lastError: String? = null,
    val reachable: Boolean? = null,
)
