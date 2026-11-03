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

@Database(entities = [Task::class, PersonNote::class, PersonField::class], version = 2, exportSchema = false)
abstract class AppDatabase : RoomDatabase() {
    abstract fun tasks(): TaskDao
    abstract fun people(): PersonDao

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

        @Volatile private var instance: AppDatabase? = null

        fun get(context: Context): AppDatabase = instance ?: synchronized(this) {
            instance ?: Room.databaseBuilder(context.applicationContext, AppDatabase::class.java, "calendario.db")
                .addMigrations(MIGRATION_1_2)
                .build().also { instance = it }
        }
    }
}
