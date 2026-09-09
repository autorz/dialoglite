package me.zippert.dialoglite.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import kotlinx.coroutines.flow.Flow

@Dao
interface DiaLogDao {

    @Query("SELECT * FROM days ORDER BY date DESC")
    fun observeDays(): Flow<List<DayEntity>>

    @Query("SELECT * FROM days WHERE date = :date")
    suspend fun getDay(date: String): DayEntity?

    @Query("SELECT * FROM balance WHERE id = 0")
    fun observeBalance(): Flow<BalanceEntity?>

    @Query("SELECT * FROM pending_edits ORDER BY date ASC")
    fun observePending(): Flow<List<PendingEditEntity>>

    @Query("SELECT * FROM pending_edits WHERE date = :date")
    suspend fun getPending(date: String): PendingEditEntity?

    @Query("SELECT * FROM pending_edits WHERE blocked = 0 ORDER BY date ASC")
    suspend fun pendingToSend(): List<PendingEditEntity>

    @Query("SELECT COUNT(*) FROM pending_edits WHERE blocked = 0")
    suspend fun pendingCount(): Int

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertDays(days: List<DayEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertBalance(balance: BalanceEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertPending(edit: PendingEditEntity)

    @Query("DELETE FROM pending_edits WHERE date = :date")
    suspend fun deletePending(date: String)

    @Query("DELETE FROM days WHERE date NOT IN (:keep)")
    suspend fun deleteDaysNotIn(keep: List<String>)

    @Query("UPDATE pending_edits SET attempts = :attempts, lastError = :error, blocked = :blocked WHERE date = :date")
    suspend fun markPendingFailure(date: String, attempts: Int, error: String?, blocked: Boolean)

    /**
     * Substitui o cache pelo que o servidor devolveu. A fila de pendencias fica
     * em outra tabela justamente pra nao ser atropelada por um refresh.
     */
    @Transaction
    suspend fun replaceHistory(days: List<DayEntity>, balance: BalanceEntity) {
        upsertDays(days)
        if (days.isNotEmpty()) {
            deleteDaysNotIn(days.map { it.date })
        }
        upsertBalance(balance)
    }
}
