package me.zippert.dialoglite.ui.days

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import me.zippert.dialoglite.DiaLogApp
import me.zippert.dialoglite.data.DayUi
import me.zippert.dialoglite.data.SyncStatus
import me.zippert.dialoglite.data.local.PeriodValue
import me.zippert.dialoglite.sync.SyncScheduler

data class DaysUiState(
    val baseUrl: String? = null,
    val balancePretty: String? = null,
    val days: List<DayUi> = emptyList(),
    val pendingCount: Int = 0,
    val blockedCount: Int = 0,
    val sync: SyncStatus = SyncStatus(),
) {
    val needsSetup: Boolean get() = baseUrl.isNullOrBlank()

    /**
     * Com edicao na fila, os numeros na tela sao os do servidor de antes da
     * edicao. A UI precisa dizer isso — o saldo NAO e recalculado localmente.
     */
    val balanceIsStale: Boolean get() = pendingCount > 0 || blockedCount > 0
}

class DaysViewModel(app: Application) : AndroidViewModel(app) {

    private val repository = (app as DiaLogApp).container.repository

    val state: StateFlow<DaysUiState> = combine(
        repository.baseUrl,
        repository.balancePretty,
        repository.days,
        repository.pendingCount,
        repository.blockedCount,
        repository.status,
    ) { values ->
        @Suppress("UNCHECKED_CAST")
        DaysUiState(
            baseUrl = values[0] as String?,
            balancePretty = values[1] as String?,
            days = values[2] as List<DayUi>,
            pendingCount = values[3] as Int,
            blockedCount = values[4] as Int,
            sync = values[5] as SyncStatus,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), DaysUiState())

    fun refresh() = SyncScheduler.syncNow(getApplication())

    fun saveEdit(date: String, notes: String?, periods: List<PeriodValue>?) {
        viewModelScope.launch {
            repository.queueEdit(date, notes, periods)
            SyncScheduler.syncNow(getApplication())
        }
    }

    fun discardEdit(date: String) {
        viewModelScope.launch { repository.discardEdit(date) }
    }

    fun saveBaseUrl(value: String) {
        viewModelScope.launch {
            repository.setBaseUrl(value)
            SyncScheduler.syncNow(getApplication())
        }
    }
}
