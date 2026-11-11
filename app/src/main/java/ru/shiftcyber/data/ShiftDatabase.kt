package ru.shiftcyber.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [ShiftEntity::class, AppSettings::class],
    version = 2,
    exportSchema = false
)
abstract class ShiftDatabase : RoomDatabase() {
    abstract fun shifts(): ShiftDao
    abstract fun settings(): SettingsDao

    companion object {
        val MIGRATION_1_2: Migration = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE shifts ADD COLUMN payType TEXT NOT NULL DEFAULT 'HOURLY'")
                db.execSQL("ALTER TABLE shifts ADD COLUMN dayRate REAL NOT NULL DEFAULT 0.0")
                db.execSQL("ALTER TABLE shifts ADD COLUMN extrasJson TEXT NOT NULL DEFAULT '[]'")

                db.execSQL("ALTER TABLE app_settings ADD COLUMN dailyRate REAL NOT NULL DEFAULT 2500.0")
                db.execSQL("ALTER TABLE app_settings ADD COLUMN autoGoogleSync INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE app_settings ADD COLUMN lastSyncMillis INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE app_settings ADD COLUMN googleAccountEmail TEXT NOT NULL DEFAULT ''")
            }
        }

        @Volatile
        private var INSTANCE: ShiftDatabase? = null

        fun get(context: Context): ShiftDatabase = INSTANCE ?: synchronized(this) {
            INSTANCE ?: Room.databaseBuilder(
                context.applicationContext,
                ShiftDatabase::class.java,
                "shift-cyber.db"
            )
                .addMigrations(MIGRATION_1_2)
                .build()
                .also { INSTANCE = it }
        }
    }
}
