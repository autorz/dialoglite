package me.zippert.dialoglite.sync

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import me.zippert.dialoglite.DiaLogApp
import me.zippert.dialoglite.data.SyncOutcome

class SyncWorker(
    context: Context,
    params: WorkerParameters,
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val repository = (applicationContext as DiaLogApp).container.repository

        return when (val outcome = repository.sync()) {
            is SyncOutcome.Success ->
                // Datas em `failed` ja foram marcadas na fila (com attempts /
                // blocked). Reagendar o worker inteiro por causa delas so
                // repetiria as que deram certo — o retry mora na fila.
                Result.success()

            // Inalcancavel e o estado NORMAL fora da mesh netbird, nao um erro.
            // Tenta algumas vezes com backoff exponencial (30s, 1m, 2m, 4m ~=
            // 8 min de janela) e para. Insistir alem disso so gasta bateria com
            // o celular longe da mesh: o worker periodico de 6h e a abertura do
            // app cuidam do resto, e a fila continua intacta.
            is SyncOutcome.Unreachable ->
                if (runAttemptCount < MAX_UNREACHABLE_RETRIES) Result.retry() else Result.success()

            is SyncOutcome.Failed -> if (runAttemptCount < MAX_RETRIES) Result.retry() else Result.failure()

            // Sem endereco configurado nao adianta insistir: quando o usuario
            // configurar, o app dispara um sync na hora.
            SyncOutcome.NotConfigured -> Result.failure()
        }
    }

    private companion object {
        const val MAX_RETRIES = 5
        const val MAX_UNREACHABLE_RETRIES = 4
    }
}
