package me.zippert.dialoglite.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(
    entities = [DayEntity::class, PendingEditEntity::class, BalanceEntity::class],
    version = 1,
    exportSchema = true,
)
abstract class DiaLogDatabase : RoomDatabase() {

    abstract fun dao(): DiaLogDao

    companion object {
        fun build(context: Context): DiaLogDatabase =
            Room.databaseBuilder(context, DiaLogDatabase::class.java, "dialoglite.db")
                // O cache de dias e descartavel (vem do servidor); a fila de
                // pendencias nao. Por isso nada de fallbackToDestructiveMigration:
                // migracao futura tem que ser escrita a mao.
                .build()
    }
}
