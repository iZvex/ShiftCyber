package ru.shiftcyber.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(entities = [ShiftEntity::class, AppSettings::class], version = 1, exportSchema = false)
abstract class ShiftDatabase : RoomDatabase() {
    abstract fun shifts(): ShiftDao
    abstract fun settings(): SettingsDao

    companion object {
        @Volatile private var INSTANCE: ShiftDatabase? = null
        fun get(context: Context): ShiftDatabase = INSTANCE ?: synchronized(this) {
            INSTANCE ?: Room.databaseBuilder(
                context.applicationContext, ShiftDatabase::class.java, "shift-cyber.db"
            ).build().also { INSTANCE = it }
        }
    }
}
