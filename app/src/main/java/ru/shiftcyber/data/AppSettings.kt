package ru.shiftcyber.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "app_settings")
data class AppSettings(
    @PrimaryKey val id: Int = 1,
    val hourlyRate: Double = 15.0,
    val darkTheme: Boolean = true,
    val notifyDayBefore: Boolean = true,
    val notifySameDay: Boolean = true,
    val widgetTransparency: Int = 88,
    val dailyRate: Double = 2500.0,
    val autoGoogleSync: Boolean = false,
    val lastSyncMillis: Long = 0L,
    val googleAccountEmail: String = ""
)
