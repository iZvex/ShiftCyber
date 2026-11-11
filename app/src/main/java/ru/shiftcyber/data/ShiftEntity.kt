package ru.shiftcyber.data

import androidx.room.Entity
import androidx.room.PrimaryKey

object ShiftPayType {
    const val HOURLY = "HOURLY"
    const val DAILY = "DAILY"
}

data class ShiftExtraItem(
    val comment: String,
    val quantity: Double,
    val unitPrice: Double
) {
    val total: Double
        get() = quantity.coerceAtLeast(0.0) * unitPrice.coerceAtLeast(0.0)
}

@Entity(tableName = "shifts")
data class ShiftEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val startMillis: Long,
    val endMillis: Long,
    val title: String = "Рабочая смена",
    val hourlyRate: Double = 15.0,
    val breakMinutes: Int = 0,
    val colorKey: String = "violet",
    val isManual: Boolean = false,
    val payType: String = ShiftPayType.HOURLY,
    val dayRate: Double = 0.0,
    val extrasJson: String = "[]"
) {
    val isDaily: Boolean
        get() = payType == ShiftPayType.DAILY
}
