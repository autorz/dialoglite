package me.zippert.dialoglite

import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import me.zippert.dialoglite.data.DayRepository
import me.zippert.dialoglite.data.SyncOutcome
import me.zippert.dialoglite.data.local.PeriodValue
import me.zippert.dialoglite.data.remote.ApiFactory
import me.zippert.dialoglite.data.remote.BaseUrlInterceptor
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.RecordedRequest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * Cobre as armadilhas do contrato do Dia Log Lite. Cada teste aqui existe
 * porque quebrar aquilo silenciosamente e o modo de falha natural.
 */
class SyncSequenceTest {

    private lateinit var server: MockWebServer
    private lateinit var dao: FakeDao
    private lateinit var repository: DayRepository

    @Before
    fun setUp() {
        server = MockWebServer()
        server.start()
        dao = FakeDao()
        val interceptor = BaseUrlInterceptor()
        repository = DayRepository(
            dao = dao,
            prefs = FakePreferences(server.url("/").toString()),
            api = ApiFactory.create(interceptor),
            baseUrlInterceptor = interceptor,
        )
    }

    @After
    fun tearDown() = server.shutdown()

    private fun historyBody(
        entry: String = "09:00:00",
        exit: String = "12:00:00",
        notes: String = "",
    ) = """
        {
          "current_balance": 1.5,
          "current_balance_pretty": "+01:30",
          "days": [
            {
              "date": "2026-09-08",
              "is_weekend": false,
              "is_holiday": false,
              "worked_hours": 8.0, "worked_hours_pretty": "08:00",
              "expected_hours": 8.0, "expected_hours_pretty": "08:00",
              "daily_delta": 0.0, "daily_delta_pretty": "00:00",
              "balance": 1.5, "balance_pretty": "+01:30",
              "notes": "$notes",
              "manual_holiday": false,
              "override": null, "override_pretty": null,
              "is_consolidated": false,
              "periods": [{"entry_time": "$entry", "exit_time": "$exit"}]
            }
          ]
        }
    """.trimIndent()

    private fun ok(body: String) = MockResponse().setResponseCode(200).setBody(body)

    private fun RecordedRequest.bodyJson(): JsonObject =
        Json.parseToJsonElement(body.readUtf8()).jsonObject

    /**
     * ORDEM OBRIGATORIA: `/api/history` antes de `/day/bulk_update`.
     * `bulk_update` faz `DayRecord.query.get(date)` e nao cria dia nenhum;
     * quem cria e o `auto_populate_days()` que roda no GET. Sem esta ordem,
     * ficar offline atravessando a meia-noite faz o dia novo falhar.
     */
    @Test
    fun `history vem antes do bulk_update`() = runTest {
        server.enqueue(ok(historyBody()))
        server.enqueue(ok("""{"status":"ok","updated":1,"errors":[]}"""))
        server.enqueue(ok(historyBody()))

        repository.queueEdit("2026-09-08", notes = "reuniao", periods = null)
        val outcome = repository.sync()

        assertTrue(outcome is SyncOutcome.Success)
        assertEquals("/api/history", server.takeRequest().path)
        assertEquals("/day/bulk_update", server.takeRequest().path)
        assertEquals("/api/history", server.takeRequest().path)
    }

    /**
     * ARMADILHA 1: HTTP 200 + `"status":"ok"` com `errors[]` nao-vazio e FALHA.
     * O cliente web do projeto engole isso. Aqui a edicao tem que permanecer
     * na fila, marcada com o erro.
     */
    @Test
    fun `errors nao-vazio com HTTP 200 mantem a edicao na fila`() = runTest {
        server.enqueue(ok(historyBody()))
        server.enqueue(
            ok("""{"status":"ok","updated":0,"errors":[{"date":"2026-09-08","error":"dia não encontrado"}]}""")
        )

        repository.queueEdit("2026-09-08", notes = "x", periods = null)
        val outcome = repository.sync() as SyncOutcome.Success

        assertEquals(0, outcome.pushed)
        assertEquals(listOf("2026-09-08"), outcome.failed)

        val pending = dao.pending.value.single()
        assertEquals("2026-09-08", pending.date)
        assertEquals(1, pending.attempts)
        assertEquals("dia não encontrado", pending.lastError)
        // "dia nao encontrado" costuma sumir depois do auto_populate: vale
        // insistir, entao nao bloqueia na primeira.
        assertFalse(pending.blocked)
    }

    /** Erro que nao melhora sozinho sai da fila de envio na hora. */
    @Test
    fun `mais de 2 periodos bloqueia a pendencia`() = runTest {
        server.enqueue(ok(historyBody()))
        server.enqueue(
            ok(
                """{"status":"ok","updated":1,"errors":[{"date":"2026-09-08",""" +
                    """"error":"mais de 2 períodos; horários não alterados — use edição avançada"}]}"""
            )
        )

        repository.queueEdit("2026-09-08", notes = null, periods = listOf(PeriodValue("09:00", "12:00")))
        repository.sync()

        assertTrue(dao.pending.value.single().blocked)
    }

    /**
     * ARMADILHA 4 (nao estava no briefing): o servidor DEVOLVE `HH:MM:SS` mas
     * `core.update_day_periods` faz `strptime(..., '%H:%M')`. Reenviar cru o
     * que veio do `/api/history` vira `erro nos horários` dentro de um 200.
     */
    @Test
    fun `horarios sobem em HH MM, nunca com segundos`() = runTest {
        server.enqueue(ok(historyBody(entry = "09:00:00", exit = "12:00:00")))
        server.enqueue(ok("""{"status":"ok","updated":1,"errors":[]}"""))
        server.enqueue(ok(historyBody()))

        repository.queueEdit(
            "2026-09-08",
            notes = null,
            periods = listOf(PeriodValue("09:15", "12:00")),
        )
        repository.sync()

        server.takeRequest() // GET /api/history
        val row = server.takeRequest().bodyJson()["rows"]!!.jsonArray.single().jsonObject

        assertEquals("09:15", row["entries"]!!.jsonArray.single().jsonPrimitive.content)
        assertEquals("12:00", row["exits"]!!.jsonArray.single().jsonPrimitive.content)
    }

    /**
     * O cache tambem normaliza na ENTRADA: o dia lido do servidor guarda
     * `09:00`, nao `09:00:00`, pra nunca haver segundos no caminho de volta.
     */
    @Test
    fun `cache normaliza HH MM SS vindo do servidor`() = runTest {
        server.enqueue(ok(historyBody(entry = "08:30:00", exit = "17:45:00")))

        repository.sync()

        val periods = repository.days.first().single().serverPeriods
        assertEquals(listOf(PeriodValue("08:30", "17:45")), periods)
    }

    /**
     * ARMADILHA de payload: `entries: []` no servidor APAGA os periodos do dia.
     * Editar so a observacao tem que omitir `entries`/`exits` por completo.
     */
    @Test
    fun `editar so a observacao nao manda entries`() = runTest {
        server.enqueue(ok(historyBody()))
        server.enqueue(ok("""{"status":"ok","updated":1,"errors":[]}"""))
        server.enqueue(ok(historyBody()))

        repository.queueEdit("2026-09-08", notes = "so nota", periods = null)
        repository.sync()

        server.takeRequest()
        val row = server.takeRequest().bodyJson()["rows"]!!.jsonArray.single().jsonObject

        assertNull(row["entries"])
        assertNull(row["exits"])
        assertEquals("so nota", row["notes"]!!.jsonPrimitive.content)
    }

    /** Simetrico: editar so horario nao pode zerar a observacao no servidor. */
    @Test
    fun `editar so horario nao manda notes`() = runTest {
        server.enqueue(ok(historyBody()))
        server.enqueue(ok("""{"status":"ok","updated":1,"errors":[]}"""))
        server.enqueue(ok(historyBody()))

        repository.queueEdit("2026-09-08", notes = null, periods = listOf(PeriodValue("09:00", null)))
        repository.sync()

        server.takeRequest()
        val row = server.takeRequest().bodyJson()["rows"]!!.jsonArray.single().jsonObject

        assertNull(row["notes"])
        // Periodo em aberto: `exits` precisa existir e ter o MESMO tamanho de
        // `entries` — o servidor faz `zip(entries, exits)` e trunca pelo menor.
        assertEquals(1, row["entries"]!!.jsonArray.size)
        assertEquals(1, row["exits"]!!.jsonArray.size)
    }

    /** Servidor fora do ar e estado normal: a fila fica intacta. */
    @Test
    fun `servidor inalcancavel preserva a fila`() = runTest {
        repository.queueEdit("2026-09-08", notes = "x", periods = null)
        server.shutdown()

        val outcome = repository.sync()

        assertTrue(outcome is SyncOutcome.Unreachable)
        assertEquals(1, dao.pending.value.size)
        assertEquals(0, dao.pending.value.single().attempts)
    }

    /** Edicoes sucessivas do mesmo dia colapsam numa linha so. */
    @Test
    fun `edicoes do mesmo dia colapsam e fundem os campos`() = runTest {
        repository.queueEdit("2026-09-08", notes = "primeira", periods = null)
        repository.queueEdit("2026-09-08", notes = null, periods = listOf(PeriodValue("10:00", "19:00")))

        val pending = dao.pending.value.single()
        assertEquals("primeira", pending.notes)
        assertTrue(pending.periodsJson!!.contains("10:00"))
    }
}
