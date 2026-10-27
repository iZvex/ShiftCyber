package ru.shiftcyber.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "shifts")
data class ShiftEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val startMillis: Long,
    val endMillis: Long,
    val title: String = "Рабочая смена",
    val hourlyRate: Double = 15.0,
    val breakMinutes: Int = 0,
    val colorKey: String = "violet",
    val isManual: Boolean = false
)
