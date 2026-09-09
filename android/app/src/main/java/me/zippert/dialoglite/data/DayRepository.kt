package me.zippert.dialoglite.data

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.encodeToString
import me.zippert.dialoglite.data.local.BalanceEntity
import me.zippert.dialoglite.data.local.DayEntity
import me.zippert.dialoglite.data.local.DiaLogDao
import me.zippert.dialoglite.data.local.PendingEditEntity
import me.zippert.dialoglite.data.local.PeriodValue
import me.zippert.dialoglite.data.prefs.PreferencesSource
import me.zippert.dialoglite.data.remote.ApiFactory
import me.zippert.dialoglite.data.remote.BaseUrlInterceptor
import me.zippert.dialoglite.data.remote.DiaLogApi
import me.zippert.dialoglite.data.remote.NoBaseUrlException
import me.zippert.dialoglite.data.remote.dto.BulkRowDto
import me.zippert.dialoglite.data.remote.dto.BulkUpdateRequest
import me.zippert.dialoglite.data.remote.dto.DayDto
import me.zippert.dialoglite.data.remote.dto.HistoryDto
import me.zippert.dialoglite.util.TimeFormats
import retrofit2.HttpException
import java.io.IOException

class DayRepository(
    private val dao: DiaLogDao,
    private val prefs: PreferencesSource,
    private val api: DiaLogApi,
    private val baseUrlInterceptor: BaseUrlInterceptor,
) {

    private val syncMutex = Mutex()
    private val _status = MutableStateFlow(SyncStatus())
    val status: StateFlow<SyncStatus> = _status.asStateFlow()

    val baseUrl: Flow<String?> = prefs.baseUrl

    val balancePretty: Flow<String?> = dao.observeBalance().map { it?.currentBalancePretty }

    val pendingCount: Flow<Int> = dao.observePending().map { list -> list.count { !it.blocked } }

    val blockedCount: Flow<Int> = dao.observePending().map { list -> list.count { it.blocked } }

    /** Dias do servidor com as edicoes locais pendentes sobrepostas. */
    val days: Flow<List<DayUi>> =
        combine(dao.observeDays(), dao.observePending()) { days, pending ->
            val byDate = pending.associateBy { it.date }
            days.map { day ->
                val edit = byDate[day.date]
                val serverPeriods = decodePeriods(day.periodsJson)
                DayUi(
                    date = day.date,
                    isWeekend = day.isWeekend,
                    isHoliday = day.isHoliday,
                    isConsolidated = day.isConsolidated,
                    periods = edit?.periodsJson?.let(::decodePeriods) ?: serverPeriods,
                    serverPeriods = serverPeriods,
                    notes = edit?.notes ?: day.notes,
                    workedHoursPretty = day.workedHoursPretty,
                    expectedHoursPretty = day.expectedHoursPretty,
                    dailyDeltaPretty = day.dailyDeltaPretty,
                    balancePretty = day.balancePretty,
                    overridePretty = day.overridePretty,
                    hasPendingEdit = edit != null,
                    pendingBlocked = edit?.blocked == true,
                    pendingError = edit?.lastError,
                )
            }
        }

    suspend fun setBaseUrl(value: String) {
        prefs.setBaseUrl(value)
        baseUrlInterceptor.setBaseUrl(value)
    }

    /** Chamado no start do processo pra o interceptor ja sair configurado. */
    suspend fun primeBaseUrl() {
        baseUrlInterceptor.setBaseUrl(prefs.baseUrl.first())
    }

    /**
     * Guarda uma edicao local. Nada vai pra rede aqui — quem envia e o
     * [me.zippert.dialoglite.sync.SyncWorker].
     *
     * [notes] e [periods] nulos significam "nao editei isso", e ficam de fora
     * do payload. Cuidado especifico do contrato: mandar `entries: []` para o
     * servidor APAGA os periodos do dia, entao "nao editei" nao pode virar
     * lista vazia no caminho.
     */
    suspend fun queueEdit(date: String, notes: String?, periods: List<PeriodValue>?) {
        val now = System.currentTimeMillis()
        val existing = dao.getPending(date)
        val merged = PendingEditEntity(
            date = date,
            notes = notes ?: existing?.notes,
            periodsJson = periods?.let { ApiFactory.json.encodeToString(it) } ?: existing?.periodsJson,
            createdAt = existing?.createdAt ?: now,
            updatedAt = now,
            // Uma edicao nova zera o historico de falha: o usuario pode ter
            // acabado de corrigir justamente o que o servidor recusou.
            attempts = 0,
            lastError = null,
            blocked = false,
        )
        dao.upsertPending(merged)
    }

    suspend fun discardEdit(date: String) = dao.deletePending(date)

    /**
     * Ordem obrigatoria, ditada pelo servidor:
     *
     *  1. `GET /api/history` — e a unica rota que chama `auto_populate_days()`,
     *     ou seja, a unica que CRIA `DayRecord`. `/day/bulk_update` faz
     *     `DayRecord.query.get(date)` e devolve "dia nao encontrado" pra data
     *     inexistente. Sem este passo, ficar offline atravessando a meia-noite
     *     faz o lancamento do dia novo falhar.
     *  2. despejar a fila em `/day/bulk_update`.
     *  3. `GET /api/history` de novo, pra trazer saldo e deltas recalculados.
     */
    suspend fun sync(): SyncOutcome = syncMutex.withLock {
        _status.value = _status.value.copy(inProgress = true, lastAttemptAt = System.currentTimeMillis())
        val outcome = runSync()
        _status.value = _status.value.copy(
            inProgress = false,
            reachable = outcome is SyncOutcome.Success,
            lastSuccessAt = if (outcome is SyncOutcome.Success) System.currentTimeMillis() else _status.value.lastSuccessAt,
            lastError = when (outcome) {
                is SyncOutcome.Success -> null
                is SyncOutcome.Unreachable -> outcome.reason
                is SyncOutcome.Failed -> outcome.reason
                SyncOutcome.NotConfigured -> null
            },
        )
        outcome
    }

    private suspend fun runSync(): SyncOutcome {
        val base = prefs.baseUrl.first()
        if (base.isNullOrBlank()) return SyncOutcome.NotConfigured
        baseUrlInterceptor.setBaseUrl(base)

        // Passo 1 — cria os dias faltantes no servidor e atualiza o cache.
        val firstHistory = try {
            api.getHistory()
        } catch (e: NoBaseUrlException) {
            return SyncOutcome.NotConfigured
        } catch (e: IOException) {
            return SyncOutcome.Unreachable(e.friendlyMessage())
        } catch (e: HttpException) {
            return SyncOutcome.Failed("Servidor respondeu HTTP ${e.code()}")
        } catch (e: Exception) {
            return SyncOutcome.Failed(e.friendlyMessage())
        }
        storeHistory(firstHistory)

        // Passo 2 — despeja a fila.
        val queue = dao.pendingToSend()
        if (queue.isEmpty()) return SyncOutcome.Success(pushed = 0, failed = emptyList())

        val rows = queue.map { it.toRow() }
        val response = try {
            api.bulkUpdate(BulkUpdateRequest(rows))
        } catch (e: IOException) {
            return SyncOutcome.Unreachable(e.friendlyMessage())
        } catch (e: HttpException) {
            return SyncOutcome.Failed("Servidor respondeu HTTP ${e.code()}")
        } catch (e: Exception) {
            return SyncOutcome.Failed(e.friendlyMessage())
        }

        // ARMADILHA: HTTP 200 + status "ok" NAO significa sucesso. As falhas —
        // parciais ou totais — vem em `errors[]`. O cliente web do projeto tem
        // esse bug e engole o erro; aqui `errors[]` manda.
        val errorsByDate = response.errors
            .mapNotNull { err -> err.date?.let { it to err.error } }
            .toMap()

        var pushed = 0
        for (edit in queue) {
            val error = errorsByDate[edit.date]
            // `updatedAt` e o guarda contra a corrida: se o usuario salvou de
            // novo o mesmo dia enquanto o POST estava no ar, a linha mudou e
            // nem o delete nem a marcacao de falha a alcancam.
            if (error == null) {
                dao.deletePendingIfUnchanged(edit.date, edit.updatedAt)
                pushed++
            } else {
                val attempts = edit.attempts + 1
                dao.markPendingFailure(
                    date = edit.date,
                    updatedAt = edit.updatedAt,
                    attempts = attempts,
                    error = error,
                    blocked = BulkErrorPolicy.isPermanent(error) || attempts >= MAX_ATTEMPTS,
                )
            }
        }

        // Passo 3 — saldo e deltas recalculados pelo servidor.
        runCatching { api.getHistory() }.getOrNull()?.let { storeHistory(it) }

        return SyncOutcome.Success(pushed = pushed, failed = errorsByDate.keys.toList())
    }

    private suspend fun storeHistory(history: HistoryDto) {
        val now = System.currentTimeMillis()
        dao.replaceHistory(
            days = history.days.map { it.toEntity(now) },
            balance = BalanceEntity(
                currentBalance = history.currentBalance,
                currentBalancePretty = history.currentBalancePretty,
                fetchedAt = now,
            ),
        )
    }

    private fun PendingEditEntity.toRow(): BulkRowDto {
        val periods = periodsJson?.let(::decodePeriods)
        return BulkRowDto(
            date = date,
            // `zip(entries, exits)` no servidor trunca pelo menor: as duas
            // listas tem que ter o mesmo tamanho, com null onde o periodo
            // esta em aberto.
            entries = periods?.map { it.entry },
            exits = periods?.map { it.exit },
            notes = notes,
        )
    }

    private fun decodePeriods(json: String): List<PeriodValue> =
        runCatching { ApiFactory.json.decodeFromString<List<PeriodValue>>(json) }.getOrDefault(emptyList())

    private fun DayDto.toEntity(now: Long) = DayEntity(
        date = date,
        isWeekend = isWeekend,
        isHoliday = isHoliday,
        manualHoliday = manualHoliday,
        isConsolidated = isConsolidated,
        workedHours = workedHours,
        workedHoursPretty = workedHoursPretty,
        expectedHours = expectedHours,
        expectedHoursPretty = expectedHoursPretty,
        dailyDelta = dailyDelta,
        dailyDeltaPretty = dailyDeltaPretty,
        balance = balance,
        balancePretty = balancePretty,
        notes = notes,
        overrideValue = override,
        overridePretty = overridePretty,
        // Normaliza HH:MM:SS -> HH:MM na entrada, ja no formato que o
        // bulk_update aceita. Evita reenviar segundos por engano.
        periodsJson = ApiFactory.json.encodeToString(
            periods.mapNotNull { p ->
                TimeFormats.toHourMinute(p.entryTime)?.let { entry ->
                    PeriodValue(entry = entry, exit = TimeFormats.toHourMinute(p.exitTime))
                }
            }
        ),
        fetchedAt = now,
    )

    private companion object {
        const val MAX_ATTEMPTS = 5

        fun Throwable.friendlyMessage(): String =
            message?.takeIf { it.isNotBlank() } ?: this::class.simpleName.orEmpty()
    }
}
