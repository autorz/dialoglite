package me.zippert.dialoglite.sync

import android.content.Context
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkInfo
import androidx.work.WorkManager
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.util.concurrent.TimeUnit

object SyncScheduler {

    private const val PERIODIC_WORK = "dialoglite-sync-periodic"
    private const val ONE_SHOT_WORK = "dialoglite-sync-now"

    /**
     * `NetworkType.CONNECTED` e o maximo que da pra pedir: a alcancabilidade
     * real depende da mesh netbird estar de pe, e isso o WorkManager nao ve.
     * Ter rede e condicao necessaria, nao suficiente — o resto e retry.
     */
    private val constraints = Constraints.Builder()
        .setRequiredNetworkType(NetworkType.CONNECTED)
        .build()

    fun schedulePeriodic(context: Context) {
        val request = PeriodicWorkRequestBuilder<SyncWorker>(6, TimeUnit.HOURS)
            .setConstraints(constraints)
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 5, TimeUnit.MINUTES)
            .build()

        WorkManager.getInstance(context).enqueueUniquePeriodicWork(
            PERIODIC_WORK,
            ExistingPeriodicWorkPolicy.KEEP,
            request,
        )
    }

    /** Dispara agora: abertura do app, pull-to-refresh, ou edicao salva. */
    fun syncNow(context: Context) {
        val request = OneTimeWorkRequestBuilder<SyncWorker>()
            .setConstraints(constraints)
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.SECONDS)
            .build()

        WorkManager.getInstance(context).enqueueUniqueWork(
            ONE_SHOT_WORK,
            // REPLACE e nao KEEP: a edicao mais recente tem que entrar na
            // rodada, mesmo que ja exista um envio enfileirado.
            ExistingWorkPolicy.REPLACE,
            request,
        )
    }

    fun observeRunning(context: Context): Flow<Boolean> =
        WorkManager.getInstance(context)
            .getWorkInfosForUniqueWorkFlow(ONE_SHOT_WORK)
            .map { infos -> infos.any { it.state == WorkInfo.State.RUNNING || it.state == WorkInfo.State.ENQUEUED } }
}
