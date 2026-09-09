package me.zippert.dialoglite.data.remote

import me.zippert.dialoglite.data.remote.dto.BulkUpdateRequest
import me.zippert.dialoglite.data.remote.dto.BulkUpdateResponse
import me.zippert.dialoglite.data.remote.dto.HistoryDto
import me.zippert.dialoglite.data.remote.dto.SettingsDto
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST

interface DiaLogApi {

    /**
     * Alem de ler, este GET tem efeito colateral no servidor: ele chama
     * `auto_populate_days()`, que e o UNICO caminho que cria `DayRecord` novo.
     * Como `/day/bulk_update` nao cria dia, chamar isto antes de despejar a
     * fila e obrigatorio (ver [me.zippert.dialoglite.data.DayRepository.sync]).
     */
    @GET("api/history")
    suspend fun getHistory(): HistoryDto

    @GET("api/settings")
    suspend fun getSettings(): SettingsDto

    @POST("day/bulk_update")
    suspend fun bulkUpdate(@Body body: BulkUpdateRequest): BulkUpdateResponse
}
