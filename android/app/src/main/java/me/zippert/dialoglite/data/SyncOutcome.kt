package me.zippert.dialoglite.data

sealed interface SyncOutcome {
    /** Sincronizou. [failed] traz as datas que o servidor recusou. */
    data class Success(val pushed: Int, val failed: List<String>) : SyncOutcome

    /**
     * Servidor inalcancavel. Nao e erro: fora da mesh netbird este e o estado
     * esperado. Vale tentar de novo mais tarde.
     */
    data class Unreachable(val reason: String) : SyncOutcome

    /** Endereco base ainda nao configurado. Nao adianta reagendar. */
    data object NotConfigured : SyncOutcome

    /** O servidor respondeu, mas errado (5xx, JSON invalido, ...). */
    data class Failed(val reason: String) : SyncOutcome
}
