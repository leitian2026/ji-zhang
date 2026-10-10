package com.shopledger.app

import android.content.Context
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Delete
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.Insert
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.Transaction
import androidx.room.TypeConverter
import androidx.room.TypeConverters
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.room.Update
import kotlinx.coroutines.flow.Flow
import java.math.BigDecimal

/** 一趟（一次超市购物）。endedAt 为空表示还没结束。 */
@Entity(tableName = "trips")
data class Trip(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val startedAt: Long,
    val endedAt: Long? = null,
    val name: String = ""
)

/** 一笔账。 */
@Entity(
    tableName = "entries",
    foreignKeys = [
        ForeignKey(
            entity = Trip::class,
            parentColumns = ["id"],
            childColumns = ["tripId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index("tripId")]
)
data class Entry(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val tripId: Long,
    val name: String,
    val unitPrice: BigDecimal,
    val quantity: BigDecimal,
    val createdAt: Long,
    /** 数量后面的单位，比如 斤、把、个；可以为空 */
    val unit: String = ""
)

class Converters {
    @TypeConverter
    fun bigToString(value: BigDecimal): String = value.toPlainString()

    @TypeConverter
    fun stringToBig(value: String): BigDecimal = BigDecimal(value)
}

@Dao
abstract class LedgerDao {
    @Query("SELECT * FROM trips ORDER BY id")
    abstract fun trips(): Flow<List<Trip>>

    @Query("SELECT * FROM entries ORDER BY id")
    abstract fun entries(): Flow<List<Entry>>

    @Query("SELECT * FROM trips WHERE endedAt IS NULL ORDER BY id DESC LIMIT 1")
    abstract suspend fun activeTrip(): Trip?

    @Query("SELECT COUNT(*) FROM entries WHERE tripId = :tripId")
    abstract suspend fun countEntries(tripId: Long): Int

    @Query("SELECT MAX(createdAt) FROM entries WHERE tripId = :tripId")
    abstract suspend fun lastEntryAt(tripId: Long): Long?

    @Insert
    abstract suspend fun insertTrip(trip: Trip): Long

    @Insert
    abstract suspend fun insertEntries(list: List<Entry>)

    @Update
    abstract suspend fun updateEntry(entry: Entry)

    @Delete
    abstract suspend fun deleteEntry(entry: Entry)

    @Query("UPDATE trips SET endedAt = :endedAt, name = :name WHERE id = :id")
    abstract suspend fun finishTrip(id: Long, endedAt: Long, name: String)

    @Query("UPDATE trips SET endedAt = NULL WHERE id = :id")
    abstract suspend fun clearEnd(id: Long)

    @Query("DELETE FROM trips WHERE id = :id")
    abstract suspend fun deleteTrip(id: Long)

    @Query("DELETE FROM entries WHERE tripId IN (:ids)")
    abstract suspend fun deleteEntriesOfTrips(ids: List<Long>)

    @Query("DELETE FROM trips WHERE id IN (:ids)")
    abstract suspend fun deleteTripsByIds(ids: List<Long>)

    /** 删除整张表格（一趟），里面的记录一起删掉。 */
    @Transaction
    open suspend fun deleteTrips(ids: List<Long>) {
        if (ids.isEmpty()) return
        deleteEntriesOfTrips(ids)
        deleteTripsByIds(ids)
    }

    /** 记一笔（或多笔）。没有进行中的一趟时自动开一趟。整个过程是一个事务。 */
    @Transaction
    open suspend fun addEntries(items: List<Parsed>, now: Long) {
        val active = activeTrip()
        val tripId = active?.id ?: insertTrip(Trip(startedAt = now))
        insertEntries(
            items.map {
                Entry(
                    tripId = tripId,
                    name = it.name,
                    unitPrice = it.price,
                    quantity = it.qty,
                    createdAt = now,
                    unit = it.unit
                )
            }
        )
    }

    /** 结束进行中的一趟。没有记录就不结束，返回 false。 */
    @Transaction
    open suspend fun endActive(now: Long, name: String): Boolean {
        val trip = activeTrip() ?: return false
        if (countEntries(trip.id) == 0) return false
        finishTrip(trip.id, now, name.trim())
        return true
    }

    /** 重新打开已结束的一趟。已经有带记录的进行中一趟时不处理。 */
    @Transaction
    open suspend fun reopen(id: Long) {
        val active = activeTrip()
        if (active != null) {
            if (countEntries(active.id) > 0) return
            deleteTrip(active.id)
        }
        clearEnd(id)
    }
}

/** 1 → 2：每一笔多一个"单位"（斤、把……） */
val MIGRATION_1_2 = object : Migration(1, 2) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE entries ADD COLUMN unit TEXT NOT NULL DEFAULT ''")
    }
}

@Database(entities = [Trip::class, Entry::class], version = 2, exportSchema = false)
@TypeConverters(Converters::class)
abstract class LedgerDb : RoomDatabase() {
    abstract fun dao(): LedgerDao

    companion object {
        @Volatile
        private var instance: LedgerDb? = null

        fun get(context: Context): LedgerDb =
            instance ?: synchronized(this) {
                instance ?: Room.databaseBuilder(
                    context.applicationContext,
                    LedgerDb::class.java,
                    "ledger.db"
                ).addMigrations(MIGRATION_1_2).build().also { instance = it }
            }
    }
}
