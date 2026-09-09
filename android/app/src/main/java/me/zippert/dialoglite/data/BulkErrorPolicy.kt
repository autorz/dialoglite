package me.zippert.dialoglite.data

/**
 * Classifica os erros que `/day/bulk_update` devolve DENTRO de um HTTP 200.
 *
 * Textos observados em `app/routes.py`:
 *  - `"data inválida"`
 *  - `"dia não encontrado"`
 *  - `"mais de 2 períodos; horários não alterados — use edição avançada"`
 *  - `"erro nos horários: <excecao>"`
 */
object BulkErrorPolicy {

    /**
     * Erro que nao melhora sozinho: reenviar so gasta bateria e mantem lixo na
     * fila. Vai pra [me.zippert.dialoglite.data.local.PendingEditEntity.blocked]
     * e aparece na UI pedindo acao do usuario.
     *
     * `"dia não encontrado"` fica FORA desta lista de proposito: depois do
     * `GET /api/history` (que roda `auto_populate_days()`) ele costuma sumir,
     * entao vale tentar de novo ate o teto de tentativas.
     */
    fun isPermanent(error: String): Boolean {
        val normalized = error.lowercase()
        return PERMANENT_MARKERS.any { it in normalized }
    }

    private val PERMANENT_MARKERS = listOf(
        "mais de 2",
        "data inválida",
        "data invalida",
        "erro nos horários",
        "erro nos horarios",
    )
}
