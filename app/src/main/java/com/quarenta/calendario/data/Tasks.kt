package com.quarenta.calendario.data

import android.content.Context
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.Update
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import kotlinx.coroutines.flow.Flow

@Entity(tableName = "tasks")
data class Task(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val title: String,
    val done: Boolean = false,
    /** Due day as epoch day (LocalDate.toEpochDay), or null for "no date". */
    val dueEpochDay: Long? = null,
    val note: String? = null,
    val createdAt: Long = System.currentTimeMillis(),
)

@Dao
interface TaskDao {
    @Query("SELECT * FROM tasks ORDER BY done ASC, dueEpochDay IS NULL, dueEpochDay ASC, createdAt ASC")
    fun observeAll(): Flow<List<Task>>

    @Insert
    suspend fun insert(task: Task): Long

    @Update
    suspend fun update(task: Task)

    @Query("DELETE FROM tasks WHERE id = :id")
    suspend fun delete(id: Long)
}

/**
 * The plan for one occurrence of an event, kept in this app: where the party is, at what time, notes.
 * Works for read-only events too (contact birthdays), because nothing is written to the calendar.
 * [key] is "eventId@yyyy-mm-dd", so a yearly birthday gets a fresh plan every year.
 */
@Entity(tableName = "event_plans")
data class EventPlan(
    @PrimaryKey val key: String,
    val place: String = "",
    val time: String = "",
    val notes: String = "",
    val updatedAt: Long = System.currentTimeMillis(),
)

@Dao
interface EventPlanDao {
    @Query("SELECT * FROM event_plans WHERE `key` = :key")
    fun observe(key: String): Flow<EventPlan?>

    @Insert(onConflict = androidx.room.OnConflictStrategy.REPLACE)
    suspend fun save(plan: EventPlan)
}

@Database(entities = [Task::class, PersonNote::class, PersonField::class, EventPlan::class], version = 3, exportSchema = false)
abstract class AppDatabase : RoomDatabase() {
    abstract fun tasks(): TaskDao
    abstract fun people(): PersonDao
    abstract fun plans(): EventPlanDao

    companion object {
        /** v2 adds notes and custom fields for people; existing tasks are kept. */
        private val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `person_notes` (`lookupKey` TEXT NOT NULL, `notes` TEXT NOT NULL, " +
                        "`updatedAt` INTEGER NOT NULL, PRIMARY KEY(`lookupKey`))",
                )
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `person_fields` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                        "`lookupKey` TEXT NOT NULL, `label` TEXT NOT NULL, `value` TEXT NOT NULL)",
                )
            }
        }

        /** v3 adds plans for events (party place, time, notes); everything else is kept. */
        private val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `event_plans` (`key` TEXT NOT NULL, `place` TEXT NOT NULL, " +
                        "`time` TEXT NOT NULL, `notes` TEXT NOT NULL, `updatedAt` INTEGER NOT NULL, PRIMARY KEY(`key`))",
                )
            }
        }

        @Volatile private var instance: AppDatabase? = null

        fun get(context: Context): AppDatabase = instance ?: synchronized(this) {
            instance ?: Room.databaseBuilder(context.applicationContext, AppDatabase::class.java, "calendario.db")
                .addMigrations(MIGRATION_1_2, MIGRATION_2_3)
                .build().also { instance = it }
        }
    }
}
