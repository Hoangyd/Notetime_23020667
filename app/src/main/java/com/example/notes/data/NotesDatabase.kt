package com.example.notes.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.room.migration.Migration

@Database(
    entities = [
        NoteEntity::class,
        NoteItemEntity::class,
        TaskEntity::class,
        SubtaskEntity::class,
        EventEntity::class,
        HabitEntity::class,
        HabitLogEntity::class,
        TimeLogEntity::class
    ],
    version = 5,
    exportSchema = true
)
abstract class NotesDatabase : RoomDatabase() {
    abstract fun noteDao(): NoteDao
    abstract fun taskDao(): TaskDao
    abstract fun eventDao(): EventDao
    abstract fun habitDao(): HabitDao
    abstract fun insightsDao(): InsightsDao

    companion object {
        @Volatile
        private var instance: NotesDatabase? = null

        val MIGRATION_1_2: Migration = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE notes ADD COLUMN category TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE notes ADD COLUMN isPinned INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE notes ADD COLUMN isArchived INTEGER NOT NULL DEFAULT 0")
            }
        }

        /** Adds planner tables (tasks, events, habits, time logs, badges) and extends notes. */
        val MIGRATION_2_3: Migration = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE notes ADD COLUMN color INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE notes ADD COLUMN labels TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE notes ADD COLUMN isChecklist INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE notes ADD COLUMN linkedTaskId INTEGER")
                db.execSQL("ALTER TABLE notes ADD COLUMN linkedDate INTEGER")
                db.execSQL("ALTER TABLE notes ADD COLUMN isDraft INTEGER NOT NULL DEFAULT 0")

                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `note_items` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                        "`noteId` INTEGER NOT NULL, `text` TEXT NOT NULL, `isDone` INTEGER NOT NULL, " +
                        "`position` INTEGER NOT NULL, FOREIGN KEY(`noteId`) REFERENCES `notes`(`id`) " +
                        "ON UPDATE NO ACTION ON DELETE CASCADE )"
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_note_items_noteId` ON `note_items` (`noteId`)")

                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `tasks` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                        "`title` TEXT NOT NULL, `description` TEXT NOT NULL, `category` TEXT NOT NULL, " +
                        "`priority` INTEGER NOT NULL, `deadline` INTEGER, `estimatedMinutes` INTEGER, " +
                        "`scheduledStart` INTEGER, `scheduledEnd` INTEGER, `isDone` INTEGER NOT NULL, " +
                        "`completedAt` INTEGER, `createdAt` INTEGER NOT NULL, `updatedAt` INTEGER NOT NULL)"
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_tasks_deadline` ON `tasks` (`deadline`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_tasks_isDone` ON `tasks` (`isDone`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_tasks_scheduledStart` ON `tasks` (`scheduledStart`)")

                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `subtasks` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                        "`taskId` INTEGER NOT NULL, `title` TEXT NOT NULL, `isDone` INTEGER NOT NULL, " +
                        "`position` INTEGER NOT NULL, FOREIGN KEY(`taskId`) REFERENCES `tasks`(`id`) " +
                        "ON UPDATE NO ACTION ON DELETE CASCADE )"
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_subtasks_taskId` ON `subtasks` (`taskId`)")

                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `events` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                        "`title` TEXT NOT NULL, `type` TEXT NOT NULL, `category` TEXT NOT NULL, " +
                        "`color` INTEGER NOT NULL, `startAt` INTEGER NOT NULL, `endAt` INTEGER NOT NULL, " +
                        "`allDay` INTEGER NOT NULL, `location` TEXT NOT NULL, `description` TEXT NOT NULL, " +
                        "`repeatDaysMask` INTEGER NOT NULL, `repeatUntil` INTEGER, " +
                        "`createdAt` INTEGER NOT NULL, `updatedAt` INTEGER NOT NULL)"
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_events_startAt` ON `events` (`startAt`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_events_endAt` ON `events` (`endAt`)")

                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `habits` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                        "`name` TEXT NOT NULL, `category` TEXT NOT NULL, `color` INTEGER NOT NULL, " +
                        "`icon` TEXT NOT NULL, `daysMask` INTEGER NOT NULL, `reminderMinute` INTEGER, " +
                        "`isArchived` INTEGER NOT NULL, `createdAt` INTEGER NOT NULL)"
                )

                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `habit_logs` (`habitId` INTEGER NOT NULL, " +
                        "`epochDay` INTEGER NOT NULL, `completedAt` INTEGER NOT NULL, " +
                        "PRIMARY KEY(`habitId`, `epochDay`), FOREIGN KEY(`habitId`) REFERENCES `habits`(`id`) " +
                        "ON UPDATE NO ACTION ON DELETE CASCADE )"
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_habit_logs_epochDay` ON `habit_logs` (`epochDay`)")

                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `time_logs` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                        "`taskId` INTEGER, `category` TEXT NOT NULL, `startAt` INTEGER NOT NULL, " +
                        "`endAt` INTEGER NOT NULL, `note` TEXT NOT NULL)"
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_time_logs_startAt` ON `time_logs` (`startAt`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_time_logs_taskId` ON `time_logs` (`taskId`)")

                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `achievements` (`key` TEXT NOT NULL, " +
                        "`unlockedAt` INTEGER NOT NULL, PRIMARY KEY(`key`))"
                )
            }
        }

        /** Removes the badges feature: drops the `achievements` table. */
        val MIGRATION_3_4: Migration = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("DROP TABLE IF EXISTS `achievements`")
            }
        }

        /**
         * Removes the unused event `type` column. The table is rebuilt (instead of ALTER ... DROP COLUMN,
         * which older SQLite versions on Android do not support) and every existing event is copied over.
         */
        val MIGRATION_4_5: Migration = object : Migration(4, 5) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `events_new` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                        "`title` TEXT NOT NULL, `category` TEXT NOT NULL, " +
                        "`color` INTEGER NOT NULL, `startAt` INTEGER NOT NULL, `endAt` INTEGER NOT NULL, " +
                        "`allDay` INTEGER NOT NULL, `location` TEXT NOT NULL, `description` TEXT NOT NULL, " +
                        "`repeatDaysMask` INTEGER NOT NULL, `repeatUntil` INTEGER, " +
                        "`createdAt` INTEGER NOT NULL, `updatedAt` INTEGER NOT NULL)"
                )
                db.execSQL(
                    "INSERT INTO `events_new` (`id`, `title`, `category`, `color`, `startAt`, `endAt`, `allDay`, " +
                        "`location`, `description`, `repeatDaysMask`, `repeatUntil`, `createdAt`, `updatedAt`) " +
                        "SELECT `id`, `title`, `category`, `color`, `startAt`, `endAt`, `allDay`, " +
                        "`location`, `description`, `repeatDaysMask`, `repeatUntil`, `createdAt`, `updatedAt` " +
                        "FROM `events`"
                )
                db.execSQL("DROP TABLE `events`")
                db.execSQL("ALTER TABLE `events_new` RENAME TO `events`")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_events_startAt` ON `events` (`startAt`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_events_endAt` ON `events` (`endAt`)")
            }
        }

        fun getDatabase(context: Context): NotesDatabase {
            return instance ?: synchronized(this) {
                val database = Room.databaseBuilder(
                    context.applicationContext,
                    NotesDatabase::class.java,
                    "notes.db"
                )
                    .addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5)
                    .build()
                instance = database
                database
            }
        }
    }
}
