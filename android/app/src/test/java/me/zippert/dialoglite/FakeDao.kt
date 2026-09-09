package me.zippert.dialoglite

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import me.zippert.dialoglite.data.local.BalanceEntity
import me.zippert.dialoglite.data.local.DayEntity
import me.zippert.dialoglite.data.local.DiaLogDao
import me.zippert.dialoglite.data.local.PendingEditEntity
import me.zippert.dialoglite.data.prefs.PreferencesSource

/** DAO em memoria: o teste alvo e a sequencia de sync, nao o Room. */
class FakeDao : DiaLogDao {

    val days = MutableStateFlow<List<DayEntity>>(emptyList())
    val pending = MutableStateFlow<List<PendingEditEntity>>(emptyList())
    val balance = MutableStateFlow<BalanceEntity?>(null)

    override fun observeDays(): Flow<List<DayEntity>> = days
    override fun observeBalance(): Flow<BalanceEntity?> = balance
    override fun observePending(): Flow<List<PendingEditEntity>> = pending

    override suspend fun getDay(date: String): DayEntity? = days.value.firstOrNull { it.date == date }
    override suspend fun getPending(date: String): PendingEditEntity? = pending.value.firstOrNull { it.date == date }
    override suspend fun pendingToSend(): List<PendingEditEntity> = pending.value.filter { !it.blocked }.sortedBy { it.date }
    override suspend fun pendingCount(): Int = pending.value.count { !it.blocked }

    override suspend fun upsertDays(days: List<DayEntity>) {
        val byDate = this.days.value.associateBy { it.date }.toMutableMap()
        days.forEach { byDate[it.date] = it }
        this.days.value = byDate.values.sortedByDescending { it.date }
    }

    override suspend fun upsertBalance(balance: BalanceEntity) {
        this.balance.value = balance
    }

    override suspend fun upsertPending(edit: PendingEditEntity) {
        pending.value = pending.value.filterNot { it.date == edit.date } + edit
    }

    override suspend fun deletePending(date: String) {
        pending.value = pending.value.filterNot { it.date == date }
    }

    override suspend fun deleteDaysNotIn(keep: List<String>) {
        days.value = days.value.filter { it.date in keep }
    }

    override suspend fun deletePendingIfUnchanged(date: String, updatedAt: Long) {
        pending.value = pending.value.filterNot { it.date == date && it.updatedAt == updatedAt }
    }

    override suspend fun markPendingFailure(
        date: String,
        updatedAt: Long,
        attempts: Int,
        error: String?,
        blocked: Boolean,
    ) {
        pending.value = pending.value.map {
            if (it.date == date && it.updatedAt == updatedAt) {
                it.copy(attempts = attempts, lastError = error, blocked = blocked)
            } else {
                it
            }
        }
    }
}

class FakePreferences(initial: String?) : PreferencesSource {
    private val state = MutableStateFlow(initial)
    override val baseUrl: Flow<String?> = state
    override suspend fun setBaseUrl(value: String) { state.value = value }
}
