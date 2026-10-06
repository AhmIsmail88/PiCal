package com.ahmedismail.flowtrack.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.ahmedismail.flowtrack.data.dao.AreaDao
import com.ahmedismail.flowtrack.data.dao.AttendanceDao
import com.ahmedismail.flowtrack.data.dao.ProgressEntryDao
import com.ahmedismail.flowtrack.data.dao.ProjectDao
import com.ahmedismail.flowtrack.data.dao.TaskDao
import com.ahmedismail.flowtrack.data.dao.TeamMemberDao
import com.ahmedismail.flowtrack.data.entity.Area
import com.ahmedismail.flowtrack.data.entity.AttendanceRecord
import com.ahmedismail.flowtrack.data.entity.Project
import com.ahmedismail.flowtrack.data.entity.ProgressEntry
import com.ahmedismail.flowtrack.data.entity.TaskItem
import com.ahmedismail.flowtrack.data.entity.TeamMember

@Database(
    entities = [Project::class, Area::class, TeamMember::class, TaskItem::class, ProgressEntry::class, AttendanceRecord::class],
    version = 6,
    exportSchema = true
)
@TypeConverters(Converters::class)
abstract class AppDatabase : RoomDatabase() {
    abstract fun projectDao(): ProjectDao
    abstract fun areaDao(): AreaDao
    abstract fun teamMemberDao(): TeamMemberDao
    abstract fun taskDao(): TaskDao
    abstract fun progressEntryDao(): ProgressEntryDao
    abstract fun attendanceDao(): AttendanceDao

    companion object {
        /** Repair diameter-inches stored by versions that multiplied millimetres directly. */
        private val MIGRATION_5_6 = object : Migration(5, 6) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("UPDATE progress_entries SET pipeInches = (CASE WHEN unit = 'MM' THEN diameter / 25.4 ELSE diameter END) * jointQuantity")
            }
        }
        @Volatile private var instance: AppDatabase? = null

        /** v1 -> v2: adds the nullable photoUri column for photo attachments per entry. */
        private val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE progress_entries ADD COLUMN photoUri TEXT")
            }
        }

        /** v2 -> v3: adds the nullable customRoleLabel column for the Team Member "Other" role. */
        private val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE team_members ADD COLUMN customRoleLabel TEXT")
            }
        }

        /** v3 -> v4: adds the nullable hoursWorked column, used by the per-employee report. */
        private val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE progress_entries ADD COLUMN hoursWorked REAL")
            }
        }

        /** v4 -> v5: adds the attendance_records table (one row per Team Member per day). */
        private val MIGRATION_4_5 = object : Migration(4, 5) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `attendance_records` (
                        `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        `projectId` INTEGER NOT NULL,
                        `teamMemberId` INTEGER NOT NULL,
                        `date` INTEGER NOT NULL,
                        `status` TEXT NOT NULL,
                        `checkInMillis` INTEGER,
                        `checkOutMillis` INTEGER,
                        FOREIGN KEY(`projectId`) REFERENCES `projects`(`id`) ON DELETE CASCADE,
                        FOREIGN KEY(`teamMemberId`) REFERENCES `team_members`(`id`) ON DELETE CASCADE
                    )
                    """.trimIndent()
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_attendance_records_projectId` ON `attendance_records` (`projectId`)")
                db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_attendance_records_teamMemberId_date` ON `attendance_records` (`teamMemberId`, `date`)")
            }
        }

        // Offline-first (spec §9): everything lives locally, no network required.
        fun get(context: Context): AppDatabase =
            instance ?: synchronized(this) {
                instance ?: Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "flowtrack.db"
                ).addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5, MIGRATION_5_6).build().also { instance = it }
            }
    }
}
